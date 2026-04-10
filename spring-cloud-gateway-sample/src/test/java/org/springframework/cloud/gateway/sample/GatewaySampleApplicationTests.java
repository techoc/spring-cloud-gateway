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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.config.GatewayMetricsProperties;
import org.springframework.cloud.gateway.test.HttpBinCompatibleController;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.SocketUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * GatewaySampleApplication 的集成测试类。
 * <p>
 * 本测试类对 Spring Cloud Gateway 示例应用进行端到端集成测试，验证以下功能：
 * <ul>
 * <li>基本路由功能</li>
 * <li>请求体读取断言</li>
 * <li>请求体/响应体修改</li>
 * <li>复杂断言组合</li>
 * <li>Kotlin 路由支持</li>
 * <li>Actuator 管理端点</li>
 * <li>指标收集功能</li>
 * </ul>
 * <p>
 * 测试使用随机端口启动应用，并通过 WebTestClient 发送 HTTP 请求验证响应。 同时启动独立的管理端口用于测试 Actuator 端点。
 *
 * @author Spencer Gibb
 */
@SpringBootTest(classes = { GatewaySampleApplicationTests.TestConfig.class }, webEnvironment = RANDOM_PORT,
		properties = "management.server.port=${test.port}")
public class GatewaySampleApplicationTests {

	/**
	 * 管理端点端口，用于测试 Actuator 功能。 在 @BeforeAll 中动态分配可用端口。
	 */
	protected static int managementPort;

	/**
	 * 应用服务器端口，由 Spring Boot 自动注入。
	 */
	@LocalServerPort
	protected int port = 0;

	/**
	 * WebTestClient 实例，用于发送 HTTP 请求和验证响应。
	 */
	protected WebTestClient webClient;

	/**
	 * 基础 URI，格式为 http://localhost:{port}。
	 */
	protected String baseUri;

	/**
	 * 网关指标属性配置，用于获取指标名称前缀。
	 */
	@Autowired
	GatewayMetricsProperties metricsProperties;

	/**
	 * 测试类级别的初始化方法，在所有测试方法执行前运行。
	 * <p>
	 * 查找一个可用的 TCP 端口作为管理端口，并设置到系统属性中。
	 *
	 * @see SocketUtils#findAvailableTcpPort()
	 */
	@BeforeAll
	public static void beforeClass() {
		managementPort = SocketUtils.findAvailableTcpPort();

		System.setProperty("test.port", String.valueOf(managementPort));
	}

	/**
	 * 测试类级别的清理方法，在所有测试方法执行后运行。
	 * <p>
	 * 清除之前设置的系统属性，避免影响其他测试。
	 */
	@AfterAll
	public static void afterClass() {
		System.clearProperty("test.port");
	}

	/**
	 * 测试方法级别的初始化方法，在每个测试方法执行前运行。
	 * <p>
	 * 构建基础 URI 并创建 WebTestClient 实例，设置 10 秒超时。
	 */
	@BeforeEach
	public void setup() {
		baseUri = "http://localhost:" + port;
		this.webClient = WebTestClient.bindToServer().responseTimeout(Duration.ofSeconds(10)).baseUrl(baseUri).build();
	}

	/**
	 * 测试应用上下文是否正确加载。
	 * <p>
	 * 发送 GET 请求到 /get 端点，验证返回 200 OK 状态码。
	 */
	@Test
	public void contextLoads() {
		webClient.get().uri("/get").exchange().expectStatus().isOk();
	}

	/**
	 * 测试读取请求体断言功能。
	 * <p>
	 * 验证当请求体内容为 "hi" 时，路由匹配成功并返回预期的响应头。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的
	 * "read_body_pred" 路由
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void readBodyPredicateStringWorks() {
		webClient.post().uri("/post").header("Host", "www.readbody.org").bodyValue("hi").exchange().expectStatus()
				.isOk().expectHeader().valueEquals("X-TestHeader", "read_body_pred").expectBody(Map.class)
				.consumeWith(result -> assertThat(result.getResponseBody()).containsEntry("data", "hi"));
	}

	/**
	 * 测试请求体修改功能（字符串转换）。
	 * <p>
	 * 验证请求体 "hello" 被转换为大写并重复两次（"HELLOHELLO"）。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的
	 * "rewrite_request_upper" 路由
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void rewriteRequestBodyStringWorks() {
		webClient.post().uri("/post").header("Host", "www.rewriterequestupper.org").bodyValue("hello").exchange()
				.expectStatus().isOk().expectHeader().valueEquals("X-TestHeader", "rewrite_request_upper")
				.expectBody(Map.class)
				.consumeWith(result -> assertThat(result.getResponseBody()).containsEntry("data", "HELLOHELLO"));
	}

	/**
	 * 测试请求体修改功能（对象转换）。
	 * <p>
	 * 验证请求体字符串被转换为 Hello 对象，并序列化为 JSON 格式。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的
	 * "rewrite_request_obj" 路由
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void rewriteRequestBodyObjectWorks() {
		webClient.post().uri("/post").header("Host", "www.rewriterequestobj.org").bodyValue("hello").exchange()
				.expectStatus().isOk().expectHeader().valueEquals("X-TestHeader", "rewrite_request")
				.expectBody(Map.class).consumeWith(result -> assertThat(result.getResponseBody()).containsEntry("data",
						"{\"message\":\"HELLO\"}"));
	}

	/**
	 * 测试响应体修改功能（字符串转换）。
	 * <p>
	 * 验证响应体被转换为大写形式。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的
	 * "rewrite_response_upper" 路由
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void rewriteResponseBodyStringWorks() {
		webClient.post().uri("/post").header("Host", "www.rewriteresponseupper.org").bodyValue("hello").exchange()
				.expectStatus().isOk().expectHeader().valueEquals("X-TestHeader", "rewrite_response_upper")
				.expectBody(Map.class)
				.consumeWith(result -> assertThat(result.getResponseBody()).containsEntry("DATA", "HELLO"));
	}

	/**
	 * 测试空响应体处理功能。
	 * <p>
	 * 验证当响应体为空时，返回默认字符串 "emptybody"。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的
	 * "rewrite_empty_response" 路由
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void rewriteResponseEmptyBodyToStringWorks() {
		webClient.post().uri("/post/empty").header("Host", "www.rewriteemptyresponse.org").exchange().expectStatus()
				.isOk().expectHeader().valueEquals("X-TestHeader", "rewrite_empty_response").expectBody(String.class)
				.consumeWith(result -> assertThat(result.getResponseBody()).isEqualTo("emptybody"));
	}

	/**
	 * 测试空体供应商在非空体时不被调用。
	 * <p>
	 * 验证当响应体存在时，不会触发错误供应商（fail supplier）。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的
	 * "rewrite_response_fail_supplier" 路由
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void emptyBodySupplierNotCalledWhenBodyPresent() {
		webClient.post().uri("/post").header("Host", "www.rewriteresponsewithfailsupplier.org").bodyValue("hello")
				.exchange().expectStatus().isOk().expectHeader()
				.valueEquals("X-TestHeader", "rewrite_response_fail_supplier").expectBody(Map.class)
				.consumeWith(result -> assertThat(result.getResponseBody()).containsEntry("DATA", "HELLO"));
	}

	/**
	 * 测试响应体对象转换功能。
	 * <p>
	 * 验证 Map 类型的响应体被转换为纯文本字符串。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的
	 * "rewrite_response_obj" 路由
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void rewriteResponeBodyObjectWorks() {
		webClient.post().uri("/post").header("Host", "www.rewriteresponseobj.org").bodyValue("hello").exchange()
				.expectStatus().isOk().expectHeader().valueEquals("X-TestHeader", "rewrite_response_obj")
				.expectBody(String.class)
				.consumeWith(result -> assertThat(result.getResponseBody()).isEqualTo("hello"));
	}

	/**
	 * 测试复杂断言组合功能。
	 * <p>
	 * 验证基于 Host 和 Path 的组合断言路由正常工作。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder) 中的第一个路由配置
	 */
	@Test
	public void complexPredicate() {
		webClient.get().uri("/anything/png").header("Host", "www.abc.org").exchange().expectHeader()
				.valueEquals("X-TestHeader", "foobar").expectStatus().isOk();
	}

	/**
	 * 测试 Kotlin 路由配置。
	 * <p>
	 * 验证使用 Kotlin 定义的路由正常工作。
	 * <p>
	 * 注意：在 Java 16+ 版本中禁用此测试，因为 Kotlin 路由可能有兼容性问题。
	 *
	 * @see GatewaySampleApplication#customRouteLocator(RouteLocatorBuilder)
	 * @see DisabledForJreRange
	 */
	@Test
	@DisabledForJreRange(min = JRE.JAVA_16)
	public void routeFromKotlin() {
		webClient.get().uri("/anything/kotlinroute").header("Host", "kotlin.abc.org").exchange().expectHeader()
				.valueEquals("X-TestHeader", "foobar").expectStatus().isOk();
	}

	/**
	 * 测试 Actuator 管理端口。
	 * <p>
	 * 验证独立的 Actuator 管理端口可以正常访问网关路由端点。
	 */
	@Test
	public void actuatorManagementPort() {
		webClient.get().uri("http://localhost:" + managementPort + "/actuator/gateway/routes").exchange().expectStatus()
				.isOk();
	}

	/**
	 * 测试 Actuator 指标端点。
	 * <p>
	 * 验证网关请求指标被正确收集，可以通过 Actuator 端点访问。 首先调用 contextLoads() 产生一些请求，然后查询指标端点验证指标存在。
	 *
	 * @see GatewayMetricsProperties
	 */
	@Test
	public void actuatorMetrics() {
		contextLoads();
		String metricName = metricsProperties.getPrefix() + ".requests";
		webClient.get().uri("http://localhost:" + managementPort + "/actuator/metrics/" + metricName).exchange()
				.expectStatus().isOk().expectBody().consumeWith(i -> {
					String body = new String(i.getResponseBodyContent());
					ObjectMapper mapper = new ObjectMapper();
					try {
						JsonNode actualObj = mapper.readTree(body);
						JsonNode findValue = actualObj.findValue("name");
						assertThat(findValue.asText()).as("Expected to find metric with name gateway.requests")
								.isEqualTo(metricName);
					}
					catch (IOException e) {
						throw new IllegalStateException(e);
					}
				});
	}

	/**
	 * 测试配置类。
	 * <p>
	 * 提供测试所需的 Bean 配置，包括：
	 * <ul>
	 * <li>HttpBinCompatibleController - 模拟 httpbin 服务的控制器</li>
	 * <li>LoadBalancerClient 配置 - 用于负载均衡测试</li>
	 * </ul>
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@LoadBalancerClient(name = "httpbin", configuration = LoadBalancerConfig.class)
	@Import(GatewaySampleApplication.class)
	protected static class TestConfig {

		/**
		 * 创建 HttpBin 兼容控制器 Bean。
		 * @return HttpBinCompatibleController 实例
		 */
		@Bean
		public HttpBinCompatibleController httpBinCompatibleController() {
			return new HttpBinCompatibleController();
		}

	}

	/**
	 * 负载均衡配置类。
	 * <p>
	 * 配置固定服务实例列表供应商，用于测试负载均衡功能。 将 "httpbin" 服务映射到本地测试服务器端口。
	 */
	protected static class LoadBalancerConfig {

		/**
		 * 本地服务器端口，由 Spring Boot 自动注入。
		 */
		@LocalServerPort
		int port;

		/**
		 * 创建固定服务实例列表供应商。
		 * <p>
		 * 返回一个包含单个服务实例的供应商，该实例指向本地测试服务器。
		 * @param env Spring 环境对象
		 * @return ServiceInstanceListSupplier 实例
		 */
		@Bean
		public ServiceInstanceListSupplier fixedServiceInstanceListSupplier(Environment env) {
			return ServiceInstanceListSuppliers.from("httpbin",
					new DefaultServiceInstance("httpbin-1", "httpbin", "localhost", port, false));
		}

	}

}
