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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory.NameConfig;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;

/**
 * RemoveRequestHeaderGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 RemoveRequestHeaderGatewayFilterFactory 的请求头移除功能，包括： - 测试移除指定的请求头 - 测试
 * toString 格式输出
 *
 * RemoveRequestHeaderGatewayFilterFactory 负责在请求转发前移除指定的 HTTP 请求头， 常用于清理敏感信息或不需要的请求头。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RemoveRequestHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试移除请求头的功能 验证：X-Request-Foo 请求头应被移除，不出现在后端请求中
	 */
	@Test
	public void removeRequestHeaderFilterWorks() {
		testClient.get().uri("/headers").header("Host", "www.removerequestheader.org").header("X-Request-Foo", "Bar")
				.exchange().expectStatus().isOk().expectBody(Map.class).consumeWith(result -> {
					Map<String, Object> headers = getMap(result.getResponseBody(), "headers");
					assertThat(headers).doesNotContainKey("X-Request-Foo");
				});
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的名称
	 */
	@Test
	public void toStringFormat() {
		NameConfig config = new NameConfig();
		config.setName("myname");
		GatewayFilter filter = new RemoveRequestHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
