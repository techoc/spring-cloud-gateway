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

package org.springframework.cloud.gateway.support;

import java.util.Map;

/**
 * 有状态可配置接口，继承自 {@link Configurable} 接口。
 * <p>
 * 与无状态的 {@link Configurable} 不同，该接口表示实现类维护了多个配置实例， 通常以路由 ID 为 key 进行组织。这允许同一个组件（如过滤器工厂）
 * 为不同路由持有不同的配置。
 * </p>
 * <p>
 * 使用场景：
 * <ul>
 * <li>按路由配置不同的超时策略</li>
 * <li>按路由配置不同的重试次数</li>
 * <li>其他需要为每个路由单独维护配置的场景</li>
 * </ul>
 * </p>
 *
 * @param <C> 配置对象的类型参数
 * @see AbstractStatefulConfigurable
 * @see Configurable
 */
public interface StatefulConfigurable<C> extends Configurable<C> {

	/**
	 * 返回以路由 ID 为 key 的配置映射。
	 * <p>
	 * 该方法返回的 Map 以路由唯一标识符（routeId）为键， 对应的配置对象为值。通过路由 ID，可以快速获取或更新 特定路由的配置。
	 * </p>
	 * @return 路由 ID 到配置对象的映射，不会为 null
	 */
	Map<String, C> getConfig();

}
