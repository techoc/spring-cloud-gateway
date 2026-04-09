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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.CONTENT_SECURITY_POLICY_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.REFERRER_POLICY_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.STRICT_TRANSPORT_SECURITY_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_CONTENT_TYPE_OPTIONS_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_DOWNLOAD_OPTIONS_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_FRAME_OPTIONS_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER;
import static org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory.X_XSS_PROTECTION_HEADER;
import static org.springframework.cloud.gateway.test.TestUtils.assertStatus;

/**
 * SecureHeadersGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 SecureHeadersGatewayFilterFactory 的安全响应头添加功能，包括： - 测试默认安全响应头的添加 -
 * 测试在收到后端响应后添加安全头（验证过滤器的非侵入性）
 *
 * SecureHeadersGatewayFilterFactory 负责在响应中添加安全相关的 HTTP 响应头， 包括
 * X-XSS-Protection、Strict-Transport-Security、X-Frame-Options 等， 以提高 Web 应用的安全性。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class SecureHeadersGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试默认安全响应头的添加 验证：响应应包含所有配置的安全头，且值与默认值一致
	 */
	@Test
	public void secureHeadersFilterWorks() {
		Mono<ClientResponse> result = webClient.get().uri("/headers").header("Host", "www.secureheaders.org")
				.exchangeToMono(Mono::just);

		SecureHeadersProperties defaults = new SecureHeadersProperties();

		StepVerifier.create(result).consumeNextWith(response -> {
			assertStatus(response, HttpStatus.OK);
			HttpHeaders httpHeaders = response.headers().asHttpHeaders();
			assertThat(httpHeaders.getFirst(X_XSS_PROTECTION_HEADER)).isEqualTo(defaults.getXssProtectionHeader());
			assertThat(httpHeaders.getFirst(STRICT_TRANSPORT_SECURITY_HEADER))
					.isEqualTo(defaults.getStrictTransportSecurity());
			assertThat(httpHeaders.getFirst(X_FRAME_OPTIONS_HEADER)).isEqualTo(defaults.getFrameOptions());
			assertThat(httpHeaders.getFirst(X_CONTENT_TYPE_OPTIONS_HEADER)).isEqualTo(defaults.getContentTypeOptions());
			assertThat(httpHeaders.getFirst(REFERRER_POLICY_HEADER)).isEqualTo(defaults.getReferrerPolicy());
			assertThat(httpHeaders.getFirst(CONTENT_SECURITY_POLICY_HEADER))
					.isEqualTo(defaults.getContentSecurityPolicy());
			assertThat(httpHeaders.getFirst(X_DOWNLOAD_OPTIONS_HEADER)).isEqualTo(defaults.getDownloadOptions());
			assertThat(httpHeaders.getFirst(X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER))
					.isEqualTo(defaults.getPermittedCrossDomainPolicies());
		}).expectComplete().verify(DURATION);
	}

	/**
	 * 测试在收到后端响应后添加安全头 验证：即使后端返回了自定义的 X-Frame-Options，安全头过滤器仍能正确添加
	 */
	@Test
	public void addsSecureHeadersAfterResponseIsReceived() {
		Mono<ClientResponse> result = webClient.patch().uri("/headers").header("Host", "www.secureheaders.org")
				.contentType(MediaType.APPLICATION_JSON).bodyValue("{ \"X-Frame-Options\": \"sameorigin\" }")
				.exchange();

		StepVerifier.create(result).consumeNextWith(response -> {
			assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.headers().header(X_FRAME_OPTIONS_HEADER)).containsOnly("sameorigin");
		}).expectComplete().verify(DURATION);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
