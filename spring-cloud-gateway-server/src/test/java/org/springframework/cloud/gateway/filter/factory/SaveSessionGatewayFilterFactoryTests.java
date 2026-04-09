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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.session.WebSessionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * SaveSessionGatewayFilterFactory 集成测试类
 *
 * 本测试类用于验证 SaveSessionGatewayFilterFactory 的会话保存功能，包括： - 测试 Web 请求触发会话保存操作 - 测试 toString
 * 格式输出
 *
 * SaveSessionGatewayFilterFactory 负责强制保存 Web 会话， 确保在请求转发前将会话状态持久化到会话存储中。
 *
 * @author Greg Turnquist
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "save-session-web-filter")
public class SaveSessionGatewayFilterFactoryTests extends BaseWebClientTests {

	static WebSession mockWebSession = mock(WebSession.class);

	/**
	 * 测试 Web 请求应触发会话保存操作 验证：模拟的 WebSession.save() 方法应被调用
	 */
	@Test
	public void webCallShouldTriggerWebSessionSaveAction() {

		when(mockWebSession.getAttributes()).thenReturn(new HashMap<>());
		when(mockWebSession.save()).thenReturn(Mono.empty());

		Mono<Map> result = webClient.get().uri("/get").retrieve().bodyToMono(Map.class);

		StepVerifier.create(result).consumeNextWith(response -> {
			// Don't care about data, just need to catch signal
		}).expectComplete().verify(Duration.ofMinutes(10));

		verify(mockWebSession).save();
	}

	/**
	 * 测试过滤器的 toString 格式输出 验证：toString 应包含 SaveSession
	 */
	@Test
	public void toStringFormat() {
		GatewayFilter filter = new SaveSessionGatewayFilterFactory().apply("");
		assertThat(filter.toString()).contains("SaveSession");
	}

	/**
	 * 测试配置类
	 *
	 * 提供模拟的 WebSessionManager bean，用于测试
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class TestConfig {

		@Bean
		WebSessionManager webSessionManager() {
			return exchange -> Mono.just(mockWebSession);
		}

	}

}
