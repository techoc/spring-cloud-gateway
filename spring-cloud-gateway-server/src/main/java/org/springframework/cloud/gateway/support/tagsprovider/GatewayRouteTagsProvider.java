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

package org.springframework.cloud.gateway.support.tagsprovider;

import io.micrometer.core.instrument.Tags;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 路由指标标签提供者，为请求生成路由相关的指标标签。
 * <p>
 * 该提供者生成以下标签：
 * <ul>
 * <li><b>routeId</b> - 匹配路由的唯一标识符</li>
 * <li><b>routeUri</b> - 路由的目标 URI（后端服务地址）</li>
 * </ul>
 * </p>
 * <p>
 * 此提供者用于分析各路由的流量分布，便于：
 * <ul>
 * <li>识别高流量路由</li>
 * <li>监控各后端服务的使用情况</li>
 * <li>排查路由级别的问题</li>
 * </ul>
 * </p>
 *
 * @author Ingyu Hwang
 * @see GatewayTagsProvider
 * @see Tags
 * @see Route
 */
public class GatewayRouteTagsProvider implements GatewayTagsProvider {

	/**
	 * 生成路由相关的指标标签。
	 * <p>
	 * 从交换属性中获取匹配的路由信息，生成 routeId 和 routeUri 标签。
	 * </p>
	 * @param exchange 当前请求交换对象
	 * @return 包含路由标签的 Tags 对象
	 */
	@Override
	public Tags apply(ServerWebExchange exchange) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

		if (route != null) {
			return Tags.of("routeId", route.getId(), "routeUri", route.getUri().toString());
		}

		return Tags.empty();
	}

}
