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

package org.springframework.cloud.gateway.filter;

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;

/**
 * Forward 协议路径过滤器。
 * <p>
 * 当路由 URI 使用 {@code forward} 协议（如 {@code forward:/path}）时， 该过滤器负责将请求 URI 的路径部分替换为路由 URI
 * 中指定的路径， 以便后续的 {@link ForwardRoutingFilter} 能将请求正确转发到本地服务端点。
 * <p>
 * 执行顺序为 0，在大多数路由过滤器之前执行，但仅对 {@code forward} 协议有效。
 *
 * @author Ryan Baxter
 */
public class ForwardPathFilter implements GlobalFilter, Ordered {

	/**
	 * 过滤请求，若路由使用 {@code forward} 协议，则将请求路径更新为路由 URI 中的路径。
	 * <p>
	 * 若请求已被路由处理（{@code isAlreadyRouted} 为 true）或路由协议不是 {@code forward}，
	 * 则直接将请求传递给过滤器链的下一个过滤器。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		URI routeUri = route.getUri();
		String scheme = routeUri.getScheme();
		if (isAlreadyRouted(exchange) || !"forward".equals(scheme)) {
			return chain.filter(exchange);
		}
		exchange = exchange.mutate().request(exchange.getRequest().mutate().path(routeUri.getPath()).build()).build();
		return chain.filter(exchange);
	}

	/**
	 * 返回过滤器执行顺序。
	 * @return 执行顺序，值为 0
	 */
	@Override
	public int getOrder() {
		return 0;
	}

}
