/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.webflux;

import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 代理交换类，用于在 Spring WebFlux 控制器方法中实现请求代理转发功能。
 * <p>
 * 该类可以在 <code>@RequestMapping</code> 方法中作为参数使用，能够将请求转发到后端服务。 Spring
 * 会自动将该类的实例注入到控制器方法中，开发者可以通过调用 {@link #get()}、
 * {@link #post()}、{@link #put()}、{@link #patch()}、{@link #delete()} 等 HTTP 方法 来返回一个
 * <code>ResponseEntity</code>。
 * <p>
 * 使用示例： <pre>
 * &#64;GetMapping("/proxy/{id}")
 * public Mono&lt;ResponseEntity&lt;?&gt;&gt; proxy(@PathVariable Integer id, ProxyExchange&lt;?&gt; proxy)
 * 		throws Exception {
 * 	return proxy.uri("http://localhost:9000/foos/" + id).get();
 * }
 * </pre>
 * <p>
 * 默认情况下，传入的请求体和请求头会原封不动地发送到下游服务（"敏感"请求头除外）。 要操作下游请求，可以使用 {@link ProxyExchange}
 * 中的"构建器"风格方法， 但只有 {@link #uri(String)} 是必需的。可以通过调用 {@link #sensitive(String...)} 方法
 * 来修改敏感请求头（Authorization 和 Cookie 默认就是敏感的）。
 * <p>
 * <code>ProxyExchange&lt;T&gt;</code> 中的类型参数 <code>T</code> 是响应体的类型， 因此它会出现在从
 * <code>@RequestMapping</code> 返回的 <code>ResponseEntity</code> 中。
 * 如果不关心请求和响应体的类型（例如只是简单透传），可以使用通配符或 <code>byte[]</code> （除非提供转换器，否则 <code>Object</code>
 * 可能无法工作）。 如果想要转换或操作响应，或者想要断言它可以转换为声明的类型，请使用具体类型。
 * <p>
 * 要操作响应，可以使用带有 <code>Function</code> 参数的重载 HTTP 方法， 并传入代码来转换响应。例如： <pre>
 * &#64;PostMapping("/proxy")
 * public Mono&lt;ResponseEntity&lt;Foo&gt;&gt; proxy(ProxyExchange&lt;Foo&gt; proxy) throws Exception {
 * 	return proxy.uri("http://localhost:9000/foos/") //
 * 			.post(response -> ResponseEntity.status(response.getStatusCode()) //
 * 					.headers(response.getHeaders()) //
 * 					.header("X-Custom", "MyCustomHeader") //
 * 					.body(response.getBody()) //
 * 			);
 * }
 * </pre>
 * <p>
 * Spring 的 {@link HttpMessageConverter 消息转换器}的完整机制会应用于 传入的请求和响应以及后端请求。如果需要额外的转换器， 需要在 MVC
 * 配置的上游添加，同时也需要添加到用于后端调用的 {@link WebClient} 中 （详见
 * {@link ProxyExchange#ProxyExchange(WebClient, ServerWebExchange, BindingContext, Type)
 * 构造函数}）。
 *
 * @author Dave Syer
 * @author Spencer Gibb
 */
public class ProxyExchange<T> {

	/**
	 * 默认敏感请求头集合。
	 * <p>
	 * 包含 "cookie" 和 "authorization" 请求头，这些请求头出于安全考虑 默认不会被转发到下游服务。
	 */
	public static Set<String> DEFAULT_SENSITIVE = Collections
			.unmodifiableSet(new HashSet<>(Arrays.asList("cookie", "authorization")));

	/**
	 * HTTP 请求方法，用于确定使用哪种 HTTP 操作。
	 */
	private HttpMethod httpMethod;

	/**
	 * 后端服务的 URI 地址。
	 */
	private URI uri;

	/**
	 * WebClient 实例，用于执行 HTTP 请求。
	 */
	private WebClient rest;

	/**
	 * 请求体发布者，用于向下游服务发送请求数据。
	 */
	private Publisher<Object> body;

	/**
	 * 标记请求是否包含请求体。
	 */
	private boolean hasBody = false;

	/**
	 * 服务器 Web 交换对象，包含当前请求和响应信息。
	 */
	private ServerWebExchange exchange;

	/**
	 * 数据绑定上下文，用于处理请求体绑定和验证。
	 */
	private BindingContext bindingContext;

	/**
	 * 敏感请求头名称集合，这些请求头不会被转发到下游。
	 */
	private Set<String> sensitive;

	/**
	 * 要发送到下游服务的 HTTP 请求头。
	 */
	private HttpHeaders headers = new HttpHeaders();

	/**
	 * 响应体的类型信息。
	 */
	private Type responseType;

	/**
	 * 构造函数，初始化代理交换器。
	 * @param rest WebClient 实例，用于执行 HTTP 请求
	 * @param exchange 服务器 Web 交换对象，包含当前请求信息
	 * @param bindingContext 数据绑定上下文，用于处理请求体绑定
	 * @param type 响应体的类型信息
	 */
	public ProxyExchange(WebClient rest, ServerWebExchange exchange, BindingContext bindingContext, Type type) {
		this.exchange = exchange;
		this.bindingContext = bindingContext;
		this.responseType = type;
		this.rest = rest;
		this.httpMethod = exchange.getRequest().getMethod();
	}

	/**
	 * 设置下游请求的请求体。
	 * <p>
	 * 该方法用于 POST、PUT 或 PATCH 请求。如果只是想将传入请求原封不动地传递下去， 可以省略此方法。如果需要转换传入请求，可以在
	 * <code>@RequestMapping</code> 中 使用 <code>@RequestBody</code> 声明请求体，以常规的 Spring MVC
	 * 方式处理。
	 * @param body 要发送到下游的请求体
	 * @return 当前 ProxyExchange 实例，便于链式调用
	 */
	public ProxyExchange<T> body(Object body) {
		this.body = Mono.just(body);
		return this;
	}

	/**
	 * 设置下游请求的请求体（Publisher 版本）。
	 * <p>
	 * 该方法用于 POST、PUT 或 PATCH 请求，支持响应式流。
	 * @param body 要发送到下游的请求体发布者
	 * @return 当前 ProxyExchange 实例，便于链式调用
	 */
	@SuppressWarnings("unchecked")
	public ProxyExchange<T> body(Publisher<?> body) {
		this.body = (Publisher<Object>) body;
		return this;
	}

	/**
	 * 为下游调用设置请求头。
	 * <p>
	 * 该方法允许自定义要转发到后端服务的 HTTP 请求头信息。
	 * @param name 请求头名称
	 * @param value 请求头值，支持多个值
	 * @return 当前 ProxyExchange 实例，便于链式调用
	 */
	public ProxyExchange<T> header(String name, String... value) {
		this.headers.put(name, Arrays.asList(value));
		return this;
	}

	/**
	 * 设置额外的请求头或覆盖传入的请求头。
	 * <p>
	 * 这些请求头将用于下游调用。
	 * @param headers 要用于下游调用的 HTTP 请求头
	 * @return 当前 ProxyExchange 实例，便于链式调用
	 */
	public ProxyExchange<T> headers(HttpHeaders headers) {
		this.headers.putAll(headers);
		return this;
	}

	/**
	 * 设置敏感请求头名称。
	 * <p>
	 * 被标记为敏感的请求头不会被转发到下游服务。 默认敏感请求头包括 "cookie" 和 "authorization"。
	 * @param names 敏感请求头名称
	 * @return 当前 ProxyExchange 实例，便于链式调用
	 */
	public ProxyExchange<T> sensitive(String... names) {
		if (this.sensitive == null) {
			this.sensitive = new HashSet<>();
		}

		this.sensitive.clear();
		for (String name : names) {
			this.sensitive.add(name.toLowerCase());
		}
		return this;
	}

	/**
	 * 设置后端调用的 URI。
	 * <p>
	 * 这是唯一必需的配置项，用于指定请求要转发到的后端服务地址。
	 * @param uri 后端服务的 URI 地址
	 * @return 当前 ProxyExchange 实例，便于链式调用
	 * @throws IllegalStateException 如果 URI 语法无效
	 */
	public ProxyExchange<T> uri(String uri) {
		try {
			this.uri = new URI(uri);
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Cannot create URI", e);
		}
		return this;
	}

	/**
	 * 获取当前请求的路径。
	 * @return 请求路径值
	 */
	public String path() {
		return exchange.getRequest().getPath().pathWithinApplication().value();
	}

	/**
	 * 获取当前请求的路径，并去除指定的前缀。
	 * @param prefix 要去除的路径前缀
	 * @return 去除前缀后的路径
	 * @throws IllegalArgumentException 如果路径不以指定前缀开头
	 */
	public String path(String prefix) {
		String path = path();
		if (!path.startsWith(prefix)) {
			throw new IllegalArgumentException("Path does not start with prefix (" + prefix + "): " + path);
		}
		return path.substring(prefix.length());
	}

	/**
	 * 向下游服务发送 GET 请求。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> get() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.get(uri)).build();
		return exchange(requestEntity);
	}

	/**
	 * 向下游服务发送 GET 请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> get(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return get().map(converter::apply);
	}

	/**
	 * 向下游服务发送 HEAD 请求。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> head() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.head(uri)).build();
		return exchange(requestEntity);
	}

	/**
	 * 向下游服务发送 HEAD 请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> head(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return head().map(converter::apply);
	}

	/**
	 * 向下游服务发送 OPTIONS 请求。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> options() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.options(uri)).build();
		return exchange(requestEntity);
	}

	/**
	 * 向下游服务发送 OPTIONS 请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> options(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return options().map(converter::apply);
	}

	/**
	 * 向下游服务发送 POST 请求。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> post() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.post(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 向下游服务发送 POST 请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> post(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return post().map(converter::apply);
	}

	/**
	 * 向下游服务发送 DELETE 请求。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> delete() {
		RequestEntity<Object> requestEntity = headers((BodyBuilder) RequestEntity.delete(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 向下游服务发送 DELETE 请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> delete(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return delete().map(converter::apply);
	}

	/**
	 * 向下游服务发送 PUT 请求。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> put() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.put(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 向下游服务发送 PUT 请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> put(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return put().map(converter::apply);
	}

	/**
	 * 向下游服务发送 PATCH 请求。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> patch() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.patch(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 向下游服务发送 PATCH 请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> patch(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return patch().map(converter::apply);
	}

	/**
	 * 根据当前请求的 HTTP 方法自动转发请求。
	 * <p>
	 * 该方法会根据原始的 HTTP 方法（GET、POST、PUT 等）自动选择对应的代理方法。
	 * @return 包含响应数据的 Mono 对象
	 */
	public Mono<ResponseEntity<T>> forward() {
		switch (httpMethod) {
		case GET:
			return get();
		case HEAD:
			return head();
		case OPTIONS:
			return options();
		case POST:
			return post();
		case DELETE:
			return delete();
		case PUT:
			return put();
		case PATCH:
			return patch();
		default:
			return Mono.empty();
		}
	}

	/**
	 * 根据当前请求的 HTTP 方法自动转发请求，并使用转换器处理响应。
	 * @param converter 响应转换函数
	 * @param <S> 转换后的响应类型
	 * @return 包含转换后响应数据的 Mono 对象
	 */
	public <S> Mono<ResponseEntity<S>> forward(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		switch (httpMethod) {
		case GET:
			return get(converter);
		case HEAD:
			return head(converter);
		case OPTIONS:
			return options(converter);
		case POST:
			return post(converter);
		case DELETE:
			return delete(converter);
		case PUT:
			return put(converter);
		case PATCH:
			return patch(converter);
		default:
			return Mono.empty();
		}
	}

	/**
	 * 执行实际的 HTTP 交换请求。
	 * <p>
	 * 使用 WebClient 发送请求到下游服务，并将响应转换为指定类型。
	 * @param requestEntity 要发送的请求实体
	 * @return 包含响应数据的 Mono 对象
	 */
	private Mono<ResponseEntity<T>> exchange(RequestEntity<?> requestEntity) {
		Type type = this.responseType;
		RequestBodySpec builder = rest.method(requestEntity.getMethod()).uri(requestEntity.getUrl())
				.headers(headers -> addHeaders(headers, requestEntity.getHeaders()));
		Mono<ClientResponse> result;
		if (requestEntity.getBody() instanceof Publisher) {
			@SuppressWarnings("unchecked")
			Publisher<Object> publisher = (Publisher<Object>) requestEntity.getBody();
			result = builder.body(publisher, Object.class).exchange();
		}
		else if (requestEntity.getBody() != null) {
			result = builder.body(BodyInserters.fromValue(requestEntity.getBody())).exchange();
		}
		else {
			if (hasBody) {
				result = builder.headers(headers -> addHeaders(headers, exchange.getRequest().getHeaders()))
						.body(exchange.getRequest().getBody(), DataBuffer.class).exchange();
			}
			else {
				result = builder.headers(headers -> addHeaders(headers, exchange.getRequest().getHeaders())).exchange();
			}
		}
		return result.flatMap(response -> response.toEntity(ParameterizedTypeReference.forType(type)));
	}

	/**
	 * 将请求头添加到给定的 HttpHeaders 对象中，排除已存在的请求头。
	 * @param headers 要添加到的目标请求头对象
	 * @param toAdd 要添加的请求头
	 */
	private void addHeaders(HttpHeaders headers, HttpHeaders toAdd) {
		Set<String> filteredKeys = filterHeaderKeys(toAdd);
		filteredKeys.stream().filter(key -> !headers.containsKey(key))
				.forEach(header -> headers.addAll(header, toAdd.get(header)));
	}

	/**
	 * 过滤请求头，移除敏感请求头。
	 * @param headers 要过滤的请求头
	 * @return 过滤后的请求头名称集合
	 */
	private Set<String> filterHeaderKeys(HttpHeaders headers) {
		final Set<String> sensitiveHeaders = this.sensitive != null ? this.sensitive : DEFAULT_SENSITIVE;
		return headers.keySet().stream().filter(header -> !sensitiveHeaders.contains(header.toLowerCase()))
				.collect(Collectors.toSet());
	}

	/**
	 * 将代理请求头添加到构建器中。
	 * @param builder 请求体构建器
	 * @return 添加了请求头的构建器
	 */
	private BodyBuilder headers(BodyBuilder builder) {
		proxy();
		for (String name : filterHeaderKeys(headers)) {
			builder.header(name, headers.get(name).toArray(new String[0]));
		}
		return builder;
	}

	/**
	 * 添加代理相关的请求头信息，包括 Forwarded 和 X-Forwarded 头。
	 * <p>
	 * 这些请求头用于告知后端服务原始请求的信息，支持代理链追踪。
	 */
	private void proxy() {
		URI uri = exchange.getRequest().getURI();
		appendForwarded(uri);
		appendXForwarded(uri);
	}

	/**
	 * 追加 X-Forwarded 请求头（遗留格式）。
	 * <p>
	 * 如果上游已经添加了这些头，则追加而不是覆盖。
	 * @param uri 请求 URI
	 */
	private void appendXForwarded(URI uri) {
		// 如果上游已经添加了这些头，则追加而不是覆盖
		String host = headers.getFirst("x-forwarded-host");
		if (host == null) {
			return;
		}
		host = host + "," + uri.getHost();
		headers.set("x-forwarded-host", host);
		String proto = headers.getFirst("x-forwarded-proto");
		if (proto == null) {
			return;
		}
		proto = proto + "," + uri.getScheme();
		headers.set("x-forwarded-proto", proto);
	}

	/**
	 * 追加 Forwarded 请求头（标准格式，RFC 7239）。
	 * <p>
	 * Forwarded 头是 X-Forwarded-* 系列头的标准化替代方案。
	 * @param uri 请求 URI
	 */
	private void appendForwarded(URI uri) {
		String forwarded = headers.getFirst("forwarded");
		if (forwarded != null) {
			forwarded = forwarded + ",";
		}
		else {
			forwarded = "";
		}
		forwarded = forwarded + forwarded(uri, exchange.getRequest().getHeaders().getFirst("host"));
		headers.set("forwarded", forwarded);
	}

	/**
	 * 构建 Forwarded 请求头的值。
	 * @param uri 请求 URI
	 * @param hostHeader 主机请求头的值
	 * @return Forwarded 请求头的字符串
	 */
	private String forwarded(URI uri, String hostHeader) {
		if (!StringUtils.isEmpty(hostHeader)) {
			return "host=" + hostHeader;
		}
		if ("http".equals(uri.getScheme())) {
			return "host=" + uri.getHost();
		}
		return String.format("host=%s;proto=%s", uri.getHost(), uri.getScheme());
	}

	/**
	 * 获取请求体。如果已经设置了请求体则直接返回， 否则尝试从请求体绑定上下文中获取。
	 * @return 请求体发布者
	 */
	private Publisher<?> body() {
		Publisher<?> body = this.body;
		if (body != null) {
			return body;
		}
		body = getRequestBody();
		hasBody = true; // 即使为 null 也标记为有请求体
		return body;
	}

	/**
	 * 搜索是否已经使用 <code>@RequestBody</code> 反序列化的请求体。 如果未找到，则以与 <code>@RequestBody</code>
	 * 相同的方式进行反序列化。
	 * @return 请求体对象
	 */
	private Mono<Object> getRequestBody() {
		for (String key : bindingContext.getModel().asMap().keySet()) {
			if (key.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
				BindingResult result = (BindingResult) bindingContext.getModel().asMap().get(key);
				return Mono.just(result.getTarget());
			}
		}
		return null;
	}

	/**
	 * 内部类，用于获取请求体。
	 * <p>
	 * 该类通过 <code>@RequestBody</code> 注解标记方法参数， 以便 Spring 自动绑定请求体。
	 */
	protected static class BodyGrabber {

		/**
		 * 获取请求体发布者。
		 * @param body 请求体发布者
		 * @return 请求体发布者
		 */
		public Publisher<Object> body(@RequestBody Publisher<Object> body) {
			return body;
		}

	}

	/**
	 * 内部类，用于发送请求体。
	 * <p>
	 * 该类通过 <code>@ResponseBody</code> 注解标记方法， 用于处理响应体的返回。
	 */
	protected static class BodySender {

		/**
		 * 发送请求体（占位方法）。
		 * @return null 占位符
		 */
		@ResponseBody
		public Publisher<Object> body() {
			return null;
		}

	}

}
