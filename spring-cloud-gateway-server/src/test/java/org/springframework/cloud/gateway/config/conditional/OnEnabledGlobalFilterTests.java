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

import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.ForwardPathFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OnEnabledGlobalFilterTests - 全局过滤器名称规范化测试类
 *
 * 本测试类验证OnEnabledGlobalFilter对全局过滤器类名的规范化逻辑： -
 * 将GlobalFilter类名转换为spring.cloud.gateway.global-filter.{name}格式的属性名
 *
 * 例如： - ForwardPathFilter -> global-filter.forward-path - AdaptCachedBodyGlobalFilter ->
 * global-filter.adapt-cached-body - WebsocketRoutingFilter ->
 * global-filter.websocket-routing
 *
 * @author test
 */
class OnEnabledGlobalFilterTests {

	/** 待测试的OnEnabledGlobalFilter实例 */
	private OnEnabledGlobalFilter onEnabledGlobalFilter;

	/**
	 * setUp - 测试前准备
	 *
	 * 初始化OnEnabledGlobalFilter实例
	 */
	@BeforeEach
	void setUp() {
		this.onEnabledGlobalFilter = new OnEnabledGlobalFilter();
	}

	/**
	 * shouldNormalizeGlobalFiltersNames - 测试全局过滤器名称规范化
	 *
	 * 验证各种GlobalFilter的类名能够被正确转换为配置属性名格式
	 */
	@Test
	void shouldNormalizeGlobalFiltersNames() {
		List<Class<? extends GlobalFilter>> predicates = Arrays.asList(ForwardPathFilter.class,
				AdaptCachedBodyGlobalFilter.class, WebsocketRoutingFilter.class);

		List<String> resultNames = predicates.stream().map(onEnabledGlobalFilter::normalizeComponentName)
				.collect(Collectors.toList());

		List<String> expectedNames = Stream.of("forward-path", "adapt-cached-body", "websocket-routing")
				.map(s -> "global-filter." + s).collect(Collectors.toList());

		assertThat(resultNames).isEqualTo(expectedNames);
	}

}
