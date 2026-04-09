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

package org.springframework.cloud.gateway.route;

import java.util.LinkedHashMap;
import java.util.Map;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.util.ObjectUtils;

import static java.util.Collections.synchronizedMap;

/**
 * 基于内存的路由定义仓库实现。
 * <p>
 * 该类提供了 {@link RouteDefinitionRepository} 的内存存储实现，使用 {@link LinkedHashMap}
 * 存储路由定义，保证插入顺序。所有操作都是线程安全的（使用 {@link java.util.Collections#synchronizedMap(Map)} 包装）。
 * <p>
 * 适用场景：
 * <ul>
 * <li>开发测试环境</li>
 * <li>路由数量较少且不需要持久化的场景</li>
 * <li>作为其他持久化实现的 fallback</li>
 * </ul>
 * <p>
 * 注意：应用重启后数据会丢失，生产环境建议使用 {@link RedisRouteDefinitionRepository} 等持久化实现。
 *
 * @author Spencer Gibb
 */
public class InMemoryRouteDefinitionRepository implements RouteDefinitionRepository {

	/**
	 * 内部存储，使用同步的 LinkedHashMap 保证线程安全和插入顺序。 键为路由 ID，值为路由定义对象。
	 */
	private final Map<String, RouteDefinition> routes = synchronizedMap(new LinkedHashMap<String, RouteDefinition>());

	/**
	 * 保存（新增或更新）一条路由定义。
	 * <p>
	 * 如果路由 ID 为空，将抛出 {@link IllegalArgumentException}。 如果路由已存在，则更新；否则新增。
	 * @param route 要保存的路由定义
	 * @return 操作完成的信号（{@link Mono}{@code <Void>}）
	 * @throws IllegalArgumentException 路由 ID 为空时抛出
	 */
	@Override
	public Mono<Void> save(Mono<RouteDefinition> route) {
		return route.flatMap(r -> {
			if (ObjectUtils.isEmpty(r.getId())) {
				return Mono.error(new IllegalArgumentException("id may not be empty"));
			}
			routes.put(r.getId(), r);
			return Mono.empty();
		});
	}

	/**
	 * 删除指定 ID 的路由定义。
	 * <p>
	 * 如果路由定义不存在，将抛出 {@link NotFoundException}。
	 * @param routeId 要删除的路由 ID
	 * @return 操作完成的信号（{@link Mono}{@code <Void>}）
	 * @throws NotFoundException 路由定义不存在时抛出
	 */
	@Override
	public Mono<Void> delete(Mono<String> routeId) {
		return routeId.flatMap(id -> {
			if (routes.containsKey(id)) {
				routes.remove(id);
				return Mono.empty();
			}
			return Mono.defer(() -> Mono.error(new NotFoundException("RouteDefinition not found: " + routeId)));
		});
	}

	/**
	 * 获取所有路由定义。
	 * <p>
	 * 返回的是内部 Map 的安全副本，避免并发修改问题。
	 * @return 包含所有路由定义的响应式流
	 */
	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		Map<String, RouteDefinition> routesSafeCopy = new LinkedHashMap<>(routes);
		return Flux.fromIterable(routesSafeCopy.values());
	}

}
