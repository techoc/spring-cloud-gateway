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

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * 请求限流过滤器工厂，用于控制网关的请求速率。
 *
 * <p>
 * 该过滤器基于令牌桶算法实现，支持多种限流策略：
 * <ul>
 * <li>按 IP 地址限流</li>
 * <li>按用户限流</li>
 * <li>按路由限流</li>
 * <li>自定义限流键解析器</li>
 * </ul>
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: my-route
 *           uri: http://example.com
 *           filters:
 *             - name: RequestRateLimiter
 *               args:
 *                 key-resolver: "#{@userKeyResolver}"
 *                 rate-limiter: "#{@redisRateLimiter}"
 *                 deny-empty-key: false
 *                 status-code: 429
 * </pre>
 *
 * @author Spencer Gibb
 * @see KeyResolver
 * @see RateLimiter
 * @see <a href="https://stripe.com/blog/rate-limiters">Rate Limiting: Patterns and
 * Algorithms</a>
 */
@ConfigurationProperties("spring.cloud.gateway.filter.request-rate-limiter")
public class RequestRateLimiterGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestRateLimiterGatewayFilterFactory.Config> {

	/**
	 * 配置参数名称：键解析器。 用于指定如何识别需要进行限流的客户端。
	 */
	public static final String KEY_RESOLVER_KEY = "keyResolver";

	/** 空键的占位符，用于标识 KeyResolver 返回空值的情况 */
	private static final String EMPTY_KEY = "____EMPTY_KEY__";

	/** 默认的限流器实现 */
	private final RateLimiter defaultRateLimiter;

	/** 默认的键解析器实现 */
	private final KeyResolver defaultKeyResolver;

	/**
	 * 当 KeyResolver 返回空键时是否拒绝请求。 默认为 true，表示拒绝请求并返回配置的状态码。
	 */
	private boolean denyEmptyKey = true;

	/**
	 * 当 denyEmptyKey 为 true 时，返回的 HTTP 状态码。 默认为 FORBIDDEN (403)。
	 */
	private String emptyKeyStatusCode = HttpStatus.FORBIDDEN.name();

	/**
	 * 构造函数，注入默认的限流器和键解析器。
	 * @param defaultRateLimiter 默认的限流器
	 * @param defaultKeyResolver 默认的键解析器
	 */
	public RequestRateLimiterGatewayFilterFactory(RateLimiter defaultRateLimiter, KeyResolver defaultKeyResolver) {
		super(Config.class);
		this.defaultRateLimiter = defaultRateLimiter;
		this.defaultKeyResolver = defaultKeyResolver;
	}

	/**
	 * 获取默认的键解析器。
	 * @return 默认的 KeyResolver 实现
	 */
	public KeyResolver getDefaultKeyResolver() {
		return defaultKeyResolver;
	}

	/**
	 * 获取默认的限流器。
	 * @return 默认的 RateLimiter 实现
	 */
	public RateLimiter getDefaultRateLimiter() {
		return defaultRateLimiter;
	}

	/**
	 * 检查当 KeyResolver 返回空键时是否拒绝请求。
	 * @return true 表示拒绝，false 表示允许通过
	 */
	public boolean isDenyEmptyKey() {
		return denyEmptyKey;
	}

	/**
	 * 设置当 KeyResolver 返回空键时是否拒绝请求。
	 * @param denyEmptyKey true 表示拒绝，false 表示允许通过
	 */
	public void setDenyEmptyKey(boolean denyEmptyKey) {
		this.denyEmptyKey = denyEmptyKey;
	}

	/**
	 * 获取当 KeyResolver 返回空键时拒绝请求的 HTTP 状态码。
	 * @return HTTP 状态码名称
	 */
	public String getEmptyKeyStatusCode() {
		return emptyKeyStatusCode;
	}

	/**
	 * 设置当 KeyResolver 返回空键时拒绝请求的 HTTP 状态码。
	 * @param emptyKeyStatusCode HTTP 状态码名称，如 FORBIDDEN、NOT_FOUND 等
	 */
	public void setEmptyKeyStatusCode(String emptyKeyStatusCode) {
		this.emptyKeyStatusCode = emptyKeyStatusCode;
	}

	/**
	 * 应用此过滤器，根据配置创建限流过滤器。
	 * @param config 限流过滤器配置
	 * @return 新的 GatewayFilter 实例
	 */
	@SuppressWarnings("unchecked")
	@Override
	public GatewayFilter apply(Config config) {
		// 获取配置的或默认的键解析器和限流器
		KeyResolver resolver = getOrDefault(config.keyResolver, defaultKeyResolver);
		RateLimiter<Object> limiter = getOrDefault(config.rateLimiter, defaultRateLimiter);
		boolean denyEmpty = getOrDefault(config.denyEmptyKey, this.denyEmptyKey);
		HttpStatusHolder emptyKeyStatus = HttpStatusHolder
				.parse(getOrDefault(config.emptyKeyStatus, this.emptyKeyStatusCode));

		// 创建限流过滤器
		return (exchange, chain) -> resolver.resolve(exchange).defaultIfEmpty(EMPTY_KEY).flatMap(key -> {
			// 处理空键情况
			if (EMPTY_KEY.equals(key)) {
				if (denyEmpty) {
					// 设置拒绝状态并终止请求
					setResponseStatus(exchange, emptyKeyStatus);
					return exchange.getResponse().setComplete();
				}
				// 允许空键请求通过
				return chain.filter(exchange);
			}

			// 获取路由 ID
			String routeId = config.getRouteId();
			if (routeId == null) {
				Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
				routeId = route.getId();
			}

			// 检查是否允许请求通过
			return limiter.isAllowed(routeId, key).flatMap(response -> {
				// 将限流器的响应头添加到响应中（如 X-RateLimit-Remaining）
				for (Map.Entry<String, String> header : response.getHeaders().entrySet()) {
					exchange.getResponse().getHeaders().add(header.getKey(), header.getValue());
				}

				// 如果允许请求通过，继续执行过滤器链
				if (response.isAllowed()) {
					return chain.filter(exchange);
				}

				// 请求被限流，设置状态码并终止请求
				setResponseStatus(exchange, config.getStatusCode());
				return exchange.getResponse().setComplete();
			});
		});
	}

	/**
	 * 获取配置值，如果为空则返回默认值。
	 * @param configValue 配置值
	 * @param defaultValue 默认值
	 * @param <T> 值类型
	 * @return 配置值或默认值
	 */
	private <T> T getOrDefault(T configValue, T defaultValue) {
		return (configValue != null) ? configValue : defaultValue;
	}

	/**
	 * 限流过滤器配置类。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>keyResolver - 键解析器，用于识别限流对象</li>
	 * <li>rateLimiter - 限流器实现，默认为 RedisRateLimiter</li>
	 * <li>statusCode - 请求被限流时返回的状态码，默认为 429 TOO_MANY_REQUESTS</li>
	 * <li>denyEmptyKey - KeyResolver 返回空键时是否拒绝</li>
	 * <li>emptyKeyStatus - 拒绝空键请求时的状态码</li>
	 * <li>routeId - 路由 ID</li>
	 * </ul>
	 */
	public static class Config implements HasRouteId {

		/** 键解析器，用于识别需要进行限流的客户端 */
		private KeyResolver keyResolver;

		/** 限流器实现 */
		private RateLimiter rateLimiter;

		/** 请求被限流时返回的 HTTP 状态码，默认为 429 TOO_MANY_REQUESTS */
		private HttpStatus statusCode = HttpStatus.TOO_MANY_REQUESTS;

		/** KeyResolver 返回空键时是否拒绝请求 */
		private Boolean denyEmptyKey;

		/** 拒绝空键请求时返回的 HTTP 状态码 */
		private String emptyKeyStatus;

		/** 路由 ID */
		private String routeId;

		// Getters and Setters
		public KeyResolver getKeyResolver() {
			return keyResolver;
		}

		public Config setKeyResolver(KeyResolver keyResolver) {
			this.keyResolver = keyResolver;
			return this;
		}

		public RateLimiter getRateLimiter() {
			return rateLimiter;
		}

		public Config setRateLimiter(RateLimiter rateLimiter) {
			this.rateLimiter = rateLimiter;
			return this;
		}

		public HttpStatus getStatusCode() {
			return statusCode;
		}

		public Config setStatusCode(HttpStatus statusCode) {
			this.statusCode = statusCode;
			return this;
		}

		public Boolean getDenyEmptyKey() {
			return denyEmptyKey;
		}

		public Config setDenyEmptyKey(Boolean denyEmptyKey) {
			this.denyEmptyKey = denyEmptyKey;
			return this;
		}

		public String getEmptyKeyStatus() {
			return emptyKeyStatus;
		}

		public Config setEmptyKeyStatus(String emptyKeyStatus) {
			this.emptyKeyStatus = emptyKeyStatus;
			return this;
		}

		@Override
		public void setRouteId(String routeId) {
			this.routeId = routeId;
		}

		@Override
		public String getRouteId() {
			return this.routeId;
		}

	}

}
