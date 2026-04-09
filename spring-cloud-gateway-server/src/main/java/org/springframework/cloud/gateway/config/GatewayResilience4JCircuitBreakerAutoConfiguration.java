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

package org.springframework.cloud.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledFilter;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * Gateway Resilience4J 熔断器自动配置类。
 * <p>
 * 当类路径中存在 Resilience4J 相关依赖时，自动配置基于 Resilience4J 的熔断器过滤器工厂， 为网关提供请求熔断和回退功能。
 * </p>
 *
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@AutoConfigureAfter({ ReactiveResilience4JAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class, ReactiveResilience4JAutoConfiguration.class,
		ReactiveCircuitBreakerFactory.class, ReactiveResilience4JCircuitBreakerFactory.class })
public class GatewayResilience4JCircuitBreakerAutoConfiguration {

	/**
	 * 创建 Resilience4J 熔断器过滤器工厂。
	 * <p>
	 * 封装 {@link ReactiveResilience4JCircuitBreakerFactory}， 为路由配置提供熔断能力。
	 * </p>
	 * @param reactiveCircuitBreakerFactory Resilience4J 响应式熔断器工厂
	 * @param dispatcherHandler 分发器处理器（用于回退转发）
	 * @return Resilience4J 熔断器过滤器工厂
	 */
	@Bean
	@ConditionalOnBean(ReactiveResilience4JCircuitBreakerFactory.class)
	@ConditionalOnEnabledFilter
	public SpringCloudCircuitBreakerResilience4JFilterFactory springCloudCircuitBreakerResilience4JFilterFactory(
			ReactiveResilience4JCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandler) {
		return new SpringCloudCircuitBreakerResilience4JFilterFactory(reactiveCircuitBreakerFactory, dispatcherHandler);
	}

	/**
	 * 创建回退头过滤器工厂 Bean。 当容器中不存在 FallbackHeadersGatewayFilterFactory 且过滤器启用时自动配置。
	 * @return 回退头过滤器工厂
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnEnabledFilter
	public FallbackHeadersGatewayFilterFactory fallbackHeadersGatewayFilterFactory() {
		return new FallbackHeadersGatewayFilterFactory();
	}

}
