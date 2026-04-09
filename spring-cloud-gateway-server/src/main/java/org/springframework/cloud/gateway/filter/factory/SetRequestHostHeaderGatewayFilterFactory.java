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

import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;

/**
 * 设置请求 Host 头过滤器工厂。
 * <p>
 * 该过滤器在转发请求前，设置指定的 HOST 头值。 支持 SpEL 表达式动态取值，通过 {@link ServerWebExchangeUtils#expand}
 * 方法解析。 设置后会保留该 HOST 头不被后续过滤器覆盖。
 * <p>
 * 配置参数：
 * <ul>
 * <li>host：HOST 头的值（支持 SpEL 表达式）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - SetRequestHostHeader=example.com
 * </pre>
 *
 * @author Andrew Fitzgerald
 */
public class SetRequestHostHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<SetRequestHostHeaderGatewayFilterFactory.Config> {

	/**
	 * 默认构造方法。
	 */
	public SetRequestHostHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("host");
	}

	/**
	 * 创建设置请求 Host 头过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 解析 SpEL 表达式
				String value = ServerWebExchangeUtils.expand(exchange, config.getHost());

				// 设置 HOST 头（先移除再添加，确保在头部最前面）
				ServerHttpRequest request = exchange.getRequest().mutate().headers(httpHeaders -> {
					httpHeaders.remove("Host");
					httpHeaders.add("Host", value);
				}).build();

				// 确保刚设置的 HOST 头被保留
				exchange.getAttributes().put(PRESERVE_HOST_HEADER_ATTRIBUTE, true);

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(SetRequestHostHeaderGatewayFilterFactory.this).append(config.getHost())
						.toString();
			}
		};
	}

	/**
	 * 设置请求 Host 头过滤器配置类。
	 */
	public static class Config {

		/** HOST 头值 */
		private String host;

		/**
		 * 获取 HOST 头值。
		 * @return HOST 头值
		 */
		public String getHost() {
			return host;
		}

		/**
		 * 设置 HOST 头值。
		 * @param host HOST 头值
		 */
		public void setHost(String host) {
			this.host = host;
		}

	}

}
