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

package org.springframework.cloud.gateway.actuate;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.assertj.core.util.Maps;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * GatewayControllerEndpointTests - Gateway控制器端点测试类
 *
 * 本测试类用于验证Spring Cloud Gateway Actuator端点的功能，包括： - 路由刷新功能（/actuator/gateway/refresh） -
 * 路由列表查询（/actuator/gateway/routes） - 单个路由详情查询 - 路由过滤器列表查询（/actuator/gateway/routefilters）
 * - 路由谓词列表查询（/actuator/gateway/routepredicates） - 动态创建路由（POST
 * /actuator/gateway/routes/{id}） - 动态删除路由（DELETE /actuator/gateway/routes/{id}） -
 * 路由定义的完整生命周期测试
 *
 * @author test
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "management.endpoints.web.exposure.include=*",
		"spring.cloud.gateway.actuator.verbose.enabled=true" }, webEnvironment = RANDOM_PORT)
public class GatewayControllerEndpointTests {

	@Autowired
	WebTestClient testClient;

	@LocalServerPort
	int port;

	@Test
	public void testRefresh() {
		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/refresh").exchange().expectStatus()
				.isOk();
	}

	/**
	 * testRoutes - 测试获取所有路由列表
	 *
	 * 验证GET请求到/actuator/gateway/routes端点能够返回所有已配置的路由信息 期望响应状态码为200 OK，且响应体包含非空的路由列表
	 */
	@Test
	public void testRoutes() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routes").exchange().expectStatus().isOk()
				.expectBodyList(Map.class).consumeWith(result -> {
					List<Map> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testGetSpecificRoute() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routes/test-service").exchange()
				.expectStatus().isOk().expectBodyList(Map.class).consumeWith(result -> {
					List<Map> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotNull();
					assertThat(responseBody.size()).isEqualTo(1);
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testRouteReturnsMetadata() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routes/route_with_metadata").exchange()
				.expectStatus().isOk().expectBody().jsonPath("$.metadata")
				.value(map -> assertThat((Map<String, Object>) map).hasSize(3)
						.containsEntry("optionName", "OptionValue").containsEntry("iAmNumber", 1)
						.containsEntry("compositeObject", Maps.newHashMap("name", "value")));
	}

	@Test
	public void testRouteFilters() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routefilters").exchange().expectStatus()
				.isOk().expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testRoutePredicates() {
		testClient.get().uri("http://localhost:" + port + "/actuator/gateway/routepredicates").exchange().expectStatus()
				.isOk().expectBody(Map.class).consumeWith(result -> {
					Map<?, ?> responseBody = result.getResponseBody();
					assertThat(responseBody).isNotEmpty();
				});
	}

	@Test
	public void testRouteDelete() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");

		testRouteDefinition.setPredicates(Arrays.asList(methodRoutePredicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route-to-be-delete")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isCreated();

		testClient.delete().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route-to-be-delete")
				.exchange().expectStatus().isOk().expectBody(ResponseEntity.class).consumeWith(result -> {
					HttpStatus httpStatus = result.getStatus();
					Assert.assertEquals(HttpStatus.OK, httpStatus);
				});
	}

	@Test
	public void testPostValidRouteDefinition() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition prefixPathFilterDefinition = new FilterDefinition("PrefixPath=/test-path");
		FilterDefinition redirectToFilterDefinition = new FilterDefinition("RemoveResponseHeader=Sensitive-Header");
		FilterDefinition testFilterDefinition = new FilterDefinition("TestFilter");
		testRouteDefinition.setFilters(
				Arrays.asList(prefixPathFilterDefinition, redirectToFilterDefinition, testFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition("Host=myhost.org");
		PredicateDefinition methodRoutePredicateDefinition = new PredicateDefinition("Method=GET");
		PredicateDefinition testPredicateDefinition = new PredicateDefinition("Test=value");
		testRouteDefinition.setPredicates(
				Arrays.asList(hostRoutePredicateDefinition, methodRoutePredicateDefinition, testPredicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isCreated();
	}

	@Test
	public void testPostValidShortcutRouteDefinition() {
		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setId(
				"gatewaywithgrpcfiltertest-0-104014-8916263311295787431172436062-test-gateway-tls-client-mapping-0");
		testRouteDefinition.setUri(URI.create("https://localhost:8095"));
		testRouteDefinition.setOrder(0);
		testRouteDefinition.setMetadata(Collections.emptyMap());

		FilterDefinition longFilterDefinition = new FilterDefinition();
		FilterDefinition stripPrefix = new FilterDefinition();
		stripPrefix.setName("StripPrefix");
		stripPrefix.addArg("_genkey_0", "1");

		longFilterDefinition.setName("JsonToGrpc");
		longFilterDefinition.addArg("_genkey_0", "file:src/main/proto/hello.pb");
		longFilterDefinition.addArg("_genkey_1", "file:src/main/proto/hello.proto");
		longFilterDefinition.addArg("_genkey_2", "HelloService");
		longFilterDefinition.addArg("_genkey_3", "hello");
		testRouteDefinition.setFilters(Collections.singletonList(longFilterDefinition));

		PredicateDefinition hostRoutePredicateDefinition = new PredicateDefinition();
		hostRoutePredicateDefinition.setName("Path");
		hostRoutePredicateDefinition.addArg("_genkey_0", "/json/hello");
		testRouteDefinition.setPredicates(Arrays.asList(hostRoutePredicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isCreated();
	}

	@Test
	public void testPostRouteWithNotExistingFilter() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		FilterDefinition filterDefinition = new FilterDefinition("NotExistingFilter=test-config");
		testRouteDefinition.setFilters(Collections.singletonList(filterDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isBadRequest().expectBody().jsonPath("$.message")
				.isEqualTo("Invalid FilterDefinition: [NotExistingFilter]");
	}

	@Test
	public void testPostRouteWithUriWithoutScheme() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("example.org"));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/no-scheme-test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isBadRequest().expectBody().jsonPath("$.message")
				.isEqualTo("The URI format [example.org] is incorrect, scheme can not be empty");
	}

	@Test
	public void testPostRouteWithUri() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(null);

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/no-scheme-test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isBadRequest().expectBody().jsonPath("$.message").isEqualTo("The URI can not be empty");
	}

	@Test
	public void testPostRouteWithNotExistingPredicate() {

		RouteDefinition testRouteDefinition = new RouteDefinition();
		testRouteDefinition.setUri(URI.create("http://example.org"));

		PredicateDefinition predicateDefinition = new PredicateDefinition("NotExistingPredicate=test-config");
		testRouteDefinition.setPredicates(Collections.singletonList(predicateDefinition));

		testClient.post().uri("http://localhost:" + port + "/actuator/gateway/routes/test-route")
				.accept(MediaType.APPLICATION_JSON).body(BodyInserters.fromValue(testRouteDefinition)).exchange()
				.expectStatus().isBadRequest().expectBody().jsonPath("$.message")
				.isEqualTo("Invalid PredicateDefinition: [NotExistingPredicate]");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	static class TestConfig {

		@Bean
		RouteLocator testRouteLocator(RouteLocatorBuilder routeLocatorBuilder) {
			return routeLocatorBuilder.routes()
					.route("test-service", r -> r.path("/test-service/**").uri("lb://test-service")).build();
		}

		@Bean
		public TestFilterGatewayFilterFactory customGatewayFilterFactory() {
			return new TestFilterGatewayFilterFactory();
		}

		@Bean
		public TestRoutePredicateFactory customGatewayPredicateFactory() {
			return new TestRoutePredicateFactory(Object.class);
		}

	}

	/**
	 * TestFilterGatewayFilterFactory - 测试用自定义过滤器工厂
	 *
	 * 继承AbstractGatewayFilterFactory，用于测试Gateway对自定义过滤器的注册和识别
	 */
	private static class TestFilterGatewayFilterFactory extends AbstractGatewayFilterFactory {

		/**
		 * apply - 创建测试用GatewayFilter
		 * @param config 过滤器配置（此处未使用，返回null）
		 * @return GatewayFilter实例
		 */
		@Override
		public GatewayFilter apply(Object config) {
			return null;
		}

	}

	/**
	 * TestRoutePredicateFactory - 测试用自定义谓词工厂
	 *
	 * 继承AbstractRoutePredicateFactory，用于测试Gateway对自定义谓词的注册和识别
	 */
	private static class TestRoutePredicateFactory extends AbstractRoutePredicateFactory {

		/**
		 * 构造函数
		 * @param configClass 配置类类型
		 */
		TestRoutePredicateFactory(Class configClass) {
			super(configClass);
		}

		/**
		 * apply - 创建测试用谓词
		 * @param config 谓词配置（此处未使用）
		 * @return 始终返回true的谓词
		 */
		@Override
		public Predicate<ServerWebExchange> apply(Object config) {
			return (GatewayPredicate) serverWebExchange -> true;
		}

	}

}
