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
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

/**
 * PreserveHostHeaderGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 PreserveHostHeaderGatewayFilterFactory 的 Host 头保留功能，包括： - 测试保留原始请求的 Host 头 -
 * 测试 toString 格式输出
 *
 * PreserveHostHeaderGatewayFilterFactory 负责保留原始请求的 Host 头，
 * 而不是使用网关自动生成的主机名。这对于需要知道原始请求目标的应用非常重要。
 *
 * @author Spencer Gibb
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class PreserveHostHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试保留 Host 头的功能 验证：即使路由配置修改了 Host 头为 myhost.net， preserveHostHeader() 仍应保留原始请求的 Host
	 * 头
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void preserveHostHeaderGatewayFilterFactoryWorks() {
		testClient.get().uri("/multivalueheaders").header("Host", "www.preservehostheader.org").exchange()
				.expectStatus().isOk().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).containsKey("Host");
					List<String> values = (List<String>) headers.get("Host");
					assertThat(values).containsExactly("myhost.net");
				});
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含 PreserveHostHeader
	 */
	@Test
	public void toStringFormat() {
		GatewayFilter filter = new PreserveHostHeaderGatewayFilterFactory().apply();
		assertThat(filter.toString()).contains("PreserveHostHeader");
	}

	/**
	 * 测试配置类
	 *
	 * 定义测试路由配置： - 路由顺序为 -1（高优先级） - 使用 preserveHostHeader() 保留原始 Host 头 - 使用
	 * setRequestHeader 设置 Host 为 myhost.net（但会被 preserveHostHeader 覆盖）
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

		@Value("${test.uri}")
		String uri;

		@Bean
		public RouteLocator testRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("test_preserve_host_header", r -> r.order(-1).host("**.preservehostheader.org").filters(
							f -> f.prefixPath("/httpbin").preserveHostHeader().setRequestHeader("Host", "myhost.net"))
							.uri(uri))
					.build();
		}

	}

}
