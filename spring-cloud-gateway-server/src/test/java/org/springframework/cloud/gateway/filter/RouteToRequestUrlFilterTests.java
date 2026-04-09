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

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringBootVersion;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

/**
 * RouteToRequestUrlFilter 单元测试类
 *
 * 本测试类用于验证 RouteToRequestUrlFilter 的路由 URL 转换功能，包括： - 测试标准 HTTP/HTTPS URL 的转换 - 测试 lb://
 * 负载均衡协议的处理 - 测试带 lb: 前缀的协议转换 - 测试 URL 参数的正确处理（编码/非编码） - 测试 URI 匹配器的工作逻辑
 *
 * RouteToRequestUrlFilter 负责将路由定义中的 URI 转换为实际的请求 URL， 支持 lb:// 协议（负载均衡）和 lb:http:// 前缀协议。
 *
 * @author Spencer Gibb
 * @author 译者：Spring Cloud Gateway 团队
 */
public class RouteToRequestUrlFilterTests {

	/**
	 * 测试标准 HTTP URL 转换的正常路径 验证：路由 URI 应正确转换为请求 URL，保持协议、主机和参数
	 */
	@Test
	public void happyPath() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get?a=b").build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost/mypath");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost").hasPath("/get").hasParameter("a", "b");
	}

	/**
	 * 测试 lb:// 负载均衡协议的转换 验证：lb://myhost 应保持 lb 协议，由后续的 ReactiveLoadBalancerClientFilter
	 * 处理
	 */
	@Test
	public void happyPathLb() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/getb").build();

		ServerWebExchange webExchange = testFilter(request, "lb://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("lb").hasHost("myhost");
	}

	/**
	 * 测试包含无效主机名（带下划线）的 URI 抛出异常 验证：lb://my_host 格式应抛出 IllegalStateException
	 */
	@Test(expected = IllegalStateException.class)
	public void invalidHost() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/getb").build();
		testFilter(request, "lb://my_host");
	}

	/**
	 * 测试 lb:http:// 格式的前缀协议转换 验证：lb:http://myhost 应转换为 http://myhost，同时在属性中保留 lb 前缀
	 */
	@Test
	public void happyPathLbPlusScheme() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/getb").build();

		ServerWebExchange webExchange = testFilter(request, "lb:http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost");
		String schemePrefix = webExchange.getRequiredAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
		assertThat(schemePrefix).isEqualTo("lb");
	}

	/**
	 * 测试不包含查询参数的 URL 转换 验证：转换后应保持协议和主机，无参数
	 */
	@Test
	public void noQueryParams() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost/get").build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost");
	}

	/**
	 * 测试已编码参数的 URL 转换不会导致双重编码 验证：原始编码的查询参数应保持不变，不会被重复编码
	 */
	@Test
	public void encodedParameters() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?a=b&c=d[]").buildAndExpand().encode()
				.toUri();

		// prove that it is encoded
		assertThat(url.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");

		assertThat(url).hasParameter("c", "d[]");

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost").hasParameter("a", "b").hasParameter("c", "d[]");

		// prove that it is not double encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d%5B%5D");
	}

	/**
	 * 测试部分编码参数的 URL 转换行为 验证：部分编码的参数可能产生双重编码，这是预期行为 注意：此测试仅在 Spring Boot 2.3 版本下运行
	 */
	@Test
	public void partialEncodedParameters() {
		assumeTrue("partialEncodedParameters ignored for boot 2.2", SpringBootVersion.getVersion().startsWith("2.3."));

		URI url = UriComponentsBuilder.fromUriString("http://localhost/get?key[]=test= key&start=1533108081").build()
				.toUri();

		// prove that it is partial encoded
		assertThat(url.getRawQuery()).isEqualTo("key[]=test=%20key&start=1533108081");

		assertThat(url).hasParameter("key[]", "test= key");
		assertThat(url).hasParameter("start", "1533108081");

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost")
				// since https://github.com/joel-costigliola/assertj-core/issues/1699
				// assertj uses raw query
				.hasParameter("key[]", "test=%20key").hasParameter("start", "1533108081");

		// prove that it is double encoded since partial encoded uri is treated as
		// unencoded.
		assertThat(uri.getRawQuery()).isEqualTo("key[]=test=%2520key&start=1533108081");
	}

	/**
	 * 测试 URL 路径中包含编码空格的转换 验证：编码的空格（%20）应保持不变，不会被双重编码
	 */
	@Test
	public void encodedUrl() {
		URI url = UriComponentsBuilder.fromUriString("http://localhost/abc def/get").buildAndExpand().encode().toUri();

		// prove that it is encoded
		assertThat(url.getRawPath()).isEqualTo("/abc%20def/get");

		assertThat(url).hasPath("/abc def/get");

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost/abc%20def/get");
		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost").hasPath("/abc def/get");

		// prove that it is not double encoded
		assertThat(uri.getRawPath()).isEqualTo("/abc%20def/get");
	}

	/**
	 * 测试未编码参数的 URL 转换 验证：未编码的参数应保持原样，不进行额外编码
	 */
	@Test
	public void unencodedParameters() {
		URI url = URI.create("http://localhost/get?a=b&c=d[]");

		// prove that it is unencoded
		assertThat(url.getRawQuery()).isEqualTo("a=b&c=d[]");

		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		ServerWebExchange webExchange = testFilter(request, "http://myhost");

		URI uri = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(uri).hasScheme("http").hasHost("myhost").hasParameter("a", "b").hasParameter("c", "d[]");

		// prove that it is NOT encoded
		assertThat(uri.getRawQuery()).isEqualTo("a=b&c=d[]");
	}

	/**
	 * 测试 hasAnotherScheme 方法的 URI 匹配逻辑 验证：lb:a123:stuff 等多协议格式应匹配，lb:a 等简单格式不应匹配
	 */
	@Test
	public void matcherWorks() {
		testMatcher(true, "lb:a123:stuff", "lb:abc:stuff", "lb:a.bc:stuff", "lb:a-bc:stuff", "lb:a+bc:stuff");
		testMatcher(false, "lb:a", "lb:a123", "lb:123:stuff", "lb:a//:stuff");
	}

	/**
	 * 测试 URI 匹配器对给定 URI 列表的匹配结果
	 * @param shouldMatch 预期是否应匹配
	 * @param uris 待测试的 URI 字符串数组
	 */
	private void testMatcher(boolean shouldMatch, String... uris) {
		for (String s : uris) {
			URI uri = URI.create(s);
			boolean result = RouteToRequestUrlFilter.hasAnotherScheme(uri);
			assertThat(result).as("%s should match: %s", s, result).isEqualTo(shouldMatch);
		}
	}

	/**
	 * 执行过滤器测试的辅助方法
	 *
	 * 创建模拟的 ServerWebExchange，配置路由信息， 然后应用 RouteToRequestUrlFilter 并返回转换后的 exchange。
	 * @param request 模拟的 HTTP 请求
	 * @param routeUri 路由目标 URI
	 * @return 应用过滤器后的 ServerWebExchange
	 */
	private ServerWebExchange testFilter(MockServerHttpRequest request, String routeUri) {
		Route value = Route.async().id("1").uri(URI.create(routeUri)).order(0).predicate(swe -> true).build();

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, value);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		RouteToRequestUrlFilter filter = new RouteToRequestUrlFilter();
		filter.filter(exchange, filterChain);

		return captor.getValue();
	}

}
