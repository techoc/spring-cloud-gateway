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

import java.util.Map;

import org.springframework.context.ApplicationEvent;

/**
 * Filter 过滤器参数配置事件。
 *
 * <p>
 * 当网关在初始化阶段为指定路由配置 Filter（过滤器）参数时，会发布此事件。 事件携带路由 ID 以及该路由上所有 Filter
 * 的配置参数键值对，供监听器读取或进一步处理。
 * </p>
 *
 * <p>
 * 典型使用场景：
 * </p>
 * <ul>
 * <li>监听器收集路由 Filter 配置信息，用于配置校验或日志记录</li>
 * <li>动态修改或增强 Filter 参数</li>
 * <li>将 Filter 配置信息同步至管理平台或监控系统</li>
 * </ul>
 *
 * @see PredicateArgsEvent
 * @see org.springframework.cloud.gateway.filter.GatewayFilter
 */
public class FilterArgsEvent extends ApplicationEvent {

	/**
	 * Filter 的配置参数映射表。 Key 为参数名称（String），Value 为参数值（Object）。 具体参数结构取决于所配置的 Filter 类型。
	 */
	private final Map<String, Object> args;

	/**
	 * 关联的路由唯一标识。 用于指明这组 Filter 参数属于哪条路由。
	 */
	private String routeId;

	/**
	 * 构造一个 Filter 参数配置事件。
	 * @param source 事件源对象（通常为发布事件的组件）
	 * @param routeId 目标路由的唯一标识
	 * @param args Filter 的配置参数映射表（可为 null）
	 */
	public FilterArgsEvent(Object source, String routeId, Map<String, Object> args) {
		super(source);
		this.routeId = routeId;
		this.args = args;
	}

	/**
	 * 获取事件关联的路由 ID。
	 * @return 路由的唯一标识字符串
	 */
	public String getRouteId() {
		return routeId;
	}

	/**
	 * 获取 Filter 的配置参数映射表。
	 * @return 参数键值对映射表，若未设置则可能为 null
	 */
	public Map<String, Object> getArgs() {
		return args;
	}

}
