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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.LinkedHashSet;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.Config;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * RewritePathGatewayFilterFactory 单元测试类
 *
 * 本测试类用于验证 RewritePathGatewayFilterFactory 的路径重写功能，包括： - 测试基本的路径重写 - 测试编码路径的重写 -
 * 测试带命名捕获组的路径重写 - 测试编码参数的保留 - 测试 toString 格式输出
 *
 * RewritePathGatewayFilterFactory 负责使用正则表达式重写请求路径， 支持正则表达式的命名捕获组，用于路径参数的提取和替换。
 *
 * @author Spencer Gibb
 * @author 译者：Spring Cloud Gateway 团队
 */
public class RewritePathGatewayFilterFactoryTests {

	/**
	 * 测试基本的路径重写 验证：/foo/bar 应被重写为 /baz/bar
	 */
	@Test
	public void rewritePathFilterWorks() {
		testRewriteFilter("/foo", "/baz", "/foo/bar", "/baz/bar");
	}

	/**
	 * 测试编码路径的重写 验证：/foo/bar%20foobar 应被重写为 /baz/bar foobar
	 */
	@Test
	public void rewriteEncodedPathFilterWorks() {
		testRewriteFilter("/foo", "/baz", "/foo/bar%20foobar", "/baz/bar foobar");
	}

	/**
	 * 测试带命名捕获组的路径重写 验证：/foo/123 应被重写为 /bar/baz/123（提取 id 参数）
	 */
	@Test
	public void rewritePathFilterWithNamedGroupWorks() {
		testRewriteFilter("/foo/(?<id>\\d.*)", "/bar/baz/$\\{id}", "/foo/123", "/bar/baz/123");
	}

	/**
	 * 执行路径重写测试的辅助方法
	 * @param regex 正则表达式
	 * @param replacement 替换字符串
	 * @param actualPath 实际请求路径
	 * @param expectedPath 预期重写后的路径
	 * @return 应用过滤器后的 ServerWebExchange
	 */
	private ServerWebExchange testRewriteFilter(String regex, String replacement, String actualPath,
			String expectedPath) {
		GatewayFilter filter = new RewritePathGatewayFilterFactory()
				.apply(c -> c.setRegexp(regex).setReplacement(replacement));

		URI url = UriComponentsBuilder.fromUriString("http://localhost" + actualPath).build(true).toUri();
		MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url).build();

		ServerWebExchange exchange = MockServerWebExchange.from(request);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		assertThat(webExchange.getRequest().getURI()).hasPath(expectedPath);

		URI requestUrl = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(requestUrl).hasScheme("http").hasHost("localhost").hasNoPort().hasPath(expectedPath);
		LinkedHashSet<URI> uris = webExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(uris).contains(request.getURI());

		return webExchange;
	}

	/**
	 * 测试重写路径时保留编码参数 验证：查询参数应被正确保留，不受路径重写影响
	 */
	@Test
	public void rewritePathWithEncodedParams() {
		ServerWebExchange exchange = testRewriteFilter("/foo", "/baz", "/foo/bar?name=%E6%89%8E%E6%A0%B9", "/baz/bar");

		URI uri = exchange.getRequest().getURI();
		assertThat(uri.getRawQuery()).isEqualTo("name=%E6%89%8E%E6%A0%B9");
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的正则表达式和替换字符串
	 */
	@Test
	public void toStringFormat() {
		Config config = new Config().setRegexp("regexp1").setReplacement("replacement1");
		GatewayFilter filter = new RewritePathGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("regexp1").contains("replacement1");
	}

}
