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

/**
 * 路由定义仓库接口，继承自 {@link RouteDefinitionLocator} 和 {@link RouteDefinitionWriter}。
 * <p>
 * 该接口是路由定义的完整 CRUD 抽象，同时支持读取和写入操作。 实现类通常作为路由定义的持久化层，支持增删改查操作。
 * <p>
 * 常见实现包括：
 * <ul>
 * <li>{@link InMemoryRouteDefinitionRepository} — 基于内存的实现</li>
 * <li>{@link RedisRouteDefinitionRepository} — 基于 Redis 的实现</li>
 * </ul>
 * <p>
 * 可通过 Spring 的自动配置机制注入自定义的仓库实现。
 *
 * @author Spencer Gibb
 */
public interface RouteDefinitionRepository extends RouteDefinitionLocator, RouteDefinitionWriter {

}
