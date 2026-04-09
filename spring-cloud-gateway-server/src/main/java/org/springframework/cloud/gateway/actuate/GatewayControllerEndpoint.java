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

import reactor.core.publisher.Flux;
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
 * Spring Cloud Gateway的REST控制器端点，提供全面的路由管理API
 * <p>
 * 该类是Gateway网关的主要Actuator端点实现，通过Spring Boot Actuator框架 将其方法暴露为HTTP
 * REST接口。端点ID为"gateway"，可通过 {@code /actuator/gateway}路径访问。
 * <p>
 * <b>主要功能：</b>
 * <ul>
 * <li>路由定义查询：获取所有已配置的路由定义</li>
 * <li>路由实例查询：获取运行时路由的详细信息，包括过滤器和元数据</li>
 * <li>单路由查询：根据ID获取特定路由的完整信息</li>
 * </ul>
 * <p>
 * <b>与Legacy端点的区别：</b> 该端点采用现代化的响应格式，返回Map结构的数据，便于前端解析和使用。
 * 而{@link GatewayLegacyControllerEndpoint}则返回更详细的嵌套结构，
 * 包含route_definition和route_object的区分。
 *
 * @author Spencer Gibb
 * @see AbstractGatewayControllerEndpoint
 * @see GatewayLegacyControllerEndpoint
 */
@RestControllerEndpoint(id = "gateway")
public class GatewayControllerEndpoint extends AbstractGatewayControllerEndpoint {

	/**
	 * 构造方法，初始化网关控制器端点所需的所有依赖组件
	 * <p>
	 * 该构造方法接收所有必要的组件，用于查询和操作路由信息。
	 * @param globalFilters 全局过滤器列表，作用于所有路由请求
	 * @param gatewayFilters 网关过滤器工厂列表，用于创建路由级别的过滤器
	 * @param routePredicates 路由谓词工厂列表，用于判断请求是否匹配路由
	 * @param routeDefinitionWriter 路由定义写入器，用于保存/删除路由定义
	 * @param routeLocator 路由定位器，用于获取运行时路由信息
	 * @param routeDefinitionLocator 路由定义定位器，用于获取路由定义
	 */
	public GatewayControllerEndpoint(List<GlobalFilter> globalFilters, List<GatewayFilterFactory> gatewayFilters,
			List<RoutePredicateFactory> routePredicates, RouteDefinitionWriter routeDefinitionWriter,
			RouteLocator routeLocator, RouteDefinitionLocator routeDefinitionLocator) {
		super(routeDefinitionLocator, globalFilters, gatewayFilters, routePredicates, routeDefinitionWriter,
				routeLocator);
	}

	/**
	 * 获取所有路由定义
	 * <p>
	 * 返回Flux流，包含当前网关配置的所有路由定义对象。 路由定义是静态的配置信息，包含路由的基本属性。
	 * @return Flux流，包含所有路由定义对象
	 * @see RouteDefinition
	 */
	@GetMapping("/routedefinitions")
	public Flux<RouteDefinition> routesdef() {
		return this.routeDefinitionLocator.getRouteDefinitions();
	}

	/**
	 * 获取所有路由的运行时信息
	 * <p>
	 * 返回Flux流，包含每个路由的详细信息，格式为Map结构。 每个路由信息包含以下字段：
	 * <ul>
	 * <li><b>route_id</b>：路由唯一标识符</li>
	 * <li><b>uri</b>：目标服务URI</li>
	 * <li><b>order</b>：路由执行顺序（数值越小优先级越高）</li>
	 * <li><b>predicate</b>：路由谓词的条件描述</li>
	 * <li><b>metadata</b>：路由元数据（可选）</li>
	 * <li><b>filters</b>：应用于该路由的过滤器列表</li>
	 * </ul>
	 *
	 * <b>返回示例：</b> <pre>
	 * [
	 *   {
	 *     "route_id": "my-route",
	 *     "uri": "http://httpbin.org",
	 *     "order": 0,
	 *     "predicate": "Host:**.example.com",
	 *     "filters": ["[AddRequestHeader] order=0]"]
	 *   }
	 * ]
	 * </pre>
	 * @return Flux流，包含所有路由信息的Map列表
	 * @see Route
	 */
	@GetMapping("/routes")
	public Flux<Map<String, Object>> routes() {
		return this.routeLocator.getRoutes().map(this::serialize);
	}

	/**
	 * 将路由对象序列化为Map结构
	 * <p>
	 * 该方法将Route对象的运行时信息转换为Map格式，便于JSON序列化返回给客户端。 转换过程中会提取路由的核心属性，包括ID、URI、执行顺序、谓词和过滤器。
	 * @param route 路由对象
	 * @return 包含路由核心信息的Map
	 */
	Map<String, Object> serialize(Route route) {
		HashMap<String, Object> r = new HashMap<>();
		r.put("route_id", route.getId());
		r.put("uri", route.getUri().toString());
		r.put("order", route.getOrder());
		r.put("predicate", route.getPredicate().toString());
		if (!CollectionUtils.isEmpty(route.getMetadata())) {
			r.put("metadata", route.getMetadata());
		}

		ArrayList<String> filters = new ArrayList<>();

		for (int i = 0; i < route.getFilters().size(); i++) {
			GatewayFilter gatewayFilter = route.getFilters().get(i);
			filters.add(gatewayFilter.toString());
		}

		r.put("filters", filters);
		return r;
	}

	/**
	 * 根据路由ID获取特定路由的详细信息
	 * <p>
	 * 查询指定ID的路由并返回其完整信息。 如果找到匹配的路由，返回200 OK状态码和路由信息； 如果未找到，返回404 Not Found状态码。
	 * @param id 路由的唯一标识符
	 * @return 如果找到返回200 OK及路由信息；未找到返回404 Not Found
	 */
	@GetMapping("/routes/{id}")
	public Mono<ResponseEntity<Map<String, Object>>> route(@PathVariable String id) {
		// @formatter:off
		return this.routeLocator.getRoutes()
				.filter(route -> route.getId().equals(id))
				.singleOrEmpty()
				.map(this::serialize)
				.map(ResponseEntity::ok)
				.switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
		// @formatter:on
	}

}
