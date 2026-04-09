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

package org.springframework.cloud.gateway.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 网关过滤器处理核心类，负责执行全局过滤器和路由级过滤器的过滤链。
 *
 * <p>
 * FilteringWebHandler 是 Spring Cloud Gateway 处理请求的核心处理器。它实现了 {@link WebHandler} 接口，
 * 负责将请求委托给一个由 {@link GlobalFilter}（全局过滤器）和
 * {@link GatewayFilter}（路由过滤器）{@link GatewayFilterFactory} 组成的过滤链进行处理。
 * </p>
 *
 * <p>
 * <b>核心职责：</b>
 * </p>
 * <ul>
 * <li>管理全局过滤器的注册和执行</li>
 * <li>合并全局过滤器和路由特定过滤器</li>
 * <li>按照优先级顺序执行过滤器链</li>
 * <li>处理过滤链的递进和回溯</li>
 * </ul>
 *
 * <p>
 * <b>执行流程：</b>
 * </p>
 * <ol>
 * <li>从 ServerWebExchange 获取匹配的路由信息</li>
 * <li>获取路由上定义的 GatewayFilter 列表</li>
 * <li>合并全局过滤器和路由过滤器</li>
 * <li>按优先级排序所有过滤器</li>
 * <li>创建 DefaultGatewayFilterChain 并执行过滤链</li>
 * </ol>
 *
 * <p>
 * <b>过滤器执行顺序：</b>
 * </p>
 * <ul>
 * <li>使用 {@link AnnotationAwareOrderComparator} 排序</li>
 * <li>实现了 {@link Ordered} 接口的过滤器按指定顺序执行</li>
 * <li>数值越小，优先级越高</li>
 * </ul>
 *
 * <p>
 * <b>使用示例：</b>
 * </p>
 * <pre>{@code
 * // 在配置类中注入全局过滤器
 * &#64;Bean
 * public GlobalFilter customFilter() {
 *     return new CustomGlobalFilter();
 * }
 * }</pre>
 *
 * @author Rossen Stoyanchev
 * @author Spencer Gibb
 * @since 0.1
 * @see WebHandler
 * @see GlobalFilter
 * @see GatewayFilter
 */
public class FilteringWebHandler implements WebHandler {

	/** 日志记录器 */
	protected static final Log logger = LogFactory.getLog(FilteringWebHandler.class);

	/** 全局过滤器列表，在构造时从 GlobalFilter 实例转换而来 */
	private final List<GatewayFilter> globalFilters;

	/**
	 * 构造函数，接收全局过滤器列表并转换为 GatewayFilter。
	 * @param globalFilters 全局过滤器列表，通常由 Spring 容器注入
	 */
	public FilteringWebHandler(List<GlobalFilter> globalFilters) {
		this.globalFilters = loadFilters(globalFilters);
	}

	private static List<GatewayFilter> loadFilters(List<GlobalFilter> filters) {
		return filters.stream().map(filter -> {
			GatewayFilterAdapter gatewayFilter = new GatewayFilterAdapter(filter);
			if (filter instanceof Ordered) {
				int order = ((Ordered) filter).getOrder();
				return new OrderedGatewayFilter(gatewayFilter, order);
			}
			return gatewayFilter;
		}).collect(Collectors.toList());
	}

	/*
	 * TODO: relocate @EventListener(RefreshRoutesEvent.class) void handleRefresh() {
	 * this.combinedFiltersForRoute.clear();
	 */

	@Override
	public Mono<Void> handle(ServerWebExchange exchange) {
		Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
		List<GatewayFilter> gatewayFilters = route.getFilters();

		List<GatewayFilter> combined = new ArrayList<>(this.globalFilters);
		combined.addAll(gatewayFilters);
		// TODO: needed or cached?
		AnnotationAwareOrderComparator.sort(combined);

		if (logger.isDebugEnabled()) {
			logger.debug("Sorted gatewayFilterFactories: " + combined);
		}

		return new DefaultGatewayFilterChain(combined).filter(exchange);
	}

	private static class DefaultGatewayFilterChain implements GatewayFilterChain {

		private final int index;

		private final List<GatewayFilter> filters;

		DefaultGatewayFilterChain(List<GatewayFilter> filters) {
			this.filters = filters;
			this.index = 0;
		}

		private DefaultGatewayFilterChain(DefaultGatewayFilterChain parent, int index) {
			this.filters = parent.getFilters();
			this.index = index;
		}

		public List<GatewayFilter> getFilters() {
			return filters;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange) {
			return Mono.defer(() -> {
				if (this.index < filters.size()) {
					GatewayFilter filter = filters.get(this.index);
					DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(this, this.index + 1);
					return filter.filter(exchange, chain);
				}
				else {
					return Mono.empty(); // complete
				}
			});
		}

	}

	/**
	 * 全局过滤器适配器，将 GlobalFilter 适配为 GatewayFilter 接口。
	 *
	 * <p>
	 * 该适配器允许将实现 {@link GlobalFilter} 接口的过滤器 无缝集成到使用 {@link GatewayFilter} 接口的过滤链中。
	 * </p>
	 *
	 * @see GlobalFilter
	 * @see GatewayFilter
	 */
	private static class GatewayFilterAdapter implements GatewayFilter {

		/** 被适配的全局过滤器 */
		private final GlobalFilter delegate;

		GatewayFilterAdapter(GlobalFilter delegate) {
			this.delegate = delegate;
		}

		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			return this.delegate.filter(exchange, chain);
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("GatewayFilterAdapter{");
			sb.append("delegate=").append(delegate);
			sb.append('}');
			return sb.toString();
		}

	}

}
