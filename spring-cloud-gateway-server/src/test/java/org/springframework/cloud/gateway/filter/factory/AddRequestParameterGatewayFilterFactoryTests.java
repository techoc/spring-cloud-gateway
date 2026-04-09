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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory.NameValueConfig;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

/**
 * AddRequestParameterGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 AddRequestParameterGatewayFilterFactory 的请求参数添加功能，包括： - 测试在无查询参数时添加请求参数 -
 * 测试在有查询参数时添加请求参数 - 测试添加编码的请求参数 - 测试通过 Java DSL 配置添加请求参数 - 测试 toString 格式输出
 *
 * AddRequestParameterGatewayFilterFactory 负责在请求转发前添加指定的查询参数， 支持静态值和占位符形式的动态值。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-parameter-web-filter")
public class AddRequestParameterGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试在无查询参数时添加请求参数 验证：应添加 example=ValueA 参数
	 */
	@Test
	public void addRequestParameterFilterWorksBlankQuery() {
		testRequestParameterFilter(null, null);
	}

	/**
	 * 测试在有查询参数时添加请求参数 验证：原有参数应保留，同时添加 example=ValueA 参数
	 */
	@Test
	public void addRequestParameterFilterWorksNonBlankQuery() {
		testRequestParameterFilter("baz", "bam");
	}

	/**
	 * 测试添加编码的请求参数 验证：URL 编码的参数值应正确解码后添加到请求中
	 */
	@Test
	public void addRequestParameterFilterWorksEncodedQuery() {
		testRequestParameterFilter("name", "%E6%89%8E%E6%A0%B9");
	}

	/**
	 * 测试通过 Java DSL 配置添加编码的请求参数 验证：参数值应包含动态替换的 {sub} 占位符
	 */
	@Test
	public void addRequestParameterFilterWorksEncodedQueryJavaDsl() {
		testRequestParameterFilter("www.addreqparamjava.org", "ValueB-www", "javaname", "%E6%89%8E%E6%A0%B9");
	}

	/**
	 * 执行请求参数测试的辅助方法（使用默认 host）
	 * @param name 参数名称
	 * @param value 参数值
	 */
	private void testRequestParameterFilter(String name, String value) {
		testRequestParameterFilter("www.addrequestparameter.org", "ValueA", name, value);
	}

	/**
	 * 执行请求参数测试的辅助方法（完整版本）
	 * @param host 请求的 Host 头
	 * @param expectedValue 预期的 example 参数值
	 * @param name 额外参数的名称
	 * @param value 额外参数的值
	 */
	private void testRequestParameterFilter(String host, String expectedValue, String name, String value) {
		String query;
		if (name != null) {
			query = "?" + name + "=" + value;
		}
		else {
			query = "";
		}
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/get" + query).build(true).toUri();
		boolean checkForEncodedValue = containsEncodedParts(uri);
		testClient.get().uri(uri).header("Host", host).exchange().expectBody(Map.class).consumeWith(response -> {
			Map<String, Object> args = getMap(response.getResponseBody(), "args");
			assertThat(args).containsEntry("example", expectedValue);
			if (name != null) {
				if (checkForEncodedValue) {
					try {
						assertThat(args).containsEntry(name, URLDecoder.decode(value, "UTF-8"));
					}
					catch (UnsupportedEncodingException e) {
						throw new RuntimeException(e);
					}
				}
				else {
					assertThat(args).containsEntry(name, value);
				}
			}
		});
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的名称和值
	 */
	@Test
	public void toStringFormat() {
		NameValueConfig config = new NameValueConfig().setName("myname").setValue("myvalue");
		GatewayFilter filter = new AddRequestParameterGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname").contains("myvalue");
	}

	/**
	 * 测试配置类
	 *
	 * 定义测试路由配置，测试通过 Java DSL 添加带动态值的请求参数
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("add_request_param_java_test",
					r -> r.path("/get").and().host("{sub}.addreqparamjava.org")
							.filters(f -> f.prefixPath("/httpbin").addRequestParameter("example", "ValueB-{sub}"))
							.uri(uri))
					.build();
		}

	}

}
