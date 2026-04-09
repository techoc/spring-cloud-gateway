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

package org.springframework.cloud.gateway.actuate;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

/**
 * 网关控制器端点的抽象基类，提供路由管理的核心REST API实现
 * <p>
 * 该类是Spring Cloud Gateway Actuator端点的核心抽象类，定义了路由管理的基本功能，
 * 包括路由定义的创建、删除、查询，以及过滤器、谓词等组件的查询功能。
 * <p>
 * <b>核心功能：</b>
 * <ul>
 * <li>路由刷新：触发路由重新加载事件</li>
 * <li>路由CRUD：创建、删除、查询路由定义</li>
 * <li>组件查询：查询全局过滤器、网关过滤器、路由谓词等组件信息</li>
 * <li>路由校验：验证路由定义的合法性和完整性</li>
 * </ul>
 *
 * <b>使用场景：</b>
 * <ul>
 * <li>通过Spring Boot Actuator暴露网关管理接口</li>
 * <li>动态管理网关路由配置，无需重启应用</li>
 * <li>监控和调试网关过滤器、谓词的执行顺序</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see GatewayControllerEndpoint
 * @see GatewayLegacyControllerEndpoint
 */
public class AbstractGatewayControllerEndpoint implements ApplicationEventPublisherAware {

	/**
	 * 日志记录器，用于记录路由操作和错误信息
	 */
	private static final Log log = LogFactory.getLog(GatewayControllerEndpoint.class);

	/**
	 * 路由定义定位器，用于获取当前所有的路由定义
	 * <p>
	 * 路由定义包含路由的基本配置信息，如ID、URI、谓词和过滤器等。
	 */
	protected RouteDefinitionLocator routeDefinitionLocator;

	/**
	 * 全局过滤器列表，作用于所有路由
	 * <p>
	 * 全局过滤器在请求处理的早期和晚期都会执行，常用于日志记录、认证、限流等通用功能。 过滤器按照order值升序排列执行，order值越小越先执行。
	 *
	 * @see GlobalFilter
	 */
	protected List<GlobalFilter> globalFilters;

	/**
	 * 网关过滤器工厂列表，用于创建特定路由的过滤器实例
	 * <p>
	 * 这些工厂类负责根据路由配置生成具体的GatewayFilter实例。 网关过滤器只能作用于配置了该过滤器的特定路由。
	 *
	 * @see GatewayFilterFactory
	 * @see org.springframework.cloud.gateway.filter.GatewayFilter
	 */
	// TODO change casing in next major release
	protected List<GatewayFilterFactory> GatewayFilters;

	/**
	 * 路由谓词工厂列表，用于判断请求是否匹配特定路由
	 * <p>
	 * 谓词是路由匹配的第一关，只有满足谓词条件的请求才会被路由到对应的目标服务。 常见的谓词包括：Path、Host、Method、Header、Query等。
	 *
	 * @see RoutePredicateFactory
	 */
	protected List<RoutePredicateFactory> routePredicates;

	/**
	 * 路由定义写入器，用于保存和删除路由定义
	 * <p>
	 * 该组件负责将路由定义持久化到配置存储中，如内存、Redis或数据库。
	 *
	 * @see RouteDefinitionWriter
	 */
	protected RouteDefinitionWriter routeDefinitionWriter;

	/**
	 * 路由定位器，用于获取已解析的路由实例
	 * <p>
	 * 路由实例是路由定义的运行时表示，包含了具体的过滤器和谓词实现。
	 *
	 * @see RouteLocator
	 */
	protected RouteLocator routeLocator;

	/**
	 * 应用程序事件发布器，用于发布路由刷新事件
	 * <p>
	 * 当路由配置发生变更时，通过发布RefreshRoutesEvent事件通知所有监听器重新加载路由。
	 */
	protected ApplicationEventPublisher publisher;

	/**
	 * 构造方法，初始化网关控制器端点所需的所有依赖组件
	 * @param routeDefinitionLocator 路由定义定位器，用于查询路由定义
	 * @param globalFilters 全局过滤器列表，作用于所有路由
	 * @param gatewayFilters 网关过滤器工厂列表，用于创建路由级别过滤器
	 * @param routePredicates 路由谓词工厂列表，用于路由匹配判断
	 * @param routeDefinitionWriter 路由定义写入器，用于保存/删除路由定义
	 * @param routeLocator 路由定位器，用于获取运行时路由信息
	 */
	public AbstractGatewayControllerEndpoint(RouteDefinitionLocator routeDefinitionLocator,
			List<GlobalFilter> globalFilters, List<GatewayFilterFactory> gatewayFilters,
			List<RoutePredicateFactory> routePredicates, RouteDefinitionWriter routeDefinitionWriter,
			RouteLocator routeLocator) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		this.globalFilters = globalFilters;
		this.GatewayFilters = gatewayFilters;
		this.routePredicates = routePredicates;
		this.routeDefinitionWriter = routeDefinitionWriter;
		this.routeLocator = routeLocator;
	}

	/**
	 * 设置应用程序事件发布器
	 * <p>
	 * 实现ApplicationEventPublisherAware接口，用于在运行时发布事件。
	 * 当路由发生变更时，会通过该发布器发布RefreshRoutesEvent事件。
	 * @param publisher 应用程序事件发布器实例
	 */
	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	// TODO: Add uncommited or new but not active routes endpoint

	/**
	 * 刷新网关路由配置
	 * <p>
	 * 该端点发布一个RefreshRoutesEvent事件，触发所有监听该事件的组件重新加载路由配置。 主要用途包括：
	 * <ul>
	 * <li>动态添加新路由后，手动触发路由生效</li>
	 * <li>修改现有路由配置后，使变更立即生效</li>
	 * <li>清除缓存的路由信息，强制重新获取</li>
	 * </ul>
	 * @return 返回空的Mono，表示操作完成（实际路由刷新是异步的）
	 * @see RefreshRoutesEvent
	 */
	@PostMapping("/refresh")
	public Mono<Void> refresh() {
		this.publisher.publishEvent(new RefreshRoutesEvent(this));
		return Mono.empty();
	}

	/**
	 * 获取所有全局过滤器的名称和执行顺序
	 * <p>
	 * 返回一个Map，其中key为过滤器名称，value为过滤器的执行顺序（order值）。 order值越小，过滤器执行越早。全局过滤器作用于所有路由。
	 * @return 包含全局过滤器名称和顺序的Map对象
	 * @see GlobalFilter
	 */
	@GetMapping("/globalfilters")
	public Mono<HashMap<String, Object>> globalfilters() {
		return getNamesToOrders(this.globalFilters);
	}

	/**
	 * 获取所有网关过滤器工厂的名称和顺序
	 * <p>
	 * 返回一个Map，其中key为过滤器工厂名称，value为过滤器工厂的默认顺序。 网关过滤器工厂负责创建路由级别的过滤器实例。
	 * @return 包含网关过滤器工厂名称和顺序的Map对象
	 * @see GatewayFilterFactory
	 */
	@GetMapping("/routefilters")
	public Mono<HashMap<String, Object>> routefilers() {
		return getNamesToOrders(this.GatewayFilters);
	}

	/**
	 * 获取所有路由谓词工厂的名称和顺序
	 * <p>
	 * 返回一个Map，其中key为谓词工厂名称，value为谓词工厂的默认顺序。 路由谓词用于判断请求是否匹配特定路由条件。
	 * @return 包含路由谓词工厂名称和顺序的Map对象
	 * @see RoutePredicateFactory
	 */
	@GetMapping("/routepredicates")
	public Mono<HashMap<String, Object>> routepredicates() {
		return getNamesToOrders(this.routePredicates);
	}

	/**
	 * 将列表中的元素转换为名称到顺序的Map
	 * <p>
	 * 通用方法，将实现Ordered接口的元素列表转换为Map。 如果元素未实现Ordered接口，则其顺序值为null。
	 * @param <T> 列表元素的类型，必须实现Ordered接口或重写toString方法
	 * @param list 待转换的元素列表
	 * @return 包含元素名称和顺序的Map
	 */
	private <T> Mono<HashMap<String, Object>> getNamesToOrders(List<T> list) {
		return Flux.fromIterable(list).reduce(new HashMap<>(), this::putItem);
	}

	/**
	 * 将单个元素放入Map中
	 * <p>
	 * 如果元素实现了Ordered接口，则提取其order值；否则order为null。 Map的key使用元素的toString()表示。
	 * @param map 目标Map
	 * @param o 待处理的元素对象
	 * @return 更新后的Map
	 */
	private HashMap<String, Object> putItem(HashMap<String, Object> map, Object o) {
		Integer order = null;
		if (o instanceof Ordered) {
			order = ((Ordered) o).getOrder();
		}
		// filters.put(o.getClass().getName(), order);
		map.put(o.toString(), order);
		return map;
	}

	/*
	 * http POST :8080/admin/gateway/routes/apiaddreqhead uri=http://httpbin.org:80
	 * predicates:='["Host=**.apiaddrequestheader.org", "Path=/headers"]'
	 * filters:='["AddRequestHeader=X-Request-ApiFoo, ApiBar"]'
	 */

	/**
	 * 创建或更新路由定义
	 * <p>
	 * 该端点用于通过HTTP POST请求创建新的路由或更新已存在的路由。 请求体应包含完整的RouteDefinition对象，包括：
	 * <ul>
	 * <li>路由ID（通过URL路径参数指定）</li>
	 * <li>目标URI</li>
	 * <li>路由谓词列表</li>
	 * <li>路由过滤器列表</li>
	 * <li>元数据（可选）</li>
	 * </ul>
	 *
	 * <b>使用示例：</b> <pre>
	 * POST /admin/gateway/routes/my-route
	 * Content-Type: application/json
	 *
	 * {
	 *   "uri": "http://httpbin.org:80",
	 *   "predicates": [
	 *     {"name": "Host", "args": {"_genkey_0": "**.example.com"}}
	 *   ],
	 *   "filters": [
	 *     {"name": "AddRequestHeader", "args": {"_genkey_0": "X-Custom", "_genkey_1": "Value"}}
	 *   ]
	 * }
	 * </pre>
	 * @param id 路由的唯一标识符
	 * @param route 路由定义对象，包含路由的完整配置
	 * @return 创建成功返回201 Created状态码及路由URI；路由无效返回400 Bad Request
	 */
	@PostMapping("/routes/{id}")
	@SuppressWarnings("unchecked")
	public Mono<ResponseEntity<Object>> save(@PathVariable String id, @RequestBody RouteDefinition route) {

		return Mono.just(route).doOnNext(this::validateRouteDefinition)
				.flatMap(routeDefinition -> this.routeDefinitionWriter.save(Mono.just(routeDefinition).map(r -> {
					r.setId(id);
					log.debug("Saving route: " + route);
					return r;
				})).then(Mono.defer(() -> Mono.just(ResponseEntity.created(URI.create("/routes/" + id)).build()))))
				.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.badRequest().build())));
	}

	/**
	 * 验证路由定义的合法性
	 * <p>
	 * 验证内容包括：
	 * <ul>
	 * <li>所有配置的过滤器是否可用（存在于GatewayFilterFactory列表中）</li>
	 * <li>所有配置的谓词是否可用（存在于RoutePredicateFactory列表中）</li>
	 * <li>路由URI是否有效（不为空且包含有效的scheme）</li>
	 * </ul>
	 * 如果验证失败，将抛出ResponseStatusException异常。
	 * @param routeDefinition 待验证的路由定义
	 * @throws ResponseStatusException 如果路由定义包含无效的过滤器、谓词或URI
	 */
	private void validateRouteDefinition(RouteDefinition routeDefinition) {
		Set<String> unavailableFilterDefinitions = routeDefinition.getFilters().stream().filter(rd -> !isAvailable(rd))
				.map(FilterDefinition::getName).collect(Collectors.toSet());

		Set<String> unavailablePredicatesDefinitions = routeDefinition.getPredicates().stream()
				.filter(rd -> !isAvailable(rd)).map(PredicateDefinition::getName).collect(Collectors.toSet());
		if (!unavailableFilterDefinitions.isEmpty()) {
			handleUnavailableDefinition(FilterDefinition.class.getSimpleName(), unavailableFilterDefinitions);
		}
		else if (!unavailablePredicatesDefinitions.isEmpty()) {
			handleUnavailableDefinition(PredicateDefinition.class.getSimpleName(), unavailablePredicatesDefinitions);
		}

		validateRouteUri(routeDefinition.getUri());
	}

	/**
	 * 验证路由URI的有效性
	 * <p>
	 * URI必须满足以下条件：
	 * <ul>
	 * <li>URI不能为null</li>
	 * <li>URI必须包含有效的scheme（如http、https、lb等）</li>
	 * </ul>
	 * @param uri 待验证的URI对象
	 * @throws ResponseStatusException 如果URI为空或格式不正确
	 */
	private void validateRouteUri(URI uri) {
		if (uri == null) {
			handleError("The URI can not be empty");
		}

		if (!StringUtils.hasText(uri.getScheme())) {
			handleError(String.format("The URI format [%s] is incorrect, scheme can not be empty", uri));
		}
	}

	/**
	 * 处理不可用的组件定义
	 * <p>
	 * 当路由配置中引用的过滤器或谓词在系统中不存在时，调用此方法记录警告并抛出异常。
	 * @param simpleName 组件类型名称（如"FilterDefinition"或"PredicateDefinition"）
	 * @param unavailableDefinitions 不可用的组件名称集合
	 * @throws ResponseStatusException 始终抛出400 Bad Request异常
	 */
	private void handleUnavailableDefinition(String simpleName, Set<String> unavailableDefinitions) {
		final String errorMessage = String.format("Invalid %s: %s", simpleName, unavailableDefinitions);
		log.warn(errorMessage);
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
	}

	/**
	 * 处理通用错误
	 * <p>
	 * 记录错误日志并抛出400 Bad Request异常。
	 * @param errorMessage 错误描述信息
	 * @throws ResponseStatusException 始终抛出400 Bad Request异常
	 */
	private void handleError(String errorMessage) {
		log.warn(errorMessage);
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
	}

	/**
	 * 检查过滤器定义是否可用
	 * <p>
	 * 通过检查过滤器名称是否存在于GatewayFilterFactory列表中来判断。
	 * @param filterDefinition 待检查的过滤器定义
	 * @return 如果过滤器可用返回true，否则返回false
	 */
	private boolean isAvailable(FilterDefinition filterDefinition) {
		return GatewayFilters.stream()
				.anyMatch(gatewayFilterFactory -> filterDefinition.getName().equals(gatewayFilterFactory.name()));
	}

	/**
	 * 检查谓词定义是否可用
	 * <p>
	 * 通过检查谓词名称是否存在于RoutePredicateFactory列表中来判断。
	 * @param predicateDefinition 待检查的谓词定义
	 * @return 如果谓词可用返回true，否则返回false
	 */
	private boolean isAvailable(PredicateDefinition predicateDefinition) {
		return routePredicates.stream()
				.anyMatch(routePredicate -> predicateDefinition.getName().equals(routePredicate.name()));
	}

	/**
	 * 删除指定ID的路由定义
	 * <p>
	 * 该端点通过HTTP DELETE请求删除指定的路由定义。 删除成功后返回200 OK；如果路由不存在返回404 Not Found。
	 * @param id 要删除的路由唯一标识符
	 * @return 删除成功返回200 OK；路由不存在返回404 Not Found
	 */
	@DeleteMapping("/routes/{id}")
	public Mono<ResponseEntity<Object>> delete(@PathVariable String id) {
		return this.routeDefinitionWriter.delete(Mono.just(id))
				.then(Mono.defer(() -> Mono.just(ResponseEntity.ok().build())))
				.onErrorResume(t -> t instanceof NotFoundException, t -> Mono.just(ResponseEntity.notFound().build()));
	}

	/**
	 * 获取指定路由的组合过滤器列表
	 * <p>
	 * 返回指定路由ID的所有过滤器的名称和执行顺序。 注意：目前该方法仅返回网关过滤器，不包含全局过滤器。
	 *
	 * <b>TODO：</b>需要补充全局过滤器的返回
	 * @param id 路由的唯一标识符
	 * @return 包含过滤器名称和顺序的Map；如果路由不存在则返回空Map
	 */
	@GetMapping("/routes/{id}/combinedfilters")
	public Mono<HashMap<String, Object>> combinedfilters(@PathVariable String id) {
		// TODO: missing global filters
		return this.routeLocator.getRoutes().filter(route -> route.getId().equals(id)).reduce(new HashMap<>(),
				this::putItem);
	}

}
