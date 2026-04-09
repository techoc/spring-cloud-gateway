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
 * 启用响应体缓存事件。
 *
 * <p>
 * 当网关需要对特定路由的响应体进行缓存（以支持多次读取，如日志记录、指标采集等场景） 时，会发布此事件。事件携带目标路由的唯一标识，监听器据此对相应路由启用 body
 * 缓存机制。
 * </p>
 *
 * <p>
 * 典型使用场景：
 * </p>
 * <ul>
 * <li>某些 Filter 需要在转发后读取响应体多次</li>
 * <li>请求/响应日志记录需要完整 body 内容</li>
 * <li>需要对下游服务响应进行缓存以提升性能</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see ApplicationEvent
 */
public class EnableBodyCachingEvent extends ApplicationEvent {

	/**
	 * 目标路由的唯一标识。 用于指定对哪条路由启用响应体缓存功能。
	 */
	private final String routeId;

	/**
	 * 构造一个启用响应体缓存事件。
	 * @param source 事件源对象（通常为发布事件的组件，如 {@code this}）
	 * @param routeId 需要启用响应体缓存的路由 ID（非空）
	 */
	public EnableBodyCachingEvent(Object source, String routeId) {
		super(source);
		this.routeId = routeId;
	}

	/**
	 * 获取需要启用响应体缓存的路由 ID。
	 * @return 路由的唯一标识字符串
	 */
	public String getRouteId() {
		return this.routeId;
	}

}
