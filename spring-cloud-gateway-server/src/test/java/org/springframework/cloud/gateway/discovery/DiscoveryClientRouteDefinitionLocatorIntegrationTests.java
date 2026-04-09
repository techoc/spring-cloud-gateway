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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DiscoveryClientRouteDefinitionLocatorIntegrationTests - 服务发现路由定位器集成测试类
 *
 * 本测试类验证DiscoveryClientRouteDefinitionLocator的服务发现与路由动态更新功能： - 启用服务发现路由定位器 -
 * 当新的服务实例出现时自动添加路由 - 通过HeartbeatEvent触发路由刷新
 *
 * @author test
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = DiscoveryClientRouteDefinitionLocatorIntegrationTests.Config.class,
		properties = { "spring.cloud.gateway.discovery.locator.enabled=true",
				"spring.cloud.gateway.discovery.locator.route-id-prefix=test__" })
public class DiscoveryClientRouteDefinitionLocatorIntegrationTests {

	/** 路由定位器 - 用于验证路由的动态变化 */
	@Autowired
	private RouteLocator routeLocator;

	/** 应用事件发布器 - 用于发布心跳事件触发路由刷新 */
	@Autowired
	private ApplicationEventPublisher publisher;

	/** 测试用服务发现客户端 */
	@Autowired
	private TestDiscoveryClient discoveryClient;

	/**
	 * newServiceAddsRoute - 测试新服务添加路由
	 *
	 * 验证当服务发现客户端返回新服务时，通过心跳事件能够动态添加新路由： 1. 初始状态只有1个服务，路由数为1 2.
	 * 模拟新增服务（调用discoveryClient.multiple()） 3. 发布HeartbeatEvent触发路由刷新 4. 验证路由数增加到2
	 * @throws Exception 测试异常
	 */
	@Test
	public void newServiceAddsRoute() throws Exception {
		List<Route> routes = routeLocator.getRoutes().filter(route -> route.getId().startsWith("test__")).collectList()
				.block();
		assertThat(routes).hasSize(1);

		discoveryClient.multiple();

		publisher.publishEvent(new HeartbeatEvent(this, 1L));

		Thread.sleep(2000);

		routes = routeLocator.getRoutes().filter(route -> route.getId().startsWith("test__")).collectList().block();
		assertThat(routes).hasSize(2);
	}

	/**
	 * Config - 测试配置类
	 *
	 * 提供测试所需的Spring Boot配置
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

		/**
		 * discoveryClient - 测试用服务发现客户端
		 * @return TestDiscoveryClient实例
		 */
		@Bean
		TestDiscoveryClient discoveryClient() {
			return new TestDiscoveryClient();
		}

	}

	/**
	 * TestDiscoveryClient - 测试用响应式服务发现客户端实现
	 *
	 * 模拟ReactiveDiscoveryClient，支持动态切换返回的服务列表
	 */
	private static class TestDiscoveryClient implements ReactiveDiscoveryClient {

		/** 控制返回单个还是多个服务 */
		AtomicBoolean single = new AtomicBoolean(true);

		/** service1的服务实例 */
		DefaultServiceInstance instance1 = new DefaultServiceInstance("service1_1", "service1", "localhost", 8001,
				false);

		/** service2的服务实例 */
		DefaultServiceInstance instance2 = new DefaultServiceInstance("service2_1", "service2", "localhost", 8001,
				false);

		/**
		 * multiple - 切换到返回多个服务
		 */
		public void multiple() {
			single.set(false);
		}

		@Override
		public String description() {
			return null;
		}

		/**
		 * getInstances - 获取指定服务的实例列表
		 * @param serviceId 服务ID
		 * @return 服务实例的Flux流
		 */
		@Override
		public Flux<ServiceInstance> getInstances(String serviceId) {
			if (serviceId.equals("service1")) {
				return Flux.just(instance1);
			}
			if (serviceId.equals("service2")) {
				return Flux.just(instance2);
			}
			return Flux.empty();
		}

		/**
		 * getServices - 获取所有服务ID列表
		 * @return 服务ID的Flux流
		 */
		@Override
		public Flux<String> getServices() {
			if (single.get()) {
				return Flux.just("service1");
			}
			return Flux.just("service1", "service2");
		}

	}

}
