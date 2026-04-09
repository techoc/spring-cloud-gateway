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

package org.springframework.cloud.gateway.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * 从路由定义定位器加载路由的核心实现类。
 * <p>
 * 该类是 Spring Cloud Gateway 路由系统的核心组件，负责将配置层的 {@link RouteDefinition} 转换为运行时的
 * {@link Route} 对象。主要功能包括：
 * <ul>
 * <li>从 {@link RouteDefinitionLocator} 获取路由定义</li>
 * <li>解析并组合断言（Predicate）</li>
 * <li>加载并排序过滤器（Filter）</li>
 * <li>应用默认过滤器配置</li>
 * </ul>
 * <p>
 * 支持通过 {@link GatewayProperties#setFailOnRouteDefinitionError(boolean)} 控制路由定义错误时的行为： 设置为
 * false 时，错误的路由定义会被跳过，不影响其他路由。
 *
 * @author Spencer Gibb
 */
public class RouteDefinitionRouteLocator implements RouteLocator {

	/**
	 * 默认过滤器的名称常量，用于标识配置文件中的默认过滤器配置。
	 */
	public static final String DEFAULT_FILTERS = "defaultFilters";

	/** 日志记录器 */
	protected final Log logger = LogFactory.getLog(getClass());

	/** 路由定义定位器，用于获取原始路由配置 */
	private final RouteDefinitionLocator routeDefinitionLocator;

	/** 配置服务，用于绑定断言和过滤器的配置参数 */
	private final ConfigurationService configurationService;

	/** 断言工厂映射，键为工厂名称，值为工厂实例 */
	private final Map<String, RoutePredicateFactory> predicates = new LinkedHashMap<>();

	/** 过滤器工厂映射，键为工厂名称，值为工厂实例 */
	private final Map<String, GatewayFilterFactory> gatewayFilterFactories = new HashMap<>();

	/** 网关配置属性 */
	private final GatewayProperties gatewayProperties;

	/**
	 * 构造方法，初始化断言和过滤器工厂。
	 * @param routeDefinitionLocator 路由定义定位器
	 * @param predicates 断言工厂列表
	 * @param gatewayFilterFactories 过滤器工厂列表
	 * @param gatewayProperties 网关配置属性
	 * @param configurationService 配置服务
	 */
	public RouteDefinitionRouteLocator(RouteDefinitionLocator routeDefinitionLocator,
			List<RoutePredicateFactory> predicates, List<GatewayFilterFactory> gatewayFilterFactories,
			GatewayProperties gatewayProperties, ConfigurationService configurationService) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		this.configurationService = configurationService;
		initFactories(predicates);
		gatewayFilterFactories.forEach(factory -> this.gatewayFilterFactories.put(factory.name(), factory));
		this.gatewayProperties = gatewayProperties;
	}

	/**
	 * 初始化断言工厂，建立名称到工厂的映射。
	 * <p>
	 * 如果存在同名工厂，后加载的会覆盖先加载的，并记录警告日志。
	 * @param predicates 断言工厂列表
	 */
	private void initFactories(List<RoutePredicateFactory> predicates) {
		predicates.forEach(factory -> {
			String key = factory.name();
			if (this.predicates.containsKey(key)) {
				this.logger.warn("A RoutePredicateFactory named " + key + " already exists, class: "
						+ this.predicates.get(key) + ". It will be overwritten.");
			}
			this.predicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RoutePredicateFactory [" + key + "]");
			}
		});
	}

	/**
	 * 获取所有路由。
	 * <p>
	 * 从路由定义定位器获取定义，逐个转换为运行时路由对象。 如果配置了
	 * {@code failOnRouteDefinitionError=false}，错误的路由定义会被跳过。
	 * @return 包含所有路由的响应式流
	 */
	@Override
	public Flux<Route> getRoutes() {
		Flux<Route> routes = this.routeDefinitionLocator.getRouteDefinitions().map(this::convertToRoute);

		if (!gatewayProperties.isFailOnRouteDefinitionError()) {
			// 不抛出错误，继续处理其他路由
			routes = routes.onErrorContinue((error, obj) -> {
				if (logger.isWarnEnabled()) {
					logger.warn("RouteDefinition id " + ((RouteDefinition) obj).getId()
							+ " will be ignored. Definition has invalid configs, " + error.getMessage());
				}
			});
		}

		return routes.map(route -> {
			if (logger.isDebugEnabled()) {
				logger.debug("RouteDefinition matched: " + route.getId());
			}
			return route;
		});
	}

	/**
	 * 将路由定义转换为运行时路由对象。
	 * <p>
	 * 组合所有断言，加载所有过滤器（包括默认过滤器），构建最终的 Route 对象。
	 * @param routeDefinition 路由定义
	 * @return 运行时路由对象
	 */
	private Route convertToRoute(RouteDefinition routeDefinition) {
		AsyncPredicate<ServerWebExchange> predicate = combinePredicates(routeDefinition);
		List<GatewayFilter> gatewayFilters = getFilters(routeDefinition);

		return Route.async(routeDefinition).asyncPredicate(predicate).replaceFilters(gatewayFilters).build();
	}

	/**
	 * 加载并配置网关过滤器。
	 * <p>
	 * 根据过滤器定义，查找对应的过滤器工厂，绑定配置参数，创建过滤器实例。 如果过滤器实现了 {@link Ordered}
	 * 接口，直接使用其顺序值；否则使用定义中的索引位置作为顺序。
	 * @param id 路由 ID
	 * @param filterDefinitions 过滤器定义列表
	 * @return 配置好的过滤器列表
	 * @throws IllegalArgumentException 找不到对应的过滤器工厂时抛出
	 */
	@SuppressWarnings("unchecked")
	List<GatewayFilter> loadGatewayFilters(String id, List<FilterDefinition> filterDefinitions) {
		ArrayList<GatewayFilter> ordered = new ArrayList<>(filterDefinitions.size());
		for (int i = 0; i < filterDefinitions.size(); i++) {
			FilterDefinition definition = filterDefinitions.get(i);
			GatewayFilterFactory factory = this.gatewayFilterFactories.get(definition.getName());
			if (factory == null) {
				throw new IllegalArgumentException(
						"Unable to find GatewayFilterFactory with name " + definition.getName());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("RouteDefinition " + id + " applying filter " + definition.getArgs() + " to "
						+ definition.getName());
			}

			// @formatter:off
			Object configuration = this.configurationService.with(factory)
					.name(definition.getName())
					.properties(definition.getArgs())
					.eventFunction((bound, properties) -> new FilterArgsEvent(
							// TODO: 为什么需要显式转换，否则 Java 编译失败
							RouteDefinitionRouteLocator.this, id, (Map<String, Object>) properties))
					.bind();
			// @formatter:on

			// 某些过滤器需要路由 ID
			// TODO: 是否有更好的地方应用这个？
			if (configuration instanceof HasRouteId) {
				HasRouteId hasRouteId = (HasRouteId) configuration;
				hasRouteId.setRouteId(id);
			}

			GatewayFilter gatewayFilter = factory.apply(configuration);
			if (gatewayFilter instanceof Ordered) {
				ordered.add(gatewayFilter);
			}
			else {
				ordered.add(new OrderedGatewayFilter(gatewayFilter, i + 1));
			}
		}

		return ordered;
	}

	/**
	 * 获取路由的所有过滤器，包括默认过滤器和路由特定过滤器。
	 * <p>
	 * 先加载全局默认过滤器，再加载路由特定的过滤器，最后按顺序排序。
	 * @param routeDefinition 路由定义
	 * @return 排序后的过滤器列表
	 */
	private List<GatewayFilter> getFilters(RouteDefinition routeDefinition) {
		List<GatewayFilter> filters = new ArrayList<>();

		// TODO: 支持在路由特定过滤器之后应用默认过滤器的选项？
		if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
			filters.addAll(loadGatewayFilters(routeDefinition.getId(),
					new ArrayList<>(this.gatewayProperties.getDefaultFilters())));
		}

		final List<FilterDefinition> definitionFilters = routeDefinition.getFilters();
		if (!CollectionUtils.isEmpty(definitionFilters)) {
			filters.addAll(loadGatewayFilters(routeDefinition.getId(), definitionFilters));
		}

		AnnotationAwareOrderComparator.sort(filters);
		return filters;
	}

	/**
	 * 组合路由定义中的所有断言。
	 * <p>
	 * 多个断言之间为逻辑与（AND）关系。如果没有配置断言，返回一个始终为 true 的断言（匹配所有请求）。
	 * @param routeDefinition 路由定义
	 * @return 组合后的异步断言
	 */
	private AsyncPredicate<ServerWebExchange> combinePredicates(RouteDefinition routeDefinition) {
		List<PredicateDefinition> predicates = routeDefinition.getPredicates();
		if (predicates == null || predicates.isEmpty()) {
			// 这是一个非常罕见的情况，但可能发生，直接匹配所有
			return AsyncPredicate.from(exchange -> true);
		}
		AsyncPredicate<ServerWebExchange> predicate = lookup(routeDefinition, predicates.get(0));

		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			AsyncPredicate<ServerWebExchange> found = lookup(routeDefinition, andPredicate);
			predicate = predicate.and(found);
		}

		return predicate;
	}

	/**
	 * 根据断言定义查找并配置断言工厂。
	 * <p>
	 * 查找对应的断言工厂，绑定配置参数，创建异步断言实例。
	 * @param route 路由定义
	 * @param predicate 断言定义
	 * @return 配置好的异步断言
	 * @throws IllegalArgumentException 找不到对应的断言工厂时抛出
	 */
	@SuppressWarnings("unchecked")
	private AsyncPredicate<ServerWebExchange> lookup(RouteDefinition route, PredicateDefinition predicate) {
		RoutePredicateFactory<Object> factory = this.predicates.get(predicate.getName());
		if (factory == null) {
			throw new IllegalArgumentException("Unable to find RoutePredicateFactory with name " + predicate.getName());
		}
		if (logger.isDebugEnabled()) {
			logger.debug("RouteDefinition " + route.getId() + " applying " + predicate.getArgs() + " to "
					+ predicate.getName());
		}

		// @formatter:off
		Object config = this.configurationService.with(factory)
				.name(predicate.getName())
				.properties(predicate.getArgs())
				.eventFunction((bound, properties) -> new PredicateArgsEvent(
						RouteDefinitionRouteLocator.this, route.getId(), properties))
				.bind();
		// @formatter:on

		return factory.applyAsync(config);
	}

}
