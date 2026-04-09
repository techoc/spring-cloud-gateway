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

package org.springframework.cloud.gateway.discovery;

import java.util.List;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReactiveGatewayDiscoveryClientAutoConfigurationTests - 响应式服务发现自动配置测试类
 *
 * 本测试类验证ReactiveGatewayDiscoveryClientAutoConfiguration的配置功能： - 通过属性启用服务发现路由定位器 -
 * 404模式配置验证 - 默认禁用状态的验证
 *
 * @author test
 */
@RunWith(Enclosed.class)
public class ReactiveGatewayDiscoveryClientAutoConfigurationTests {

	/**
	 * EnabledByProperty - 测试通过属性启用服务发现
	 *
	 * 验证当配置spring.cloud.gateway.discovery.locator.enabled=true时： -
	 * DiscoveryClientRouteDefinitionLocator Bean被创建 - 路由定义数量正确 - use404配置正确
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class,
			properties = { "spring.cloud.gateway.discovery.locator.enabled=true",
					"spring.cloud.gateway.loadbalancer.use404=true",
					"spring.cloud.discovery.client.simple.instances.service[0].uri=https://service1:443" })
	public static class EnabledByProperty {

		/** 服务发现路由定义定位器 */
		@Autowired(required = false)
		private DiscoveryClientRouteDefinitionLocator locator;

		/** 负载均衡器属性 */
		@Autowired(required = false)
		private GatewayLoadBalancerProperties properties;

		/**
		 * routeLocatorBeanExists - 验证路由定位器Bean存在
		 *
		 * 确认DiscoveryClientRouteDefinitionLocator被正确创建 并且从服务注册表创建了1条路由定义
		 */
		@Test
		public void routeLocatorBeanExists() {
			assertThat(locator).as("DiscoveryClientRouteDefinitionLocator was null").isNotNull();
			List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();
			assertThat(definitions).hasSize(1);
		}

		/**
		 * use404 - 验证404模式配置
		 *
		 * 确认GatewayLoadBalancerProperties的use404属性被正确设置为true
		 */
		@Test
		public void use404() {
			assertThat(properties.isUse404()).isTrue();
		}

	}

	/**
	 * DisabledByDefault - 测试默认禁用状态
	 *
	 * 验证在默认情况下DiscoveryClientRouteDefinitionLocator不会被创建
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class DisabledByDefault {

		/** 服务发现路由定义定位器 */
		@Autowired(required = false)
		private DiscoveryClientRouteDefinitionLocator locator;

		/**
		 * routeLocatorBeanMissing - 验证路由定位器Bean不存在
		 *
		 * 确认默认情况下DiscoveryClientRouteDefinitionLocator为null
		 */
		@Test
		public void routeLocatorBeanMissing() {
			assertThat(locator).as("DiscoveryClientRouteDefinitionLocator exists").isNull();
		}

	}

	/**
	 * Config - 基础测试配置类
	 *
	 * 提供最小化的Spring Boot配置用于测试
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

	}

}
