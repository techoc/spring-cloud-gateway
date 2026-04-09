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

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RedisRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * Gateway Redis 自动配置类。
 * <p>
 * 当类路径中存在 Redis 相关依赖且 {@code ReactiveRedisTemplate} Bean 可用时， 自动配置基于 Redis
 * 的限流器、路由定义仓库等组件。
 * </p>
 *
 * @author Spencer Gibb
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(RedisReactiveAutoConfiguration.class)
@AutoConfigureBefore(GatewayAutoConfiguration.class)
@ConditionalOnBean(ReactiveRedisTemplate.class)
@ConditionalOnClass({ RedisTemplate.class, DispatcherHandler.class })
@ConditionalOnProperty(name = "spring.cloud.gateway.redis.enabled", matchIfMissing = true)
class GatewayRedisAutoConfiguration {

	/**
	 * 创建 Redis 请求限流脚本 Bean。
	 * <p>
	 * 加载 Lua 脚本（{@code request_rate_limiter.lua}）用于实现基于 Redis 的令牌桶限流算法。
	 * </p>
	 * @return Redis 脚本对象
	 */
	@Bean
	@SuppressWarnings("unchecked")
	public RedisScript redisRequestRateLimiterScript() {
		DefaultRedisScript redisScript = new DefaultRedisScript<>();
		redisScript.setScriptSource(
				new ResourceScriptSource(new ClassPathResource("META-INF/scripts/request_rate_limiter.lua")));
		redisScript.setResultType(List.class);
		return redisScript;
	}

	/**
	 * 创建 Redis 限流器 Bean。
	 * <p>
	 * 基于 Redis 和 Lua 脚本实现分布式令牌桶限流。
	 * </p>
	 * @param redisTemplate Redis 字符串操作模板
	 * @param redisScript 限流 Lua 脚本
	 * @param configurationService 配置服务
	 * @return Redis 限流器实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public RedisRateLimiter redisRateLimiter(ReactiveStringRedisTemplate redisTemplate,
			@Qualifier(RedisRateLimiter.REDIS_SCRIPT_NAME) RedisScript<List<Long>> redisScript,
			ConfigurationService configurationService) {
		return new RedisRateLimiter(redisTemplate, redisScript, configurationService);
	}

	/**
	 * 创建 Redis 路由定义仓库 Bean。 当启用
	 * spring.cloud.gateway.redis-route-definition-repository.enabled 配置时生效。
	 * @param reactiveRedisTemplate Reactive Redis 模板
	 * @return Redis 路由定义仓库
	 */
	@Bean
	@ConditionalOnProperty(value = "spring.cloud.gateway.redis-route-definition-repository.enabled",
			havingValue = "true")
	@ConditionalOnClass(ReactiveRedisTemplate.class)
	public RedisRouteDefinitionRepository redisRouteDefinitionRepository(
			ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate) {
		return new RedisRouteDefinitionRepository(reactiveRedisTemplate);
	}

	/**
	 * 创建用于路由定义存储的 Reactive Redis 模板。
	 * <p>
	 * 使用 StringRedisSerializer 作为键序列化器，Jackson2JsonRedisSerializer 作为值序列化器， 支持
	 * {@link RouteDefinition} 的 JSON 序列化。
	 * </p>
	 * @param factory Redis 响应式连接工厂
	 * @return 路由定义专用的 Reactive Redis 模板
	 */
	@Bean
	public ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisRouteDefinitionTemplate(
			ReactiveRedisConnectionFactory factory) {
		StringRedisSerializer keySerializer = new StringRedisSerializer();
		Jackson2JsonRedisSerializer<RouteDefinition> valueSerializer = new Jackson2JsonRedisSerializer<>(
				RouteDefinition.class);
		RedisSerializationContext.RedisSerializationContextBuilder<String, RouteDefinition> builder = RedisSerializationContext
				.newSerializationContext(keySerializer);
		RedisSerializationContext<String, RouteDefinition> context = builder.value(valueSerializer).build();

		return new ReactiveRedisTemplate<>(factory, context);
	}

}
