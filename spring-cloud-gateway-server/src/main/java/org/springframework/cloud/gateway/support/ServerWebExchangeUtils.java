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

package org.springframework.cloud.gateway.support;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import io.netty.buffer.Unpooled;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * ServerWebExchange 工具类，提供网关请求处理相关的通用工具方法。
 * <p>
 * 该类是 Spring Cloud Gateway 中最核心的工具类之一，封装了大量与请求交换 (ServerWebExchange) 相关的操作，包括：
 * <ul>
 * <li>请求/响应属性（Attributes）的存储和读取</li>
 * <li>路由状态管理（已路由标记）</li>
 * <li>响应状态设置</li>
 * <li>URI 模板变量处理</li>
 * <li>请求体缓存</li>
 * <li>URL 编码检测</li>
 * </ul>
 * </p>
 * <p>
 * 所有属性名称使用类的全限定名作为前缀（如
 * "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.xxx"）， 避免与其他组件的属性冲突。
 * </p>
 *
 * @author Spencer Gibb
 * @see ServerWebExchange
 * @see AsyncPredicate
 */
public final class ServerWebExchangeUtils {

	private static final Log log = LogFactory.getLog(ServerWebExchangeUtils.class);

	/**
	 * 保留原始 Host 头属性的名称。
	 * <p>
	 * 当需要保留原始请求的 Host 头而不是使用后端服务的主机名时，设置此属性。
	 */
	public static final String PRESERVE_HOST_HEADER_ATTRIBUTE = qualify("preserveHostHeader");

	/**
	 * URI 模板变量属性的名称。
	 * <p>
	 * 存储从路由谓词中提取的 URI 模板变量，如路径参数。 例如：{@code /user/{id}} 匹配到 {@code /user/123} 时，
	 * variables 中包含 {@code id -> "123"}。
	 */
	public static final String URI_TEMPLATE_VARIABLES_ATTRIBUTE = qualify("uriTemplateVariables");

	/**
	 * 客户端响应属性的名称。
	 * <p>
	 * 存储网关向后端服务发起请求后获得的响应对象。
	 */
	public static final String CLIENT_RESPONSE_ATTR = qualify("gatewayClientResponse");

	/**
	 * 客户端响应连接属性的名称。
	 * <p>
	 * 存储客户端响应的底层网络连接信息。
	 */
	public static final String CLIENT_RESPONSE_CONN_ATTR = qualify("gatewayClientResponseConnection");

	/**
	 * 客户端响应头名称集合属性的名称。
	 * <p>
	 * 存储网关添加的响应头名称，用于后续清理。
	 */
	public static final String CLIENT_RESPONSE_HEADER_NAMES = qualify("gatewayClientResponseHeaderNames");

	/**
	 * 网关路由属性的名称。
	 * <p>
	 * 存储与当前请求匹配的路由对象（{@link org.springframework.cloud.gateway.route.Route}）。
	 */
	public static final String GATEWAY_ROUTE_ATTR = qualify("gatewayRoute");

	/**
	 * 网关请求 URL 属性的名称。
	 * <p>
	 * 存储实际转发请求的目标 URL。
	 */
	public static final String GATEWAY_REQUEST_URL_ATTR = qualify("gatewayRequestUrl");

	/**
	 * 网关原始请求 URL 属性的名称。
	 * <p>
	 * 存储请求的原始 URL（可能经过多次重写）。
	 */
	public static final String GATEWAY_ORIGINAL_REQUEST_URL_ATTR = qualify("gatewayOriginalRequestUrl");

	/**
	 * 网关处理器映射器属性的名称。
	 * <p>
	 * 存储匹配路由的处理器映射器信息。
	 */
	public static final String GATEWAY_HANDLER_MAPPER_ATTR = qualify("gatewayHandlerMapper");

	/**
	 * 网关协议前缀属性的名称。
	 * <p>
	 * 存储路由的原始协议前缀。
	 */
	public static final String GATEWAY_SCHEME_PREFIX_ATTR = qualify("gatewaySchemePrefix");

	/**
	 * 网关谓词路由属性的名称。
	 * <p>
	 * 存储路由谓词匹配的结果信息。
	 */
	public static final String GATEWAY_PREDICATE_ROUTE_ATTR = qualify("gatewayPredicateRouteAttr");

	/**
	 * 网关谓词匹配路径属性的名称。
	 * <p>
	 * 存储实际匹配的路径模式。
	 */
	public static final String GATEWAY_PREDICATE_MATCHED_PATH_ATTR = qualify("gatewayPredicateMatchedPathAttr");

	/**
	 * 网关谓词匹配路径路由 ID 属性的名称。
	 * <p>
	 * 存储匹配路径所属的路由 ID。
	 */
	public static final String GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR = qualify(
			"gatewayPredicateMatchedPathRouteIdAttr");

	/**
	 * 网关谓词路径容器属性的名称。
	 * <p>
	 * 存储路由谓词解析后的路径容器对象。
	 */
	public static final String GATEWAY_PREDICATE_PATH_CONTAINER_ATTR = qualify("gatewayPredicatePathContainer");

	/**
	 * 权重属性的名称。
	 * <p>
	 * 存储权重路由的权重值。
	 */
	public static final String WEIGHT_ATTR = qualify("routeWeight");

	/**
	 * 原始响应 Content-Type 属性名称。
	 */
	public static final String ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR = "original_response_content_type";

	/**
	 * 断路器执行异常属性名称。
	 * <p>
	 * 存储断路器执行过程中的异常信息。
	 */
	public static final String CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR = qualify("circuitBreakerExecutionException");

	/**
	 * 已路由标记属性名称。
	 * <p>
	 * 当路由过滤器成功执行后设置此属性，允许自定义过滤器禁用内置路由过滤器。
	 */
	public static final String GATEWAY_ALREADY_ROUTED_ATTR = qualify("gatewayAlreadyRouted");

	/**
	 * 已添加前缀标记属性名称。
	 * <p>
	 * 标记请求是否已添加路径前缀。
	 */
	public static final String GATEWAY_ALREADY_PREFIXED_ATTR = qualify("gatewayAlreadyPrefixed");

	/**
	 * 缓存的请求装饰器属性名称。
	 * <p>
	 * 当调用 {@link #cacheRequestBodyAndRequest(ServerWebExchange, Function)} 时，
	 * 将请求装饰器缓存到此属性中。
	 *
	 * @see #cacheRequestBodyAndRequest(ServerWebExchange, Function)
	 */
	public static final String CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR = "cachedServerHttpRequestDecorator";

	/**
	 * 缓存的请求体属性名称。
	 * <p>
	 * 当调用 {@link #cacheRequestBodyAndRequest(ServerWebExchange, Function)} 或
	 * {@link #cacheRequestBody(ServerWebExchange, Function)} 时， 将请求体缓存到此属性中。
	 *
	 * @see #cacheRequestBodyAndRequest(ServerWebExchange, Function)
	 * @see #cacheRequestBody(ServerWebExchange, Function)
	 */
	public static final String CACHED_REQUEST_BODY_ATTR = "cachedRequestBody";

	/**
	 * 网关负载均衡器响应属性名称。
	 * <p>
	 * 存储负载均衡器的响应结果。
	 */
	public static final String GATEWAY_LOADBALANCER_RESPONSE_ATTR = qualify("gatewayLoadBalancerResponse");

	/** 空字节数组，用于空请求体的默认包装 */
	private static final byte[] EMPTY_BYTES = {};

	/**
	 * 私有构造函数，防止实例化。
	 */
	private ServerWebExchangeUtils() {
		throw new AssertionError("Must not instantiate utility class.");
	}

	/**
	 * 使用类名作为属性名称前缀。
	 * @param attr 属性名
	 * @return 带前缀的属性名
	 */
	private static String qualify(String attr) {
		return ServerWebExchangeUtils.class.getName() + "." + attr;
	}

	/**
	 * 标记请求已路由。
	 * <p>
	 * 在路由过滤器执行成功后调用此方法，标记请求已被路由， 防止重复路由或用于判断是否需要执行某些后置逻辑。
	 * @param exchange 当前请求交换
	 */
	public static void setAlreadyRouted(ServerWebExchange exchange) {
		exchange.getAttributes().put(GATEWAY_ALREADY_ROUTED_ATTR, true);
	}

	/**
	 * 移除已路由标记。
	 * @param exchange 当前请求交换
	 */
	public static void removeAlreadyRouted(ServerWebExchange exchange) {
		exchange.getAttributes().remove(GATEWAY_ALREADY_ROUTED_ATTR);
	}

	/**
	 * 检查请求是否已路由。
	 * @param exchange 当前请求交换
	 * @return true 表示已路由
	 */
	public static boolean isAlreadyRouted(ServerWebExchange exchange) {
		return exchange.getAttributeOrDefault(GATEWAY_ALREADY_ROUTED_ATTR, false);
	}

	/**
	 * 设置响应 HTTP 状态。
	 * @param exchange 当前请求交换
	 * @param httpStatus 要设置的 HTTP 状态
	 * @return true 表示设置成功，false 表示响应已提交无法设置
	 */
	public static boolean setResponseStatus(ServerWebExchange exchange, HttpStatus httpStatus) {
		boolean response = exchange.getResponse().setStatusCode(httpStatus);
		if (!response && log.isWarnEnabled()) {
			log.warn("Unable to set status code to " + httpStatus + ". Response already committed.");
		}
		return response;
	}

	/**
	 * 重置请求交换状态。
	 * <p>
	 * 清除网关添加的响应头，移除已路由标记。 用于在路由失败后清理状态。
	 * @param exchange 当前请求交换
	 */
	public static void reset(ServerWebExchange exchange) {
		// TODO: what else to do to reset exchange?
		Set<String> addedHeaders = exchange.getAttributeOrDefault(CLIENT_RESPONSE_HEADER_NAMES, Collections.emptySet());
		addedHeaders.forEach(header -> exchange.getResponse().getHeaders().remove(header));
		removeAlreadyRouted(exchange);
	}

	/**
	 * 使用 HttpStatusHolder 设置响应状态。
	 * <p>
	 * 支持标准和非标准的 HTTP 状态码。
	 * @param exchange 当前请求交换
	 * @param statusHolder 状态持有器
	 * @return true 表示设置成功
	 */
	public static boolean setResponseStatus(ServerWebExchange exchange, HttpStatusHolder statusHolder) {
		if (exchange.getResponse().isCommitted()) {
			return false;
		}
		if (log.isDebugEnabled()) {
			log.debug("Setting response status to " + statusHolder);
		}
		if (statusHolder.getHttpStatus() != null) {
			return setResponseStatus(exchange, statusHolder.getHttpStatus());
		}
		if (statusHolder.getStatus() != null && exchange.getResponse() instanceof AbstractServerHttpResponse) { // non-standard
			((AbstractServerHttpResponse) exchange.getResponse()).setRawStatusCode(statusHolder.getStatus());
			return true;
		}
		return false;
	}

	/**
	 * 检测 URI 是否包含编码部分。
	 * <p>
	 * 检查 URI 的路径和查询参数是否包含 URL 编码的字符。
	 * </p>
	 * @param uri 要检查的 URI
	 * @return true 表示包含编码部分
	 */
	public static boolean containsEncodedParts(URI uri) {
		boolean encoded = (uri.getRawQuery() != null && uri.getRawQuery().contains("%"))
				|| (uri.getRawPath() != null && uri.getRawPath().contains("%"));

		// 验证是否是完全编码的，部分编码视为未编码
		if (encoded) {
			try {
				UriComponentsBuilder.fromUri(uri).build(true);
				return true;
			}
			catch (IllegalArgumentException ignored) {
				if (log.isTraceEnabled()) {
					log.trace("Error in containsEncodedParts", ignored);
				}
			}

			return false;
		}

		return encoded;
	}

	/**
	 * 解析字符串为 HttpStatus。
	 * <p>
	 * 支持整数状态码（如 "200"）和枚举名称（如 "OK"）。
	 * @param statusString 状态字符串
	 * @return HttpStatus 枚举值，若解析失败返回 null
	 */
	public static HttpStatus parse(String statusString) {
		HttpStatus httpStatus;

		try {
			// 尝试解析为整数状态码
			int status = Integer.parseInt(statusString);
			httpStatus = HttpStatus.resolve(status);
		}
		catch (NumberFormatException e) {
			// 尝试解析为枚举名称
			httpStatus = HttpStatus.valueOf(statusString.toUpperCase());
		}
		return httpStatus;
	}

	/**
	 * 添加原始请求 URL 到列表。
	 * <p>
	 * 存储请求经历的所有 URL（可能经过多次重写）。
	 * @param exchange 当前请求交换
	 * @param url 要添加的 URL
	 */
	public static void addOriginalRequestUrl(ServerWebExchange exchange, URI url) {
		exchange.getAttributes().computeIfAbsent(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, s -> new LinkedHashSet<>());
		LinkedHashSet<URI> uris = exchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		uris.add(url);
	}

	/**
	 * 将 Predicate 转换为 AsyncPredicate。
	 * @param predicate 要转换的谓词
	 * @return 异步谓词
	 */
	public static AsyncPredicate<ServerWebExchange> toAsyncPredicate(Predicate<? super ServerWebExchange> predicate) {
		Assert.notNull(predicate, "predicate must not be null");
		return AsyncPredicate.from(predicate);
	}

	/**
	 * 扩展 URI 模板中的变量。
	 * <p>
	 * 使用请求匹配时提取的变量值替换模板中的占位符。 例如：模板 "/user/{id}" 使用变量 {"id": "123"} 扩展后返回 "/user/123"。
	 * </p>
	 * @param exchange 当前请求交换
	 * @param template 包含占位符的 URI 模板
	 * @return 扩展后的 URI 字符串
	 */
	public static String expand(ServerWebExchange exchange, String template) {
		Assert.notNull(exchange, "exchange may not be null");
		Assert.notNull(template, "template may not be null");

		// 无占位符，直接返回
		if (template.indexOf('{') == -1) { // short circuit
			return template;
		}

		Map<String, String> variables = getUriTemplateVariables(exchange);
		return UriComponentsBuilder.fromPath(template).build().expand(variables).getPath();
	}

	/**
	 * 存储 URI 模板变量。
	 * <p>
	 * 如果已存在变量，会合并新旧变量。
	 * @param exchange 当前请求交换
	 * @param uriVariables URI 变量映射
	 */
	@SuppressWarnings("unchecked")
	public static void putUriTemplateVariables(ServerWebExchange exchange, Map<String, String> uriVariables) {
		if (exchange.getAttributes().containsKey(URI_TEMPLATE_VARIABLES_ATTRIBUTE)) {
			Map<String, Object> existingVariables = (Map<String, Object>) exchange.getAttributes()
					.get(URI_TEMPLATE_VARIABLES_ATTRIBUTE);
			HashMap<String, Object> newVariables = new HashMap<>();
			newVariables.putAll(existingVariables);
			newVariables.putAll(uriVariables);
			exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, newVariables);
		}
		else {
			exchange.getAttributes().put(URI_TEMPLATE_VARIABLES_ATTRIBUTE, uriVariables);
		}
	}

	/**
	 * 获取 URI 模板变量。
	 * @param exchange 当前请求交换
	 * @return URI 变量映射，若无则返回空 Map
	 */
	public static Map<String, String> getUriTemplateVariables(ServerWebExchange exchange) {
		return exchange.getAttributeOrDefault(URI_TEMPLATE_VARIABLES_ATTRIBUTE, new HashMap<>());
	}

	/**
	 * 缓存请求体并提供请求装饰器。
	 * <p>
	 * 当 {@link ServerWebExchange} 不能被修改时（如在 {@link RoutePredicateFactory} 中），
	 * 此方法非常有用。它会将请求体和装饰后的请求对象缓存到交换属性中。
	 * </p>
	 * <p>
	 * 缓存的属性键：
	 * <ul>
	 * <li>{@link #CACHED_REQUEST_BODY_ATTR} - 缓存的请求体</li>
	 * <li>{@link #CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR} - 装饰后的请求</li>
	 * </ul>
	 * </p>
	 * @param exchange 当前请求交换
	 * @param function 处理装饰请求的函数
	 * @param <T> 返回值的类型
	 * @return 由函数产生的 Mono
	 */
	public static <T> Mono<T> cacheRequestBodyAndRequest(ServerWebExchange exchange,
			Function<ServerHttpRequest, Mono<T>> function) {
		return cacheRequestBody(exchange, true, function);
	}

	/**
	 * 缓存请求体。
	 * <p>
	 * 当 {@link ServerWebExchange} 可以被修改时（如在 {@link GatewayFilterFactory} 中）使用。
	 * </p>
	 * @param exchange 当前请求交换
	 * @param function 处理请求的函数
	 * @param <T> 返回值的类型
	 * @return 由函数产生的 Mono
	 */
	public static <T> Mono<T> cacheRequestBody(ServerWebExchange exchange,
			Function<ServerHttpRequest, Mono<T>> function) {
		return cacheRequestBody(exchange, false, function);
	}

	/**
	 * 内部方法：缓存请求体的实际实现。
	 * @param exchange 当前请求交换
	 * @param cacheDecoratedRequest 是否缓存装饰后的请求
	 * @param function 处理请求的函数
	 * @param <T> 返回值的类型
	 * @return 由函数产生的 Mono
	 */
	private static <T> Mono<T> cacheRequestBody(ServerWebExchange exchange, boolean cacheDecoratedRequest,
			Function<ServerHttpRequest, Mono<T>> function) {
		ServerHttpResponse response = exchange.getResponse();
		DataBufferFactory factory = response.bufferFactory();
		// 合并所有 DataBuffer 为单一数据块
		return DataBufferUtils.join(exchange.getRequest().getBody()).defaultIfEmpty(factory.wrap(EMPTY_BYTES))
				.map(dataBuffer -> decorate(exchange, dataBuffer, cacheDecoratedRequest))
				.switchIfEmpty(Mono.just(exchange.getRequest())).flatMap(function);
	}

	/**
	 * 装饰请求，缓存请求体。
	 * @param exchange 当前请求交换
	 * @param dataBuffer 缓存的数据缓冲区
	 * @param cacheDecoratedRequest 是否缓存装饰请求
	 * @return 装饰后的请求对象
	 */
	private static ServerHttpRequest decorate(ServerWebExchange exchange, DataBuffer dataBuffer,
			boolean cacheDecoratedRequest) {
		if (dataBuffer.readableByteCount() > 0) {
			if (log.isTraceEnabled()) {
				log.trace("retaining body in exchange attribute");
			}

			Object cachedDataBuffer = exchange.getAttribute(CACHED_REQUEST_BODY_ATTR);
			// 如果已有缓存则不再缓存
			if (!(cachedDataBuffer instanceof DataBuffer)) {
				exchange.getAttributes().put(CACHED_REQUEST_BODY_ATTR, dataBuffer);
			}
		}

		// 创建请求装饰器，重写 getBody 方法从缓存读取
		ServerHttpRequest decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
			@Override
			public Flux<DataBuffer> getBody() {
				return Mono.fromSupplier(() -> {
					if (exchange.getAttribute(CACHED_REQUEST_BODY_ATTR) == null) {
						// 下游可能关闭了连接或无请求体
						return null;
					}
					if (dataBuffer instanceof NettyDataBuffer) {
						NettyDataBuffer pdb = (NettyDataBuffer) dataBuffer;
						return pdb.factory().wrap(pdb.getNativeBuffer().retainedSlice());
					}
					else if (dataBuffer instanceof DefaultDataBuffer) {
						DefaultDataBuffer ddf = (DefaultDataBuffer) dataBuffer;
						return ddf.factory().wrap(Unpooled.wrappedBuffer(ddf.getNativeBuffer()).nioBuffer());
					}
					else {
						throw new IllegalArgumentException(
								"Unable to handle DataBuffer of type " + dataBuffer.getClass());
					}
				}).flux();
			}
		};
		if (cacheDecoratedRequest) {
			exchange.getAttributes().put(CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR, decorator);
		}
		return decorator;
	}

	/**
	 * 使用 DispatcherHandler 处理请求的统一入口。
	 * <p>
	 * 允许复用通用代码，处理前会清理可能干扰转发的属性。
	 * @param handler DispatcherHandler
	 * @param exchange 当前请求交换
	 * @return 处理结果
	 */
	public static Mono<Void> handle(DispatcherHandler handler, ServerWebExchange exchange) {
		// 移除可能干扰转发请求的属性
		exchange.getAttributes().remove(GATEWAY_PREDICATE_PATH_CONTAINER_ATTR);

		return handler.handle(exchange);
	}

}
