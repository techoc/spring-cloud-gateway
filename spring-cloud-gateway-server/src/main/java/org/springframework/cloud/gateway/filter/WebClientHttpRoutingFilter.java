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
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * 基于WebClient的HTTP路由过滤器。
 *
 * <p>
 * 该过滤器是Spring Cloud Gateway的核心过滤器之一，负责将请求转发到后端服务。
 *
 * <p>
 * 功能说明：
 * <ul>
 * <li>实现GlobalFilter接口，作为全局过滤器作用于所有路由</li>
 * <li>使用WebClient发起HTTP/HTTPS请求到后端服务</li>
 * <li>处理请求头的过滤和转发</li>
 * <li>支持带请求体的请求转发（PUT/POST/PATCH）</li>
 * <li>支持保留Host头的配置选项</li>
 * </ul>
 *
 * <p>
 * 执行时机：
 * <ul>
 * <li>过滤器顺序为最低优先级（Ordered.LOWEST_PRECEDENCE）</li>
 * <li>在路由匹配后、响应写入前执行</li>
 * <li>负责实际的后端服务调用</li>
 * </ul>
 *
 * <p>
 * 工作流程：
 * <ol>
 * <li>从exchange获取目标URL</li>
 * <li>检查是否已路由或协议是否为HTTP/HTTPS</li>
 * <li>标记请求为已路由状态</li>
 * <li>获取并处理HTTP请求头</li>
 * <li>根据请求方法决定是否包含请求体</li>
 * <li>使用WebClient发送请求并获取响应</li>
 * <li>将后端响应保存到exchange属性中</li>
 * <li>继续执行过滤器链</li>
 * </ol>
 *
 * <p>
 * 注意事项：
 * <ul>
 * <li>必须确保WebClient Bean已正确配置</li>
 * <li>响应暂存到CLIENT_RESPONSE_ATTR，由NettyWriteResponseFilter实际写入</li>
 * <li>HTTP/HTTPS之外的协议将被跳过</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see GlobalFilter
 * @see Ordered
 * @see WebClient
 */
public class WebClientHttpRoutingFilter implements GlobalFilter, Ordered {

	/**
	 * WebClient实例，用于发起HTTP请求。
	 * <p>
	 * 通过构造器注入，支持自定义配置
	 * </p>
	 */
	private final WebClient webClient;

	/**
	 * HTTP头过滤器提供者。
	 * <p>
	 * 使用ObjectProvider延迟获取，支持多实例注入
	 * </p>
	 */
	private final ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider;

	/**
	 * 缓存的HTTP头过滤器列表。
	 * <p>
	 * 使用volatile保证可见性，通过getHeadersFilters()懒加载
	 * </p>
	 */
	// do not use this headersFilters directly, use getHeadersFilters() instead.
	private volatile List<HttpHeadersFilter> headersFilters;

	/**
	 * 构造函数，创建WebClientHttpRoutingFilter实例。
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>webClient - WebClient实例，用于发起HTTP请求</li>
	 * <li>headersFiltersProvider - HTTP头过滤器提供者，支持延迟解析</li>
	 * </ul>
	 * @param webClient WebClient实例
	 * @param headersFiltersProvider HTTP头过滤器提供者
	 */
	public WebClientHttpRoutingFilter(WebClient webClient,
			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider) {
		this.webClient = webClient;
		this.headersFiltersProvider = headersFiltersProvider;
	}

	/**
	 * 获取HTTP头过滤器列表。
	 *
	 * <p>
	 * 使用懒加载模式，首次访问时从Provider获取并缓存。 缓存后的列表使用volatile保证线程可见性。
	 * @return HTTP头过滤器列表
	 */
	public List<HttpHeadersFilter> getHeadersFilters() {
		if (headersFilters == null) {
			headersFilters = headersFiltersProvider.getIfAvailable();
		}
		return headersFilters;
	}

	/**
	 * 获取过滤器执行顺序。
	 *
	 * <p>
	 * 返回最低优先级，确保在其他过滤器之后执行。 这允许路由决策和其他预处理先完成。
	 * @return 过滤器顺序值
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * 执行请求路由过滤。
	 *
	 * <p>
	 * 核心业务逻辑：
	 * <ol>
	 * <li>从exchange获取目标请求URL</li>
	 * <li>验证URL协议（仅支持http和https）</li>
	 * <li>标记请求已路由，防止重复路由</li>
	 * <li>获取并过滤HTTP请求头</li>
	 * <li>根据配置决定是否保留Host头</li>
	 * <li>对于有请求体的方法，读取请求体数据</li>
	 * <li>使用WebClient发起请求</li>
	 * <li>将后端响应保存到exchange属性</li>
	 * <li>继续过滤器链执行</li>
	 * </ol>
	 *
	 * <p>
	 * 请求头处理：
	 * <ul>
	 * <li>X-Forwarded-* 系列头由ForwardedHeadersFilter处理</li>
	 * <li>默认移除Host头，除非配置保留</li>
	 * <li>Gateway自定义头通过HttpHeadersFilter处理</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>非HTTP/HTTPS请求将被跳过，继续执行过滤器链</li>
	 * <li>已路由的请求不会再次路由</li>
	 * <li>WebClient响应暂存，实际写入由后续过滤器完成</li>
	 * </ul>
	 * @param exchange 服务器Web交换对象
	 * @param chain 过滤器链
	 * @return 完成信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"http".equals(scheme) && !"https".equals(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		ServerHttpRequest request = exchange.getRequest();

		HttpMethod method = request.getMethod();

		HttpHeaders filteredHeaders = filterRequest(getHeadersFilters(), exchange);

		boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);

		RequestBodySpec bodySpec = this.webClient.method(method).uri(requestUrl).headers(httpHeaders -> {
			httpHeaders.addAll(filteredHeaders);
			// TODO: can this support preserviceHostHeader?
			if (!preserveHost) {
				httpHeaders.remove(HttpHeaders.HOST);
			}
		});

		RequestHeadersSpec<?> headersSpec;
		if (requiresBody(method)) {
			headersSpec = bodySpec.body(BodyInserters.fromDataBuffers(request.getBody()));
		}
		else {
			headersSpec = bodySpec;
		}

		return headersSpec.exchangeToMono(Mono::just)
				// .log("webClient route")
				.flatMap(res -> {
					ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().putAll(res.headers().asHttpHeaders());
					response.setStatusCode(res.statusCode());
					// Defer committing the response until all route filters have run
					// Put client response as ServerWebExchange attribute and write
					// response later NettyWriteResponseFilter
					exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
					return chain.filter(exchange);
				});
	}

	/**
	 * 判断HTTP方法是否需要请求体。
	 *
	 * <p>
	 * 以下HTTP方法被认为需要请求体：
	 * <ul>
	 * <li>PUT - 用于更新资源</li>
	 * <li>POST - 用于创建资源</li>
	 * <li>PATCH - 用于部分更新资源</li>
	 * </ul>
	 *
	 * <p>
	 * 以下HTTP方法不需要请求体：
	 * <ul>
	 * <li>GET - 获取资源</li>
	 * <li>DELETE - 删除资源</li>
	 * <li>HEAD - 获取头部信息</li>
	 * <li>OPTIONS - 获取支持的选项</li>
	 * </ul>
	 * @param method HTTP方法
	 * @return 如果需要请求体返回true，否则返回false
	 */
	private boolean requiresBody(HttpMethod method) {
		switch (method) {
		case PUT:
		case POST:
		case PATCH:
			return true;
		default:
			return false;
		}
	}

}
