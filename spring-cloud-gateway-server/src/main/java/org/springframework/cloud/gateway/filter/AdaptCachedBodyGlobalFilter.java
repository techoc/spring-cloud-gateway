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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.event.EnableBodyCachingEvent;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 适配缓存请求体的全局过滤器。
 * <p>
 * 该过滤器监听 {@link EnableBodyCachingEvent} 事件，当某条路由需要缓存请求体时， 将对应路由 ID
 * 记录到内部映射表中。在处理请求时，若检测到请求体已被缓存（例如在谓词 中已读取并缓存了请求体），则将缓存的请求体重新装配到请求对象中，确保后续过滤器
 * 和路由处理器仍能正常读取请求体。
 * <p>
 * 执行顺序为 {@link Ordered#HIGHEST_PRECEDENCE} + 1000，确保在大多数其他过滤器之前执行。
 * <p>
 * 注意：该过滤器解决了 ServerWebExchange 不可变性导致的请求体无法重复读取的问题。
 */
public class AdaptCachedBodyGlobalFilter implements GlobalFilter, Ordered, ApplicationListener<EnableBodyCachingEvent> {

	/**
	 * 需要缓存请求体的路由 ID 集合（线程安全）。 key 为路由 ID，value 固定为 true。
	 */
	private ConcurrentMap<String, Boolean> routesToCache = new ConcurrentHashMap<>();

	/**
	 * 监听 {@link EnableBodyCachingEvent} 事件，将需要缓存请求体的路由 ID 加入映射表。
	 * @param event 包含需要启用请求体缓存的路由 ID 的事件
	 */
	@Override
	public void onApplicationEvent(EnableBodyCachingEvent event) {
		this.routesToCache.putIfAbsent(event.getRouteId(), true);
	}

	/**
	 * 过滤请求，适配已缓存的请求体。
	 * <p>
	 * 处理逻辑如下：
	 * <ol>
	 * <li>若 exchange 中存在已缓存的请求装饰器（{@code CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR}），
	 * 则使用该缓存请求重建 exchange 并继续过滤链；</li>
	 * <li>若请求体数据（{@code CACHED_REQUEST_BODY_ATTR}）已存在，或当前路由无需缓存， 则直接继续过滤链；</li>
	 * <li>否则，将请求体缓存后再继续过滤链。</li>
	 * </ol>
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 当 ServerWebExchange 无法被修改时（例如在谓词读取请求体后），使用已缓存的 ServerHttpRequest
		ServerHttpRequest cachedRequest = exchange.getAttributeOrDefault(CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR,
				null);
		if (cachedRequest != null) {
			exchange.getAttributes().remove(CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR);
			return chain.filter(exchange.mutate().request(cachedRequest).build());
		}

		// 获取已缓存的请求体数据和当前路由
		DataBuffer body = exchange.getAttributeOrDefault(CACHED_REQUEST_BODY_ATTR, null);
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

		// 若请求体已缓存或当前路由无需缓存，直接继续过滤链
		if (body != null || !this.routesToCache.containsKey(route.getId())) {
			return chain.filter(exchange);
		}

		// 缓存请求体后继续过滤链
		return ServerWebExchangeUtils.cacheRequestBody(exchange, (serverHttpRequest) -> {
			// 若缓存后请求对象未变，无需重建 exchange
			if (serverHttpRequest == exchange.getRequest()) {
				return chain.filter(exchange);
			}
			return chain.filter(exchange.mutate().request(serverHttpRequest).build());
		});
	}

	/**
	 * 返回过滤器执行顺序。
	 * <p>
	 * 执行顺序为 {@link Ordered#HIGHEST_PRECEDENCE} + 1000，确保尽早执行以适配缓存请求体。
	 * @return 过滤器执行顺序值
	 */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1000;
	}

}
