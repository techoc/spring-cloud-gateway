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
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * AddResponseHeaderGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 AddResponseHeaderGatewayFilterFactory 的响应头添加功能，包括： - 测试为响应添加单个响应头 - 测试通过 Java
 * DSL 配置添加响应头 - 测试 toString 格式输出
 *
 * AddResponseHeaderGatewayFilterFactory 负责在响应返回前添加指定的 HTTP 响应头， 支持静态值和占位符形式的动态值。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
class AddResponseHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试添加响应头的功能 验证：响应应包含 X-Request-Foo: Bar 头
	 */
	@Test
	void testResposneHeaderFilter() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/headers").build(true).toUri();
		String host = "www.addresponseheader.org";
		String expectedValue = "Bar";
		testClient.get().uri(uri).header("Host", host).exchange().expectHeader().valueEquals("X-Request-Foo",
				expectedValue);
	}

	/**
	 * 测试通过 Java DSL 配置添加响应头的功能 验证：响应头值应包含动态替换的 {sub} 占位符
	 */
	@Test
	void testResposneHeaderFilterJavaDsl() {
		URI uri = UriComponentsBuilder.fromUriString(this.baseUri + "/get").build(true).toUri();
		String host = "www.addresponseheaderjava.org";
		String expectedValue = "myresponsevalue-www";
		testClient.get().uri(uri).header("Host", host).exchange().expectHeader().valueEquals("example", expectedValue);
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的名称和值
	 */
	@Test
	void toStringFormat() {
		NameValueConfig config = new NameValueConfig().setName("myname").setValue("myvalue");
		GatewayFilter filter = new AddResponseHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname").contains("myvalue");
	}

	/**
	 * 测试配置类
	 *
	 * 定义测试路由配置，测试通过 Java DSL 添加动态响应头
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("add_response_header_java_test",
							r -> r.path("/get").and().host("{sub}.addresponseheaderjava.org").filters(
									f -> f.prefixPath("/httpbin").addResponseHeader("example", "myresponsevalue-{sub}"))
									.uri(uri))
					.build();
		}

	}

}
