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

package org.springframework.cloud.gateway.cors;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * SimpleUrlHandlerCorsTests - 简单URL处理器CORS测试类
 *
 * 本测试类验证通过SimpleUrlHandler处理CORS请求的场景： -
 * 配置spring.cloud.gateway.globalcors.add-to-simple-url-handler-mapping=true时 -
 * CORS预检请求由SimpleUrlHandler处理 - 测试CORS处理与Gateway路由的交互
 *
 * @author test
 */
@SpringBootTest(webEnvironment = RANDOM_PORT,
		properties = "spring.cloud.gateway.globalcors.add-to-simple-url-handler-mapping=true")
@DirtiesContext
@ActiveProfiles("request-header-web-filter")
public class SimpleUrlHandlerCorsTests extends BaseWebClientTests {

	/**
	 * testPreFlightCorsRequestNotHandledByGW - 测试由SimpleUrlHandler处理的预检请求
	 *
	 * 验证当配置由SimpleUrlHandler处理CORS时，预检请求能够正确返回CORS响应头
	 */
	@Test
	public void testPreFlightCorsRequestNotHandledByGW() {
		ClientResponse clientResponse = webClient.options().uri("/abc/123/function").header("Origin", "domain.com")
				.header("Access-Control-Request-Method", "GET").exchange().block();
		HttpHeaders asHttpHeaders = clientResponse.headers().asHttpHeaders();
		Mono<String> bodyToMono = clientResponse.bodyToMono(String.class);
		// pre-flight request shouldn't return the response body
		assertThat(bodyToMono.block()).isNull();
		assertThat(asHttpHeaders.getAccessControlAllowOrigin())
				.as("Missing header value in response: " + HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEqualTo("*");
		assertThat(asHttpHeaders.getAccessControlAllowMethods())
				.as("Missing header value in response: " + HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
				.isEqualTo(Arrays.asList(new HttpMethod[] { HttpMethod.GET }));
		assertThat(clientResponse.statusCode()).as("Pre Flight call failed.").isEqualTo(HttpStatus.OK);
	}

	/**
	 * testCorsRequestNotHandledByGW - 测试由SimpleUrlHandler处理的实际跨域请求
	 *
	 * 验证GET请求在路由未匹配时，返回404但仍包含CORS响应头
	 */
	@Test
	public void testCorsRequestNotHandledByGW() {
		ClientResponse clientResponse = webClient.get().uri("/abc/123/function").header("Origin", "domain.com")
				.header(HttpHeaders.HOST, "www.path.org").exchange().block();
		HttpHeaders asHttpHeaders = clientResponse.headers().asHttpHeaders();
		Mono<String> bodyToMono = clientResponse.bodyToMono(String.class);
		assertThat(bodyToMono.block()).isNotNull();
		assertThat(asHttpHeaders.getAccessControlAllowOrigin())
				.as("Missing header value in response: " + HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN).isEqualTo("*");
		assertThat(clientResponse.statusCode()).as("CORS request failed.").isEqualTo(HttpStatus.NOT_FOUND);
	}

	/**
	 * TestConfig - SimpleUrlHandler CORS测试配置类
	 *
	 * 提供测试所需的Spring Boot配置
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
