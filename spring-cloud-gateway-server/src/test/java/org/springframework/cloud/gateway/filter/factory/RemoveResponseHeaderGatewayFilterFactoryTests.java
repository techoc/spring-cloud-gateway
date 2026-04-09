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

/**
 * RemoveResponseHeaderGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 RemoveResponseHeaderGatewayFilterFactory 的响应头移除功能，包括： - 测试移除指定的响应头 - 测试
 * toString 格式输出
 *
 * RemoveResponseHeaderGatewayFilterFactory 负责在响应返回前移除指定的 HTTP 响应头，
 * 常用于移除敏感信息（如后端服务器信息）或避免重复的响应头。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class RemoveResponseHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	/**
	 * 测试移除响应头的功能 验证：X-Request-Foo 响应头应不存在于响应中
	 */
	@Test
	public void removeResponseHeaderFilterWorks() {
		testClient.get().uri("/headers").header("Host", "www.removereresponseheader.org").exchange().expectStatus()
				.isOk().expectHeader().doesNotExist("X-Request-Foo");
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含配置的名称
	 */
	@Test
	public void toStringFormat() {
		NameConfig config = new NameConfig();
		config.setName("myname");
		GatewayFilter filter = new RemoveResponseHeaderGatewayFilterFactory().apply(config);
		assertThat(filter.toString()).contains("myname");
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig {

	}

}
