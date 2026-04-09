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

import reactor.core.publisher.Flux;

/**
 * 路由定义定位器接口，负责提供路由定义列表。
 * <p>
 * 这是 Spring Cloud Gateway 路由系统的配置层接口，用于获取原始的路由配置信息（{@link RouteDefinition}）。
 * 路由定义通常来源于配置文件、数据库、服务发现等，尚未转换为运行时的 {@link Route} 对象。
 * <p>
 * 不同的实现类可以从不同来源加载路由定义，如：
 * <ul>
 * <li>{@link InMemoryRouteDefinitionRepository} — 内存存储</li>
 * <li>{@link RedisRouteDefinitionRepository} — Redis 存储</li>
 * <li>{@link CompositeRouteDefinitionLocator} — 组合多个定位器的结果</li>
 * </ul>
 * <p>
 * 注意：该接口返回的是 {@link Flux}，表明路由定义获取是响应式的。
 *
 * @author Spencer Gibb
 */
public interface RouteDefinitionLocator {

	/**
	 * 获取所有可用的路由定义列表。
	 * <p>
	 * 返回的 Flux 流包含当前系统中所有已配置/已加载的路由定义。 该方法可能会被多次调用，实现类应确保线程安全。
	 * @return 包含所有 {@link RouteDefinition} 的响应式流
	 */
	Flux<RouteDefinition> getRouteDefinitions();

}
