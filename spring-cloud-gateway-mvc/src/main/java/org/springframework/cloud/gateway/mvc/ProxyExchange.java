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

package org.springframework.cloud.gateway.mvc;

import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.RequestEntity.BodyBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 一个可以代理请求到后端的@RequestMapping参数类型。
 * Spring会将此对象注入到MVC处理方法中，您可以从HTTP方法{@link #get()}、
 * {@link #post()}、{@link #put()}、{@link #patch()}、{@link #delete()}等返回一个
 * <code>ResponseEntity</code>。示例：
 *
 * <pre>
 * &#64;GetMapping("/proxy/{id}")
 * public ResponseEntity&lt;?&gt; proxy(@PathVariable Integer id, ProxyExchange&lt;?&gt; proxy)
 * 		throws Exception {
 * 	return proxy.uri("http://localhost:9000/foos/" + id).get();
 * }
 * </pre>
 *
 * <p>
 * 默认情况下，传入的请求体和头部会完整地发送到下游服务（除了"敏感"头部）。
 * 要操作下游请求，{@link ProxyExchange}中提供了"构建器"风格的方法，
 * 但只有{@link #uri(String)}是必需的。您可以通过调用{@link #sensitive(String...)}方法
 * 更改敏感头部（Authorization和Cookie默认为敏感头部）。
 * </p>
 * <p>
 * <code>ProxyExchange&lt;T&gt;</code>中的类型参数<code>T</code>是响应体的类型，
 * 因此它会出现在您从@RequestMapping返回的{@link ResponseEntity}中。
 * 如果您不关心请求和响应体的类型（例如只是透传），则可以使用通配符、
 * <code>byte[]</code>或<code>Object</code>。
 * 如果要转换或操作响应，或者要断言它可以转换为您声明的类型，请使用具体类型。
 * </p>
 * <p>
 * 要操作响应，请使用带有<code>Function</code>参数的重载HTTP方法并传入代码来转换响应。例如：
 *
 * <pre>
 * &#64;PostMapping("/proxy")
 * public ResponseEntity&lt;Foo&gt; proxy(ProxyExchange&lt;Foo&gt; proxy) throws Exception {
 * 	return proxy.uri("http://localhost:9000/foos/") //
 * 			.post(response -> ResponseEntity.status(response.getStatusCode()) //
 * 					.headers(response.getHeaders()) //
 * 					.header("X-Custom", "MyCustomHeader") //
 * 					.body(response.getBody()) //
 * 			);
 * }
 *
 * </pre>
 *
 * </p>
 * <p>
 * Spring {@link HttpMessageConverter 消息转换器}的完整机制应用于传入的请求和响应以及后端请求。
 * 如果需要额外的转换器，则需要在MVC配置的上游添加它们，
 * 并在用于后端调用的{@link RestTemplate}中添加（详见
 * {@link ProxyExchange#ProxyExchange(RestTemplate, NativeWebRequest, ModelAndViewContainer, WebDataBinderFactory, Type)
 * 构造函数}）。
 * </p>
 * <p>
 * 除了用于后端调用的HTTP方法外，您还可以使用{@link #forward(String)}进行本地容器内分发。
 * <p>
 * </p>
 *
 * @author Dave Syer
 */
public class ProxyExchange<T> {

	/**
	 * 包含默认视为敏感的头部。
	 */
	public static Set<String> DEFAULT_SENSITIVE = Collections
			.unmodifiableSet(new HashSet<>(Arrays.asList("cookie", "authorization")));

	/**
	 * 后端URI。
	 */
	private URI uri;

	/**
	 * RestTemplate实例。
	 */
	private RestTemplate rest;

	/**
	 * 请求体。
	 */
	private Object body;

	/**
	 * 请求体响应方法处理器代理。
	 */
	private RequestResponseBodyMethodProcessor delegate;

	/**
	 * Web请求。
	 */
	private NativeWebRequest webRequest;

	/**
	 * ModelAndView容器。
	 */
	private ModelAndViewContainer mavContainer;

	/**
	 * 数据绑定工厂。
	 */
	private WebDataBinderFactory binderFactory;

	/**
	 * 敏感头部名称集合。
	 */
	private Set<String> sensitive;

	/**
	 * HTTP头部信息。
	 */
	private HttpHeaders headers = new HttpHeaders();

	/**
	 * 响应类型。
	 */
	private Type responseType;

	/**
	 * 构造ProxyExchange实例。
	 * @param rest RestTemplate实例
	 * @param webRequest Web请求
	 * @param mavContainer ModelAndView容器
	 * @param binderFactory 数据绑定工厂
	 * @param type 响应类型
	 */
	public ProxyExchange(RestTemplate rest, NativeWebRequest webRequest, ModelAndViewContainer mavContainer,
			WebDataBinderFactory binderFactory, Type type) {
		this.responseType = type;
		this.rest = rest;
		this.webRequest = webRequest;
		this.mavContainer = mavContainer;
		this.binderFactory = binderFactory;
		this.delegate = new RequestResponseBodyMethodProcessor(rest.getMessageConverters());
	}

	/**
	 * Sets the body for the downstream request (if using {@link #post()}, {@link #put()}
	 * or {@link #patch()}). The body can be omitted if you just want to pass the incoming
	 * request downstream without changing it. If you want to transform the incoming
	 * request you can declare it as a <code>@RequestBody</code> in your
	 * <code>@RequestMapping</code> in the usual Spring MVC way.
	 * @param body the request body to send downstream
	 * @return this for convenience
	 */
	public ProxyExchange<T> body(Object body) {
		this.body = body;
		return this;
	}

	/**
	 * Sets a header for the downstream call.
	 * @param name Header name
	 * @param value Header values
	 * @return this for convenience
	 */
	public ProxyExchange<T> header(String name, String... value) {
		this.headers.put(name, Arrays.asList(value));
		return this;
	}

	/**
	 * Additional headers, or overrides of the incoming ones, to be used in the downstream
	 * call.
	 * @param headers the http headers to use in the downstream call
	 * @return this for convenience
	 */
	public ProxyExchange<T> headers(HttpHeaders headers) {
		this.headers.putAll(headers);
		return this;
	}

	/**
	 * Sets the names of sensitive headers that are not passed downstream to the backend
	 * service.
	 * @param names the names of sensitive headers
	 * @return this for convenience
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
	 * Sets the uri for the backend call when triggered by the HTTP methods.
	 * @param uri the backend uri to send the request to
	 * @return this for convenience
	 */
	public ProxyExchange<T> uri(URI uri) {
		this.uri = uri;
		return this;
	}

	/**
	 * Sets the uri for the backend call when triggered by the HTTP methods.
	 * @param uri the backend uri to send the request to
	 * @return this for convenience
	 */
	public ProxyExchange<T> uri(String uri) {
		try {
			return this.uri(new URI(uri));
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Cannot create URI", e);
		}
	}

	/**
	 * 获取请求路径。
	 * @return 请求路径
	 */
	public String path() {
		return (String) this.webRequest.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
				WebRequest.SCOPE_REQUEST);
	}

	/**
	 * 获取去除前缀后的请求路径。
	 * @param prefix 前缀
	 * @return 去除前缀后的路径
	 */
	public String path(String prefix) {
		String path = path();
		if (!path.startsWith(prefix)) {
			throw new IllegalArgumentException("Path does not start with prefix (" + prefix + "): " + path);
		}
		return path.substring(prefix.length());
	}

	/**
	 * 转发请求到本地路径。
	 * @param path 本地路径
	 */
	public void forward(String path) {
		HttpServletRequest request = this.webRequest.getNativeRequest(HttpServletRequest.class);
		HttpServletResponse response = this.webRequest.getNativeResponse(HttpServletResponse.class);
		try {
			request.getRequestDispatcher(path).forward(new BodyForwardingHttpServletRequest(request, response),
					response);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot forward request", e);
		}
	}

	/**
	 * 执行GET请求。
	 * @return 响应实体
	 */
	public ResponseEntity<T> get() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.get(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 执行GET请求并转换响应。
	 * @param converter 响应转换器
	 * @param <S> 转换后的响应类型
	 * @return 转换后的响应实体
	 */
	public <S> ResponseEntity<S> get(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(get());
	}

	/**
	 * 执行HEAD请求。
	 * @return 响应实体
	 */
	public ResponseEntity<T> head() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.head(uri)).build();
		return exchange(requestEntity);
	}

	/**
	 * 执行HEAD请求并转换响应。
	 * @param converter 响应转换器
	 * @param <S> 转换后的响应类型
	 * @return 转换后的响应实体
	 */
	public <S> ResponseEntity<S> head(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(head());
	}

	/**
	 * 执行OPTIONS请求。
	 * @return 响应实体
	 */
	public ResponseEntity<T> options() {
		RequestEntity<?> requestEntity = headers((BodyBuilder) RequestEntity.options(uri)).build();
		return exchange(requestEntity);
	}

	/**
	 * 执行OPTIONS请求并转换响应。
	 * @param converter 响应转换器
	 * @param <S> 转换后的响应类型
	 * @return 转换后的响应实体
	 */
	public <S> ResponseEntity<S> options(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(options());
	}

	/**
	 * 执行POST请求。
	 * @return 响应实体
	 */
	public ResponseEntity<T> post() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.post(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 执行POST请求并转换响应。
	 * @param converter 响应转换器
	 * @param <S> 转换后的响应类型
	 * @return 转换后的响应实体
	 */
	public <S> ResponseEntity<S> post(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(post());
	}

	/**
	 * 执行DELETE请求。
	 * @return 响应实体
	 */
	public ResponseEntity<T> delete() {
		RequestEntity<Object> requestEntity = headers((BodyBuilder) RequestEntity.delete(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 执行DELETE请求并转换响应。
	 * @param converter 响应转换器
	 * @param <S> 转换后的响应类型
	 * @return 转换后的响应实体
	 */
	public <S> ResponseEntity<S> delete(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(delete());
	}

	/**
	 * 执行PUT请求。
	 * @return 响应实体
	 */
	public ResponseEntity<T> put() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.put(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 执行PUT请求并转换响应。
	 * @param converter 响应转换器
	 * @param <S> 转换后的响应类型
	 * @return 转换后的响应实体
	 */
	public <S> ResponseEntity<S> put(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(put());
	}

	/**
	 * 执行PATCH请求。
	 * @return 响应实体
	 */
	public ResponseEntity<T> patch() {
		RequestEntity<Object> requestEntity = headers(RequestEntity.patch(uri)).body(body());
		return exchange(requestEntity);
	}

	/**
	 * 执行PATCH请求并转换响应。
	 * @param converter 响应转换器
	 * @param <S> 转换后的响应类型
	 * @return 转换后的响应实体
	 */
	public <S> ResponseEntity<S> patch(Function<ResponseEntity<T>, ResponseEntity<S>> converter) {
		return converter.apply(patch());
	}

	/**
	 * 执行请求交换。
	 * @param requestEntity 请求实体
	 * @return 响应实体
	 */
	private ResponseEntity<T> exchange(RequestEntity<?> requestEntity) {
		Type type = this.responseType;
		if (type instanceof TypeVariable || type instanceof WildcardType) {
			type = Object.class;
		}
		return rest.exchange(requestEntity, ParameterizedTypeReference.forType(type));
	}

	/**
	 * 添加头部信息。
	 * @param headers HTTP头部
	 */
	private void addHeaders(HttpHeaders headers) {
		ArrayList<String> headerNames = new ArrayList<>();
		webRequest.getHeaderNames().forEachRemaining(headerNames::add);
		Set<String> filteredKeys = filterHeaderKeys(headerNames);
		filteredKeys.stream().filter(key -> !headers.containsKey(key))
				.forEach(header -> headers.addAll(header, Arrays.asList(webRequest.getHeaderValues(header))));
	}

	/**
	 * 设置头部信息到请求构建器。
	 * @param builder 请求体构建器
	 * @return 请求体构建器
	 */
	private BodyBuilder headers(BodyBuilder builder) {
		proxy();
		for (String name : filterHeaderKeys(headers)) {
			builder.header(name, headers.get(name).toArray(new String[0]));
		}
		builder.headers(this::addHeaders);
		return builder;
	}

	/**
	 * 过滤HTTP头部键。
	 * @param headers HTTP头部
	 * @return 过滤后的头部键集合
	 */
	private Set<String> filterHeaderKeys(HttpHeaders headers) {
		return filterHeaderKeys(headers.keySet());
	}

	/**
	 * 过滤头部名称集合，移除敏感头部。
	 * @param headerNames 头部名称集合
	 * @return 过滤后的头部名称集合
	 */
	private Set<String> filterHeaderKeys(Collection<String> headerNames) {
		final Set<String> sensitiveHeaders = this.sensitive != null ? this.sensitive : DEFAULT_SENSITIVE;
		return headerNames.stream().filter(header -> !sensitiveHeaders.contains(header.toLowerCase()))
				.collect(Collectors.toSet());
	}

	/**
	 * 设置代理相关头部信息。
	 */
	private void proxy() {
		try {
			URI uri = new URI(webRequest.getNativeRequest(HttpServletRequest.class).getRequestURL().toString());
			appendForwarded(uri);
			appendXForwarded(uri);
		}
		catch (URISyntaxException e) {
			throw new IllegalStateException("Cannot create URI for request: "
					+ webRequest.getNativeRequest(HttpServletRequest.class).getRequestURL());
		}
	}

	/**
	 * 追加X-Forwarded头部信息。
	 * @param uri URI
	 */
	private void appendXForwarded(URI uri) {
		// Append the legacy headers if they were already added upstream
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
	 * 追加Forwarded头部信息。
	 * @param uri URI
	 */
	private void appendForwarded(URI uri) {
		String forwarded = headers.getFirst("forwarded");
		if (forwarded != null) {
			forwarded = forwarded + ",";
		}
		else {
			forwarded = "";
		}
		forwarded = forwarded + forwarded(uri, webRequest.getHeader("host"));
		headers.set("forwarded", forwarded);
	}

	/**
	 * 构建Forwarded头部值。
	 * @param uri URI
	 * @param hostHeader 主机头部
	 * @return Forwarded头部值
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
	 * 获取请求体。
	 * @return 请求体对象
	 */
	private Object body() {
		if (body != null) {
			return body;
		}
		body = getRequestBody();
		return body;
	}

	/**
	 * 获取请求体。
	 * 如果已经使用@RequestBody反序列化，则搜索请求体；
	 * 如果未找到，则以与@RequestBody相同的方式反序列化它。
	 * @return 请求体
	 */
	private Object getRequestBody() {
		for (String key : mavContainer.getModel().keySet()) {
			if (key.startsWith(BindingResult.MODEL_KEY_PREFIX)) {
				BindingResult result = (BindingResult) mavContainer.getModel().get(key);
				return result.getTarget();
			}
		}
		MethodParameter input = new MethodParameter(ClassUtils.getMethod(BodyGrabber.class, "body", Object.class), 0);
		try {
			delegate.resolveArgument(input, mavContainer, webRequest, binderFactory);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot resolve body", e);
		}
		String name = Conventions.getVariableNameForParameter(input);
		BindingResult result = (BindingResult) mavContainer.getModel().get(BindingResult.MODEL_KEY_PREFIX + name);
		return result.getTarget();
	}

	/**
	 * 用于获取请求体的辅助类。
	 */
	protected static class BodyGrabber {

		/**
		 * 获取请求体。
		 * @param body 请求体对象
		 * @return 请求体对象
		 */
		public Object body(@RequestBody(required = false) Object body) {
			return body;
		}

	}

	/**
	 * 用于发送请求体的辅助类。
	 */
	protected static class BodySender {

		/**
		 * 获取响应体。
		 * @return 响应体对象
		 */
		@ResponseBody
		public Object body() {
			return null;
		}

	}

	/**
	 * 一个servlet请求包装器，可以安全地传递到内部转发分发，
	 * 缓存其主体，并使用Spring消息转换器以转换形式提供。
	 */
	class BodyForwardingHttpServletRequest extends HttpServletRequestWrapper {

		/**
		 * HTTP请求。
		 */
		private HttpServletRequest request;

		/**
		 * HTTP响应。
		 */
		private HttpServletResponse response;

		/**
		 * 构造BodyForwardingHttpServletRequest实例。
		 * @param request HTTP请求
		 * @param response HTTP响应
		 */
		BodyForwardingHttpServletRequest(HttpServletRequest request, HttpServletResponse response) {
			super(request);
			this.request = request;
			this.response = response;
		}

		/**
		 * 获取指定名称的头部值列表。
		 * @param name 头部名称
		 * @return 头部值列表
		 */
		private List<String> header(String name) {
			List<String> list = headers.get(name);
			return list;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			Object body = body();
			MethodParameter output = new MethodParameter(ClassUtils.getMethod(BodySender.class, "body"), -1);
			ServletOutputToInputConverter response = new ServletOutputToInputConverter(this.response);
			ServletWebRequest webRequest = new ServletWebRequest(this.request, response);
			try {
				delegate.handleReturnValue(body, output, mavContainer, webRequest);
			}
			catch (HttpMessageNotWritableException | HttpMediaTypeNotAcceptableException e) {
				throw new IllegalStateException("Cannot convert body", e);
			}
			return response.getInputStream();
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			Set<String> names = headers.keySet();
			if (names.isEmpty()) {
				return super.getHeaderNames();
			}
			Set<String> result = new LinkedHashSet<>(names);
			result.addAll(Collections.list(super.getHeaderNames()));
			return new Vector<String>(result).elements();
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			List<String> list = header(name);
			if (list != null) {
				return new Vector<String>(list).elements();
			}
			return super.getHeaders(name);
		}

		@Override
		public String getHeader(String name) {
			List<String> list = header(name);
			if (list != null && !list.isEmpty()) {
				return list.iterator().next();
			}
			return super.getHeader(name);
		}

	}

}

/**
 * 便捷类，将传入的请求输入流转换为可以使用Spring消息转换器轻松反序列化为Java对象的形式。
 * 它仅在本地转发分发中使用，在这种情况下，请求体可能需要多次读取和分析。
 * 除了使用消息转换器外，此类的主要功能是缓存请求体，并可以根据需要重复读取。
 *
 * @author Dave Syer
 */
class ServletOutputToInputConverter extends HttpServletResponseWrapper {

	/**
	 * 字符串构建器，用于缓存请求体。
	 */
	private StringBuilder builder = new StringBuilder();

	/**
	 * 构造ServletOutputToInputConverter实例。
	 * @param response HTTP响应
	 */
	ServletOutputToInputConverter(HttpServletResponse response) {
		super(response);
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return new ServletOutputStream() {

			@Override
			public void write(int b) throws IOException {
				builder.append(new Character((char) b));
			}

			@Override
			public void setWriteListener(WriteListener listener) {
			}

			@Override
			public boolean isReady() {
				return true;
			}
		};
	}

	public ServletInputStream getInputStream() {
		ByteArrayInputStream body = new ByteArrayInputStream(builder.toString().getBytes());
		return new ServletInputStream() {

			@Override
			public int read() throws IOException {
				return body.read();
			}

			@Override
			public void setReadListener(ReadListener listener) {
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public boolean isFinished() {
				return body.available() <= 0;
			}
		};
	}

}
