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
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory.Config;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * StripPrefixGatewayFilterFactory 单元测试类
 *
 * 本测试类用于验证 StripPrefixGatewayFilterFactory 的路径前缀移除功能，包括： - 测试移除指定数量的路径前缀 -
 * 测试各种边界情况（空路径、单斜杠、多斜杠等） - 测试 toString 格式输出
 *
 * StripPrefixGatewayFilterFactory 负责移除请求路径的前缀部分， 用于将请求路径中的网关前缀剥离后再转发给后端服务。
 *
 * @author Ryan Baxter
 * @author 译者：Spring Cloud Gateway 团队
 */
public class StripPrefixGatewayFilterFactoryTests {

	/**
	 * 测试移除路径前缀的各种场景 验证：包括标准路径、空路径、尾部斜杠、多个斜杠等边界情况
	 */
	@Test
	public void testStripPrefix() {
		testStripPrefixFilter("/foo/bar", "/bar", 1);
		testStripPrefixFilter("/foo/bar", "/", 2);
		testStripPrefixFilter("/foo/bar", "/foo/bar", 0);
		testStripPrefixFilter("/foo/bar/", "/", 2);
		testStripPrefixFilter("/foo/bar/", "/foo/bar/", 0);
		testStripPrefixFilter("", "/", 1);
		testStripPrefixFilter("/", "/", 1);
		testStripPrefixFilter("/", "/", 2);
		testStripPrefixFilter("", "/", 2);
		testStripPrefixFilter("/this/is/a/long/path/with/a/lot/of/slashes", "/path/with/a/lot/of/slashes", 4);
	}

	/**
	 * 执行路径前缀移除测试的辅助方法
	 * @param actualPath 原始请求路径
	 * @param expectedPath 移除前缀后的预期路径
	 * @param parts 要移除的路径段数
	 */
	private void testStripPrefixFilter(String actualPath, String expectedPath, int parts) {
		GatewayFilter filter = new StripPrefixGatewayFilterFactory().apply(c -> c.setParts(parts));

		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost" + actualPath).build();

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
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的 parts 数量
	 */
	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setParts(2);
		GatewayFilter filter = new StripPrefixGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("2");
	}

}
