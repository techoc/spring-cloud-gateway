/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.sample.GatewaySampleApplicationTests.TestConfig;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.SocketUtils;

import java.time.Duration;

/**
 * 无指标依赖的 GatewaySampleApplication 测试类。
 * <p>
 * 本测试类验证当 Micrometer 和 Spring Boot Actuator 依赖被排除时，
 * 网关应用仍然可以正常工作，并且模拟的 Actuator 端点可以正常返回。
 * <p>
 * 使用 {@link ModifiedClassPathRunner} 和 {@link ClassPathExclusions} 注解
 * 在测试运行时从类路径中排除指定的依赖 JAR 文件。
 * <p>
 * 主要测试场景：
 * <ul>
 *   <li>应用在无指标依赖时正常启动</li>
 *   <li>模拟的 Actuator 指标端点返回预期响应</li>
 *   <li>基本路由功能正常工作</li>
 * </ul>
 *
 * @see ModifiedClassPathRunner
 * @see ClassPathExclusions
 * @see GatewaySampleApplication#HELLO_FROM_FAKE_ACTUATOR_METRICS_GATEWAY_REQUESTS
 */
@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({ "micrometer-*.jar", "spring-boot-actuator-*.jar", "spring-boot-actuator-autoconfigure-*.jar" })
@DirtiesContext
public class GatewaySampleApplicationWithoutMetricsTests {

	/**
	 * 应用服务器端口，在 @BeforeClass 中动态分配。
	 */
	static protected int port;

	/**
	 * WebTestClient 实例，用于发送 HTTP 请求和验证响应。
	 */
	protected WebTestClient webClient;

	/**
	 * 基础 URI，格式为 http://localhost:{port}。
	 */
	protected String baseUri;

	/**
	 * 测试类级别的初始化方法，在所有测试方法执行前运行。
	 * <p>
	 * 查找一个可用的 TCP 端口作为应用端口，并设置到系统属性中。
	 *
	 * @see SocketUtils#findAvailableTcpPort()
	 */
	@BeforeClass
	public static void beforeClass() {
		port = SocketUtils.findAvailableTcpPort();
		System.setProperty("server.port", Integer.toString(port));
	}

	/**
	 * 测试类级别的清理方法，在所有测试方法执行后运行。
	 * <p>
	 * 清除之前设置的系统属性，避免影响其他测试。
	 */
	@AfterClass
	public static void afterClass() {
		System.clearProperty("server.port");
	}

	/**
	 * 测试方法级别的初始化方法，在每个测试方法执行前运行。
	 * <p>
	 * 构建基础 URI 并创建 WebTestClient 实例，设置 10 秒超时。
	 */
	@Before
	public void setup() {
		baseUri = "http://localhost:" + port;
		this.webClient = WebTestClient.bindToServer().responseTimeout(Duration.ofSeconds(10)).baseUrl(baseUri).build();
	}

	/**
	 * 初始化 Spring 应用上下文。
	 * <p>
	 * 使用 SpringApplicationBuilder 创建响应式 Web 应用，
	 * 加载 GatewaySampleApplication 和指定的配置类。
	 *
	 * @param config 配置类，用于加载额外的 Bean 定义
	 * @return 可配置的应用上下文实例
	 */
	protected ConfigurableApplicationContext init(Class<?> config) {
		return new SpringApplicationBuilder().web(WebApplicationType.REACTIVE)
				.sources(GatewaySampleApplication.class, config).run();
	}

	/**
	 * 测试无指标依赖时的 Actuator 端点行为。
	 * <p>
	 * 验证当 Micrometer 和 Actuator 依赖被排除时：
	 * <ol>
	 *   <li>应用可以正常启动并处理请求</li>
	 *   <li>模拟的 Actuator 指标端点返回预定义的响应</li>
	 * </ol>
	 * <p>
	 * 预期响应为 {@link GatewaySampleApplication#HELLO_FROM_FAKE_ACTUATOR_METRICS_GATEWAY_REQUESTS}。
	 *
	 * @see GatewaySampleApplication#testWhenMetricPathIsNotMeet()
	 */
	@Test
	public void actuatorMetrics() {
		init(TestConfig.class);
		webClient.get().uri("/get").exchange().expectStatus().isOk();
		webClient.get().uri("http://localhost:" + port + "/actuator/metrics/spring.cloud.gateway.requests").exchange()
				.expectStatus().isOk().expectBody(String.class)
				.isEqualTo(GatewaySampleApplication.HELLO_FROM_FAKE_ACTUATOR_METRICS_GATEWAY_REQUESTS);
	}

}
