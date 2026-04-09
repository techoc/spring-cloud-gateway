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

package org.springframework.cloud.gateway.filter;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.config.GatewayMetricsProperties.DEFAULT_PREFIX;

/**
 * GatewayMetricsFilter 集成测试类
 *
 * 本测试类用于验证 GatewayMetricsFilter 的指标收集功能，包括： -
 * 验证请求指标包含正确的标签信息（outcome、status、httpStatusCode、httpMethod、routeId、routeUri） -
 * 验证对异常目标的请求也能正确记录指标 - 验证自定义 HTTP 状态码的指标记录
 *
 * GatewayMetricsFilter 是 Spring Cloud Gateway 的指标过滤器， 用于收集和记录网关请求的各种指标数据，支持 Micrometer
 * 集成。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
public class GatewayMetricsFilterTests extends BaseWebClientTests {

	private static final String REQUEST_METRICS_NAME = DEFAULT_PREFIX + ".requests";

	@Autowired
	private MeterRegistry meterRegistry;

	@Value("${test.uri}")
	private String testUri;

	/**
	 * 测试网关请求指标是否包含正确的标签 验证：成功请求应包含
	 * outcome=SUCCESSFUL、status=OK、httpStatusCode=200、httpMethod=GET、
	 * routeId=default_path_to_httpbin、routeUri 等标签
	 */
	@Test
	public void gatewayRequestsMeterFilterHasTags() {
		testClient.get().uri("/headers").exchange().expectStatus().isOk();
		assertMetricsContainsTag("outcome", HttpStatus.Series.SUCCESSFUL.name());
		assertMetricsContainsTag("status", HttpStatus.OK.name());
		assertMetricsContainsTag("httpStatusCode", String.valueOf(HttpStatus.OK.value()));
		assertMetricsContainsTag("httpMethod", HttpMethod.GET.toString());
		assertMetricsContainsTag("routeId", "default_path_to_httpbin");
		assertMetricsContainsTag("routeUri", testUri);
	}

	/**
	 * 测试对错误目标URI的请求是否正确记录指标 验证：5xx 错误响应应包含
	 * outcome=SERVER_ERROR、status=INTERNAL_SERVER_ERROR 等标签
	 */
	@Test
	public void gatewayRequestsMeterFilterHasTagsForBadTargetUri() {
		testClient.get().uri("/badtargeturi").exchange().expectStatus().is5xxServerError();
		assertMetricsContainsTag("outcome", HttpStatus.Series.SERVER_ERROR.name());
		assertMetricsContainsTag("status", HttpStatus.INTERNAL_SERVER_ERROR.name());
		assertMetricsContainsTag("httpStatusCode", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
		assertMetricsContainsTag("httpMethod", HttpMethod.GET.toString());
		assertMetricsContainsTag("routeId", "default_path_to_httpbin");
		assertMetricsContainsTag("routeUri", testUri);
	}

	/**
	 * 测试 SetStatus 过滤器设置的自定义状态码是否正确记录到指标中 验证：自定义状态码 432 应正确记录，outcome 应为 CUSTOM，status 应为
	 * 432
	 */
	@Test
	public void hasMetricsForSetStatusFilter() {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.HOST, "www.setcustomstatusmetrics.org");
		// cannot use netty client since we cannot read custom http status
		ResponseEntity<String> response = new TestRestTemplate().exchange(baseUri + "/headers", HttpMethod.POST,
				new HttpEntity<>(headers), String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(432);
		assertMetricsContainsTag("outcome", "CUSTOM");
		assertMetricsContainsTag("status", "432");
		assertMetricsContainsTag("routeId", "test_custom_http_status_metrics");
		assertMetricsContainsTag("routeUri", testUri);
		assertMetricsContainsTag("httpStatusCode", "432");
		assertMetricsContainsTag("httpMethod", HttpMethod.POST.toString());
	}

	/**
	 * 断言指定标签键值对存在于请求指标中
	 * @param tagKey 标签键名
	 * @param tagValue 标签值
	 */
	private void assertMetricsContainsTag(String tagKey, String tagValue) {
		// @formatter:off
		assertThat(this.meterRegistry.get(REQUEST_METRICS_NAME).tag(tagKey, tagValue)
				.timer().count())
				.as("Wrong value for metric %s: %s", tagKey, tagValue)
				.isGreaterThanOrEqualTo(1);
		// @formatter:on
	}

	/**
	 * 自定义测试配置类
	 *
	 * 用于配置带有自定义 HTTP 状态码（432）的路由，以测试 SetStatus 过滤器 的指标记录功能。
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@RestController
	@Import(DefaultTestConfig.class)
	public static class CustomConfig {

		@Value("${test.uri}")
		protected String testUri;

		@Bean
		public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("test_custom_http_status_metrics",
							r -> r.host("*.setcustomstatusmetrics.org").filters(f -> f.setStatus(432)).uri(testUri))
					.build();
		}

		@GetMapping("/httpbin/badtargeturi")
		public String exception() {
			throw new RuntimeException("an error");
		}

	}

}
