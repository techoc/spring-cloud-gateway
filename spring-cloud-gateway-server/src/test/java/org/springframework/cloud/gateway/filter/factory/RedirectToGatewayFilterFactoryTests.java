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

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory.Config;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * RedirectToGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 RedirectToGatewayFilterFactory 的重定向功能，包括： - 测试绝对 URL 重定向 - 测试相对 URL 重定向 -
 * 测试使用字符串状态码的重定向 - 测试 toString 格式输出
 *
 * RedirectToGatewayFilterFactory 负责生成 HTTP 重定向响应， 支持绝对 URL 和相对 URL，以及自定义 HTTP 状态码。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RedirectToGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试绝对 URL 重定向 验证：响应状态码应为 302，Location 头应为 https://example.org
	 */
	@Test
	public void redirectToFilterWorks() {
		testClient.get().uri("/").header("Host", "www.redirectto.org").exchange().expectStatus()
				.isEqualTo(HttpStatus.FOUND).expectHeader().valueEquals(HttpHeaders.LOCATION, "https://example.org");
	}

	/**
	 * 测试相对 URL 重定向 验证：响应状态码应为 302，Location 头应为 /index.html#/customers
	 */
	@Test
	public void redirectToRelativeUrlFilterWorks() {
		testClient.get().uri("/").header("Host", "www.relativeredirect.org").exchange().expectStatus()
				.isEqualTo(HttpStatus.FOUND).expectHeader().valueEquals(HttpHeaders.LOCATION, "/index.html#/customers");
	}

	@Test
	public void redirectToRelativeUrlFilterWorksWithStrStatusCode() {
		testClient.get().uri("/").header("Host", "strcode.relativeredirect.org").exchange().expectStatus()
				.isEqualTo(HttpStatus.FOUND).expectHeader().valueEquals(HttpHeaders.LOCATION, "/index.html#/customers");
	}

	@Test
	public void toStringFormat() {
		Config config = new Config();
		config.setStatus("301");
		config.setUrl("http://newurl");
		GatewayFilter filter = new RedirectToGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("301").contains("http://newurl");
	}

	/**
	 * 测试配置类
	 *
	 * 定义测试路由配置，包括： - relative_redirect_uri_object: 测试使用 URI 对象和字符串状态码的重定向 -
	 * relative_redirect: 测试使用相对 URL 的重定向
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("relative_redirect_uri_object", r -> r.host("strcode.relativeredirect.org")
							.filters(f -> f.redirect("302", URI.create("/index.html#/customers"))).uri("no://op"))
					.route("relative_redirect", r -> r.host("**.relativeredirect.org")
							.filters(f -> f.redirect(302, "/index.html#/customers")).uri("no://op"))
					.build();
		}

	}

}
