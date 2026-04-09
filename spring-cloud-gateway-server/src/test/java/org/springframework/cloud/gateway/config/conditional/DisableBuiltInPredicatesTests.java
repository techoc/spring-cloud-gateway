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
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DisableBuiltInPredicatesTests - 禁用内置路由谓词测试类
 *
 * 本测试类验证Gateway内置路由谓词的启用/禁用配置功能： - 默认情况下所有内置路由谓词都会被注册 - 可以通过属性禁用特定的路由谓词 -
 * 可以通过属性禁用所有内置路由谓词
 *
 * 通过spring.cloud.gateway.predicate.{predicate-name}.enabled=false配置即可禁用对应谓词
 *
 * @author test
 */
@RunWith(Enclosed.class)
public class DisableBuiltInPredicatesTests {

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
	 * RoutePredicateDefault - 测试默认路由谓词注册
	 *
	 * 验证在默认情况下，所有内置路由谓词都会被正确注册
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class RoutePredicateDefault {

		/** 路由谓词工厂列表 */
		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		/**
		 * shouldInjectBuiltInPredicates - 验证内置路由谓词被注入
		 *
		 * 确认至少注册了13个内置路由谓词
		 */
		@Test
		public void shouldInjectBuiltInPredicates() {
			assertThat(predicates).hasSizeGreaterThanOrEqualTo(13);
		}

	}

	/**
	 * DisableSpecificsPredicatesByProperty - 测试通过属性禁用特定路由谓词
	 *
	 * 验证可以通过属性禁用特定的路由谓词： - AfterRoutePredicateFactory - BeforeRoutePredicateFactory
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = { "spring.cloud.gateway.predicate.after.enabled=false",
			"spring.cloud.gateway.predicate.before.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableSpecificsPredicatesByProperty {

		/** 路由谓词工厂列表 */
		@Autowired
		private List<RoutePredicateFactory<?>> predicates;

		/**
		 * shouldInjectOnlyEnabledBuiltInPredicates - 验证只注册了启用的路由谓词
		 *
		 * 确认predicates列表不为空，但其中不包含被禁用的谓词
		 */
		@Test
		public void shouldInjectOnlyEnabledBuiltInPredicates() {
			assertThat(predicates).hasSizeGreaterThan(0);
			assertThat(predicates).allSatisfy(filter -> assertThat(filter)
					.isNotInstanceOfAny(AfterRoutePredicateFactory.class, BeforeRoutePredicateFactory.class));
		}

	}

	/**
	 * DisableAllPredicatesByProperty - 测试通过属性禁用所有路由谓词
	 *
	 * 验证可以通过配置禁用所有内置路由谓词
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = { "spring.cloud.gateway.predicate.after.enabled=false",
			"spring.cloud.gateway.predicate.before.enabled=false",
			"spring.cloud.gateway.predicate.between.enabled=false",
			"spring.cloud.gateway.predicate.cookie.enabled=false",
			"spring.cloud.gateway.predicate.header.enabled=false", "spring.cloud.gateway.predicate.host.enabled=false",
			"spring.cloud.gateway.predicate.method.enabled=false", "spring.cloud.gateway.predicate.path.enabled=false",
			"spring.cloud.gateway.predicate.query.enabled=false",
			"spring.cloud.gateway.predicate.read-body.enabled=false",
			"spring.cloud.gateway.predicate.remote-addr.enabled=false",
			"spring.cloud.gateway.predicate.xforwarded-remote-addr.enabled=false",
			"spring.cloud.gateway.predicate.weight.enabled=false",
			"spring.cloud.gateway.predicate.cloud-foundry-route-service.enabled=false" })
	@ActiveProfiles("disable-components")
	public static class DisableAllPredicatesByProperty {

		/** 路由谓词工厂列表（允许为null） */
		@Autowired(required = false)
		private List<RoutePredicateFactory<?>> predicates;

		/**
		 * shouldDisableAllBuiltInPredicates - 验证所有内置路由谓词被禁用
		 *
		 * 确认当所有内置路由谓词都被禁用时，predicates为null
		 */
		@Test
		public void shouldDisableAllBuiltInPredicates() {
			assertThat(predicates).isNull();
		}

	}

}
