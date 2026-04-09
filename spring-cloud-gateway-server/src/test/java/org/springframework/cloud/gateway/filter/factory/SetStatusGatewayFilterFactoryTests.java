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

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * SetStatusGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 SetStatusGatewayFilterFactory 的状态码设置功能，包括： - 测试使用整数设置 HTTP 状态码 - 测试使用字符串设置
 * HTTP 状态码 - 测试使用枚举设置 HTTP 状态码 - 测试设置非标准状态码（如 432） - 测试添加原始状态码到响应头 - 测试 toString 格式输出
 *
 * SetStatusGatewayFilterFactory 负责修改响应或请求的 HTTP 状态码， 支持整数、字符串和 HttpStatus 枚举三种配置方式。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SetStatusGatewayFilterFactoryTests extends BaseWebClientTests {

	@Autowired
	private SetStatusGatewayFilterFactory filterFactory;

	/**
	 * 测试使用整数设置状态码 验证：响应状态码应为 UNAUTHORIZED (401)
	 */
	@Test
	public void setStatusIntWorks() {
		setStatusStringTest("www.setstatusint.org", HttpStatus.UNAUTHORIZED);
	}

	/**
	 * 测试使用字符串设置状态码 验证：响应状态码应为 BAD_REQUEST (400)
	 */
	@Test
	public void setStatusStringWorks() {
		setStatusStringTest("www.setstatusstring.org", HttpStatus.BAD_REQUEST);
	}

	/**
	 * 测试设置非标准状态码（如 432） 验证：自定义状态码应被正确设置 注意：由于 Spring 框架限制，Netty 客户端无法获取自定义状态码
	 */
	@Test
	public void nonStandardCodeWorks() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.HOST, "www.setcustomstatus.org");
		ResponseEntity<String> response = new TestRestTemplate().exchange(baseUri + "/headers", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(432);

		// https://jira.spring.io/browse/SPR-16748
		/*
		 * testClient.get() .uri("/status/432") .exchange() .expectStatus().isEqualTo(432)
		 * .expectBody(String.class).isEqualTo("Failed with 432");
		 */
	}

	/**
	 * 测试设置状态码时添加原始状态码到响应头 验证：响应头应包含 original-http-status，值为 [200]
	 */
	@Test
	public void shouldSetStatusIntAndAddOriginalHeader() {
		String headerName = "original-http-status";
		filterFactory.setOriginalStatusHeaderName(headerName);
		setStatusStringTest("www.setstatusint.org", HttpStatus.UNAUTHORIZED).expectHeader().value(headerName,
				Matchers.is("[200]"));

	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的状态码
	 */
	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setStatus("401");
		GatewayFilter filter = new SetStatusGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("401");
	}

	/**
	 * 执行状态码测试的辅助方法
	 * @param host 请求的 Host 头
	 * @param status 预期的 HTTP 状态码
	 * @return WebTestClient.ResponseSpec 用于链式断言
	 */
	private WebTestClient.ResponseSpec setStatusStringTest(String host, HttpStatus status) {
		return testClient.get().uri("/headers").header("Host", host).exchange().expectStatus().isEqualTo(status);
	}

	/**
	 * 测试枚举状态码配置类
	 *
	 * 定义使用 HttpStatus 枚举设置状态码的路由
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestEnumConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator enumRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("test_enum_http_status",
					r -> r.host("*.setenumstatus.org").filters(f -> f.setStatus(HttpStatus.UNAUTHORIZED)).uri(uri))
					.build();
		}

	}

	/**
	 * 测试配置类
	 *
	 * 定义使用自定义状态码（432）的路由
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
			// @formatter:off
			return builder.routes()
					.route("test_custom_http_status",
							r -> r.host("*.setcustomstatus.org")
									.filters(f -> f.setStatus(432))
									.uri(uri))
					.build();
			// @formatter:on
		}

	}

}
