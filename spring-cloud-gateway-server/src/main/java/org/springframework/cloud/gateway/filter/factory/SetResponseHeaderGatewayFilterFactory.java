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
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 设置响应头过滤器工厂。
 * <p>
 * 该过滤器在响应返回客户端之前，设置指定的响应头。 与 {@link AddResponseHeaderGatewayFilterFactory}
 * 不同，该过滤器会覆盖已存在的同名响应头。 支持 SpEL 表达式动态取值。
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - SetResponseHeader=X-Custom-Header, custom-value
 * </pre>
 *
 * @author Spencer Gibb
 */
public class SetResponseHeaderGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	/**
	 * 创建设置响应头过滤器。
	 * @param config 名称-值配置对象
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 解析 SpEL 表达式
				String value = ServerWebExchangeUtils.expand(exchange, config.getValue());
				// 在过滤器链执行完成后设置响应头（覆盖已有值）
				return chain.filter(exchange)
						.then(Mono.fromRunnable(() -> exchange.getResponse().getHeaders().set(config.name, value)));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SetResponseHeaderGatewayFilterFactory.this)
						.append(config.getName(), config.getValue()).toString();
			}
		};
	}

}
