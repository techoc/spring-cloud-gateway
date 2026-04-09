/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Collections.singletonList;
import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * 设置响应状态码过滤器工厂。
 * <p>
 * 该过滤器用于修改响应的 HTTP 状态码。 支持将非标准状态码转换为标准状态码，或调整下游服务的状态码。
 * <p>
 * 配置参数：
 * <ul>
 * <li>status：目标 HTTP 状态码（如 200、404、NOT_FOUND）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - SetStatus=UNAUTHORIZED
 *   - SetStatus=401
 * </pre>
 *
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.gateway.set-status")
public class SetStatusGatewayFilterFactory extends AbstractGatewayFilterFactory<SetStatusGatewayFilterFactory.Config> {

	/**
	 * 状态码参数键名。
	 */
	public static final String STATUS_KEY = "status";

	/**
	 * 包含原始状态码的响应头名称。
	 */
	private String originalStatusHeaderName;

	/**
	 * 默认构造方法。
	 */
	public SetStatusGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STATUS_KEY);
	}

	/**
	 * 创建设置状态码过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		HttpStatusHolder statusHolder = HttpStatusHolder.parse(config.status);

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 在过滤器链执行后设置状态码
				return chain.filter(exchange).then(Mono.fromRunnable(() -> {
					// 获取当前状态码
					HttpStatus statusCode = exchange.getResponse().getStatusCode();
					// 设置新的状态码
					boolean isStatusCodeUpdated = setResponseStatus(exchange, statusHolder);
					// 若状态码被更新且配置了原始状态码头，则添加原始状态码头
					if (isStatusCodeUpdated && originalStatusHeaderName != null) {
						exchange.getResponse().getHeaders().set(originalStatusHeaderName,
								singletonList(statusCode.value()).toString());
					}
				}));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SetStatusGatewayFilterFactory.this).append("status", config.getStatus())
						.toString();
			}
		};
	}

	/**
	 * 获取原始状态码头名称。
	 * @return 原始状态码头名称
	 */
	public String getOriginalStatusHeaderName() {
		return originalStatusHeaderName;
	}

	/**
	 * 设置原始状态码头名称。
	 * @param originalStatusHeaderName 原始状态码头名称
	 */
	public void setOriginalStatusHeaderName(String originalStatusHeaderName) {
		this.originalStatusHeaderName = originalStatusHeaderName;
	}

	/**
	 * 设置状态码过滤器配置类。
	 */
	public static class Config {

		// TODO: 使用宽松的 HttpStatus 转换器
		/** HTTP 状态码字符串 */
		private String status;

		/**
		 * 获取状态码。
		 * @return 状态码字符串
		 */
		public String getStatus() {
			return status;
		}

		/**
		 * 设置状态码。
		 * @param status 状态码字符串
		 */
		public void setStatus(String status) {
			this.status = status;
		}

	}

}
