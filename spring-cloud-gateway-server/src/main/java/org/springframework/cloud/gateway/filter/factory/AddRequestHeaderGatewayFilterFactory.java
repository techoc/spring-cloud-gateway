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

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 添加请求头过滤器工厂。
 * <p>
 * 该过滤器在转发请求到下游服务之前，向请求添加指定的 HTTP 头部。 支持 SpEL 表达式动态取值，通过
 * {@link ServerWebExchangeUtils#expand} 方法解析。
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - AddRequestHeader=X-Custom-Header, custom-value
 *   - AddRequestHeader=X-Request-ID, #{T(java.util.UUID).randomUUID()}
 * </pre>
 *
 * @author Spencer Gibb
 */
public class AddRequestHeaderGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	/**
	 * 创建添加请求头过滤器。
	 * <p>
	 * 过滤器会将配置的头名称和值添加到请求头中，支持动态表达式解析。 使用 exchange.mutate() 创建新的请求对象，不修改原始请求。
	 * @param config 名称-值配置对象
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 解析值中的 SpEL 表达式
				String value = ServerWebExchangeUtils.expand(exchange, config.getValue());
				// 创建新的请求对象，添加指定的头部
				ServerHttpRequest request = exchange.getRequest().mutate()
						.headers(httpHeaders -> httpHeaders.add(config.getName(), value)).build();

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(AddRequestHeaderGatewayFilterFactory.this)
						.append(config.getName(), config.getValue()).toString();
			}
		};
	}

}
