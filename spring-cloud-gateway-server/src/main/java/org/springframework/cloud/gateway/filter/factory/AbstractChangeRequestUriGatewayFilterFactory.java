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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * 变更请求 URI 的网关过滤器工厂抽象基类。
 * <p>
 * 该类提供了修改请求目标 URI 的过滤器工厂通用实现。子类只需实现
 * {@link #determineRequestUri(ServerWebExchange, Object)} 方法， 根据当前请求上下文确定新的 URI。
 * <p>
 * 特性：
 * <ul>
 * <li>自动包装为 {@link OrderedGatewayFilter}，支持指定执行顺序；</li>
 * <li>若 URI 已确定则更新到 exchange 属性中；</li>
 * <li>默认执行顺序为 {@link RouteToRequestUrlFilter#ROUTE_TO_URL_FILTER_ORDER} + 1， 确保在路由 URL
 * 转换之后执行。</li>
 * </ul>
 *
 * @param <T> 配置类类型
 * @author Toshiaki Maki
 */
public abstract class AbstractChangeRequestUriGatewayFilterFactory<T> extends AbstractGatewayFilterFactory<T> {

	/** 过滤器执行顺序 */
	private final int order;

	/**
	 * 构造方法，指定配置类类型和执行顺序。
	 * @param clazz 配置类类型
	 * @param order 过滤器执行顺序
	 */
	public AbstractChangeRequestUriGatewayFilterFactory(Class<T> clazz, int order) {
		super(clazz);
		this.order = order;
	}

	/**
	 * 默认构造方法，使用指定的配置类类型，默认执行顺序为路由 URL 过滤器之后。
	 * @param clazz 配置类类型
	 */
	public AbstractChangeRequestUriGatewayFilterFactory(Class<T> clazz) {
		this(clazz, RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
	}

	/**
	 * 根据当前请求上下文确定新的请求 URI。
	 * <p>
	 * 子类必须实现此方法，根据配置和请求信息返回新的 URI。 若返回空 Optional，则不修改请求 URI。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param config 配置对象
	 * @return 新的请求 URI，若无需修改则返回空 Optional
	 */
	protected abstract Optional<URI> determineRequestUri(ServerWebExchange exchange, T config);

	/**
	 * 创建变更请求 URI 的过滤器。
	 * <p>
	 * 过滤器调用 {@link #determineRequestUri} 获取新 URI， 若返回非空则更新到 exchange 属性中。
	 * @param config 配置对象
	 * @return 创建的 {@link GatewayFilter} 实例
	 */
	@Override
	public GatewayFilter apply(T config) {
		return new OrderedGatewayFilter((exchange, chain) -> {
			Optional<URI> uri = this.determineRequestUri(exchange, config);
			uri.ifPresent(u -> {
				Map<String, Object> attributes = exchange.getAttributes();
				attributes.put(GATEWAY_REQUEST_URL_ATTR, u);
			});
			return chain.filter(exchange);
		}, this.order);
	}

}
