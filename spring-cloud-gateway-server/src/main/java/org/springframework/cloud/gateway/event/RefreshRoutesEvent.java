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

import org.springframework.context.ApplicationEvent;

/**
 * 路由刷新事件。
 *
 * <p>
 * 当管理员或应用程序通过接口（如 {@code /actuator/gateway/refresh}）触发网关路由刷新时， 会发布此事件。监听此事件的组件（如
 * {@link org.springframework.cloud.gateway.route.RouteRefreshListener}）
 * 将重新加载路由配置，并更新网关的路由表，使新增、修改或删除的路由规则立即生效，无需重启服务。
 * </p>
 *
 * <p>
 * 典型使用场景：
 * </p>
 * <ul>
 * <li>动态路由配置变更后手动刷新</li>
 * <li>配合配置中心（如 Spring Cloud Config、Nacos）实现路由热更新</li>
 * <li>运维平台通过 API 触发路由变更</li>
 * </ul>
 *
 * <p>
 * <strong>注意事项：</strong>
 * </p>
 * <ul>
 * <li>该事件不携带任何业务数据，仅作为刷新信号</li>
 * <li>刷新过程中可能会有短暂的服务中断，建议在低峰期操作</li>
 * <li>刷新失败不会自动回滚，需关注 {@link RefreshRoutesResultEvent} 获取结果</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see RefreshRoutesResultEvent
 * @see org.springframework.cloud.gateway.route.RouteRefreshListener
 */
public class RefreshRoutesEvent extends ApplicationEvent {

	/**
	 * 构造一个路由刷新事件。
	 * @param source 事件源对象（never {@code null}，通常为发布事件的组件）
	 */
	public RefreshRoutesEvent(Object source) {
		super(source);
	}

}
