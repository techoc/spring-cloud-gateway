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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.handle;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;

/**
 * Forward 协议路由过滤器。
 * <p>
 * 该全局过滤器负责处理使用 {@code forward} 协议的请求，将请求转发到本地 Spring MVC 或 WebFlux 的
 * {@link DispatcherHandler} 处理。这使得网关可以将请求路由到自身应用内的 其他端点，而无需发起外部 HTTP 调用。
 * <p>
 * 执行顺序为 {@link Ordered#LOWEST_PRECEDENCE}，在所有路由过滤器中最后执行， 确保仅处理尚未被路由的请求。
 * <p>
 * 注意：{@code dispatcherHandler} 通过 {@link ObjectProvider} 延迟获取， 避免循环依赖问题，请勿直接使用字段，应通过
 * {@link #getDispatcherHandler()} 方法获取。
 */
public class ForwardRoutingFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(ForwardRoutingFilter.class);

	/** DispatcherHandler 的 ObjectProvider，用于延迟获取实例，避免循环依赖 */
	private final ObjectProvider<DispatcherHandler> dispatcherHandlerProvider;

	/** 缓存的 DispatcherHandler 实例，通过 volatile 保证多线程可见性，请使用 getDispatcherHandler() 获取 */
	private volatile DispatcherHandler dispatcherHandler;

	/**
	 * 构造方法。
	 * @param dispatcherHandlerProvider {@link DispatcherHandler} 的 ObjectProvider
	 */
	public ForwardRoutingFilter(ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		this.dispatcherHandlerProvider = dispatcherHandlerProvider;
	}

	/**
	 * 延迟获取 {@link DispatcherHandler} 实例。
	 * <p>
	 * 首次调用时从 {@link ObjectProvider} 中获取实例并缓存，后续调用直接返回缓存值。
	 * @return {@link DispatcherHandler} 实例
	 */
	private DispatcherHandler getDispatcherHandler() {
		if (dispatcherHandler == null) {
			dispatcherHandler = dispatcherHandlerProvider.getIfAvailable();
		}

		return dispatcherHandler;
	}

	/**
	 * 返回过滤器执行顺序。
	 * @return {@link Ordered#LOWEST_PRECEDENCE}，即最低优先级（最后执行）
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * 过滤请求，将 {@code forward} 协议的请求转发给本地 {@link DispatcherHandler} 处理。
	 * <p>
	 * 若请求已被路由或协议不是 {@code forward}，则直接传递给过滤器链的下一个过滤器。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || !"forward".equals(scheme)) {
			return chain.filter(exchange);
		}

		// TODO: 是否需要 URL 转换？

		if (log.isTraceEnabled()) {
			log.trace("Forwarding to URI: " + requestUrl);
		}

		return handle(this.getDispatcherHandler(), exchange);
	}

}
