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

package org.springframework.cloud.gateway.event;

import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.context.ApplicationEvent;

/**
 * 权重路由配置定义事件。
 *
 * <p>
 * 当网关初始化或动态配置权重路由策略时（通过 WeightRoutePredicateFactory 和 WeightResolverConfig），
 * 会发布此事件。事件携带完整的权重配置对象 {@link WeightConfig}，监听器可据此执行以下操作：
 * </p>
 *
 * <ul>
 * <li>构建或更新权重分组路由映射</li>
 * <li>记录或上报权重路由配置元数据</li>
 * <li>执行权重路由的动态变更逻辑</li>
 * </ul>
 *
 * <p>
 * <strong>权重路由使用说明：</strong>
 * </p>
 * <ul>
 * <li>权重路由用于实现灰度发布、金丝雀发布和流量分配</li>
 * <li>同一 group 下的多个路由按 weight 权重值分配流量比例</li>
 * <li>配置示例：group=version, route1.weight=80, route2.weight=20 表示 80% 流量走 route1</li>
 * </ul>
 *
 * @see WeightConfig
 * @see WeightRoutePredicateFactory
 */
public class WeightDefinedEvent extends ApplicationEvent {

	/**
	 * 权重路由的完整配置对象。 包含分组名称（group）、路由 ID（routeId）及权重值（weight）等核心属性。
	 */
	private final WeightConfig weightConfig;

	/**
	 * 构造一个权重路由配置定义事件。
	 * @param source 事件源对象（通常为发布事件的组件）
	 * @param weightConfig 权重路由配置对象（非 null）
	 */
	public WeightDefinedEvent(Object source, WeightConfig weightConfig) {
		super(source);
		this.weightConfig = weightConfig;
	}

	/**
	 * 获取权重路由的完整配置对象。
	 * @return WeightConfig 配置实例，包含 group、routeId、weight 等属性
	 */
	public WeightConfig getWeightConfig() {
		return weightConfig;
	}

}
