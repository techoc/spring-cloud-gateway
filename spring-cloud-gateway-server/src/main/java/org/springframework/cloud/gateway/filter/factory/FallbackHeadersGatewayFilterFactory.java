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

import java.util.ArrayList;
import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Collections.singletonList;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;

/**
 * 熔断降级头过滤器工厂。
 * <p>
 * 当使用熔断器（如 Spring Cloud CircuitBreaker + Resilience4j）进行服务降级时，
 * 该过滤器将异常信息添加到请求头中，以便降级处理端点获取原始错误信息。
 * <p>
 * 添加的请求头包括：
 * <ul>
 * <li>执行异常的类型（完全限定类名）</li>
 * <li>执行异常的消息</li>
 * <li>根异常的完全限定类名（若有）</li>
 * <li>根异常的消息（若有）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - FallbackHeaders
 * </pre>
 *
 * @author Olga Maciaszek-Sharma
 * @author Ryan Baxter
 */
public class FallbackHeadersGatewayFilterFactory
		extends AbstractGatewayFilterFactory<FallbackHeadersGatewayFilterFactory.Config> {

	/**
	 * 默认构造方法。
	 */
	public FallbackHeadersGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return singletonList(NAME_KEY);
	}

	/**
	 * 创建熔断降级头过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return (exchange, chain) -> {
			// 从 exchange 属性中获取熔断器抛出的异常
			Throwable exception = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
			ServerWebExchange filteredExchange;
			if (exception == null) {
				// 无异常，直接继续过滤链
				filteredExchange = exchange;
			}
			else {
				// 添加降级头信息
				filteredExchange = addFallbackHeaders(config, exchange, exception);
			}
			return chain.filter(filteredExchange);
		};
	}

	/**
	 * 向请求添加降级相关的头部信息。
	 * @param config 过滤器配置
	 * @param exchange 当前交换对象
	 * @param executionException 执行的异常
	 * @return 添加了降级头的新的交换对象
	 */
	private ServerWebExchange addFallbackHeaders(Config config, ServerWebExchange exchange,
			Throwable executionException) {
		ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
		// 添加执行异常类型和消息
		requestBuilder.header(config.executionExceptionTypeHeaderName, executionException.getClass().getName());
		requestBuilder.header(config.executionExceptionMessageHeaderName, executionException.getMessage());
		// 获取根异常并添加
		Throwable rootCause = getRootCause(executionException);
		if (rootCause != null) {
			requestBuilder.header(config.rootCauseExceptionTypeHeaderName, rootCause.getClass().getName());
			requestBuilder.header(config.rootCauseExceptionMessageHeaderName, rootCause.getMessage());
		}
		return exchange.mutate().request(requestBuilder.build()).build();
	}

	/**
	 * 获取异常的根因。
	 * @param throwable 起始异常
	 * @return 根异常，若无则返回 null
	 */
	private static Throwable getRootCause(final Throwable throwable) {
		final List<Throwable> list = getThrowableList(throwable);
		return list.isEmpty() ? null : list.get(list.size() - 1);
	}

	/**
	 * 获取异常链列表（从原始异常到根异常）。
	 * @param throwable 起始异常
	 * @return 异常链列表
	 */
	private static List<Throwable> getThrowableList(Throwable throwable) {
		final List<Throwable> list = new ArrayList<>();
		while (throwable != null && !list.contains(throwable)) {
			list.add(throwable);
			throwable = throwable.getCause();
		}
		return list;
	}

	/**
	 * 熔断降级头过滤器配置类。
	 */
	public static class Config {

		/** 默认的异常类型头名称 */
		private static final String EXECUTION_EXCEPTION_TYPE = "Execution-Exception-Type";

		/** 默认的异常消息头名称 */
		private static final String EXECUTION_EXCEPTION_MESSAGE = "Execution-Exception-Message";

		/** 默认的根异常类型头名称 */
		private static final String ROOT_CAUSE_EXCEPTION_TYPE = "Root-Cause-Exception-Type";

		/** 默认的根异常消息头名称 */
		private static final String ROOT_CAUSE_EXCEPTION_MESSAGE = "Root-Cause-Exception-Message";

		/** 执行异常类型头部名称 */
		private String executionExceptionTypeHeaderName = EXECUTION_EXCEPTION_TYPE;

		/** 执行异常消息头部名称 */
		private String executionExceptionMessageHeaderName = EXECUTION_EXCEPTION_MESSAGE;

		/** 根异常类型头部名称 */
		private String rootCauseExceptionTypeHeaderName = ROOT_CAUSE_EXCEPTION_TYPE;

		/** 根异常消息头部名称 */
		private String rootCauseExceptionMessageHeaderName = ROOT_CAUSE_EXCEPTION_MESSAGE;

		public String getExecutionExceptionTypeHeaderName() {
			return executionExceptionTypeHeaderName;
		}

		public void setExecutionExceptionTypeHeaderName(String executionExceptionTypeHeaderName) {
			this.executionExceptionTypeHeaderName = executionExceptionTypeHeaderName;
		}

		public String getExecutionExceptionMessageHeaderName() {
			return executionExceptionMessageHeaderName;
		}

		public void setExecutionExceptionMessageHeaderName(String executionExceptionMessageHeaderName) {
			this.executionExceptionMessageHeaderName = executionExceptionMessageHeaderName;
		}

		public String getRootCauseExceptionTypeHeaderName() {
			return rootCauseExceptionTypeHeaderName;
		}

		public void setRootCauseExceptionTypeHeaderName(String rootCauseExceptionTypeHeaderName) {
			this.rootCauseExceptionTypeHeaderName = rootCauseExceptionTypeHeaderName;
		}

		public String getRootCauseExceptionMessageHeaderName() {
			return rootCauseExceptionMessageHeaderName;
		}

		public void setRootCauseExceptionMessageHeaderName(String rootCauseExceptionMessageHeaderName) {
			this.rootCauseExceptionMessageHeaderName = rootCauseExceptionMessageHeaderName;
		}

	}

}
