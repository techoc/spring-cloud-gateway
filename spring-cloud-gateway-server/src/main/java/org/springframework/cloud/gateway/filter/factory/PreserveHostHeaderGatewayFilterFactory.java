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
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;

/**
 * 保留 HOST 头过滤器工厂。
 * <p>
 * 该过滤器通过设置属性标记，指示后续的过滤器（如 NettyRoutingFilter）保留原始请求的 HOST 头， 而非使用目标服务的主机信息覆盖。
 * <p>
 * 默认情况下，网关会将 HOST 头替换为下游服务的主机名。当需要保留原始 HOST 头 （如用于基于域名的路由）时，使用此过滤器。
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - PreserveHostHeader
 * </pre>
 *
 * @author Spencer Gibb
 */
public class PreserveHostHeaderGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

	/**
	 * 创建保留 HOST 头过滤器（无参数版本）。
	 * @return 网关过滤器实例
	 */
	public GatewayFilter apply() {
		return apply(o -> {
		});
	}

	/**
	 * 创建保留 HOST 头过滤器。
	 * @param config 配置对象（此过滤器无需配置）
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Object config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 设置属性标记，指示后续过滤器保留原始 HOST 头
				exchange.getAttributes().put(PRESERVE_HOST_HEADER_ATTRIBUTE, true);
				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(PreserveHostHeaderGatewayFilterFactory.this).toString();
			}
		};
	}

}
