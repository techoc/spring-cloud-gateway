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
 * 路由元数据（Route Metadata）相关的工具类。
 * <p>
 * 该类定义了路由元数据中常用的属性名称常量，供网关组件在传递超时常量等信息时使用。 通过统一的常量定义，避免硬编码字符串，提高代码的可维护性和一致性。
 * </p>
 * <p>
 * 主要用途：
 * <ul>
 * <li>在 {@code RouteMetadata} 中存储和读取响应超时时间</li>
 * <li>在 {@code RouteMetadata} 中存储和读取连接超时时间</li>
 * </ul>
 * </p>
 *
 * @see org.springframework.cloud.gateway.route.Route
 */
public final class RouteMetadataUtils {

	/**
	 * 响应超时属性的名称。
	 * <p>
	 * 用于在路由元数据（RouteMetadata）中标识响应超时的配置项。 值通常为毫秒数，表示后端服务响应所需的最大等待时间。
	 */
	public static final String RESPONSE_TIMEOUT_ATTR = "response-timeout";

	/**
	 * 连接超时属性的名称。
	 * <p>
	 * 用于在路由元数据（RouteMetadata）中标识连接超时的配置项。 值通常为毫秒数，表示与后端服务建立连接所需的最大等待时间。
	 */
	public static final String CONNECT_TIMEOUT_ATTR = "connect-timeout";

	/**
	 * 私有构造函数，防止实例化。
	 * <p>
	 * 这是一个工具类，所有方法应为静态方法，因此不允许创建实例。
	 */
	private RouteMetadataUtils() {
		throw new AssertionError("Must not instantiate utility class.");
	}

}
