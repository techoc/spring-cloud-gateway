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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

/**
 * ReactiveLoadBalancerClientFilter 单元测试类
 *
 * 本测试类用于验证 ReactiveLoadBalancerClientFilter 的负载均衡路由功能，包括： - 测试 lb:// 协议的识别和过滤逻辑 -
 * 测试服务实例的发现和选择 - 测试请求 URL 的转换（lb:// -> http://） - 测试负载均衡器的生命周期回调（成功、失败、丢弃） -
 * 测试安全上下文的协议转换（https -> http） - 测试请求提示（hint）参数传递
 *
 * ReactiveLoadBalancerClientFilter 是 Spring Cloud Gateway 的响应式负载均衡过滤器， 负责将 lb://
 * 协议的请求转换为实际的 HTTP/HTTPS 请求，通过负载均衡器选择后端服务实例。
 *
 * @author Spencer Gibb
 * @author Tim Ysewyn
 * @author Olga Maciaszek-Sharma
 * @author 译者：Spring Cloud Gateway 团队
 */
@SuppressWarnings("UnassignedFluxMonoInstance")
@ExtendWith(MockitoExtension.class)
class ReactiveLoadBalancerClientFilterTests {

	private ServerWebExchange exchange;

	private GatewayLoadBalancerProperties properties;

	@Mock
	private GatewayFilterChain chain;

	@Mock
	private LoadBalancerClientFactory clientFactory;

	@Mock
	private LoadBalancerProperties loadBalancerProperties;

	@InjectMocks
	private ReactiveLoadBalancerClientFilter filter;

	@BeforeEach
	void setup() {
		properties = new GatewayLoadBalancerProperties();
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/mypath").build());
	}

	/**
	 * 测试当网关请求 URL 属性缺失时不应进行过滤 验证：过滤器应继续调用过滤器链，不与 LoadBalancer 交互
	 */
	@Test
	void shouldNotFilterWhenGatewayRequestUrlIsMissing() {
		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyNoInteractions(clientFactory);
	}

	/**
	 * 测试当网关请求 URL 协议不是 lb 时不应进行过滤 验证：对于 http:// 协议的请求，过滤器应跳过负载均衡处理
	 */
	@Test
	void shouldNotFilterWhenGatewayRequestUrlSchemeIsNotLb() {
		URI uri = UriComponentsBuilder.fromUriString("http://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyNoInteractions(clientFactory);
	}

	/**
	 * 测试当找不到服务实例时应抛出 NotFoundException 验证：lb://myservice 在没有可用实例时应抛出异常
	 */
	@Test
	void shouldThrowExceptionWhenNoServiceInstanceIsFound() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> {
			URI uri = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

			filter.filter(exchange, chain).block();
		});
	}

	/**
	 * 测试标准的负载均衡过滤流程 验证：lb://myservice 应被转换为实际的 http://localhost:8080/mypath
	 */
	@SuppressWarnings("unchecked")
	@Test
	void shouldFilter() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		URI url = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, url);

		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				true);

		RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer(
				ServiceInstanceListSuppliers.toProvider("myservice", serviceInstance), "myservice", -1);
		when(clientFactory.getInstance("myservice", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);

		when(chain.filter(exchange)).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		assertThat((LinkedHashSet<URI>) exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).contains(url);

		verify(clientFactory).getInstance("myservice", ReactorServiceInstanceLoadBalancer.class);

		verify(clientFactory).getInstances("myservice", LoadBalancerLifecycle.class);

		verifyNoMoreInteractions(clientFactory);

		assertThat((URI) exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR))
				.isEqualTo(URI.create("https://localhost:8080/mypath"));

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	/**
	 * 测试带查询参数的 lb:// 协议正常路径 验证：参数应正确传递到转换后的 URL
	 */
	@Test
	void happyPath() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get?a=b").build();

		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange webExchange = testFilter(request, lbUri);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1").hasParameter("a", "b");
	}

	@Test
	void noQueryParams() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get").build();

		ServerWebExchange webExchange = testFilter(request, URI.create("lb://service1"));
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1");
	}

	/**
	 * 测试已编码参数的负载均衡转换不会导致双重编码 验证：编码的查询参数应保持不变
	 */
	@Test
	void encodedParameters() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?a=b&c=d[]").buildAndExpand().encode()
				.toUri();

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		URI lbUrl = UriComponentsBuilder.fromUriString("lb://service1?a=b&c=d[]").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");

		assertThat(lbUrl).hasParameter("c", "d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1").hasParameter("a", "b").hasParameter("c", "d[]");

		// prove that it is not double encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");
	}

	@Test
	void unencodedParameters() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		URI url = URI.create("http://localhost/get?a=b&c=d[]");

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		URI lbUrl = URI.create("lb://service1?a=b&c=d[]");

		// prove that it is unencoded
		assertThat(lbUrl.getRawQuery()).isEqualTo("a=b&c=d[]");

		ServerWebExchange webExchange = testFilter(request, lbUrl);

		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("service1-host1").hasParameter("a", "b").hasParameter("c", "d[]");

		// prove that it is NOT encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d[]");
	}

	/**
	 * 测试使用属性而非协议指定负载均衡前缀的场景 验证：ws://service1?a=b 在 GATEWAY_SCHEME_PREFIX_ATTR=lb 时应被正确处理
	 */
	@Test
	void happyPathWithAttributeRatherThanScheme() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		MockServerHttpRequest request = MockServerHttpRequest.get("ws://localhost/get?a=b").build();

		URI lbUri = URI.create("ws://service1?a=b");

		exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "lb");

		ServerWebExchange webExchange = testFilter(exchange, lbUri);
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("ws").hasHost("service1-host1").hasParameter("a", "b");
	}

	@Test
	void shouldNotFilterWhenGatewaySchemePrefixAttrIsNotLb() {
		URI uri = UriComponentsBuilder.fromUriString("http://myservice").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		exchange.getAttributes().put(GATEWAY_SCHEME_PREFIX_ATTR, "xx");

		filter.filter(exchange, chain);

		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
		verifyNoInteractions(clientFactory);
	}

	/**
	 * 测试当启用 use404 且找不到服务实例时应返回 404 状态码 验证：NotFoundException 应包含 HttpStatus.NOT_FOUND 状态
	 */
	@Test
	void shouldThrow4O4ExceptionWhenNoServiceInstanceIsFound() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		URI uri = UriComponentsBuilder.fromUriString("lb://service1").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer(
				ServiceInstanceListSuppliers.toProvider("service1"), "service1", -1);
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);
		properties.setUse404(true);
		ReactiveLoadBalancerClientFilter filter = new ReactiveLoadBalancerClientFilter(clientFactory, properties,
				loadBalancerProperties);
		when(chain.filter(exchange)).thenReturn(Mono.empty());
		try {
			filter.filter(exchange, chain).block();
		}
		catch (NotFoundException exception) {
			assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		}
	}

	/**
	 * 测试安全上下文的协议覆盖功能 验证：对于 https://localhost:9999 的原始请求，即使服务实例不支持 HTTPS， 也应使用 http://
	 * 协议（因为服务实例的 isSecure=false）
	 */
	@SuppressWarnings("unchecked")
	@Test
	void shouldOverrideSchemeUsingIsSecure() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		URI url = UriComponentsBuilder.fromUriString("lb://myservice").build().toUri();
		ServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get("https://localhost:9999/mypath").build());
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, url);
		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				false);
		when(clientFactory.getInstance("myservice", ReactorServiceInstanceLoadBalancer.class)).thenReturn(
				new RoundRobinLoadBalancer(ServiceInstanceListSuppliers.toProvider("myservice", serviceInstance),
						"myservice", -1));
		when(chain.filter(exchange)).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();

		assertThat((LinkedHashSet<URI>) exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR)).contains(url);
		assertThat((URI) exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR))
				.isEqualTo(URI.create("http://localhost:8080/mypath"));
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	/**
	 * 测试请求应正确传递给负载均衡器，包括 hint 参数 验证：负载均衡器应收到包含 hint 和原始 URL 的请求
	 */
	@SuppressWarnings({ "rawtypes" })
	@Test
	void shouldPassRequestToLoadBalancer() {
		String hint = "test";
		when(loadBalancerProperties.getHint()).thenReturn(buildHints(hint));
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get?a=b").build();
		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange serverWebExchange = mock(ServerWebExchange.class);
		when(serverWebExchange.getAttribute(GATEWAY_REQUEST_URL_ATTR)).thenReturn(lbUri);
		when(serverWebExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR))
				.thenReturn(new LinkedHashSet<>());
		when(serverWebExchange.getRequest()).thenReturn(request);
		RoundRobinLoadBalancer loadBalancer = mock(RoundRobinLoadBalancer.class);
		when(loadBalancer.choose(any(Request.class))).thenReturn(Mono.just(
				new DefaultResponse(new DefaultServiceInstance("myservice1", "service1", "localhost", 8080, false))));
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);
		when(chain.filter(any())).thenReturn(Mono.empty());

		filter.filter(serverWebExchange, chain);

		verify(loadBalancer).choose(argThat((Request passedRequest) -> ((RequestDataContext) passedRequest.getContext())
				.getClientRequest().getUrl().equals(request.getURI())
				&& ((RequestDataContext) passedRequest.getContext()).getHint().equals(hint)));

	}

	/**
	 * 测试负载均衡生命周期回调在成功时正确执行 验证：onStart、onStartRequest 和 onComplete（SUCCESS状态）回调应被调用
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	void loadBalancerLifecycleCallbacksExecutedForSuccess() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		LoadBalancerLifecycle lifecycleProcessor = mock(LoadBalancerLifecycle.class);
		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				false);
		ServerWebExchange serverWebExchange = mockExchange(serviceInstance, lifecycleProcessor, false);

		filter.filter(serverWebExchange, chain).subscribe();

		verify(lifecycleProcessor).onStart(any(Request.class));
		verify(lifecycleProcessor).onStartRequest(any(Request.class), any(Response.class));
		verify(lifecycleProcessor).onComplete(argThat(completionContext -> CompletionContext.Status.SUCCESS
				.equals(completionContext.status())
				&& completionContext.getLoadBalancerResponse().getServer().equals(serviceInstance)
				&& HttpMethod.GET.equals(
						((RequestDataContext) completionContext.getLoadBalancerRequest().getContext()).method())));
	}

	/**
	 * 测试负载均衡生命周期回调在丢弃请求时正确执行 验证：onStart 和 onComplete（DISCARD 状态）回调应被调用
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	void loadBalancerLifecycleCallbacksExecutedForDiscard() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		LoadBalancerLifecycle lifecycleProcessor = mock(LoadBalancerLifecycle.class);
		ServiceInstance serviceInstance = null;
		ServerWebExchange serverWebExchange = mockExchange(serviceInstance, lifecycleProcessor, false);

		filter.filter(serverWebExchange, chain).subscribe();

		verify(lifecycleProcessor).onStart(any(Request.class));
		verify(lifecycleProcessor).onComplete(argThat(completionContext -> CompletionContext.Status.DISCARD
				.equals(completionContext.status())
				&& HttpMethod.GET.equals(
						((RequestDataContext) completionContext.getLoadBalancerRequest().getContext()).method())));
	}

	/**
	 * 测试负载均衡生命周期回调在请求失败时正确执行 验证：onStart、onStartRequest 和 onComplete（FAILED状态）回调应被调用
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	void loadBalancerLifecycleCallbacksExecutedForFailed() {
		when(clientFactory.getProperties(any())).thenReturn(loadBalancerProperties);
		LoadBalancerLifecycle lifecycleProcessor = mock(LoadBalancerLifecycle.class);
		ServiceInstance serviceInstance = new DefaultServiceInstance("myservice1", "myservice", "localhost", 8080,
				false);
		ServerWebExchange serverWebExchange = mockExchange(serviceInstance, lifecycleProcessor, true);

		filter.filter(serverWebExchange, chain).subscribe();

		verify(lifecycleProcessor).onStart(any(Request.class));
		verify(lifecycleProcessor).onStartRequest(any(Request.class), any(Response.class));
		verify(lifecycleProcessor).onComplete(argThat(completionContext -> CompletionContext.Status.FAILED
				.equals(completionContext.status())
				&& HttpMethod.GET.equals(
						((RequestDataContext) completionContext.getLoadBalancerRequest().getContext()).method())));
	}

	/**
	 * 创建模拟的 ServerWebExchange 用于测试生命周期回调
	 * @param serviceInstance 服务实例（可为 null）
	 * @param lifecycleProcessor 负载均衡生命周期处理器
	 * @param shouldThrowException 是否应抛出异常
	 * @return 配置好的模拟 ServerWebExchange
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private ServerWebExchange mockExchange(ServiceInstance serviceInstance, LoadBalancerLifecycle lifecycleProcessor,
			boolean shouldThrowException) {
		Response response;
		when(lifecycleProcessor.supports(any(Class.class), any(Class.class), any(Class.class))).thenReturn(true);
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get?a=b").build();
		URI lbUri = URI.create("lb://service1?a=b");
		ServerWebExchange serverWebExchange = MockServerWebExchange.from(request);
		if (serviceInstance == null) {
			response = new EmptyResponse();
		}
		else {
			response = new DefaultResponse(serviceInstance);
		}
		serverWebExchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, lbUri);
		serverWebExchange.getAttributes().put(GATEWAY_ORIGINAL_REQUEST_URL_ATTR, new LinkedHashSet<>());
		serverWebExchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR, response);
		RoundRobinLoadBalancer loadBalancer = mock(RoundRobinLoadBalancer.class);
		when(loadBalancer.choose(any(Request.class))).thenReturn(Mono.just(response));
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);
		Map<String, LoadBalancerLifecycle> lifecycleProcessors = new HashMap<>();
		lifecycleProcessors.put("service1", lifecycleProcessor);
		when(clientFactory.getInstances("service1", LoadBalancerLifecycle.class)).thenReturn(lifecycleProcessors);
		if (shouldThrowException) {
			when(chain.filter(any())).thenReturn(Mono.error(new UnsupportedOperationException()));
		}
		else {
			when(chain.filter(any())).thenReturn(Mono.empty());
		}
		return serverWebExchange;
	}

	/**
	 * 构建负载均衡提示参数映射
	 * @param hint 提示值
	 * @return 包含默认提示的 Map
	 */
	private Map<String, String> buildHints(String hint) {
		Map<String, String> hints = new HashMap<>();
		hints.put("default", hint);
		return hints;
	}

	/**
	 * 执行过滤器测试的辅助方法
	 * @param request 模拟的 HTTP 请求
	 * @param uri 负载均衡 URI
	 * @return 应用过滤器后的 ServerWebExchange
	 */
	private ServerWebExchange testFilter(MockServerHttpRequest request, URI uri) {
		return testFilter(MockServerWebExchange.from(request), uri);
	}

	/**
	 * 执行过滤器测试的辅助方法（重载版本）
	 * @param exchange 模拟的 ServerWebExchange
	 * @param uri 负载均衡 URI
	 * @return 应用过滤器后的 ServerWebExchange
	 */
	private ServerWebExchange testFilter(ServerWebExchange exchange, URI uri) {
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		RoundRobinLoadBalancer loadBalancer = new RoundRobinLoadBalancer(
				ServiceInstanceListSuppliers.toProvider("service1",
						new DefaultServiceInstance("service1_1", "service1", "service1-host1", 8081, false)),
				"service1", -1);
		when(clientFactory.getInstance("service1", ReactorServiceInstanceLoadBalancer.class)).thenReturn(loadBalancer);

		ReactiveLoadBalancerClientFilter filter = new ReactiveLoadBalancerClientFilter(clientFactory, properties,
				loadBalancerProperties);
		filter.filter(exchange, chain).block();

		return captor.getValue();
	}

}
