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

/**
 * 标记接口，表示实现类关联了路由 ID。
 * <p>
 * 该接口用于标识那些与特定路由绑定的组件，如过滤器（GatewayFilter）、 谓词（RoutePredicate）等。通过实现此接口，可以在运行时获取或设置
 * 组件所属路由的标识符，便于路由级别的管理和追踪。
 * </p>
 */
public interface HasRouteId {

	/**
	 * 设置组件所属的路由 ID。
	 * <p>
	 * 该方法用于在路由匹配成功后，将路由标识符关联到当前组件。
	 * </p>
	 * @param routeId 路由的唯一标识符
	 */
	void setRouteId(String routeId);

	/**
	 * 获取组件所属的路由 ID。
	 * <p>
	 * 返回该组件关联的路由标识符，若未设置则返回 null。
	 * </p>
	 * @return 路由的唯一标识符，若无则返回 null
	 */
	String getRouteId();

}
