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

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 路径指标标签提供者，为请求生成路径相关的指标标签。
 * <p>
 * 该提供者生成以下标签：
 * <ul>
 * <li><b>path</b> - 实际匹配的路由谓词路径模式</li>
 * </ul>
 * </p>
 * <p>
 * 标签生成逻辑：
 * <ol>
 * <li>从交换属性中获取匹配的路由对象</li>
 * <li>获取路由谓词实际匹配的路径</li>
 * <li>验证匹配的路径属于实际选中的路由，防止跨路由污染</li>
 * <li>仅当所有检查通过时才返回 path 标签</li>
 * </ol>
 * </p>
 * <p>
 * 此提供者用于分析哪些路径接收了最多的流量，便于进行路由级别的监控和优化。
 * </p>
 *
 * @author Marta Medio
 * @author Alberto C. Ríos
 * @see GatewayTagsProvider
 * @see Tags
 * @see Route
 */
public class GatewayPathTagsProvider implements GatewayTagsProvider {

	/**
	 * 生成路径相关的指标标签。
	 * <p>
	 * 返回的标签包含匹配路径，如果没有匹配的路由或路径，则返回空标签。
	 * </p>
	 * @param exchange 当前请求交换对象
	 * @return 包含路径标签的 Tags 对象
	 */
	@Override
	public Tags apply(ServerWebExchange exchange) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

		if (route != null) {
			String matchedPathRouteId = exchange.getAttribute(GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR);
			String matchedPath = exchange.getAttribute(GATEWAY_PREDICATE_MATCHED_PATH_ATTR);

			// 检查匹配的路径是否属于实际选中的路由
			if (route.getId().equals(matchedPathRouteId) && matchedPath != null) {
				return Tags.of("path", matchedPath);
			}
		}

		return Tags.empty();
	}

}
