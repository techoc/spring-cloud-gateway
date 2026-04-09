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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepositoryTests;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayRedisAutoConfigurationTests - Redis自动配置测试类
 *
 * 本测试类验证GatewayRedisAutoConfiguration的Redis相关配置功能： - RedisRateLimiter和Redis限流脚本的自动配置 -
 * Redis启用/禁用开关 - RedisRouteDefinitionRepository的启用/禁用配置
 *
 * @author test
 */
public class GatewayRedisAutoConfigurationTests {

	/**
	 * Config - 测试配置类
	 *
	 * 提供测试所需的测试过滤器工厂和谓词工厂Bean
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

		// TODO: figure out why I need these
		/**
		 * testGatewayFilterFactory - 测试用网关过滤器工厂
		 * @return TestGatewayFilterFactory实例
		 */
		@Bean
		RedisRouteDefinitionRepositoryTests.TestGatewayFilterFactory testGatewayFilterFactory() {
			return new RedisRouteDefinitionRepositoryTests.TestGatewayFilterFactory();
		}

		/**
		 * testFilterGatewayFilterFactory - 测试用过滤器网关过滤器工厂
		 * @return TestFilterGatewayFilterFactory实例
		 */
		@Bean
		RedisRouteDefinitionRepositoryTests.TestFilterGatewayFilterFactory testFilterGatewayFilterFactory() {
			return new RedisRouteDefinitionRepositoryTests.TestFilterGatewayFilterFactory();
		}

		/**
		 * testRoutePredicateFactory - 测试用路由谓词工厂
		 * @return TestRoutePredicateFactory实例
		 */
		@Bean
		RedisRouteDefinitionRepositoryTests.TestRoutePredicateFactory testRoutePredicateFactory() {
			return new RedisRouteDefinitionRepositoryTests.TestRoutePredicateFactory();
		}

	}

	/**
	 * EnabledByDefault - 测试Redis Bean默认启用
	 *
	 * 验证在默认情况下，Redis相关的Bean会被正确创建
	 */
	@Nested
	@SpringBootTest(classes = Config.class)
	class EnabledByDefault {

		/** Redis限流脚本 */
		@Autowired(required = false)
		private RedisScript redisRequestRateLimiterScript;

		/** Redis限流器 */
		@Autowired(required = false)
		private RedisRateLimiter redisRateLimiter;

		/**
		 * shouldInjectRedisBeans - 验证Redis Bean被注入
		 *
		 * 确认RedisRateLimiter和Redis限流脚本Bean都被正确创建
		 */
		@Test
		public void shouldInjectRedisBeans() {
			assertThat(redisRequestRateLimiterScript).isNotNull();
			assertThat(redisRateLimiter).isNotNull();
		}

	}

	/**
	 * DisabledByProperty - 测试通过属性禁用Redis
	 *
	 * 验证当设置spring.cloud.gateway.redis.enabled=false时 Redis相关的Bean不会被创建
	 */
	@Nested
	@SpringBootTest(classes = Config.class, properties = "spring.cloud.gateway.redis.enabled=false")
	class DisabledByProperty {

		/** Redis限流脚本 */
		@Autowired(required = false)
		private RedisScript redisRequestRateLimiterScript;

		/** Redis限流器 */
		@Autowired(required = false)
		private RedisRateLimiter redisRateLimiter;

		/**
		 * shouldDisableRedisBeans - 验证Redis Bean被禁用
		 *
		 * 确认RedisRateLimiter和Redis限流脚本Bean都为null
		 */
		@Test
		public void shouldDisableRedisBeans() {
			assertThat(redisRequestRateLimiterScript).isNull();
			assertThat(redisRateLimiter).isNull();
		}

	}

	/**
	 * RedisRouteDefinitionRepositoryDisabledByProperty - 测试通过属性禁用Redis路由定义仓库
	 *
	 * 验证当设置spring.cloud.gateway.redis-route-definition-repository.enabled=false时
	 * RedisRouteDefinitionRepository Bean不会被创建
	 *
	 * @author Dennis Menge
	 */
	@Nested
	@SpringBootTest(classes = GatewayRedisAutoConfigurationTests.Config.class,
			properties = "spring.cloud.gateway.redis-route-definition-repository.enabled=false")
	@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
	class RedisRouteDefinitionRepositoryDisabledByProperty {

		/** Redis路由定义仓库 */
		@Autowired(required = false)
		private RedisRouteDefinitionRepository redisRouteDefinitionRepository;

		/**
		 * redisRouteDefinitionRepository - 验证RedisRouteDefinitionRepository为null
		 *
		 * 确认当通过属性禁用时，RedisRouteDefinitionRepository Bean不会被创建
		 */
		@Test
		public void redisRouteDefinitionRepository() {
			assertThat(redisRouteDefinitionRepository).isNull();
		}

	}

}
