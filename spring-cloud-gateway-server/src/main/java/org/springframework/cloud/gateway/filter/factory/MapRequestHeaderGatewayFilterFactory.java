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

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 映射请求头过滤器工厂。
 * <p>
 * 该过滤器将一个请求头的值复制（或添加）到另一个请求头。 如果源请求头不存在，则不进行任何操作。
 * <p>
 * 配置参数：
 * <ul>
 * <li>fromHeader：源请求头名称（必填）</li>
 * <li>toHeader：目标请求头名称（必填）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - MapRequestHeader=X-Original-Header, X-New-Header
 * </pre>
 *
 * @author Tony Clarke
 */
public class MapRequestHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<MapRequestHeaderGatewayFilterFactory.Config> {

	/**
	 * 源请求头参数键名。
	 */
	public static final String FROM_HEADER_KEY = "fromHeader";

	/**
	 * 目标请求头参数键名。
	 */
	public static final String TO_HEADER_KEY = "toHeader";

	/**
	 * 默认构造方法。
	 */
	public MapRequestHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(FROM_HEADER_KEY, TO_HEADER_KEY);
	}

	/**
	 * 创建映射请求头过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(MapRequestHeaderGatewayFilterFactory.Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 若源请求头不存在，直接继续过滤链
				if (!exchange.getRequest().getHeaders().containsKey(config.getFromHeader())) {
					return chain.filter(exchange);
				}
				// 获取源请求头的所有值
				List<String> headerValues = exchange.getRequest().getHeaders().get(config.getFromHeader());

				// 创建新请求，添加目标请求头
				ServerHttpRequest request = exchange.getRequest().mutate()
						.headers(i -> i.addAll(config.getToHeader(), headerValues)).build();

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				// @formatter:off
				return filterToStringCreator(MapRequestHeaderGatewayFilterFactory.this)
						.append(FROM_HEADER_KEY, config.getFromHeader())
						.append(TO_HEADER_KEY, config.getToHeader())
						.toString();
				// @formatter:on
			}
		};
	}

	/**
	 * 映射请求头过滤器配置类。
	 */
	public static class Config {

		/** 源请求头名称 */
		private String fromHeader;

		/** 目标请求头名称 */
		private String toHeader;

		/**
		 * 获取源请求头名称。
		 * @return 源请求头名称
		 */
		public String getFromHeader() {
			return this.fromHeader;
		}

		/**
		 * 设置源请求头名称。
		 * @param fromHeader 源请求头名称
		 * @return 自身，用于链式调用
		 */
		public Config setFromHeader(String fromHeader) {
			this.fromHeader = fromHeader;
			return this;
		}

		/**
		 * 获取目标请求头名称。
		 * @return 目标请求头名称
		 */
		public String getToHeader() {
			return this.toHeader;
		}

		/**
		 * 设置目标请求头名称。
		 * @param toHeader 目标请求头名称
		 * @return 自身，用于链式调用
		 */
		public Config setToHeader(String toHeader) {
			this.toHeader = toHeader;
			return this;
		}

		/**
		 * 返回配置对象的字符串表示。
		 * @return 字符串表示
		 */
		@Override
		public String toString() {
			// @formatter:off
			return new ToStringCreator(this)
					.append("fromHeader", fromHeader)
					.append("toHeader", toHeader)
					.toString();
			// @formatter:on
		}

	}

}
