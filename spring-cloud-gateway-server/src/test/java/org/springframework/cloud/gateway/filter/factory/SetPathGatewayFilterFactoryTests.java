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
import java.util.HashMap;
import java.util.LinkedHashSet;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;

/**
 * SetPathGatewayFilterFactory 单元测试类
 *
 * 本测试类用于验证 SetPathGatewayFilterFactory 的路径设置功能，包括： - 测试基本的路径设置 - 测试编码路径的设置 - 测试带模板变量的路径设置
 * - 测试带多个变量的路径设置 - 测试带编码字符的路径设置 - 测试 toString 格式输出
 *
 * SetPathGatewayFilterFactory 负责设置请求的路径， 支持使用 {variable} 占位符从路由变量中动态获取路径值。
 *
 * @author Spencer Gibb
 * @author 译者：Spring Cloud Gateway 团队
 */
public class SetPathGatewayFilterFactoryTests {

	/**
	 * 测试基本的路径设置 验证：路径应正确设置为 /baz/bar
	 */
	@Test
	public void setPathFilterWorks() {
		HashMap<String, String> variables = new HashMap<>();
		testFilter("/baz/bar", "/baz/bar", variables);
	}

	/**
	 * 测试编码路径的设置 验证：编码的空格等字符应正确处理
	 */
	@Test
	public void setEncodedPathFilterWorks() {
		HashMap<String, String> variables = new HashMap<>();
		testFilter("/baz/foo%20bar", "/baz/foo%20bar", variables);
	}

	/**
	 * 测试带模板变量的路径设置 验证：/{id} 应被替换为 /123（id=123）
	 */
	@Test
	public void setPathFilterWithTemplateVarsWorks() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "123");
		testFilter("/bar/baz/{id}", "/bar/baz/123", variables);
	}

	/**
	 * 测试带多个模板变量的路径设置 验证：/{org}/{scope}/function 应被正确替换
	 */
	@Test
	public void setPathFilterWithTemplatePrefixVarsWorks() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("org", "123");
		variables.put("scope", "abc");
		testFilter("/{org}/{scope}/function", "/123/abc/function", variables);
	}

	/**
	 * 测试带编码字符的路径设置 验证：变量值中的空格应正确保留
	 */
	@Test
	public void setPathFilterWithEncodedCharactersWorks() {
		HashMap<String, String> variables = new HashMap<>();
		variables.put("id", "12 3");
		testFilter("/bar/baz/{id}", "/bar/baz/12 3", variables);
	}

	/**
	 * 执行路径设置测试的辅助方法
	 * @param template 路径模板（支持 {variable} 占位符）
	 * @param expectedPath 预期的最终路径
	 * @param variables 路由变量映射
	 */
	private void testFilter(String template, String expectedPath, HashMap<String, String> variables) {
		GatewayFilter filter = new SetPathGatewayFilterFactory().apply(c -> c.setTemplate(template));

		MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost").build();

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
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的模板
	 */
	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setTemplate("mytemplate");
		GatewayFilter filter = new SetPathGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("mytemplate");
	}

}
