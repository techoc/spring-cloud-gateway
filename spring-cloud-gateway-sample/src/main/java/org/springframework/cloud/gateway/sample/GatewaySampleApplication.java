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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Spring Cloud Gateway 示例应用程序主类。
 * <p>
 * 本类展示了如何使用 Spring Cloud Gateway 构建一个功能完整的 API 网关。
 * 包含以下功能示例：
 * <ul>
 *   <li>基于主机名和路径的路由配置</li>
 *   <li>请求/响应体修改</li>
 *   <li>限流控制（Token Bucket 算法）</li>
 *   <li>自定义响应头</li>
 *   <li>路径重写</li>
 *   <li>模拟 Actuator 端点</li>
 * </ul>
 * <p>
 * 使用方式：运行 main 方法启动应用，默认监听 8080 端口。
 * 可以通过 application.yml 或命令行参数配置目标 URI（默认指向 httpbin.org）。
 *
 * @author Spencer Gibb
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(AdditionalRoutesImportSelector.class)
public class GatewaySampleApplication {

	/**
	 * 模拟 Actuator 指标端点的响应内容。
	 * 用于测试当实际指标端点不可用时返回的替代响应。
	 */
	public static final String HELLO_FROM_FAKE_ACTUATOR_METRICS_GATEWAY_REQUESTS = "hello from fake /actuator/metrics/spring.cloud.gateway.requests";

	/**
	 * 目标服务的 URI，通过配置文件注入。
	 * 默认值为 http://httpbin.org:80，可用于测试。
	 * 可以通过设置 test.uri 属性覆盖此值。
	 */
	@Value("${test.uri:http://httpbin.org:80}")
	String uri;

	/**
	 * 应用程序入口方法。
	 *
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(GatewaySampleApplication.class, args);
	}

	/**
	 * 配置自定义路由定位器，定义所有路由规则。
	 * <p>
	 * 本方法展示了多种路由配置模式：
	 * <ul>
	 *   <li>基于主机名和路径的组合匹配</li>
	 *   <li>读取请求体进行断言</li>
	 *   <li>修改请求体和响应体</li>
	 *   <li>限流过滤器配置</li>
	 * </ul>
	 *
	 * @param builder 路由定位器构建器，用于创建路由规则
	 * @return 配置好的路由定位器
	 */
	@Bean
	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		//@formatter:off
		// String uri = "http://httpbin.org:80";
		// String uri = "http://localhost:9080";
		return builder.routes()
				.route(r -> r.host("**.abc.org").and().path("/anything/png")
					.filters(f ->
							f.prefixPath("/httpbin")
									.addResponseHeader("X-TestHeader", "foobar"))
					.uri(uri)
				)
				.route("read_body_pred", r -> r.host("*.readbody.org")
						.and().readBody(String.class,
										s -> s.trim().equalsIgnoreCase("hi"))
					.filters(f -> f.prefixPath("/httpbin")
							.addResponseHeader("X-TestHeader", "read_body_pred")
					).uri(uri)
				)
				.route("rewrite_request_obj", r -> r.host("*.rewriterequestobj.org")
					.filters(f -> f.prefixPath("/httpbin")
							.addResponseHeader("X-TestHeader", "rewrite_request")
							.modifyRequestBody(String.class, Hello.class, MediaType.APPLICATION_JSON_VALUE,
									(exchange, s) -> {
										return Mono.just(new Hello(s.toUpperCase()));
									})
					).uri(uri)
				)
				.route("rewrite_request_upper", r -> r.host("*.rewriterequestupper.org")
					.filters(f -> f.prefixPath("/httpbin")
							.addResponseHeader("X-TestHeader", "rewrite_request_upper")
							.modifyRequestBody(String.class, String.class,
									(exchange, s) -> {
										return Mono.just(s.toUpperCase() + s.toUpperCase());
									})
					).uri(uri)
				)
				.route("rewrite_response_upper", r -> r.host("*.rewriteresponseupper.org")
					.filters(f -> f.prefixPath("/httpbin")
							.addResponseHeader("X-TestHeader", "rewrite_response_upper")
							.modifyResponseBody(String.class, String.class,
									(exchange, s) -> {
										return Mono.just(s.toUpperCase());
									})
					).uri(uri)
				)
				.route("rewrite_empty_response", r -> r.host("*.rewriteemptyresponse.org")
					.filters(f -> f.prefixPath("/httpbin")
							.addResponseHeader("X-TestHeader", "rewrite_empty_response")
							.modifyResponseBody(String.class, String.class,
									(exchange, s) -> {
										if (s == null) {
											return Mono.just("emptybody");
										}
										return Mono.just(s.toUpperCase());
									})

					).uri(uri)
				)
				.route("rewrite_response_fail_supplier", r -> r.host("*.rewriteresponsewithfailsupplier.org")
					.filters(f -> f.prefixPath("/httpbin")
							.addResponseHeader("X-TestHeader", "rewrite_response_fail_supplier")
							.modifyResponseBody(String.class, String.class,
									(exchange, s) -> {
										if (s == null) {
											return Mono.error(new IllegalArgumentException("this should not happen"));
										}
										return Mono.just(s.toUpperCase());
									})
					).uri(uri)
				)
				.route("rewrite_response_obj", r -> r.host("*.rewriteresponseobj.org")
					.filters(f -> f.prefixPath("/httpbin")
							.addResponseHeader("X-TestHeader", "rewrite_response_obj")
							.modifyResponseBody(Map.class, String.class, MediaType.TEXT_PLAIN_VALUE,
									(exchange, map) -> {
										Object data = map.get("data");
										return Mono.just(data.toString());
									})
							.setResponseHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE)
					).uri(uri)
				)
				.route(r -> r.path("/image/webp")
					.filters(f ->
							f.prefixPath("/httpbin")
									.addResponseHeader("X-AnotherHeader", "baz"))
					.uri(uri)
				)
				.route(r -> r.order(-1)
					.host("**.throttle.org").and().path("/get")
					.filters(f -> f.prefixPath("/httpbin")
								.filter(new ThrottleGatewayFilter()
								.setCapacity(1)
								.setRefillTokens(1)
								.setRefillPeriod(10)
								.setRefillUnit(TimeUnit.SECONDS)))
					.uri(uri)
				)
				.build();
		//@formatter:on
	}

	/**
	 * 配置测试用的函数式路由。
	 * <p>
	 * 当访问 /testfun 路径时，返回 "hello" 字符串响应。
	 * 展示了 Spring WebFlux 的函数式编程风格路由配置。
	 *
	 * @return 配置好的路由器函数
	 */
	@Bean
	public RouterFunction<ServerResponse> testFunRouterFunction() {
		RouterFunction<ServerResponse> route = RouterFunctions.route(RequestPredicates.path("/testfun"),
				request -> ServerResponse.ok().body(BodyInserters.fromValue("hello")));
		return route;
	}

	/**
	 * 配置模拟的 Actuator 指标端点。
	 * <p>
	 * 当访问 /actuator/metrics/spring.cloud.gateway.requests 时，
	 * 返回预定义的模拟响应，用于测试场景。
	 *
	 * @return 配置好的路由器函数
	 */
	@Bean
	public RouterFunction<ServerResponse> testWhenMetricPathIsNotMeet() {
		RouterFunction<ServerResponse> route = RouterFunctions.route(
				RequestPredicates.path("/actuator/metrics/spring.cloud.gateway.requests"), request -> ServerResponse
						.ok().body(BodyInserters.fromValue(HELLO_FROM_FAKE_ACTUATOR_METRICS_GATEWAY_REQUESTS)));
		return route;
	}

	/**
	 * 内部类，用于请求体修改示例。
	 * <p>
	 * 表示一个简单的问候消息对象，包含 message 字段。
	 * 用于演示如何将 String 类型的请求体转换为 JSON 对象。
	 */
	static class Hello {

		/**
		 * 问候消息内容。
		 */
		String message;

		/**
		 * 默认构造方法，用于 JSON 反序列化。
		 */
		Hello() {
		}

		/**
		 * 带参数的构造方法。
		 *
		 * @param message 问候消息内容
		 */
		Hello(String message) {
			this.message = message;
		}

		/**
		 * 获取问候消息。
		 *
		 * @return 消息内容
		 */
		public String getMessage() {
			return message;
		}

		/**
		 * 设置问候消息。
		 *
		 * @param message 消息内容
		 */
		public void setMessage(String message) {
			this.message = message;
		}

	}

}
