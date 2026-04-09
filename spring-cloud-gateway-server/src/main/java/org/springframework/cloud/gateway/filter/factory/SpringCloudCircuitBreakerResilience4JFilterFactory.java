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

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.support.ServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resilience4j 实现的 Spring Cloud Circuit Breaker 过滤器工厂。
 * <p>
 * 该类是 {@link SpringCloudCircuitBreakerFilterFactory} 的具体实现， 使用 Resilience4j 作为熔断器后端。
 * <p>
 * 错误处理策略：
 * <ul>
 * <li>TimeoutException → 503 Service Unavailable (GATEWAY_TIMEOUT)</li>
 * <li>CallNotPermittedException → 503 Service Unavailable</li>
 * <li>resumeWithoutError=true → 返回空的 Mono</li>
 * <li>其他异常 → 向上传播</li>
 * </ul>
 *
 * @author Ryan Baxter
 */
public class SpringCloudCircuitBreakerResilience4JFilterFactory extends SpringCloudCircuitBreakerFilterFactory {

	/**
	 * 构造方法。
	 * @param reactiveCircuitBreakerFactory Resilience4j 响应式熔断器工厂
	 * @param dispatcherHandlerProvider DispatcherHandler 的 ObjectProvider
	 */
	public SpringCloudCircuitBreakerResilience4JFilterFactory(
			ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandlerProvider) {
		super(reactiveCircuitBreakerFactory, dispatcherHandlerProvider);
	}

	/**
	 * 处理无降级 URI 时的错误。
	 * @param t 异常
	 * @param resumeWithoutError 是否在无错误时继续
	 * @return 处理后的 Mono
	 */
	@Override
	protected Mono<Void> handleErrorWithoutFallback(Throwable t, boolean resumeWithoutError) {
		// 超时异常
		if (java.util.concurrent.TimeoutException.class.isInstance(t)) {
			return Mono.error(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, t.getMessage(), t));
		}
		// 熔断器打开，不允许调用
		if (CallNotPermittedException.class.isInstance(t)) {
			return Mono.error(new ServiceUnavailableException());
		}
		if (resumeWithoutError) {
			return Mono.empty();
		}
		return Mono.error(t);
	}

}
