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

import java.util.function.Function;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.handler.AbstractHandlerMapping;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.DIFFERENT;
import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.DISABLED;
import static org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping.ManagementPortType.SAME;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 路由断言处理器映射器，负责将请求匹配到对应的路由处理器。
 *
 * <p>
 * RoutePredicateHandlerMapping 是 Spring Cloud Gateway 的核心处理器映射器，继承自
 * {@link AbstractHandlerMapping}。它负责根据请求的属性（如路径、主机、时间等） 匹配到对应的路由，并返回相应的处理器。
 * </p>
 *
 * <p>
 * <b>核心职责：</b>
 * </p>
 * <ul>
 * <li>遍历所有已定义的路由，使用路由的断言进行匹配</li>
 * <li>处理管理端口与网关端口的区分</li>
 * <li>设置 CORS 跨域配置</li>
 * <li>返回匹配路由的 FilteringWebHandler</li>
 * </ul>
 *
 * <p>
 * <b>请求匹配流程：</b>
 * </p>
 * <ol>
 * <li>检查请求端口是否为管理端口（如果是管理端口且与网关端口不同，则跳过处理）</li>
 * <li>调用 {@link #lookupRoute(ServerWebExchange)} 方法查找匹配的路由</li>
 * <li>遍历所有路由，使用每个路由的断言进行测试</li>
 * <li>找到第一个匹配的路由后，将其存入 exchange 属性并返回 FilteringWebHandler</li>
 * <li>未找到匹配路由时，返回空 Mono</li>
 * </ol>
 *
 * <p>
 * <b>管理端口处理：</b>
 * </p>
 * <ul>
 * <li>如果设置了 management.port 且与 server.port 不同，则管理端口的请求不会被网关处理</li>
 * <li>如果 management.port < 0，则表示禁用了管理端口处理</li>
 * <li>如果未设置或与 server.port 相同，则所有请求都由网关处理</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see FilteringWebHandler
 * @see RouteLocator
 * @see org.springframework.cloud.gateway.route.Route
 */
public class RoutePredicateHandlerMapping extends AbstractHandlerMapping {

	/** 网关过滤器处理器，用于处理匹配路由的请求 */
	private final FilteringWebHandler webHandler;

	/** 路由定位器，用于获取所有定义的路由 */
	private final RouteLocator routeLocator;

	/** 管理端口号 */
	private final Integer managementPort;

	/** 管理端口类型，决定如何处理管理端口请求 */
	private final ManagementPortType managementPortType;

	/**
	 * 构造函数，初始化路由断言处理器映射器。
	 * @param webHandler 网关过滤器处理器
	 * @param routeLocator 路由定位器
	 * @param globalCorsProperties 全局 CORS 配置属性
	 * @param environment Spring 环境对象，用于读取配置属性
	 */
	public RoutePredicateHandlerMapping(FilteringWebHandler webHandler, RouteLocator routeLocator,
			GlobalCorsProperties globalCorsProperties, Environment environment) {
		this.webHandler = webHandler;
		this.routeLocator = routeLocator;

		// 获取管理端口配置
		this.managementPort = getPortProperty(environment, "management.server.");
		this.managementPortType = getManagementPortType(environment);
		// 设置处理器映射器的优先级
		setOrder(environment.getProperty(GatewayProperties.PREFIX + ".handler-mapping.order", Integer.class, 1));
		// 设置 CORS 配置
		setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
	}

	/**
	 * 根据配置确定管理端口的类型。
	 * @param environment Spring 环境对象
	 * @return 管理端口类型
	 */
	private ManagementPortType getManagementPortType(Environment environment) {
		Integer serverPort = getPortProperty(environment, "server.");
		// 如果管理端口小于0，表示已禁用
		if (this.managementPort != null && this.managementPort < 0) {
			return DISABLED;
		}
		// 判断管理端口与服务器端口是否相同
		return ((this.managementPort == null || (serverPort == null && this.managementPort.equals(8080))
				|| (this.managementPort != 0 && this.managementPort.equals(serverPort))) ? SAME : DIFFERENT);
	}

	/**
	 * 从环境配置中获取端口号。
	 * @param environment Spring 环境对象
	 * @param prefix 配置属性前缀（如 "server." 或 "management.server."）
	 * @return 端口号，如果未配置则返回 null
	 */
	private static Integer getPortProperty(Environment environment, String prefix) {
		return environment.getProperty(prefix + "port", Integer.class);
	}

	/**
	 * 获取处理给定请求的处理器内部方法。
	 *
	 * <p>
	 * 该方法是处理请求的核心入口，主要功能：
	 * </p>
	 * <ul>
	 * <li>检查是否为管理端口请求，如果是则返回空</li>
	 * <li>查找匹配的路由</li>
	 * <li>将路由信息存入 exchange 属性</li>
	 * <li>返回 FilteringWebHandler</li>
	 * </ul>
	 * @param exchange 当前的服务器 Web 交换对象
	 * @return 处理器 Mono，如果没有找到匹配的路由则返回空 Mono
	 */
	@Override
	protected Mono<?> getHandlerInternal(ServerWebExchange exchange) {
		// 如果设置了管理端口且与服务器端口不同，则不处理管理端口的请求
		if (this.managementPortType == DIFFERENT && this.managementPort != null
				&& exchange.getRequest().getLocalAddress() != null
				&& exchange.getRequest().getLocalAddress().getPort() == this.managementPort) {
			return Mono.empty();
		}
		// 记录处理器映射器名称
		exchange.getAttributes().put(GATEWAY_HANDLER_MAPPER_ATTR, getSimpleName());

		// 查找匹配的路由
		return lookupRoute(exchange).flatMap((Function<Route, Mono<?>>) r -> {
			exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);
			if (logger.isDebugEnabled()) {
				logger.debug("Mapping [" + getExchangeDesc(exchange) + "] to " + r);
			}

			// 将匹配的路由存入属性
			exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, r);
			return Mono.just(webHandler);
		}).switchIfEmpty(Mono.empty().then(Mono.fromRunnable(() -> {
			exchange.getAttributes().remove(GATEWAY_PREDICATE_ROUTE_ATTR);
			if (logger.isTraceEnabled()) {
				logger.trace("No RouteDefinition found for [" + getExchangeDesc(exchange) + "]");
			}
		})));
	}

	/**
	 * 获取CORS配置信息。
	 * <p>
	 * 当前实现委托给父类处理。未来计划支持通过路由属性配置CORS（参见gh-229）。
	 * </p>
	 * @param handler 处理器对象
	 * @param exchange 服务器Web交换对象
	 * @return CORS配置信息，如果未配置则返回null
	 */
	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, ServerWebExchange exchange) {
		// TODO: support cors configuration via properties on a route see gh-229
		// see RequestMappingHandlerMapping.initCorsConfiguration()
		// also see
		// https://github.com/spring-projects/spring-framework/blob/master/spring-web/src/test/java/org/springframework/web/cors/reactive/CorsWebFilterTests.java
		return super.getCorsConfiguration(handler, exchange);
	}

	// TODO: get desc from factory?
	private String getExchangeDesc(ServerWebExchange exchange) {
		StringBuilder out = new StringBuilder();
		out.append("Exchange: ");
		out.append(exchange.getRequest().getMethod());
		out.append(" ");
		out.append(exchange.getRequest().getURI());
		return out.toString();
	}

	protected Mono<Route> lookupRoute(ServerWebExchange exchange) {
		return this.routeLocator.getRoutes()
				// individually filter routes so that filterWhen error delaying is not a
				// problem
				.concatMap(route -> Mono.just(route).filterWhen(r -> {
					// add the current route we are testing
					exchange.getAttributes().put(GATEWAY_PREDICATE_ROUTE_ATTR, r.getId());
					return r.getPredicate().apply(exchange);
				})
						// instead of immediately stopping main flux due to error, log and
						// swallow it
						.doOnError(e -> logger.error("Error applying predicate for route: " + route.getId(), e))
						.onErrorResume(e -> Mono.empty()))
				// .defaultIfEmpty() put a static Route not found
				// or .switchIfEmpty()
				// .switchIfEmpty(Mono.<Route>empty().log("noroute"))
				.next()
				// TODO: error handling
				.map(route -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Route matched: " + route.getId());
					}
					validateRoute(route, exchange);
					return route;
				});

		/*
		 * TODO: trace logging if (logger.isTraceEnabled()) {
		 * logger.trace("RouteDefinition did not match: " + routeDefinition.getId()); }
		 */
	}

	/**
	 * Validate the given handler against the current request.
	 * <p>
	 * The default implementation is empty. Can be overridden in subclasses, for example
	 * to enforce specific preconditions expressed in URL mappings.
	 * @param route the Route object to validate
	 * @param exchange current exchange
	 * @throws Exception if validation failed
	 */
	@SuppressWarnings("UnusedParameters")
	protected void validateRoute(Route route, ServerWebExchange exchange) {
	}

	protected String getSimpleName() {
		return "RoutePredicateHandlerMapping";
	}

	/**
	 * 管理端口类型枚举。
	 *
	 * <p>
	 * 用于描述网关管理端口与服务器端口之间的关系， 以便网关正确处理跨端口的请求分发。
	 * </p>
	 */
	public enum ManagementPortType {

		/**
		 * 管理端口已禁用。
		 * <p>
		 * 当 management.server.port < 0 时使用此值。
		 * </p>
		 */
		DISABLED,

		/**
		 * 管理端口与服务器端口相同。
		 * <p>
		 * 未单独配置管理端口或端口号与服务器端口一致时使用此值。
		 * </p>
		 */
		SAME,

		/**
		 * 管理端口与服务器端口不同。
		 * <p>
		 * 当管理端口与服务器端口分开配置时使用此值， 网关将不处理发往管理端口的请求。
		 * </p>
		 */
		DIFFERENT;

	}

}
