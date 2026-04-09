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
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RemoveCachedBodyFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DisableBuiltInGlobalFiltersTests - 禁用内置全局过滤器测试类
 *
 * 本测试类验证Gateway内置全局过滤器的启用/禁用配置功能： - 默认情况下所有内置全局过滤器都会被注册 - 可以通过属性禁用特定的全局过滤器 -
 * 可以通过属性禁用所有内置全局过滤器
 *
 * 通过spring.cloud.gateway.global-filter.{filter-name}.enabled=false配置即可禁用对应过滤器
 *
 * @author test
 */
@RunWith(Enclosed.class)
public class DisableBuiltInGlobalFiltersTests {

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
	 * GlobalFilterDefault - 测试默认全局过滤器注册
	 *
	 * 验证在默认情况下，所有内置全局过滤器都会被正确注册
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class GlobalFilterDefault {

		/** 全局过滤器列表 */
		@Autowired
		private List<GlobalFilter> globalFilters;

		/**
		 * shouldInjectBuiltInFilters - 验证内置全局过滤器被注入
		 *
		 * 确认至少注册了10个内置全局过滤器
		 */
		@Test
		public void shouldInjectBuiltInFilters() {
			assertThat(globalFilters).hasSizeGreaterThanOrEqualTo(10);
		}

	}

	/**
	 * DisableSpecificsFiltersByProperty - 测试通过属性禁用特定全局过滤器
	 *
	 * 验证可以通过属性禁用特定的全局过滤器： - RemoveCachedBodyFilter - RouteToRequestUrlFilter
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.global-filter.remove-cached-body.enabled=false",
					"spring.cloud.gateway.global-filter.route-to-request-url.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableSpecificsFiltersByProperty {

		/** 全局过滤器列表 */
		@Autowired
		private List<GlobalFilter> globalFilters;

		/**
		 * shouldInjectOnlyEnabledBuiltInFilters - 验证只注册了启用的全局过滤器
		 *
		 * 确认globalFilters列表不为空，但其中不包含被禁用的过滤器
		 */
		@Test
		public void shouldInjectOnlyEnabledBuiltInFilters() {
			assertThat(globalFilters).hasSizeGreaterThan(0);
			assertThat(globalFilters).allSatisfy(filter -> assertThat(filter)
					.isNotInstanceOfAny(RemoveCachedBodyFilter.class, RouteToRequestUrlFilter.class));
		}

	}

	/**
	 * DisableAllGlobalFiltersByProperty - 测试通过属性禁用所有全局过滤器
	 *
	 * 验证可以通过配置禁用所有内置全局过滤器
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.global-filter.adapt-cached-body.enabled=false",
					"spring.cloud.gateway.global-filter.remove-cached-body.enabled=false",
					"spring.cloud.gateway.global-filter.route-to-request-url.enabled=false",
					"spring.cloud.gateway.global-filter.forward-routing.enabled=false",
					"spring.cloud.gateway.global-filter.forward-path.enabled=false",
					"spring.cloud.gateway.global-filter.websocket-routing.enabled=false",
					"spring.cloud.gateway.global-filter.netty-write-response.enabled=false",
					"spring.cloud.gateway.global-filter.netty-routing.enabled=false",
					"spring.cloud.gateway.global-filter.reactive-load-balancer-client.enabled=false",
					"spring.cloud.gateway.global-filter.load-balancer-client.enabled=false",
					"spring.cloud.gateway.global-filter.load-balancer-service-instance-cookie.enabled=false",
					"spring.cloud.gateway.metrics.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableAllGlobalFiltersByProperty {

		/** 全局过滤器列表（允许为null） */
		@Autowired(required = false)
		private List<GlobalFilter> globalFilters;

		/**
		 * shouldDisableAllBuiltInFilters - 验证所有内置全局过滤器被禁用
		 *
		 * 确认当所有内置全局过滤器都被禁用时，globalFilters为null
		 */
		@Test
		public void shouldDisableAllBuiltInFilters() {
			assertThat(globalFilters).isNull();
		}

	}

}
