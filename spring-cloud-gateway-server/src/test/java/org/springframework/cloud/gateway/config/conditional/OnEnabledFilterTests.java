/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.config.conditional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OnEnabledFilterTests - 过滤器名称规范化测试类
 *
 * 本测试类验证OnEnabledFilter对过滤器类名的规范化逻辑： -
 * 将GatewayFilterFactory类名转换为spring.cloud.gateway.filter.{name}格式的属性名
 *
 * 例如： - AddRequestHeaderGatewayFilterFactory -> filter.add-request-header -
 * CircuitBreakerGatewayFilterFactory -> filter.circuit-breaker
 *
 * @author test
 */
class OnEnabledFilterTests {

	/** 待测试的OnEnabledFilter实例 */
	private OnEnabledFilter onEnabledFilter;

	/**
	 * setUp - 测试前准备
	 *
	 * 初始化OnEnabledFilter实例
	 */
	@BeforeEach
	void setUp() {
		this.onEnabledFilter = new OnEnabledFilter();
	}

	/**
	 * shouldNormalizeFiltersNames - 测试过滤器名称规范化
	 *
	 * 验证各种GatewayFilterFactory的类名能够被正确转换为配置属性名格式
	 */
	@Test
	void shouldNormalizeFiltersNames() {
		List<Class<? extends GatewayFilterFactory<?>>> predicates = Arrays.asList(
				AddRequestHeaderGatewayFilterFactory.class, DedupeResponseHeaderGatewayFilterFactory.class,
				FallbackHeadersGatewayFilterFactory.class, MapRequestHeaderGatewayFilterFactory.class,
				SpringCloudCircuitBreakerResilience4JFilterFactory.class);

		List<String> resultNames = predicates.stream().map(onEnabledFilter::normalizeComponentName)
				.collect(Collectors.toList());

		List<String> expectedNames = Stream.of("add-request-header", "dedupe-response-header", "fallback-headers",
				"map-request-header", "circuit-breaker").map(s -> "filter." + s).collect(Collectors.toList());

		assertThat(resultNames).isEqualTo(expectedNames);
	}

}
