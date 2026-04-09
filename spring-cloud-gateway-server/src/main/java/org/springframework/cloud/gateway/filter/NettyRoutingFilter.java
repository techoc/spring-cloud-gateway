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
import java.time.Duration;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.Type;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.CONNECT_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_HEADER_NAMES;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * 基于 Netty 的 HTTP/HTTPS 路由过滤器。
 * <p>
 * 该全局过滤器使用 Reactor Netty 的 {@link HttpClient} 发起实际的 HTTP/HTTPS 请求，
 * 将网关收到的请求代理转发到目标下游服务。主要处理 {@code http} 和 {@code https} 协议。
 * <p>
 * 核心功能：
 * <ul>
 * <li>过滤请求头（通过 {@link HttpHeadersFilter}），移除或修改不适合转发的头部；</li>
 * <li>支持 HOST 头保留（根据 {@code preserveHost} 属性配置）；</li>
 * <li>将下游响应（状态码、响应头）设置到当前 exchange，供后续过滤器使用；</li>
 * <li>支持按路由级别配置连接超时（{@code connectTimeout}）和响应超时（{@code responseTimeout}）；</li>
 * <li>将客户端响应连接存入 exchange 属性，供 {@link NettyWriteResponseFilter} 回写响应体。</li>
 * </ul>
 * <p>
 * 执行顺序为 {@link Ordered#LOWEST_PRECEDENCE}，在所有过滤器中最后执行。
 * <p>
 * 注意：{@code headersFilters} 通过 {@link ObjectProvider} 延迟获取，请使用
 * {@link #getHeadersFilters()} 方法访问，不要直接使用字段。
 *
 * @author Spencer Gibb
 * @author Biju Kunjummen
 */
public class NettyRoutingFilter implements GlobalFilter, Ordered {

	/**
	 * NettyRoutingFilter 的执行顺序，值为 {@link Ordered#LOWEST_PRECEDENCE}。
	 */
	public static final int ORDER = Ordered.LOWEST_PRECEDENCE;

	private static final Log log = LogFactory.getLog(NettyRoutingFilter.class);

	/** Reactor Netty HTTP 客户端，用于向下游服务发起请求 */
	private final HttpClient httpClient;

	/** 请求头过滤器列表的 ObjectProvider，延迟获取以避免循环依赖 */
	private final ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider;

	/** HTTP 客户端全局配置属性（包含响应超时等设置） */
	private final HttpClientProperties properties;

	/** 请求头过滤器列表缓存，请勿直接使用，应通过 getHeadersFilters() 方法获取 */
	private volatile List<HttpHeadersFilter> headersFilters;

	/**
	 * 构造 NettyRoutingFilter。
	 * @param httpClient Reactor Netty HTTP 客户端
	 * @param headersFiltersProvider 请求头过滤器列表的 ObjectProvider
	 * @param properties HTTP 客户端配置属性
	 */
	public NettyRoutingFilter(HttpClient httpClient, ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider,
			HttpClientProperties properties) {
		this.httpClient = httpClient;
		this.headersFiltersProvider = headersFiltersProvider;
		this.properties = properties;
	}

	/**
	 * 获取请求头过滤器列表。
	 * <p>
	 * 首次调用时从 ObjectProvider 中获取并缓存，后续调用直接返回缓存值。
	 * @return 请求头过滤器列表
	 */
	public List<HttpHeadersFilter> getHeadersFilters() {
		if (headersFilters == null) {
			headersFilters = headersFiltersProvider.getIfAvailable();
		}
		return headersFilters;
	}

	/**
	 * 返回过滤器执行顺序。
	 * @return {@link Ordered#LOWEST_PRECEDENCE}，即最低优先级（最后执行）
	 */
	@Override
	public int getOrder() {
		return ORDER;
	}

	/**
	 * 过滤请求，将 HTTP/HTTPS 请求通过 Netty 代理转发到目标服务。
	 * <p>
	 * 仅处理 {@code http} 或 {@code https} 协议且尚未被路由的请求。 流程：
	 * <ol>
	 * <li>检查协议和路由状态，不符合条件则跳过；</li>
	 * <li>过滤请求头，移除不适合转发的头部；</li>
	 * <li>使用 Netty 发起代理请求，并将响应状态码和响应头设置到 exchange；</li>
	 * <li>将响应连接对象存入 exchange 属性，供 {@link NettyWriteResponseFilter} 回写响应体；</li>
	 * <li>应用响应超时配置。</li>
	 * </ol>
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	@SuppressWarnings("Duplicates")
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (isAlreadyRouted(exchange) || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		ServerHttpRequest request = exchange.getRequest();

		final HttpMethod method = HttpMethod.valueOf(request.getMethodValue());
		final String url = requestUrl.toASCIIString();

		// 过滤请求头
		HttpHeaders filtered = filterRequest(getHeadersFilters(), exchange);

		final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
		filtered.forEach(httpHeaders::set);

		boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

		// 构建 Netty 请求并发送，获取响应流
		Flux<HttpClientResponse> responseFlux = getHttpClient(route, exchange).headers(headers -> {
			headers.add(httpHeaders);
			// HOST 头由下方逻辑或 Netty 设置
			headers.remove(HttpHeaders.HOST);
			if (preserveHost) {
				String host = request.getHeaders().getFirst(HttpHeaders.HOST);
				headers.add(HttpHeaders.HOST, host);
			}
		}).request(method).uri(url).send((req, nettyOutbound) -> {
			if (log.isTraceEnabled()) {
				nettyOutbound.withConnection(connection -> log.trace("outbound route: "
						+ connection.channel().id().asShortText() + ", inbound: " + exchange.getLogPrefix()));
			}
			return nettyOutbound.send(request.getBody().map(this::getByteBuf));
		}).responseConnection((res, connection) -> {

			// 延迟提交响应，直到所有路由过滤器运行完毕
			// 将客户端响应存入 exchange 属性，由 NettyWriteResponseFilter 后续写响应体
			exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
			exchange.getAttributes().put(CLIENT_RESPONSE_CONN_ATTR, connection);

			ServerHttpResponse response = exchange.getResponse();
			// 提前设置响应头和状态码，以便过滤器可以修改
			HttpHeaders headers = new HttpHeaders();

			res.responseHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));

			String contentTypeValue = headers.getFirst(HttpHeaders.CONTENT_TYPE);
			if (StringUtils.hasLength(contentTypeValue)) {
				exchange.getAttributes().put(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR, contentTypeValue);
			}

			setResponseStatus(res, response);

			// 在设置状态码后运行响应头过滤器，确保状态码可用
			HttpHeaders filteredResponseHeaders = HttpHeadersFilter.filter(getHeadersFilters(), headers, exchange,
					Type.RESPONSE);

			// Transfer-Encoding 和 Content-Length 不能同时存在，若有 Content-Length 则移除
			// Transfer-Encoding
			if (!filteredResponseHeaders.containsKey(HttpHeaders.TRANSFER_ENCODING)
					&& filteredResponseHeaders.containsKey(HttpHeaders.CONTENT_LENGTH)) {
				response.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
			}

			exchange.getAttributes().put(CLIENT_RESPONSE_HEADER_NAMES, filteredResponseHeaders.keySet());

			response.getHeaders().addAll(filteredResponseHeaders);

			return Mono.just(res);
		});

		// 应用响应超时配置
		Duration responseTimeout = getResponseTimeout(route);
		if (responseTimeout != null) {
			responseFlux = responseFlux
					.timeout(responseTimeout,
							Mono.error(new TimeoutException("Response took longer than timeout: " + responseTimeout)))
					.onErrorMap(TimeoutException.class,
							th -> new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, th.getMessage(), th));
		}

		return responseFlux.then(chain.filter(exchange));
	}

	/**
	 * 将 Spring {@link DataBuffer} 转换为 Netty 的 {@link ByteBuf}。
	 * <p>
	 * 支持 {@link NettyDataBuffer} 和 {@link DefaultDataBuffer} 两种类型。
	 * @param dataBuffer 待转换的数据缓冲区
	 * @return Netty {@link ByteBuf} 对象
	 * @throws IllegalArgumentException 若数据缓冲区类型不支持
	 */
	protected ByteBuf getByteBuf(DataBuffer dataBuffer) {
		if (dataBuffer instanceof NettyDataBuffer) {
			NettyDataBuffer buffer = (NettyDataBuffer) dataBuffer;
			return buffer.getNativeBuffer();
		}
		// MockServerHttpResponse 会创建 DefaultDataBuffer
		else if (dataBuffer instanceof DefaultDataBuffer) {
			DefaultDataBuffer buffer = (DefaultDataBuffer) dataBuffer;
			return Unpooled.wrappedBuffer(buffer.getNativeBuffer());
		}
		throw new IllegalArgumentException("Unable to handle DataBuffer of type " + dataBuffer.getClass());
	}

	/**
	 * 将下游响应的 HTTP 状态码设置到当前响应对象。
	 * <p>
	 * 若状态码为标准 HTTP 状态码，直接设置；否则尝试通过底层响应对象设置原始状态码。
	 * @param clientResponse Netty HTTP 客户端响应
	 * @param response 当前服务器 HTTP 响应对象
	 * @throws IllegalStateException 若无法在当前响应类型上设置状态码
	 */
	private void setResponseStatus(HttpClientResponse clientResponse, ServerHttpResponse response) {
		HttpStatus status = HttpStatus.resolve(clientResponse.status().code());
		if (status != null) {
			response.setStatusCode(status);
		}
		else {
			while (response instanceof ServerHttpResponseDecorator) {
				response = ((ServerHttpResponseDecorator) response).getDelegate();
			}
			if (response instanceof AbstractServerHttpResponse) {
				((AbstractServerHttpResponse) response).setRawStatusCode(clientResponse.status().code());
			}
			else {
				// TODO: 此处是否应记录警告而非抛出异常？
				throw new IllegalStateException("Unable to set status code " + clientResponse.status().code()
						+ " on response of type " + response.getClass().getName());
			}
		}
	}

	/**
	 * 根据路由元数据创建配置了连接超时的 {@link HttpClient}。
	 * <p>
	 * 若路由元数据中包含连接超时配置（{@code connectTimeout}），则以该值创建新的 HttpClient； 否则返回默认的
	 * HttpClient。子类覆盖此方法时，应调用 {@code super.getHttpClient()} 以保留超时配置。
	 * @param route 当前路由对象
	 * @param exchange 当前服务器 Web 交换对象
	 * @return 配置好的 {@link HttpClient} 实例
	 */
	protected HttpClient getHttpClient(Route route, ServerWebExchange exchange) {
		Object connectTimeoutAttr = route.getMetadata().get(CONNECT_TIMEOUT_ATTR);
		if (connectTimeoutAttr != null) {
			Integer connectTimeout = getInteger(connectTimeoutAttr);
			return this.httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
		}
		return httpClient;
	}

	/**
	 * 将连接超时属性值转换为 Integer。
	 * @param connectTimeoutAttr 连接超时属性值（可能为 Integer 或字符串）
	 * @return 连接超时毫秒数
	 */
	static Integer getInteger(Object connectTimeoutAttr) {
		Integer connectTimeout;
		if (connectTimeoutAttr instanceof Integer) {
			connectTimeout = (Integer) connectTimeoutAttr;
		}
		else {
			connectTimeout = Integer.parseInt(connectTimeoutAttr.toString());
		}
		return connectTimeout;
	}

	/**
	 * 获取路由级别的响应超时配置。
	 * <p>
	 * 优先读取路由元数据中的超时配置；若路由未配置或配置值为负数，则使用全局配置； 若配置值无法解析为数字，同样回退到全局配置。
	 * @param route 当前路由对象
	 * @return 响应超时 {@link Duration}，若无配置则返回全局超时设置（可能为 null）
	 */
	private Duration getResponseTimeout(Route route) {
		try {
			if (route.getMetadata().containsKey(RESPONSE_TIMEOUT_ATTR)) {
				Long routeResponseTimeout = getLong(route.getMetadata().get(RESPONSE_TIMEOUT_ATTR));
				if (routeResponseTimeout != null && routeResponseTimeout >= 0) {
					return Duration.ofMillis(routeResponseTimeout);
				}
				else {
					return null;
				}
			}
		}
		catch (NumberFormatException e) {
			// 解析失败，忽略并使用全局默认超时
		}
		return properties.getResponseTimeout();
	}

	/**
	 * 将响应超时属性值转换为 Long。
	 * @param responseTimeoutAttr 响应超时属性值（可能为 Number 子类或字符串）
	 * @return 响应超时毫秒数，若属性值为 null 则返回 null
	 */
	static Long getLong(Object responseTimeoutAttr) {
		Long responseTimeout = null;
		if (responseTimeoutAttr instanceof Number) {
			responseTimeout = ((Number) responseTimeoutAttr).longValue();
		}
		else if (responseTimeoutAttr != null) {
			responseTimeout = Long.parseLong(responseTimeoutAttr.toString());
		}
		return responseTimeout;
	}

}
