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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

/**
 * 路由权重配置类，用于实现基于权重的负载均衡。
 * <p>
 * 该类封装了权重分组、路由 ID 和权重值三个核心属性，通过配置不同路由的权重， 可以实现灰度发布、金丝雀发布等高级路由策略。
 * </p>
 * <p>
 * 配置格式示例（YAML）： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: route-a
 *           uri: https://service-a.example.com
 *           predicates:
 *             - Weight=group1,1
 *         - id: route-b
 *           uri: https://service-b.example.com
 *           predicates:
 *             - Weight=group1,9
 * </pre>
 * <p>
 * 上述配置表示：group1 组内，90% 的流量路由到 service-b，10% 路由到 service-a。
 * </p>
 *
 * @see org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory
 */
@Validated
public class WeightConfig {

	/**
	 * 配置前缀，用于 Spring 配置属性的绑定。
	 */
	public static final String CONFIG_PREFIX = "weight";

	/**
	 * 权重分组标识，用于将多个路由归入同一权重组进行流量分配。 同一组内的路由按权重比例分配流量。
	 */
	@NotEmpty
	private String group;

	/**
	 * 关联的路由唯一标识符。
	 */
	private String routeId;

	/**
	 * 权重值，数值越大表示分配的流量比例越高。 最小值为 0，表示该路由不处理任何请求。
	 */
	@Min(0)
	private int weight;

	/**
	 * 默认构造函数，供框架反射实例化使用。
	 */
	private WeightConfig() {
	}

	/**
	 * 完整构造函数，创建包含所有属性的权重配置。
	 * @param group 权重分组标识，不能为空
	 * @param routeId 路由唯一标识符
	 * @param weight 权重值，必须大于等于 0
	 */
	public WeightConfig(String group, String routeId, int weight) {
		this.routeId = routeId;
		this.group = group;
		this.weight = weight;
	}

	/**
	 * 仅指定路由 ID 的构造函数，组名和权重需要后续设置。
	 * @param routeId 路由唯一标识符
	 */
	public WeightConfig(String routeId) {
		this.routeId = routeId;
	}

	/**
	 * 获取权重分组标识。
	 * @return 分组标识
	 */
	public String getGroup() {
		return group;
	}

	/**
	 * 设置权重分组标识。
	 * @param group 分组标识，不能为空
	 * @return 当前实例，支持链式调用
	 */
	public WeightConfig setGroup(String group) {
		this.group = group;
		return this;
	}

	/**
	 * 获取关联的路由唯一标识符。
	 * @return 路由 ID
	 */
	public String getRouteId() {
		return routeId;
	}

	/**
	 * 设置关联的路由唯一标识符。
	 * @param routeId 路由 ID
	 * @return 当前实例，支持链式调用
	 */
	public WeightConfig setRouteId(String routeId) {
		this.routeId = routeId;
		return this;
	}

	/**
	 * 获取权重值。
	 * @return 权重值
	 */
	public int getWeight() {
		return weight;
	}

	/**
	 * 设置权重值。
	 * @param weight 权重值，必须大于等于 0
	 * @return 当前实例，支持链式调用
	 */
	public WeightConfig setWeight(int weight) {
		this.weight = weight;
		return this;
	}

	/**
	 * 返回当前配置的字符串表示。
	 * @return 包含 group、routeId 和 weight 的字符串描述
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("group", group).append("routeId", routeId).append("weight", weight)
				.toString();
	}

}
