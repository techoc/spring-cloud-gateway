/*
 * Copyright 2013-2022 the original author or authors.
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
import java.util.HashMap;
import java.util.LinkedHashSet;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

/**
 * PrefixPathGatewayFilterFactory 单元测试类
 *
 * 本测试类用于验证 PrefixPathGatewayFilterFactory 的路径前缀添加功能，包括： - 测试基本的路径前缀添加 - 测试带变量替换的路径前缀添加 -
 * 测试带多个变量的路径前缀添加 - 测试 toString 格式输出
 *
 * PrefixPathGatewayFilterFactory 负责为请求路径添加前缀， 支持使用 {variable} 占位符从路由变量中动态获取前缀值。
 *
 * @author Ryan Baxter
 * @author 译者：Spring Cloud Gateway 团队
 */
public class PrefixPathGatewayFilterFactoryTest {

	/**
	 * 测试基本的路径前缀添加 验证：/bar 应变为 /foo/bar
	 */
	@Test
	public void testPrefixPath() {
		testPrefixPathFilter("/foo", "/bar", "/foo/bar");
		testPrefixPathFilter("/foo", "/hello%20world", "/foo/hello%20world");
	}

	/**
	 * 测试带变量替换的路径前缀添加 验证：/{id}/bar 应变为 /foo/bar（id=foo）
	 */
	@Test
	public void testPrefixPathWithVariable() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "foo");
		testPrefixPathFilter("/{id}", "/bar", "/foo/bar", variables);
	}

	/**
	 * 测试带多个变量的路径前缀添加 验证：/{id}/v1/{hello}/{product} 应正确替换变量
	 */
	@Test
	public void testPrefixPathWithMultipleVariables() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "foo");
		variables.put("hello", "world");
		variables.put("product", "bar");
		testPrefixPathFilter("/{id}/v1/{hello}/{product}", "/test", "/foo/v1/world/bar/test", variables);
	}

	/**
	 * 执行路径前缀添加测试的辅助方法（无变量版本）
	 * @param prefix 要添加的前缀
	 * @param path 原始请求路径
	 * @param expectedPath 添加前缀后的预期路径
	 */
	private void testPrefixPathFilter(String prefix, String path, String expectedPath) {
		testPrefixPathFilter(prefix, path, expectedPath, new HashMap<>());
	}

	/**
	 * 执行路径前缀添加测试的辅助方法（带变量版本）
	 * @param prefix 要添加的前缀（支持 {variable} 占位符）
	 * @param path 原始请求路径
	 * @param expectedPath 添加前缀后的预期路径
	 * @param variables 路由变量映射
	 */
	private void testPrefixPathFilter(String prefix, String path, String expectedPath,
			HashMap<String, String> variables) {
		GatewayFilter filter = new PrefixPathGatewayFilterFactory().apply(c -> c.setPrefix(prefix));
		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost" + path).build();

		ServerWebExchange exchange = MockServerWebExchange.from(request);
		ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);

		GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, filterChain);

		ServerWebExchange webExchange = captor.getValue();

		assertThat(webExchange.getRequest().getURI()).hasPath(expectedPath);
		LinkedHashSet<URI> uris = webExchange.getRequiredAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
		assertThat(uris).contains(request.getURI());
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的前缀
	 */
	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setPrefix("myprefix");
		GatewayFilter filter = new PrefixPathGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myprefix");
	}

}
