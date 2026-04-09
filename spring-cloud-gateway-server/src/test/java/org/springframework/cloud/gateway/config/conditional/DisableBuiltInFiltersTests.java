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

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DisableBuiltInFiltersTests - 禁用内置过滤器测试类
 *
 * 本测试类验证Gateway内置过滤器的启用/禁用配置功能： - 默认情况下所有内置过滤器都会被注册 - 可以通过属性禁用特定的过滤器 - 可以通过属性禁用所有内置过滤器
 *
 * 通过spring.cloud.gateway.filter.{filter-name}.enabled=false配置即可禁用对应过滤器
 *
 * @author test
 */
@RunWith(Enclosed.class)
public class DisableBuiltInFiltersTests {

	/**
	 * Config - 基础测试配置类
	 *
	 * 提供最小化的Spring Boot配置用于测试
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {

	}

	/**
	 * FilterDefault - 测试默认过滤器注册
	 *
	 * 验证在默认情况下，所有内置Gateway过滤器都会被正确注册
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class FilterDefault {

		/** Gateway过滤器工厂列表 */
		@Autowired
		private List<GatewayFilterFactory<?>> gatewayFilters;

		/**
		 * shouldInjectBuiltInFilters - 验证内置过滤器被注入
		 *
		 * 确认至少注册了31个内置过滤器
		 */
		@Test
		public void shouldInjectBuiltInFilters() {
			assertThat(gatewayFilters).hasSizeGreaterThanOrEqualTo(31);
		}

	}

	/**
	 * DisableSpecificsFiltersByProperty - 测试通过属性禁用特定过滤器
	 *
	 * 验证可以通过属性禁用特定的过滤器： - AddRequestHeaderGatewayFilterFactory -
	 * MapRequestHeaderGatewayFilterFactory
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.filter.add-request-header.enabled=false",
					"spring.cloud.gateway.filter.map-request-header.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableSpecificsFiltersByProperty {

		/** Gateway过滤器工厂列表 */
		@Autowired
		private List<GatewayFilterFactory<?>> gatewayFilters;

		/**
		 * shouldInjectOnlyEnabledBuiltInFilters - 验证只注册了启用的过滤器
		 *
		 * 确认gatewayFilters列表不为空，但其中不包含被禁用的过滤器
		 */
		@Test
		public void shouldInjectOnlyEnabledBuiltInFilters() {
			assertThat(gatewayFilters).hasSizeGreaterThan(0);
			assertThat(gatewayFilters).allSatisfy(filter -> assertThat(filter).isNotInstanceOfAny(
					AddRequestHeaderGatewayFilterFactory.class, MapRequestHeaderGatewayFilterFactory.class));
		}

	}

	/**
	 * DisableAllFiltersByProperty - 测试通过属性禁用所有过滤器
	 *
	 * 验证可以通过配置禁用所有内置过滤器工厂
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.filter.add-request-header.enabled=false",
					"spring.cloud.gateway.filter.map-request-header.enabled=false",
					"spring.cloud.gateway.filter.add-request-parameter.enabled=false",
					"spring.cloud.gateway.filter.add-response-header.enabled=false",
					"spring.cloud.gateway.filter.json-to-grpc.enabled=false",
					"spring.cloud.gateway.filter.modify-request-body.enabled=false",
					"spring.cloud.gateway.filter.dedupe-response-header.enabled=false",
					"spring.cloud.gateway.filter.modify-response-body.enabled=false",
					"spring.cloud.gateway.filter.prefix-path.enabled=false",
					"spring.cloud.gateway.filter.preserve-host-header.enabled=false",
					"spring.cloud.gateway.filter.redirect-to.enabled=false",
					"spring.cloud.gateway.filter.remove-request-header.enabled=false",
					"spring.cloud.gateway.filter.remove-request-parameter.enabled=false",
					"spring.cloud.gateway.filter.remove-response-header.enabled=false",
					"spring.cloud.gateway.filter.request-rate-limiter.enabled=false",
					"spring.cloud.gateway.filter.rewrite-path.enabled=false",
					"spring.cloud.gateway.filter.retry.enabled=false",
					"spring.cloud.gateway.filter.set-path.enabled=false",
					"spring.cloud.gateway.filter.secure-headers.enabled=false",
					"spring.cloud.gateway.filter.set-request-header.enabled=false",
					"spring.cloud.gateway.filter.set-request-host-header.enabled=false",
					"spring.cloud.gateway.filter.set-response-header.enabled=false",
					"spring.cloud.gateway.filter.rewrite-response-header.enabled=false",
					"spring.cloud.gateway.filter.rewrite-location-response-header.enabled=false",
					"spring.cloud.gateway.filter.rewrite-location.enabled=false",
					"spring.cloud.gateway.filter.set-status.enabled=false",
					"spring.cloud.gateway.filter.save-session.enabled=false",
					"spring.cloud.gateway.filter.strip-prefix.enabled=false",
					"spring.cloud.gateway.filter.request-header-to-request-uri.enabled=false",
					"spring.cloud.gateway.filter.request-size.enabled=false",
					"spring.cloud.gateway.filter.request-header-size.enabled=false",
					"spring.cloud.gateway.filter.circuit-breaker.enabled=false",
					"spring.cloud.gateway.filter.token-relay.enabled=false",
					"spring.cloud.gateway.filter.cache-request-body.enabled=false",
					"spring.cloud.gateway.filter.fallback-headers.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableAllFiltersByProperty {

		/** Gateway过滤器工厂列表（允许为null） */
		@Autowired(required = false)
		private List<GatewayFilterFactory<?>> gatewayFilters;

		/**
		 * shouldDisableAllBuiltInFilters - 验证所有内置过滤器被禁用
		 *
		 * 确认当所有内置过滤器都被禁用时，gatewayFilters为null
		 */
		@Test
		public void shouldDisableAllBuiltInFilters() {
			assertThat(gatewayFilters).isNull();
		}

	}

}
