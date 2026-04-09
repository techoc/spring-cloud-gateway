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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.retry.Backoff;
import reactor.retry.Repeat;
import reactor.retry.RepeatContext;
import reactor.retry.Retry;
import reactor.retry.RetryContext;

import org.springframework.cloud.gateway.event.EnableBodyCachingEvent;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus.Series;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 重试过滤器工厂。
 * <p>
 * 该过滤器在请求失败时自动重试，支持基于 HTTP 状态码和异常类型的重试策略。
 * <p>
 * 配置参数：
 * <ul>
 * <li>retries：最大重试次数（默认 3）</li>
 * <li>statuses：需要重试的 HTTP 状态码列表</li>
 * <li>series：需要重试的 HTTP 状态码系列（如 SERVER_ERROR）</li>
 * <li>methods：需要重试的 HTTP 方法列表（默认 GET）</li>
 * <li>exceptions：需要重试的异常类型列表（默认 IOException、TimeoutException）</li>
 * <li>backoff：退避策略配置</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - Retry=3,503,GET,500
 * </pre>
 *
 * @author Spencer Gibb
 */
public class RetryGatewayFilterFactory extends AbstractGatewayFilterFactory<RetryGatewayFilterFactory.RetryConfig> {

	/**
	 * 重试迭代次数属性键名。
	 */
	public static final String RETRY_ITERATION_KEY = "retry_iteration";

	private static final Log log = LogFactory.getLog(RetryGatewayFilterFactory.class);

	/**
	 * 默认构造方法。
	 */
	public RetryGatewayFilterFactory() {
		super(RetryConfig.class);
	}

	/**
	 * 将可变参数转换为列表。
	 */
	private static <T> List<T> toList(T... items) {
		return new ArrayList<>(Arrays.asList(items));
	}

	/**
	 * 返回快捷字段顺序。
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("retries", "statuses", "methods", "backoff.firstBackoff", "backoff.maxBackoff",
				"backoff.factor", "backoff.basedOnPreviousValue");
	}

	/**
	 * 创建重试过滤器。
	 * @param retryConfig 重试配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(RetryConfig retryConfig) {
		retryConfig.validate();

		// 基于状态码的重试
		Repeat<ServerWebExchange> statusCodeRepeat = null;
		if (!retryConfig.getStatuses().isEmpty() || !retryConfig.getSeries().isEmpty()) {
			Predicate<RepeatContext<ServerWebExchange>> repeatPredicate = context -> {
				ServerWebExchange exchange = context.applicationContext();
				if (exceedsMaxIterations(exchange, retryConfig)) {
					return false;
				}

				HttpStatus statusCode = exchange.getResponse().getStatusCode();

				boolean retryableStatusCode = retryConfig.getStatuses().contains(statusCode);

				// null 状态码可能意味着网络异常
				if (!retryableStatusCode && statusCode != null) {
					// 尝试匹配状态码系列
					retryableStatusCode = false;
					for (int i = 0; i < retryConfig.getSeries().size(); i++) {
						if (statusCode.series().equals(retryConfig.getSeries().get(i))) {
							retryableStatusCode = true;
							break;
						}
					}
				}

				final boolean finalRetryableStatusCode = retryableStatusCode;
				trace("retryableStatusCode: %b, statusCode %s, configured statuses %s, configured series %s",
						() -> finalRetryableStatusCode, () -> statusCode, retryConfig::getStatuses,
						retryConfig::getSeries);

				HttpMethod httpMethod = exchange.getRequest().getMethod();
				boolean retryableMethod = retryConfig.getMethods().contains(httpMethod);

				trace("retryableMethod: %b, httpMethod %s, configured methods %s", () -> retryableMethod,
						() -> httpMethod, retryConfig::getMethods);
				return retryableMethod && finalRetryableStatusCode;
			};

			statusCodeRepeat = Repeat.onlyIf(repeatPredicate)
					.doOnRepeat(context -> reset(context.applicationContext()));

			BackoffConfig backoff = retryConfig.getBackoff();
			if (backoff != null) {
				statusCodeRepeat = statusCodeRepeat.backoff(getBackoff(backoff));
			}
		}

		// TODO: 支持超时、退避、抖动等

		// 基于异常的重试
		Retry<ServerWebExchange> exceptionRetry = null;
		if (!retryConfig.getExceptions().isEmpty()) {
			Predicate<RetryContext<ServerWebExchange>> retryContextPredicate = context -> {

				ServerWebExchange exchange = context.applicationContext();

				if (exceedsMaxIterations(exchange, retryConfig)) {
					return false;
				}

				Throwable exception = context.exception();
				for (Class<? extends Throwable> retryableClass : retryConfig.getExceptions()) {
					if (retryableClass.isInstance(exception)
							|| (exception != null && retryableClass.isInstance(exception.getCause()))) {
						trace("exception or its cause is retryable %s, configured exceptions %s",
								() -> getExceptionNameWithCause(exception), retryConfig::getExceptions);

						HttpMethod httpMethod = exchange.getRequest().getMethod();
						boolean retryableMethod = retryConfig.getMethods().contains(httpMethod);
						trace("retryableMethod: %b, httpMethod %s, configured methods %s", () -> retryableMethod,
								() -> httpMethod, retryConfig::getMethods);
						return retryableMethod;
					}
				}
				trace("exception or its cause is not retryable %s, configured exceptions %s",
						() -> getExceptionNameWithCause(exception), retryConfig::getExceptions);
				return false;
			};
			exceptionRetry = Retry.onlyIf(retryContextPredicate)
					.doOnRetry(context -> reset(context.applicationContext())).retryMax(retryConfig.getRetries());
			BackoffConfig backoff = retryConfig.getBackoff();
			if (backoff != null) {
				exceptionRetry = exceptionRetry.backoff(getBackoff(backoff));
			}
		}

		GatewayFilter gatewayFilter = apply(retryConfig.getRouteId(), statusCodeRepeat, exceptionRetry);
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return gatewayFilter.filter(exchange, chain);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RetryGatewayFilterFactory.this).append("routeId", retryConfig.getRouteId())
						.append("retries", retryConfig.getRetries()).append("series", retryConfig.getSeries())
						.append("statuses", retryConfig.getStatuses()).append("methods", retryConfig.getMethods())
						.append("exceptions", retryConfig.getExceptions()).toString();
			}
		};
	}

	/**
	 * 获取异常名称及根因。
	 */
	private String getExceptionNameWithCause(Throwable exception) {
		if (exception != null) {
			StringBuilder builder = new StringBuilder(exception.getClass().getName());
			Throwable cause = exception.getCause();
			if (cause != null) {
				builder.append("{cause=").append(cause.getClass().getName()).append("}");
			}
			return builder.toString();
		}
		else {
			return "null";
		}
	}

	/**
	 * 获取退避策略。
	 */
	private Backoff getBackoff(BackoffConfig backoff) {
		return Backoff.exponential(backoff.firstBackoff, backoff.maxBackoff, backoff.factor,
				backoff.basedOnPreviousValue);
	}

	/**
	 * 检查是否超过最大重试次数。
	 */
	public boolean exceedsMaxIterations(ServerWebExchange exchange, RetryConfig retryConfig) {
		Integer iteration = exchange.getAttribute(RETRY_ITERATION_KEY);

		// TODO: 处理 null iteration
		boolean exceeds = iteration != null && iteration >= retryConfig.getRetries();
		trace("exceedsMaxIterations %b, iteration %d, configured retries %d", () -> exceeds, () -> iteration,
				retryConfig::getRetries);
		return exceeds;
	}

	/**
	 * 重置交换对象状态，准备下次重试。
	 * @deprecated 使用 {@link ServerWebExchangeUtils#reset(ServerWebExchange)}
	 */
	@Deprecated
	public void reset(ServerWebExchange exchange) {
		Connection conn = exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR);
		if (conn != null) {
			trace("disposing response connection before next iteration");
			conn.dispose();
			exchange.getAttributes().remove(ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR);
		}
		ServerWebExchangeUtils.reset(exchange);
	}

	/**
	 * 创建重试过滤器。
	 * @param routeId 路由 ID
	 * @param repeat 状态码重复策略
	 * @param retry 异常重试策略
	 * @return 网关过滤器实例
	 */
	public GatewayFilter apply(String routeId, Repeat<ServerWebExchange> repeat, Retry<ServerWebExchange> retry) {
		if (routeId != null && getPublisher() != null) {
			// 发送事件以启用请求体缓存
			getPublisher().publishEvent(new EnableBodyCachingEvent(this, routeId));
		}
		return (exchange, chain) -> {
			trace("Entering retry-filter");

			// chain.filter 返回 Mono<Void>
			Publisher<Void> publisher = chain.filter(exchange)
					// .log("retry-filter", Level.INFO)
					.doOnSuccess(aVoid -> updateIteration(exchange)).doOnError(throwable -> updateIteration(exchange));

			if (retry != null) {
				// retryWhen 返回 Mono<Void>，重试需要在重复之前
				publisher = ((Mono<Void>) publisher)
						.retryWhen(reactor.util.retry.Retry.withThrowable(retry.withApplicationContext(exchange)));
			}
			if (repeat != null) {
				// repeatWhen 返回 Flux<Void>，所以这需要在最后
				publisher = ((Mono<Void>) publisher).repeatWhen(repeat.withApplicationContext(exchange));
			}

			return Mono.fromDirect(publisher);
		};
	}

	/**
	 * 更新重试迭代次数。
	 */
	private void updateIteration(ServerWebExchange exchange) {
		int iteration = exchange.getAttributeOrDefault(RETRY_ITERATION_KEY, -1);
		int newIteration = iteration + 1;
		trace("setting new iteration in attr %d", () -> newIteration);
		exchange.getAttributes().put(RETRY_ITERATION_KEY, newIteration);
	}

	/**
	 * 追踪日志。
	 */
	@SafeVarargs
	private final void trace(String message, Supplier<Object>... argSuppliers) {
		if (log.isTraceEnabled()) {
			Object[] args = new Object[argSuppliers.length];
			int i = 0;
			for (Supplier<Object> a : argSuppliers) {
				args[i] = a.get();
				++i;
			}
			log.trace(String.format(message, args));
		}
	}

	/**
	 * 重试配置类。
	 */
	@SuppressWarnings("unchecked")
	public static class RetryConfig implements HasRouteId {

		private String routeId;

		/** 最大重试次数，默认 3 */
		private int retries = 3;

		/** 需要重试的状态码系列，默认 SERVER_ERROR */
		private List<Series> series = toList(Series.SERVER_ERROR);

		/** 需要重试的状态码列表 */
		private List<HttpStatus> statuses = new ArrayList<>();

		/** 需要重试的 HTTP 方法，默认 GET */
		private List<HttpMethod> methods = toList(HttpMethod.GET);

		/** 需要重试的异常类型，默认 IOException、TimeoutException */
		private List<Class<? extends Throwable>> exceptions = toList(IOException.class, TimeoutException.class);

		/** 退避策略配置 */
		private BackoffConfig backoff;

		/**
		 * 设置所有 HTTP 方法都进行重试。
		 */
		public RetryConfig allMethods() {
			return setMethods(HttpMethod.values());
		}

		/**
		 * 验证配置。
		 */
		public void validate() {
			Assert.isTrue(this.retries > 0, "retries must be greater than 0");
			Assert.isTrue(!this.series.isEmpty() || !this.statuses.isEmpty() || !this.exceptions.isEmpty(),
					"series, status and exceptions may not all be empty");
			Assert.notEmpty(this.methods, "methods may not be empty");
			if (this.backoff != null) {
				this.backoff.validate();
			}
		}

		public BackoffConfig getBackoff() {
			return backoff;
		}

		public RetryConfig setBackoff(BackoffConfig backoff) {
			this.backoff = backoff;
			return this;
		}

		public RetryConfig setBackoff(Duration firstBackoff, Duration maxBackoff, int factor,
				boolean basedOnPreviousValue) {
			this.backoff = new BackoffConfig(firstBackoff, maxBackoff, factor, basedOnPreviousValue);
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

		public int getRetries() {
			return retries;
		}

		public RetryConfig setRetries(int retries) {
			this.retries = retries;
			return this;
		}

		public List<Series> getSeries() {
			return series;
		}

		public RetryConfig setSeries(Series... series) {
			this.series = Arrays.asList(series);
			return this;
		}

		public List<HttpStatus> getStatuses() {
			return statuses;
		}

		public RetryConfig setStatuses(HttpStatus... statuses) {
			this.statuses = Arrays.asList(statuses);
			return this;
		}

		public List<HttpMethod> getMethods() {
			return methods;
		}

		public RetryConfig setMethods(HttpMethod... methods) {
			this.methods = Arrays.asList(methods);
			return this;
		}

		public List<Class<? extends Throwable>> getExceptions() {
			return exceptions;
		}

		public RetryConfig setExceptions(Class<? extends Throwable>... exceptions) {
			this.exceptions = Arrays.asList(exceptions);
			return this;
		}

	}

	/**
	 * 退避策略配置类。
	 */
	public static class BackoffConfig {

		/** 首次退避时间，默认 5ms */
		private Duration firstBackoff = Duration.ofMillis(5);

		/** 最大退避时间 */
		private Duration maxBackoff;

		/** 退避倍数，默认 2 */
		private int factor = 2;

		/** 是否基于前一次退避值计算 */
		private boolean basedOnPreviousValue = true;

		public BackoffConfig() {
		}

		public BackoffConfig(Duration firstBackoff, Duration maxBackoff, int factor, boolean basedOnPreviousValue) {
			this.firstBackoff = firstBackoff;
			this.maxBackoff = maxBackoff;
			this.factor = factor;
			this.basedOnPreviousValue = basedOnPreviousValue;
		}

		public void validate() {
			Assert.notNull(this.firstBackoff, "firstBackoff must be present");
		}

		public Duration getFirstBackoff() {
			return firstBackoff;
		}

		public void setFirstBackoff(Duration firstBackoff) {
			this.firstBackoff = firstBackoff;
		}

		public Duration getMaxBackoff() {
			return maxBackoff;
		}

		public void setMaxBackoff(Duration maxBackoff) {
			this.maxBackoff = maxBackoff;
		}

		public int getFactor() {
			return factor;
		}

		public void setFactor(int factor) {
			this.factor = factor;
		}

		public boolean isBasedOnPreviousValue() {
			return basedOnPreviousValue;
		}

		public void setBasedOnPreviousValue(boolean basedOnPreviousValue) {
			this.basedOnPreviousValue = basedOnPreviousValue;
		}

	}

}
