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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

/**
 * MapRequestHeaderGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 MapRequestHeaderGatewayFilterFactory 的请求头映射功能，包括： - 测试基本的请求头映射 - 测试通过 Java DSL
 * 配置请求头映射 - 测试多值请求头的映射 - 测试映射 null 值请求头 - 测试源请求头不存在时的行为 - 测试 toString 格式输出
 *
 * MapRequestHeaderGatewayFilterFactory 负责将一个请求头的值映射到另一个请求头， 类似于 Apache Httpd 的
 * RewriteRule 和 Nginx 的 proxy_set_header。
 *
 * @author Tony Clarke
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-map-header-web-filter")
class MapRequestHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试基本的请求头映射功能 验证：请求头 a=tome 应被映射到 X-Request-Example=tome
	 */
	@Test
	void mapRequestHeaderFilterWorks() {
		testClient.get().uri("/headers").header("Host", "www.maprequestheader.org").header("a", "tome").exchange()
				.expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsEntry("X-Request-Example", "tome");
				});
	}

	/**
	 * 测试通过 Java DSL 配置请求头映射 验证：Java DSL 配置的映射规则应正确生效
	 */
	@Test
	void mapRequestHeaderFilterWorksJavaDsl() {
		testClient.get().uri("/headers").header("Host", "www.maprequestheaderjava.org").header("b", "tome").exchange()
				.expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsEntry("X-Request-Example-Java", "tome");
				});
	}

	/**
	 * 测试多值请求头的映射 验证：多个请求头值应都被映射到目标请求头
	 */
	@SuppressWarnings("unchecked")
	@Test
	void mapRequestHeaderWithMultiValueFilterWorks() {
		testClient.get().uri("/multivalueheaders").header("Host", "www.maprequestheader.org")
				.header("a", "tome", "toyou").exchange().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsKey("X-Request-Example");
					List<String> values = (List<String>) headers.get("X-Request-Example");
					assertThat(values).contains("tome", "toyou");
				});
	}

	/**
	 * 测试映射 null 值的请求头 验证：null 值的请求头不应被映射到目标请求头
	 */
	@Test
	void mapRequestHeaderWithNullValueFilterWorks() {
		testClient.get().uri("/headers").header("Host", "www.maprequestheader.org").header("a", (String) null)
				.exchange().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).doesNotContainKey("X-Request-Example");
				});
	}

	/**
	 * 测试源请求头不存在时的行为 验证：不存在源请求头时，目标请求头不应被创建
	 */
	@Test
	void mapRequestHeaderWhenInputHeaderDoesNotExist() {
		testClient.get().uri("/headers").header("Host", "www.maprequestheader.org").exchange().expectBody(Map.class)
				.consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).doesNotContainKey("X-Request-Example");
				});
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的源请求头和目标请求头
	 */
	@Test
	void toStringFormat() {
		Config config = new Config().setFromHeader("myfromheader").setToHeader("mytoheader");
		GatewayFilter filter = new MapRequestHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myfromheader").contains("mytoheader");
	}

	/**
	 * 测试配置类
	 *
	 * 定义测试路由配置，测试通过 Java DSL 配置请求头映射
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("map_request_header_java_test",
					r -> r.path("/headers").and().host("**.maprequestheaderjava.org")
							.filters(f -> f.prefixPath("/httpbin").mapRequestHeader("b", "X-Request-Example-Java"))
							.uri(uri))
					.build();
		}

	}

}
