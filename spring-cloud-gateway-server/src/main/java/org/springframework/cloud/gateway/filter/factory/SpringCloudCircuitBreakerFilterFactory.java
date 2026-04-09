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

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.handle;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.reset;

/**
 * Spring Cloud Circuit Breaker（熔断器）过滤器工厂抽象基类。
 * <p>
 * 该过滤器使用 Spring Cloud Circuit Breaker 包裹请求处理，当请求失败（根据配置的状态码或异常） 时自动触发熔断逻辑，可选择性地转发到降级 URI。
 * <p>
 * 配置参数：
 * <ul>
 * <li>name：熔断器名称（必填）</li>
 * <li>fallbackUri：降级时的目标 URI（可选）</li>
 * <li>statusCodes：触发熔断的状态码集合</li>
 * <li>resumeWithoutError：发生错误时是否继续处理</li>
 * </ul>
 *
 * @author Ryan Baxter
 */
public abstract class SpringCloudCircuitBreakerFilterFactory
		extends AbstractGatewayFilterFactory<SpringCloudCircuitBreakerFilterFactory.Config> {

	/** 熔断器组件名称 */
	public static final String NAME = "CircuitBreaker";

	/** 响应式熔断器工厂 */
	private ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;

	private ReactiveCircuitBreaker cb;

	/** DispatcherHandler 的 ObjectProvider */
	private final ObjectProvider<DispatcherHandler> dispatcherHandlerProvider;

	/** 缓存的 DispatcherHandler，请勿直接使用，应通过 getDispatcherHandler() 获取 */
	private volatile DispatcherHandler dispatcherHandler;

	/**
	 * 构造方法。
	 * @param reactiveCircuitBreakerFactory 响应式熔断器工厂
	 * @param dispatcherHandlerProvider DispatcherHandler 的 ObjectProvider
	 */
	public SpringCloudCircuitBreakerFilterFactory(ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		super(Config.class);
		this.reactiveCircuitBreakerFactory = reactiveCircuitBreakerFactory;
		this.dispatcherHandlerProvider = dispatcherHandlerProvider;
	}

	/**
	 * 延迟获取 DispatcherHandler。
	 */
	private DispatcherHandler getDispatcherHandler() {
		if (dispatcherHandler == null) {
			dispatcherHandler = dispatcherHandlerProvider.getIfAvailable();
		}

		return dispatcherHandler;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return singletonList(NAME_KEY);
	}

	/**
	 * 创建熔断过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		// 创建指定名称的熔断器
		ReactiveCircuitBreaker cb = reactiveCircuitBreakerFactory.create(config.getId());
		Set<HttpStatus> statuses = config.getStatusCodes().stream().map(HttpStatusHolder::parse)
				.filter(statusHolder -> statusHolder.getHttpStatus() != null).map(HttpStatusHolder::getHttpStatus)
				.collect(Collectors.toSet());

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return cb.run(chain.filter(exchange).doOnSuccess(v -> {
					// 检查响应状态码是否触发熔断
					if (statuses.contains(exchange.getResponse().getStatusCode())) {
						HttpStatus status = exchange.getResponse().getStatusCode();
						throw new CircuitBreakerStatusCodeException(status);
					}
				}), t -> {
					// 发生错误时
					if (config.getFallbackUri() == null) {
						return Mono.error(t);
					}

					exchange.getResponse().setStatusCode(null);

					// 构建降级 URI
					URI uri = exchange.getRequest().getURI();
					boolean encoded = containsEncodedParts(uri);
					URI requestUrl = UriComponentsBuilder.fromUri(uri).host(null).port(null)
							.uri(config.getFallbackUri()).scheme(null).build(encoded).toUri();
					exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
					addExceptionDetails(t, exchange);

					// 重置 exchange
					reset(exchange);

					ServerHttpRequest request = exchange.getRequest().mutate().uri(requestUrl).build();
					return handle(getDispatcherHandler(), exchange.mutate().request(request).build());
				}).onErrorResume(t -> handleErrorWithoutFallback(t, config.isResumeWithoutError()));
			}

			@Override
			public String toString() {
				return filterToStringCreator(SpringCloudCircuitBreakerFilterFactory.this)
						.append("name", config.getName()).append("fallback", config.fallbackUri).toString();
			}
		};
	}

	/**
	 * 处理无降级 URI 时的错误。
	 * @param t 异常
	 * @param resumeWithoutError 是否在无错误时继续
	 * @return 处理后的 Mono
	 */
	protected abstract Mono<Void> handleErrorWithoutFallback(Throwable t, boolean resumeWithoutError);

	/**
	 * 添加异常详情到 exchange 属性。
	 */
	private void addExceptionDetails(Throwable t, ServerWebExchange exchange) {
		ofNullable(t).ifPresent(
				exception -> exchange.getAttributes().put(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, exception));
	}

	@Override
	public String name() {
		return NAME;
	}

	/**
	 * 熔断器过滤器配置类。
	 */
	public static class Config implements HasRouteId {

		/** 熔断器名称 */
		private String name;

		/** 降级目标 URI */
		private URI fallbackUri;

		private String routeId;

		/** 触发熔断的状态码集合 */
		private Set<String> statusCodes = new HashSet<>();

		/** 发生错误时是否继续处理 */
		private boolean resumeWithoutError = false;

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		public String getRouteId() {
			return routeId;
		}

		public URI getFallbackUri() {
			return fallbackUri;
		}

		public Config setFallbackUri(URI fallbackUri) {
			this.fallbackUri = fallbackUri;
			return this;
		}

		public Config setFallbackUri(String fallbackUri) {
			return setFallbackUri(URI.create(fallbackUri));
		}

		public String getName() {
			return name;
		}

		public Config setName(String name) {
			this.name = name;
			return this;
		}

		public String getId() {
			if (StringUtils.isEmpty(name) && !StringUtils.isEmpty(routeId)) {
				return routeId;
			}
			return name;
		}

		public Set<String> getStatusCodes() {
			return statusCodes;
		}

		public Config setStatusCodes(Set<String> statusCodes) {
			this.statusCodes = statusCodes;
			return this;
		}

		public Config addStatusCode(String statusCode) {
			this.statusCodes.add(statusCode);
			return this;
		}

		public boolean isResumeWithoutError() {
			return resumeWithoutError;
		}

		public void setResumeWithoutError(boolean resumeWithoutError) {
			this.resumeWithoutError = resumeWithoutError;
		}

	}

	/**
	 * 熔断器状态码异常，用于触发熔断。
	 */
	public class CircuitBreakerStatusCodeException extends HttpStatusCodeException {

		public CircuitBreakerStatusCodeException(HttpStatus statusCode) {
			super(statusCode);
		}

	}

}
