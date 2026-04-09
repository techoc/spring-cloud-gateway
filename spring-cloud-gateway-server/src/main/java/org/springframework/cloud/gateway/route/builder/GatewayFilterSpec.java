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

package org.springframework.cloud.gateway.route.builder;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.retry.Repeat;
import reactor.retry.Retry;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractChangeRequestUriGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.CacheRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory.Strategy;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.JsonToGrpcGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderToRequestUriGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory.StripVersion;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SaveSessionGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网关过滤器规范类，用于为路由应用特定的过滤器。
 *
 * <p>
 * 此类是构建器模式的一部分，提供了流畅的API来配置和添加各种网关过滤器。 它继承自 {@link UriSpec}，支持方法链式调用，允许连续配置多个过滤器。
 * </p>
 *
 * <p>
 * 支持的主要过滤器类型包括：
 * </p>
 * <ul>
 * <li>请求/响应头操作：添加、设置、删除、重写</li>
 * <li>路径操作：前缀添加、前缀剥离、路径重写</li>
 * <li>请求/响应体修改：支持类型转换和内容重写</li>
 * <li>安全相关：安全头、熔断器、限流器</li>
 * <li>重试机制：配置失败请求的重试策略</li>
 * <li>重定向：返回HTTP重定向响应</li>
 * <li>会话管理：保存会话状态</li>
 * <li>协议转换：JSON到gRPC转换</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 * </p>
 * <pre>{@code
 * builder.routes()
 *     .route("example", r -> r
 *         .path("/api/**")
 *         .filters(f -> f
 *             .stripPrefix(1)
 *             .addRequestHeader("X-Request-Source", "Gateway")
 *             .retry(3)
 *             .circuitBreaker(config -> config.setName("myCircuitBreaker")))
 *         .uri("http://example.org"))
 *     .build();
 * }</pre>
 *
 * @author Ryan Baxter
 * @author Cora Iberkleid
 * @author Spencer Gibb
 */
public class GatewayFilterSpec extends UriSpec {

	/**
	 * 日志记录器，用于记录过滤器配置过程中的警告和信息。
	 */
	private static final Log log = LogFactory.getLog(GatewayFilterSpec.class);

	/**
	 * 构造函数，创建 GatewayFilterSpec 实例。
	 * @param routeBuilder 路由异步构建器，用于构建路由定义
	 * @param builder 路由定位器构建器，用于访问Spring上下文中的Bean
	 */
	public GatewayFilterSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
	}

	/**
	 * 将过滤器应用到路由。
	 *
	 * <p>
	 * 如果过滤器实现了 {@link Ordered} 接口，则直接使用其顺序值； 否则使用默认顺序值 0。
	 * </p>
	 * @param gatewayFilter 要应用的网关过滤器
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec filter(GatewayFilter gatewayFilter) {
		if (gatewayFilter instanceof Ordered) {
			this.routeBuilder.filter(gatewayFilter);
			return this;
		}
		return this.filter(gatewayFilter, 0);
	}

	/**
	 * 将过滤器应用到路由，并指定顺序。
	 *
	 * <p>
	 * 如果过滤器已经实现了 {@link Ordered} 接口，则记录警告并忽略指定的顺序值， 使用过滤器自身的顺序值。
	 * </p>
	 * @param gatewayFilter 要应用的网关过滤器
	 * @param order 过滤器的执行顺序（数值越小，优先级越高）
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec filter(GatewayFilter gatewayFilter, int order) {
		if (gatewayFilter instanceof Ordered) {
			this.routeBuilder.filter(gatewayFilter);
			log.warn("GatewayFilter already implements ordered " + gatewayFilter.getClass()
					+ "ignoring order parameter: " + order);
			return this;
		}
		this.routeBuilder.filter(new OrderedGatewayFilter(gatewayFilter, order));
		return this;
	}

	/**
	 * 将多个过滤器应用到路由。
	 *
	 * <p>
	 * 将过滤器数组转换为有序过滤器列表并应用到路由。
	 * </p>
	 * @param gatewayFilters 要应用的过滤器数组
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec filters(GatewayFilter... gatewayFilters) {
		List<GatewayFilter> filters = transformToOrderedFilters(Stream.of(gatewayFilters));
		this.routeBuilder.filters(filters);
		return this;
	}

	/**
	 * 将过滤器流转换为有序过滤器列表。
	 *
	 * <p>
	 * 对于未实现 {@link Ordered} 接口的过滤器，包装为 {@link OrderedGatewayFilter} 并设置默认顺序值为 0。
	 * </p>
	 * @param stream 过滤器流
	 * @return 有序过滤器列表
	 */
	public List<GatewayFilter> transformToOrderedFilters(Stream<GatewayFilter> stream) {
		return stream.map(filter -> {
			if (filter instanceof Ordered) {
				return filter;
			}
			else {
				return new OrderedGatewayFilter(filter, 0);
			}
		}).collect(Collectors.toList());
	}

	/**
	 * 将过滤器集合应用到路由。
	 * @param gatewayFilters 要应用的过滤器集合
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec filters(Collection<GatewayFilter> gatewayFilters) {
		List<GatewayFilter> filters = transformToOrderedFilters(gatewayFilters.stream());
		this.routeBuilder.filters(filters);
		return this;
	}

	/**
	 * 添加请求头过滤器，在请求被网关路由前添加指定的请求头。
	 *
	 * <p>
	 * 如果请求头已存在，则会添加新的值（形成多值头）。
	 * </p>
	 * @param headerName 请求头名称
	 * @param headerValue 请求头值
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec addRequestHeader(String headerName, String headerValue) {
		return filter(getBean(AddRequestHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * 添加请求参数过滤器，在请求被网关路由前添加指定的查询参数。
	 * @param param 参数名称
	 * @param value 参数值
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec addRequestParameter(String param, String value) {
		return filter(
				getBean(AddRequestParameterGatewayFilterFactory.class).apply(c -> c.setName(param).setValue(value)));
	}

	/**
	 * 添加响应头过滤器，在响应返回给客户端前添加指定的响应头。
	 * @param headerName 响应头名称
	 * @param headerValue 响应头值
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec addResponseHeader(String headerName, String headerValue) {
		return filter(getBean(AddResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * 添加响应头去重过滤器，在响应返回给客户端前移除响应头中的重复值。
	 *
	 * <p>
	 * 支持的去重策略：
	 * </p>
	 * <ul>
	 * <li>RETAIN_FIRST - 保留第一个值</li>
	 * <li>RETAIN_LAST - 保留最后一个值</li>
	 * <li>RETAIN_UNIQUE - 保留所有唯一值</li>
	 * </ul>
	 * @param headerName 要去重的响应头名称（多个名称用空格分隔）
	 * @param strategy 去重策略："RETAIN_FIRST"、"RETAIN_LAST" 或 "RETAIN_UNIQUE"
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec dedupeResponseHeader(String headerName, String strategy) {
		return filter(getBean(DedupeResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setStrategy(Strategy.valueOf(strategy)).setName(headerName)));
	}

	/**
	 * 添加断路器过滤器，实现熔断降级功能。
	 *
	 * <p>
	 * 当后端服务出现故障或响应过慢时，断路器会打开，阻止请求继续发送到故障服务， 而是执行回退逻辑。这可以防止故障扩散，提高系统稳定性。
	 * </p>
	 *
	 * <p>
	 * 注意：需要在类路径上有支持响应式API的断路器实现（如 Resilience4j）。
	 * </p>
	 * @param configConsumer 配置消费者，用于配置断路器参数
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 * @throws NoSuchBeanDefinitionException 如果没有找到断路器实现
	 */
	public GatewayFilterSpec circuitBreaker(Consumer<SpringCloudCircuitBreakerFilterFactory.Config> configConsumer) {
		SpringCloudCircuitBreakerFilterFactory filterFactory;
		try {
			filterFactory = getBean(SpringCloudCircuitBreakerFilterFactory.class);
		}
		catch (NoSuchBeanDefinitionException e) {
			throw new NoSuchBeanDefinitionException(SpringCloudCircuitBreakerFilterFactory.class,
					"There needs to be a circuit breaker implementation on the classpath that supports reactive APIs.");
		}
		return filter(filterFactory.apply(this.routeBuilder.getId(), configConsumer));
	}

	/**
	 * 添加 JSON 到 gRPC 转换过滤器，将 JSON 请求转换为 gRPC 调用。
	 *
	 * <p>
	 * 允许网关接收 JSON 格式的 HTTP 请求，并将其转换为 gRPC 调用发送到后端服务， 实现 RESTful API 与 gRPC 服务的桥接。
	 * </p>
	 * @param protoDescriptor proto 描述符文件的相对路径
	 * @param protoFile proto 定义文件的相对路径
	 * @param service 处理请求的服务的完全限定名
	 * @param method 服务中处理请求的方法名
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec jsonToGRPC(String protoDescriptor, String protoFile, String service, String method) {
		return filter(getBean(JsonToGrpcGatewayFilterFactory.class).apply(c -> c.setMethod(method)
				.setProtoDescriptor(protoDescriptor).setProtoFile(protoFile).setService(service)));
	}

	/**
	 * 添加请求头映射过滤器，将一个请求头的值映射到另一个请求头。
	 *
	 * <p>
	 * 从源请求头提取值，设置到目标请求头。如果源请求头不存在，则不进行映射。
	 * </p>
	 * @param fromHeader 源请求头名称
	 * @param toHeader 目标请求头名称
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec mapRequestHeader(String fromHeader, String toHeader) {
		return filter(getBean(MapRequestHeaderGatewayFilterFactory.class)
				.apply(c -> c.setFromHeader(fromHeader).setToHeader(toHeader)));
	}

	/**
	 * 添加请求体修改过滤器，修改请求体内容。
	 *
	 * <p>
	 * 使用 {@link RewriteFunction} 将请求体从一种类型转换为另一种类型。 常用于请求转换、加密解密等场景。
	 * </p>
	 *
	 * <p>
	 * 注意：修改请求体会增加内存消耗，大文件上传场景需谨慎使用。
	 * </p>
	 * @param inClass 原始请求体类型
	 * @param outClass 转换后的请求体类型
	 * @param rewriteFunction 重写函数，定义如何转换请求体
	 * @param <T> 原始请求体类型
	 * @param <R> 转换后的请求体类型
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public <T, R> GatewayFilterSpec modifyRequestBody(Class<T> inClass, Class<R> outClass,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyRequestBodyGatewayFilterFactory.class)
				.apply(c -> c.setRewriteFunction(inClass, outClass, rewriteFunction)));
	}

	/**
	 * 添加请求体修改过滤器，修改请求体内容并设置新的 Content-Type。
	 * @param inClass 原始请求体类型
	 * @param outClass 转换后的请求体类型
	 * @param newContentType 新的 Content-Type 头值
	 * @param rewriteFunction 重写函数
	 * @param <T> 原始请求体类型
	 * @param <R> 转换后的请求体类型
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public <T, R> GatewayFilterSpec modifyRequestBody(Class<T> inClass, Class<R> outClass, String newContentType,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyRequestBodyGatewayFilterFactory.class)
				.apply(c -> c.setRewriteFunction(inClass, outClass, rewriteFunction).setContentType(newContentType)));
	}

	/**
	 * 添加请求体缓存过滤器，缓存请求体内容。
	 *
	 * <p>
	 * 将请求体缓存到 ServerWebExchange 的属性中，供后续过滤器或路由使用。 这在需要多次读取请求体的场景中很有用。
	 * </p>
	 * @param bodyClass 请求体类型
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec cacheRequestBody(Class<?> bodyClass) {
		return filter(getBean(CacheRequestBodyGatewayFilterFactory.class).apply(c -> c.setBodyClass(bodyClass)));
	}

	/**
	 * 添加请求体修改过滤器，使用自定义配置。
	 *
	 * <p>
	 * 提供更灵活的配置方式，可以设置输入/输出类型、提示信息和重写函数。
	 * </p>
	 *
	 * <p>
	 * 使用示例：
	 * </p>
	 * <pre>{@code
	 * .modifyRequestBody(c -> c
	 *     .setInClass(Some.class)
	 *     .setOutClass(SomeOther.class)
	 *     .setInHints(hintsIn)
	 *     .setOutHints(hintsOut)
	 *     .setRewriteFunction(rewriteFunction))
	 * }</pre>
	 * @param configConsumer 配置消费者，用于配置请求体修改参数
	 * @param <T> 原始请求体类型
	 * @param <R> 转换后的请求体类型
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public <T, R> GatewayFilterSpec modifyRequestBody(
			Consumer<ModifyRequestBodyGatewayFilterFactory.Config> configConsumer) {
		return filter(getBean(ModifyRequestBodyGatewayFilterFactory.class).apply(configConsumer));
	}

	/**
	 * 添加响应体修改过滤器，修改响应体内容。
	 *
	 * <p>
	 * 使用 {@link RewriteFunction} 将响应体从一种类型转换为另一种类型。 常用于响应转换、数据脱敏等场景。
	 * </p>
	 * @param inClass 原始响应体类型
	 * @param outClass 转换后的响应体类型
	 * @param rewriteFunction 重写函数
	 * @param <T> 原始响应体类型
	 * @param <R> 转换后的响应体类型
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public <T, R> GatewayFilterSpec modifyResponseBody(Class<T> inClass, Class<R> outClass,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyResponseBodyGatewayFilterFactory.class)
				.apply(c -> c.setRewriteFunction(inClass, outClass, rewriteFunction)));
	}

	/**
	 * 添加响应体修改过滤器，修改响应体内容并设置新的 Content-Type。
	 * @param inClass 原始响应体类型
	 * @param outClass 转换后的响应体类型
	 * @param newContentType 新的 Content-Type 头值
	 * @param rewriteFunction 重写函数
	 * @param <T> 原始响应体类型
	 * @param <R> 转换后的响应体类型
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public <T, R> GatewayFilterSpec modifyResponseBody(Class<T> inClass, Class<R> outClass, String newContentType,
			RewriteFunction<T, R> rewriteFunction) {
		return filter(getBean(ModifyResponseBodyGatewayFilterFactory.class).apply(
				c -> c.setRewriteFunction(inClass, outClass, rewriteFunction).setNewContentType(newContentType)));
	}

	/**
	 * 添加响应体修改过滤器，使用自定义配置。
	 *
	 * <p>
	 * 提供更灵活的配置方式。
	 * </p>
	 *
	 * <p>
	 * 使用示例：
	 * </p>
	 * <pre>{@code
	 * .modifyResponseBody(c -> c
	 *     .setInClass(Some.class)
	 *     .setOutClass(SomeOther.class)
	 *     .setOutHints(hintsOut)
	 *     .setRewriteFunction(rewriteFunction))
	 * }</pre>
	 * @param configConsumer 配置消费者
	 * @param <T> 原始响应体类型
	 * @param <R> 转换后的响应体类型
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public <T, R> GatewayFilterSpec modifyResponseBody(
			Consumer<ModifyResponseBodyGatewayFilterFactory.Config> configConsumer) {
		return filter(getBean(ModifyResponseBodyGatewayFilterFactory.class).apply(configConsumer));
	}

	/**
	 * 添加路径前缀过滤器，在请求路径前添加指定的前缀。
	 *
	 * <p>
	 * 常用于将外部简洁 URL 映射到内部复杂路径结构的场景。
	 * </p>
	 *
	 * <p>
	 * 示例：prefix="/api"，原始路径"/users" -> 新路径"/api/users"
	 * </p>
	 * @param prefix 要添加的路径前缀
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec prefixPath(String prefix) {
		return filter(getBean(PrefixPathGatewayFilterFactory.class).apply(c -> c.setPrefix(prefix)));
	}

	/**
	 * 添加保留 Host 头过滤器，在出站请求中保留原始的 Host 头。
	 *
	 * <p>
	 * 默认情况下，网关会将 Host 头设置为下游服务的主机名。 使用此过滤器可以保留原始请求的 Host 头信息。
	 * </p>
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec preserveHostHeader() {
		return filter(getBean(PreserveHostHeaderGatewayFilterFactory.class).apply());
	}

	/**
	 * 添加设置 Host 头过滤器，设置出站请求的 Host 头值。
	 * @param hostName 要设置的 Host 头值
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setHostHeader(String hostName) {
		return filter(getBean(SetRequestHostHeaderGatewayFilterFactory.class).apply(c -> c.setHost(hostName)));
	}

	/**
	 * 添加重定向过滤器，返回重定向响应给客户端。
	 * @param status HTTP 状态码，应为 300 系列的重定向状态码
	 * @param url 重定向目标 URL，将设置在 Location 头中
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec redirect(int status, URI url) {
		return redirect(String.valueOf(status), url.toString());
	}

	/**
	 * 添加重定向过滤器，返回重定向响应给客户端。
	 * @param status HTTP 状态码
	 * @param url 重定向目标 URL
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec redirect(int status, String url) {
		return redirect(String.valueOf(status), url);
	}

	/**
	 * 添加重定向过滤器，返回重定向响应给客户端。
	 * @param status HTTP 状态码
	 * @param url 重定向目标 URL
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec redirect(String status, URI url) {
		return redirect(status, url.toString());
	}

	/**
	 * 添加重定向过滤器，返回重定向响应给客户端。
	 * @param status HTTP 状态码
	 * @param url 重定向目标 URL
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec redirect(String status, String url) {
		return filter(getBean(RedirectToGatewayFilterFactory.class).apply(status, url));
	}

	/**
	 * 添加重定向过滤器，返回重定向响应给客户端。
	 * @param status HTTP 状态码
	 * @param url 重定向目标 URL
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec redirect(HttpStatus status, URL url) {
		try {
			return filter(getBean(RedirectToGatewayFilterFactory.class).apply(status, url.toURI()));
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid URL", e);
		}
	}

	/**
	 * 添加移除请求头过滤器，在请求被路由前移除指定的请求头。
	 * @param headerName 要移除的请求头名称
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec removeRequestHeader(String headerName) {
		return filter(getBean(RemoveRequestHeaderGatewayFilterFactory.class).apply(c -> c.setName(headerName)));
	}

	/**
	 * 添加移除请求参数过滤器，在请求被路由前移除指定的查询参数。
	 * @param paramName 要移除的参数名称
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec removeRequestParameter(String paramName) {
		return filter(getBean(RemoveRequestParameterGatewayFilterFactory.class).apply(c -> c.setName(paramName)));
	}

	/**
	 * 添加移除响应头过滤器，在响应返回给客户端前移除指定的响应头。
	 * @param headerName 要移除的响应头名称
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec removeResponseHeader(String headerName) {
		return filter(getBean(RemoveResponseHeaderGatewayFilterFactory.class).apply(c -> c.setName(headerName)));
	}

	/**
	 * 添加请求限流过滤器，为路由设置请求速率限制。
	 *
	 * <p>
	 * 基于令牌桶算法限制请求速率，防止后端服务被过多请求压垮。
	 * </p>
	 * @param configConsumer 配置消费者，用于配置限流参数
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec requestRateLimiter(
			Consumer<RequestRateLimiterGatewayFilterFactory.Config> configConsumer) {
		return filter(
				getBean(RequestRateLimiterGatewayFilterFactory.class).apply(this.routeBuilder.getId(), configConsumer));
	}

	/**
	 * 添加请求限流过滤器，返回限流规范对象进行进一步配置。
	 *
	 * <p>
	 * 此方法提供更灵活的限流配置方式，允许链式配置限流器和参数。
	 * </p>
	 * @return {@link RequestRateLimiterSpec} 限流规范对象
	 */
	public RequestRateLimiterSpec requestRateLimiter() {
		return new RequestRateLimiterSpec(getBean(RequestRateLimiterGatewayFilterFactory.class));
	}

	/**
	 * 添加路径重写过滤器，在请求被路由前重写请求路径。
	 *
	 * <p>
	 * 使用 Java 正则表达式匹配路径，并将匹配部分替换为指定格式。
	 * </p>
	 *
	 * <p>
	 * 示例：regex="/api/(?<segment>.*)", replacement="/$\\{segment}"
	 * </p>
	 * @param regex 匹配路径的正则表达式
	 * @param replacement 替换格式，支持命名捕获组引用
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec rewritePath(String regex, String replacement) {
		return filter(getBean(RewritePathGatewayFilterFactory.class)
				.apply(c -> c.setRegexp(regex).setReplacement(replacement)));
	}

	/**
	 * 添加重试过滤器，重试失败的请求。
	 *
	 * <p>
	 * 默认情况下，5xx 错误和 GET 请求是可重试的。
	 * </p>
	 * @param retries 最大重试次数
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec retry(int retries) {
		return filter(getBean(RetryGatewayFilterFactory.class).apply(this.routeBuilder.getId(),
				retryConfig -> retryConfig.setRetries(retries)));
	}

	/**
	 * 添加重试过滤器，使用自定义配置重试失败的请求。
	 * @param retryConsumer 配置消费者，用于配置重试参数
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec retry(Consumer<RetryGatewayFilterFactory.RetryConfig> retryConsumer) {
		return filter(getBean(RetryGatewayFilterFactory.class).apply(this.routeBuilder.getId(), retryConsumer));
	}

	/**
	 * 添加重试过滤器，使用 Reactor 的 Repeat 和 Retry 对象配置重试策略。
	 * @param repeat {@link Repeat} 重复策略
	 * @param retry {@link Retry} 重试策略
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec retry(Repeat<ServerWebExchange> repeat, Retry<ServerWebExchange> retry) {
		RetryGatewayFilterFactory filterFactory = getBean(RetryGatewayFilterFactory.class);
		return filter(filterFactory.apply(this.routeBuilder.getId(), repeat, retry));
	}

	/**
	 * 添加安全头过滤器，添加安全相关的响应头。
	 *
	 * <p>
	 * 添加一系列安全相关的 HTTP 响应头，包括：
	 * </p>
	 * <ul>
	 * <li>X-XSS-Protection：XSS 防护</li>
	 * <li>X-Frame-Options：点击劫持防护</li>
	 * <li>X-Content-Type-Options：MIME 嗅探防护</li>
	 * <li>Strict-Transport-Security：HSTS</li>
	 * <li>Content-Security-Policy：内容安全策略</li>
	 * </ul>
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec secureHeaders() {
		return filter(getBean(SecureHeadersGatewayFilterFactory.class).apply(config -> {
		}));
	}

	/**
	 * 添加安全头过滤器，使用自定义配置。
	 * @param configConsumer 配置消费者，用于自定义安全头设置
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec secureHeaders(Consumer<SecureHeadersGatewayFilterFactory.Config> configConsumer) {
		return filter(getBean(SecureHeadersGatewayFilterFactory.class).apply(configConsumer));
	}

	/**
	 * 添加设置路径过滤器，在请求被路由前设置请求路径。
	 *
	 * <p>
	 * 完全替换请求的原始路径为指定路径，支持 Spring Framework 的 URI 模板。
	 * </p>
	 * @param template 要设置的路径模板
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setPath(String template) {
		return filter(getBean(SetPathGatewayFilterFactory.class).apply(c -> c.setTemplate(template)));
	}

	/**
	 * 添加设置请求头过滤器，在请求被路由前设置请求头。
	 *
	 * <p>
	 * 设置请求头的值，如果请求头已存在则覆盖原有值。
	 * </p>
	 * @param headerName 请求头名称
	 * @param headerValue 请求头值
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setRequestHeader(String headerName, String headerValue) {
		return filter(getBean(SetRequestHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * 添加设置响应头过滤器，在响应返回给客户端前设置响应头。
	 * @param headerName 响应头名称
	 * @param headerValue 响应头值
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setResponseHeader(String headerName, String headerValue) {
		return filter(getBean(SetResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setName(headerName).setValue(headerValue)));
	}

	/**
	 * 添加响应头重写过滤器，在响应返回给客户端前重写响应头值。
	 * @param headerName 响应头名称
	 * @param regex 匹配值的正则表达式
	 * @param replacement 替换格式
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec rewriteResponseHeader(String headerName, String regex, String replacement) {
		return filter(getBean(RewriteResponseHeaderGatewayFilterFactory.class)
				.apply(c -> c.setReplacement(replacement).setRegexp(regex).setName(headerName)));
	}

	/**
	 * 添加 Location 响应头重写过滤器，重写 Location 响应头的值。
	 *
	 * <p>
	 * 用于移除后端特定的详细信息，在 SSL 终止或反向代理场景中很有用。
	 * </p>
	 * @param stripVersionMode 版本剥离模式：NEVER_STRIP、AS_IN_REQUEST 或 ALWAYS_STRIP
	 * @param locationHeaderName Location 头名称
	 * @param hostValue 主机值
	 * @param protocolsRegex 协议匹配正则表达式
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec rewriteLocationResponseHeader(String stripVersionMode, String locationHeaderName,
			String hostValue, String protocolsRegex) {
		return filter(getBean(RewriteLocationResponseHeaderGatewayFilterFactory.class).apply(
				c -> c.setStripVersion(StripVersion.valueOf(stripVersionMode)).setLocationHeaderName(locationHeaderName)
						.setHostValue(hostValue).setProtocols(protocolsRegex)));
	}

	/**
	 * 添加设置状态码过滤器，在响应返回给客户端前设置 HTTP 状态码。
	 * @param status HTTP 状态码
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setStatus(int status) {
		return setStatus(String.valueOf(status));
	}

	/**
	 * 添加设置状态码过滤器，使用 HttpStatus 枚举设置状态码。
	 * @param status HTTP 状态码枚举
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setStatus(HttpStatus status) {
		return setStatus(status.name());
	}

	/**
	 * 添加设置状态码过滤器，使用状态码名称设置状态码。
	 * @param status HTTP 状态码名称
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setStatus(String status) {
		return filter(getBean(SetStatusGatewayFilterFactory.class).apply(c -> c.setStatus(status)));
	}

	/**
	 * 添加保存会话过滤器，在转发请求前强制保存 WebSession。
	 *
	 * <p>
	 * 在使用 Spring Session 等分布式会话管理时，确保会话状态已被保存。 如果集成 Spring Security 与 Spring
	 * Session，此操作对确保安全详情已转发至关重要。
	 * </p>
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	@SuppressWarnings("unchecked")
	public GatewayFilterSpec saveSession() {
		return filter(getBean(SaveSessionGatewayFilterFactory.class).apply(c -> {
		}));
	}

	/**
	 * 添加剥离前缀过滤器，在请求被路由前移除路径前缀。
	 *
	 * <p>
	 * 常用于 API 网关场景，将外部路径映射到内部路径。
	 * </p>
	 *
	 * <p>
	 * 示例：parts=2，路径"/api/v1/users" -> 新路径"/users"
	 * </p>
	 * @param parts 要移除的路径段数量
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec stripPrefix(int parts) {
		return filter(getBean(StripPrefixGatewayFilterFactory.class).apply(c -> c.setParts(parts)));
	}

	/**
	 * 添加请求头到请求 URI 过滤器，从请求头中提取 URI 进行路由。
	 *
	 * <p>
	 * 使用请求头中指定的 URI 替换原始请求的目标 URI。
	 * </p>
	 * @param headerName 包含 URI 的请求头名称
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec requestHeaderToRequestUri(String headerName) {
		return filter(getBean(RequestHeaderToRequestUriGatewayFilterFactory.class).apply(c -> c.setName(headerName)));
	}

	/**
	 * 添加更改请求 URI 过滤器，使用自定义函数确定路由目标 URI。
	 * @param determineRequestUri 函数，接收 {@link ServerWebExchange} 返回可选的 URI
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec changeRequestUri(Function<ServerWebExchange, Optional<URI>> determineRequestUri) {
		return filter(new AbstractChangeRequestUriGatewayFilterFactory<Object>(Object.class) {
			@Override
			protected Optional<URI> determineRequestUri(ServerWebExchange exchange, Object config) {
				return determineRequestUri.apply(exchange);
			}
		}.apply(c -> {
		}));
	}

	/**
	 * 添加设置请求大小限制过滤器，设置请求的最大允许大小。
	 * @param size 请求的最大大小（字节数）
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setRequestSize(Long size) {
		return setRequestSize(DataSize.ofBytes(size));
	}

	/**
	 * 添加设置请求大小限制过滤器，使用 DataSize 设置请求的最大允许大小。
	 * @param size 请求的最大大小
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setRequestSize(DataSize size) {
		return filter(getBean(RequestSizeGatewayFilterFactory.class).apply(c -> c.setMaxSize(size)));
	}

	/**
	 * 添加设置请求头大小限制过滤器，设置请求头的最大允许大小。
	 * @param size 请求头的最大大小
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec setRequestHeaderSize(DataSize size) {
		return filter(getBean(RequestHeaderSizeGatewayFilterFactory.class).apply(c -> c.setMaxSize(size)));
	}

	/**
	 * 添加令牌中继过滤器，启用 OAuth2 令牌中继功能。
	 *
	 * <p>
	 * 将客户端的 OAuth2 访问令牌传递给下游服务，实现令牌中继。
	 * </p>
	 *
	 * <p>
	 * 注意：需要在类路径上包含 spring-boot-starter-oauth2-client 依赖。
	 * </p>
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 * @throws IllegalStateException 如果没有找到 TokenRelayGatewayFilterFactory bean
	 */
	public GatewayFilterSpec tokenRelay() {
		try {
			return filter(getBean(TokenRelayGatewayFilterFactory.class).apply(o -> {
			}));
		}
		catch (NoSuchBeanDefinitionException e) {
			throw new IllegalStateException("No TokenRelayGatewayFilterFactory bean was found. Did you include the "
					+ "org.springframework.boot:spring-boot-starter-oauth2-client dependency?");
		}
	}

	/**
	 * 添加回退头过滤器，向回退请求添加 Hystrix 执行异常头。
	 *
	 * <p>
	 * 依赖 org.springframework.cloud:spring-cloud-starter-netflix-hystrix 在类路径上。
	 * </p>
	 * @param config {@link FallbackHeadersGatewayFilterFactory.Config} 配置对象
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec fallbackHeaders(FallbackHeadersGatewayFilterFactory.Config config) {
		FallbackHeadersGatewayFilterFactory factory = getFallbackHeadersGatewayFilterFactory();
		return filter(factory.apply(config));
	}

	/**
	 * 添加回退头过滤器，使用配置消费者自定义头名称。
	 *
	 * <p>
	 * 如果未提供头名称参数，则使用默认值。
	 * </p>
	 * @param configConsumer 配置消费者
	 * @return 当前 {@link GatewayFilterSpec} 实例，支持方法链式调用
	 */
	public GatewayFilterSpec fallbackHeaders(Consumer<FallbackHeadersGatewayFilterFactory.Config> configConsumer) {
		FallbackHeadersGatewayFilterFactory factory = getFallbackHeadersGatewayFilterFactory();
		return filter(factory.apply(configConsumer));
	}

	/**
	 * 获取 FallbackHeadersGatewayFilterFactory bean。
	 *
	 * <p>
	 * 如果未找到，抛出包含帮助信息的异常。
	 * </p>
	 * @return FallbackHeadersGatewayFilterFactory 实例
	 * @throws NoSuchBeanDefinitionException 如果 Hystrix 不在类路径上
	 */
	private FallbackHeadersGatewayFilterFactory getFallbackHeadersGatewayFilterFactory() {
		FallbackHeadersGatewayFilterFactory factory;
		try {
			factory = getBean(FallbackHeadersGatewayFilterFactory.class);
		}
		catch (NoSuchBeanDefinitionException e) {
			throw new NoSuchBeanDefinitionException(FallbackHeadersGatewayFilterFactory.class,
					"This is probably because Hystrix is missing from the classpath, which can be resolved by adding dependency on 'org.springframework.cloud:spring-cloud-starter-netflix-hystrix'");
		}
		return factory;
	}

	/**
	 * 请求限流规范类，用于配置限流器和限流参数。
	 *
	 * <p>
	 * 提供流畅的 API 来配置限流器类型、参数，然后返回到 GatewayFilterSpec。
	 * </p>
	 */
	public class RequestRateLimiterSpec {

		/**
		 * 限流过滤器工厂。
		 */
		private final RequestRateLimiterGatewayFilterFactory filter;

		/**
		 * 构造函数。
		 * @param filter 限流过滤器工厂
		 */
		public RequestRateLimiterSpec(RequestRateLimiterGatewayFilterFactory filter) {
			this.filter = filter;
		}

		/**
		 * 配置限流器。
		 * @param rateLimiterType 限流器类型
		 * @param configConsumer 配置消费者
		 * @param <C> 限流器配置类型
		 * @param <R> 限流器类型
		 * @return 当前 RequestRateLimiterSpec 实例
		 */
		public <C, R extends RateLimiter<C>> RequestRateLimiterSpec rateLimiter(Class<R> rateLimiterType,
				Consumer<C> configConsumer) {
			R rateLimiter = getBean(rateLimiterType);
			C config = rateLimiter.newConfig();
			configConsumer.accept(config);
			rateLimiter.getConfig().put(routeBuilder.getId(), config);
			return this;
		}

		/**
		 * 配置限流过滤器参数。
		 * @param configConsumer 配置消费者
		 * @return 父 GatewayFilterSpec 实例
		 */
		public GatewayFilterSpec configure(Consumer<RequestRateLimiterGatewayFilterFactory.Config> configConsumer) {
			filter(this.filter.apply(routeBuilder.getId(), configConsumer));
			return GatewayFilterSpec.this;
		}

		/**
		 * 完成限流配置，使用默认参数。
		 *
		 * <p>
		 * 当不需要额外配置时使用此方法返回父 GatewayFilterSpec。
		 * </p>
		 * @return 父 GatewayFilterSpec 实例
		 */
		public GatewayFilterSpec and() {
			return configure(config -> {
			});
		}

	}

}
