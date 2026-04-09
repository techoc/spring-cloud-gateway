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

package org.springframework.cloud.gateway.config;

import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

/**
 * 基于网关属性配置的路由定义定位器。
 * <p>
 * 该定位器从 {@link GatewayProperties} 中读取预定义的路由配置， 并将其作为 {@link RouteDefinition} 的 Flux 流返回。
 *
 * @author Spencer Gibb
 */
public class PropertiesRouteDefinitionLocator implements RouteDefinitionLocator {

	/**
	 * 网关属性配置，包含所有预定义的路由信息。
	 */
	private final GatewayProperties properties;

	/**
	 * 构造一个基于网关属性的路由定义定位器。
	 * @param properties 网关属性配置，用于获取路由定义列表
	 */
	public PropertiesRouteDefinitionLocator(GatewayProperties properties) {
		this.properties = properties;
	}

	/**
	 * 获取所有路由定义。
	 * <p>
	 * 从网关属性配置中提取路由列表，并将其转换为响应式 Flux 流。
	 * @return 包含所有路由定义的响应式流
	 */
	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return Flux.fromIterable(this.properties.getRoutes());
	}

}
