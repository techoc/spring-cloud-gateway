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

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;

/**
 * 路由转请求 URL 过滤器。
 * <p>
 * 该全局过滤器负责将路由配置中的 URI 与原始请求 URI 进行合并，生成最终的请求目标 URL。 主要处理路由 URI 中的 scheme、host、port
 * 部分，保留原始请求 URI 的路径和查询参数。
 * <p>
 * 处理特殊协议前缀的场景：
 * <ul>
 * <li>{@code lb:service/path} 形式：将 {@code lb} 存为 schemePrefix，解析为负载均衡路由；</li>
 * <li>{@code lb://service/path} 形式：类似上述，将 lb 提取后解析。</li>
 * </ul>
 * <p>
 * 执行顺序为 {@link #ROUTE_TO_URL_FILTER_ORDER}（值为 10000），在大多数过滤器之前执行。
 *
 * @author Spencer Gibb
 */
public class RouteToRequestUrlFilter implements GlobalFilter, Ordered {

	/**
	 * 路由转请求 URL 过滤器的执行顺序，值为 10000。
	 */
	public static final int ROUTE_TO_URL_FILTER_ORDER = 10000;

	private static final Log log = LogFactory.getLog(RouteToRequestUrlFilter.class);

	/**
	 * URI scheme 的正则表达式匹配模式。 用于检测 URI 是否包含嵌套 scheme（如 lb:http://host/path）。
	 */
	private static final String SCHEME_REGEX = "[a-zA-Z]([a-zA-Z]|\\d|\\+|\\.|-)*:.*";
	static final Pattern schemePattern = Pattern.compile(SCHEME_REGEX);

	/**
	 * 判断 URI 是否包含嵌套 scheme（特殊 URL 格式）。
	 * <p>
	 * 例如 {@code lb:http://service/path} 会被解析为 scheme="lb"，
	 * schemeSpecificPart="http://service/path"。 当 schemeSpecificPart 本身匹配 schemePattern 且
	 * host、path 均为 null 时， 表明存在嵌套 scheme。
	 * @param uri 待检测的 URI
	 * @return 若包含嵌套 scheme 则返回 {@code true}
	 */
	/* 仅用于测试 */
	static boolean hasAnotherScheme(URI uri) {
		return schemePattern.matcher(uri.getSchemeSpecificPart()).matches() && uri.getHost() == null
				&& uri.getRawPath() == null;
	}

	/**
	 * 返回过滤器执行顺序。
	 * @return {@link #ROUTE_TO_URL_FILTER_ORDER}，值为 10000
	 */
	@Override
	public int getOrder() {
		return ROUTE_TO_URL_FILTER_ORDER;
	}

	/**
	 * 过滤请求，将路由 URI 与原始请求 URI 合并为最终的目标 URL。
	 * <p>
	 * 处理流程：
	 * <ol>
	 * <li>获取当前路由和原始请求 URI；</li>
	 * <li>若路由 URI 包含嵌套 scheme，提取并保存 scheme 前缀（如 lb）；</li>
	 * <li>验证负载均衡 URI 必须有 host（host 为 null 通常表示 URI 格式错误，如包含下划线）；</li>
	 * <li>使用 UriComponentsBuilder 将路由 URI 的 scheme、host、port 与原始请求的 路径和查询参数合并，生成最终目标
	 * URL；</li>
	 * <li>将目标 URL 存入 exchange 属性。</li>
	 * </ol>
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
		if (route == null) {
			return chain.filter(exchange);
		}
		log.trace("RouteToRequestUrlFilter start");
		URI uri = exchange.getRequest().getURI();
		boolean encoded = containsEncodedParts(uri);
		URI routeUri = route.getUri();

		// 处理嵌套 scheme（如 lb:http://service/path）
		if (hasAnotherScheme(routeUri)) {
			// 将 scheme 保存到特殊属性，替换 routeUri 为 schemeSpecificPart
			exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, routeUri.getScheme());
			routeUri = URI.create(routeUri.getSchemeSpecificPart());
		}

		// 负载均衡 URI 必须有 host，host 为 null 通常表示格式错误（如包含非法字符）
		if ("lb".equalsIgnoreCase(routeUri.getScheme()) && routeUri.getHost() == null) {
			throw new IllegalStateException("Invalid host: " + routeUri.toString());
		}

		// 合并路由 URI 的 scheme/host/port 与原始请求的路径/查询参数
		URI mergedUrl = UriComponentsBuilder.fromUri(uri)
				// .uri(routeUri)
				.scheme(routeUri.getScheme()).host(routeUri.getHost()).port(routeUri.getPort()).build(encoded).toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, mergedUrl);
		return chain.filter(exchange);
	}

}
