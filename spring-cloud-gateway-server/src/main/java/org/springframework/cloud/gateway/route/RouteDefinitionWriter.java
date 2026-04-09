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

import reactor.core.publisher.Mono;

/**
 * 路由定义写入接口，提供路由定义的保存和删除操作。
 * <p>
 * 该接口定义了路由定义的写操作契约，用于动态管理路由配置。 通常与 {@link RouteDefinitionLocator} 配合使用，实现路由定义的完整生命周期管理。
 * <p>
 * 所有操作均返回 {@link Mono}{@code <Void>}，表明是响应式的异步操作。
 *
 * @author Spencer Gibb
 */
public interface RouteDefinitionWriter {

	/**
	 * 保存（新增或更新）一条路由定义。
	 * <p>
	 * 如果路由定义已存在（根据 ID 判断），则更新；否则新增。
	 * @param route 要保存的路由定义，包装在 Mono 中以支持响应式流
	 * @return 操作完成的信号（{@link Mono}{@code <Void>}）
	 */
	Mono<Void> save(Mono<RouteDefinition> route);

	/**
	 * 删除指定 ID 的路由定义。
	 * <p>
	 * 如果路由定义不存在，通常应抛出
	 * {@link org.springframework.cloud.gateway.support.NotFoundException}。
	 * @param routeId 要删除的路由 ID，包装在 Mono 中以支持响应式流
	 * @return 操作完成的信号（{@link Mono}{@code <Void>}）
	 */
	Mono<Void> delete(Mono<String> routeId);

}
