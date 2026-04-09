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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Spring Cloud Gateway的遗留风格REST控制器端点，提供传统格式的路由管理API
 * <p>
 * 该类是Gateway网关的Legacy版Actuator端点实现，保持了与传统Spring Cloud版本的兼容性。 通过Spring Boot
 * Actuator框架将其方法暴露为HTTP REST接口，端点ID同样为"gateway"。
 * <p>
 * <b>主要功能：</b>
 * <ul>
 * <li>路由列表查询：获取所有路由的详细信息，包含路由定义和路由对象</li>
 * <li>单路由查询：根据ID获取特定路由的路由定义</li>
 * </ul>
 * <p>
 * <b>与标准端点的区别：</b>
 * <ul>
 * <li>返回格式采用更详细的嵌套结构，区分route_definition和route_object</li>
 * <li>route_definition包含原始的配置信息</li>
 * <li>route_object包含运行时解析后的对象信息（如谓词和过滤器）</li>
 * </ul>
 * <p>
 * <b>使用场景：</b> 该端点主要用于向后兼容已存在的监控系统或管理界面， 新项目建议使用{@link GatewayControllerEndpoint}端点。
 *
 * @author Spencer Gibb
 * @see AbstractGatewayControllerEndpoint
 * @see GatewayControllerEndpoint
 */
@RestControllerEndpoint(id = "gateway")
public class GatewayLegacyControllerEndpoint extends AbstractGatewayControllerEndpoint {

	/**
	 * 构造方法，初始化遗留风格的网关控制器端点所需的所有依赖组件
	 * <p>
	 * 该构造方法接收所有必要的组件，用于查询和操作路由信息。 组件会被传递给父类进行初始化。
	 * @param routeDefinitionLocator 路由定义定位器，用于查询路由定义
	 * @param globalFilters 全局过滤器列表，作用于所有路由请求
	 * @param gatewayFilterFactories 网关过滤器工厂列表，用于创建路由级别的过滤器
	 * @param routePredicates 路由谓词工厂列表，用于判断请求是否匹配路由
	 * @param routeDefinitionWriter 路由定义写入器，用于保存/删除路由定义
	 * @param routeLocator 路由定位器，用于获取运行时路由信息
	 */
	public GatewayLegacyControllerEndpoint(RouteDefinitionLocator routeDefinitionLocator,
			List<GlobalFilter> globalFilters, List<GatewayFilterFactory> gatewayFilterFactories,
			List<RoutePredicateFactory> routePredicates, RouteDefinitionWriter routeDefinitionWriter,
			RouteLocator routeLocator) {
		super(routeDefinitionLocator, globalFilters, gatewayFilterFactories, routePredicates, routeDefinitionWriter,
				routeLocator);
	}

	/**
	 * 获取所有路由的详细信息列表（传统风格）
	 * <p>
	 * 返回Mono，包含每个路由的详细信息，采用传统嵌套结构。 每个路由信息包含以下核心字段：
	 * <ul>
	 * <li><b>route_id</b>：路由唯一标识符</li>
	 * <li><b>order</b>：路由执行顺序</li>
	 * <li><b>route_definition</b>：原始的路由定义对象（如果存在）</li>
	 * <li><b>route_object</b>：运行时路由对象（包含predicate、filters、metadata）</li>
	 * </ul>
	 *
	 * <b>数据来源逻辑：</b> 该方法同时获取RouteDefinitionLocator和RouteLocator的数据，
	 * 并将两者进行关联匹配。如果某个路由有对应的RouteDefinition，
	 * 则包含route_definition字段；如果没有（如动态路由），则包含route_object字段。
	 *
	 * <b>返回示例：</b> <pre>
	 * [
	 *   {
	 *     "route_id": "my-route",
	 *     "order": 0,
	 *     "route_definition": {
	 *       "id": "my-route",
	 *       "uri": "http://httpbin.org",
	 *       "predicates": [...],
	 *       "filters": [...]
	 *     }
	 *   },
	 *   {
	 *     "route_id": "dynamic-route",
	 *     "order": 1,
	 *     "route_object": {
	 *       "predicate": "Host:**.dynamic.com",
	 *       "filters": ["[AddRequestHeader]"]
	 *     }
	 *   }
	 * ]
	 * </pre>
	 * @return Mono，包含所有路由详细信息的列表
	 * @see RouteDefinition
	 * @see Route
	 */
	@GetMapping("/routes")
	public Mono<List<Map<String, Object>>> routes() {
		// 获取路由定义并按ID转换为Map
		Mono<Map<String, RouteDefinition>> routeDefs = this.routeDefinitionLocator.getRouteDefinitions()
				.collectMap(RouteDefinition::getId);
		// 获取运行时路由列表
		Mono<List<Route>> routes = this.routeLocator.getRoutes().collectList();
		// 合并两个数据源并转换格式
		return Mono.zip(routeDefs, routes).map(tuple -> {
			Map<String, RouteDefinition> defs = tuple.getT1();
			List<Route> routeList = tuple.getT2();
			List<Map<String, Object>> allRoutes = new ArrayList<>();

			// 遍历每个运行时路由，构建响应数据
			routeList.forEach(route -> {
				HashMap<String, Object> r = new HashMap<>();
				r.put("route_id", route.getId());
				r.put("order", route.getOrder());

				// 如果存在对应的RouteDefinition，添加route_definition字段
				if (defs.containsKey(route.getId())) {
					r.put("route_definition", defs.get(route.getId()));
				}
				// 否则添加route_object字段，包含运行时信息
				else {
					HashMap<String, Object> obj = new HashMap<>();

					// 添加谓词信息
					obj.put("predicate", route.getPredicate().toString());

					// 添加过滤器列表（如果存在）
					if (!route.getFilters().isEmpty()) {
						ArrayList<String> filters = new ArrayList<>();
						for (GatewayFilter filter : route.getFilters()) {
							filters.add(filter.toString());
						}
						obj.put("filters", filters);
					}

					// 添加元数据（如果存在且非空）
					if (!CollectionUtils.isEmpty(route.getMetadata())) {
						obj.put("metadata", route.getMetadata());
					}

					// 只有当route_object非空时才添加到响应中
					if (!obj.isEmpty()) {
						r.put("route_object", obj);
					}
				}
				allRoutes.add(r);
			});

			return allRoutes;
		});
	}

	/**
	 * 根据路由ID获取路由定义（传统风格）
	 * <p>
	 * 查询指定ID的路由定义并返回。 如果找到匹配的路由定义，返回200 OK状态码和路由定义对象； 如果未找到，返回404 Not Found状态码。
	 *
	 * <b>TODOs：</b>
	 * <ul>
	 * <li>该方法目前只查询RouteDefinitionLocator，可能遗漏通过RouteLocator添加的动态路由</li>
	 * <li>未来版本应考虑同时查询RouteLocator以获取更完整的路由信息</li>
	 * </ul>
	 * @param id 路由的唯一标识符
	 * @return 如果找到返回200 OK及路由定义；未找到返回404 Not Found
	 * @see RouteDefinition
	 */
	@GetMapping("/routes/{id}")
	public Mono<ResponseEntity<RouteDefinition>> route(@PathVariable String id) {
		// TODO: missing RouteLocator
		return this.routeDefinitionLocator.getRouteDefinitions().filter(route -> route.getId().equals(id))
				.singleOrEmpty().map(ResponseEntity::ok).switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
	}

}
