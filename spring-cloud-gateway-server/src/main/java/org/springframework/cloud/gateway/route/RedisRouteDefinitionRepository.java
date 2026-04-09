/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.gateway.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

/**
 * 基于 Redis 的路由定义仓库实现。
 * <p>
 * 该类提供了 {@link RouteDefinitionRepository} 的 Redis 持久化实现，支持：
 * <ul>
 * <li>将路由定义存储在 Redis 中，实现跨实例共享</li>
 * <li>应用重启后路由配置不丢失</li>
 * <li>支持动态路由的分布式管理</li>
 * </ul>
 * <p>
 * 存储格式：键名为 {@code routedefinition_<routeId>}，值为序列化的 {@link RouteDefinition} 对象。
 * <p>
 * 注意：需要配置 {@link ReactiveRedisTemplate}{@code <String, RouteDefinition>} Bean。
 *
 * @author Dennis Menge
 * @author lzhpo
 */
@Repository
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

	/** 日志记录器 */
	private static final Logger log = LoggerFactory.getLogger(RedisRouteDefinitionRepository.class);

	/**
	 * Redis 键名前缀，用于区分路由定义数据与其他数据。
	 */
	private static final String ROUTEDEFINITION_REDIS_KEY_PREFIX_QUERY = "routedefinition_";

	/** 响应式 Redis 模板，用于操作 Redis */
	private ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate;

	/** 响应式值操作，用于简化 Redis 值的读写 */
	private ReactiveValueOperations<String, RouteDefinition> routeDefinitionReactiveValueOperations;

	/**
	 * 构造方法，初始化 Redis 操作模板。
	 * @param reactiveRedisTemplate 响应式 Redis 模板
	 */
	public RedisRouteDefinitionRepository(ReactiveRedisTemplate<String, RouteDefinition> reactiveRedisTemplate) {
		this.reactiveRedisTemplate = reactiveRedisTemplate;
		this.routeDefinitionReactiveValueOperations = reactiveRedisTemplate.opsForValue();
	}

	/**
	 * 从 Redis 获取所有路由定义。
	 * <p>
	 * 使用 SCAN 命令遍历所有匹配 {@code routedefinition_*} 模式的键，然后批量获取值。 遇到错误时会记录日志并继续处理其他路由定义。
	 * @return 包含所有路由定义的响应式流
	 */
	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return reactiveRedisTemplate.scan(ScanOptions.scanOptions().match(createKey("*")).build())
				.flatMap(key -> reactiveRedisTemplate.opsForValue().get(key))
				.onErrorContinue((throwable, routeDefinition) -> {
					if (log.isErrorEnabled()) {
						log.error("get routes from redis error cause : {}", throwable.toString(), throwable);
					}
				});
	}

	/**
	 * 保存（新增或更新）一条路由定义到 Redis。
	 * <p>
	 * 键名为 {@code routedefinition_<routeId>}，值为序列化的路由定义对象。
	 * @param route 要保存的路由定义
	 * @return 操作完成的信号（{@link Mono}{@code <Void>}）
	 * @throws RuntimeException 保存失败时抛出
	 */
	@Override
	public Mono<Void> save(Mono<RouteDefinition> route) {
		return route.flatMap(routeDefinition -> routeDefinitionReactiveValueOperations
				.set(createKey(routeDefinition.getId()), routeDefinition).flatMap(success -> {
					if (success) {
						return Mono.empty();
					}
					return Mono.defer(() -> Mono.error(new RuntimeException(
							String.format("Could not add route to redis repository: %s", routeDefinition))));
				}));
	}

	/**
	 * 从 Redis 删除指定 ID 的路由定义。
	 * <p>
	 * 如果路由定义不存在，将抛出 {@link NotFoundException}。
	 * @param routeId 要删除的路由 ID
	 * @return 操作完成的信号（{@link Mono}{@code <Void>}）
	 * @throws NotFoundException 路由定义不存在时抛出
	 */
	@Override
	public Mono<Void> delete(Mono<String> routeId) {
		return routeId.flatMap(id -> routeDefinitionReactiveValueOperations.delete(createKey(id)).flatMap(success -> {
			if (success) {
				return Mono.empty();
			}
			return Mono.defer(() -> Mono.error(new NotFoundException(
					String.format("Could not remove route from redis repository with id: %s", routeId))));
		}));
	}

	/**
	 * 生成 Redis 键名。
	 * <p>
	 * 格式：{@code routedefinition_<routeId>}
	 * @param routeId 路由 ID
	 * @return 完整的 Redis 键名
	 */
	private String createKey(String routeId) {
		return ROUTEDEFINITION_REDIS_KEY_PREFIX_QUERY + routeId;
	}

}
