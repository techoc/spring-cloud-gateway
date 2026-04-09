/*
 * Copyright 2013-2021 the original author or authors.
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayRedisRouteDefinitionRepositoryEnabledByPropertyTests - 通过属性启用Redis路由定义仓库测试类
 *
 * 本测试类验证当配置spring.cloud.gateway.redis-route-definition-repository.enabled=true时： -
 * RedisRouteDefinitionRepository Bean会被正确创建 - 依赖Testcontainers启动Redis容器进行测试
 *
 * @author test
 */
@SpringBootTest(classes = GatewayRedisAutoConfigurationTests.Config.class,
		properties = "spring.cloud.gateway.redis-route-definition-repository.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Testcontainers
@Tag("DockerRequired")
public class GatewayRedisRouteDefinitionRepositoryEnabledByPropertyTests {

	/** Redis Testcontainers容器 */
	@Container
	public static GenericContainer redis = new GenericContainer<>("redis:5.0.14-alpine").withExposedPorts(6379);

	/** Redis路由定义仓库 */
	@Autowired(required = false)
	private RedisRouteDefinitionRepository redisRouteDefinitionRepository;

	/**
	 * startRedisContainer - 启动Redis容器
	 *
	 * 在所有测试方法执行前启动Redis容器
	 */
	@BeforeAll
	public static void startRedisContainer() {
		redis.start();
	}

	/**
	 * containerProperties - 配置Redis动态属性
	 *
	 * 将Testcontainers的Redis容器配置动态注册到Spring测试环境中
	 * @param registry 动态属性注册表
	 */
	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.redis.host", redis::getContainerIpAddress);
		registry.add("spring.redis.port", redis::getFirstMappedPort);
	}

	/**
	 * redisRouteDefinitionRepository - 验证RedisRouteDefinitionRepository不为空
	 *
	 * 确认当通过属性启用时，RedisRouteDefinitionRepository Bean被正确创建
	 */
	@Test
	public void redisRouteDefinitionRepository() {
		assertThat(redisRouteDefinitionRepository).isNotNull();
	}

}
