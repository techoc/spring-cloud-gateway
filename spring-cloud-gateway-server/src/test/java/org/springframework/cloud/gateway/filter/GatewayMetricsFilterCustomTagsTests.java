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
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.config.GatewayMetricsProperties.DEFAULT_PREFIX;

/**
 * GatewayMetricsFilter 自定义标签测试类
 *
 * 本测试类用于验证 GatewayMetricsFilter 支持自定义标签的功能，包括： -
 * 验证默认标签（outcome、status、httpStatusCode、httpMethod、routeId、routeUri）正确记录 - 验证通过
 * GatewayTagsProvider 配置的自定义标签（custom1、custom2）正确记录
 *
 * 通过实现自定义的 GatewayTagsProvider bean，可以为指标添加额外的业务相关标签， 用于更细粒度的指标分析和监控。
 *
 * @author Ingyu Hwang
 * @author 译者：Spring Cloud Gateway 团队
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
class GatewayMetricsFilterCustomTagsTests extends BaseWebClientTests {

	private static final String REQUEST_METRICS_NAME = DEFAULT_PREFIX + ".requests";

	@Autowired
	private MeterRegistry meterRegistry;

	@Value("${test.uri}")
	private String testUri;

	/**
	 * 测试网关请求指标包含自定义标签 验证：除了默认标签外，还应包含 custom1=tag1 和 custom2=tag2
	 */
	@Test
	void gatewayRequestsMeterFilterHasCustomTags() {
		testClient.get().uri("/headers").exchange().expectStatus().isOk();

		// default tags
		assertMetricsContainsTag("outcome", HttpStatus.Series.SUCCESSFUL.name());
		assertMetricsContainsTag("status", HttpStatus.OK.name());
		assertMetricsContainsTag("httpStatusCode", String.valueOf(HttpStatus.OK.value()));
		assertMetricsContainsTag("httpMethod", HttpMethod.GET.toString());
		assertMetricsContainsTag("routeId", "default_path_to_httpbin");
		assertMetricsContainsTag("routeUri", testUri);

		// custom tags
		assertMetricsContainsTag("custom1", "tag1");
		assertMetricsContainsTag("custom2", "tag2");
	}

	/**
	 * 断言指定标签键值对存在于请求指标中
	 * @param tagKey 标签键名
	 * @param tagValue 标签值
	 */
	private void assertMetricsContainsTag(String tagKey, String tagValue) {
		assertThat(this.meterRegistry.get(REQUEST_METRICS_NAME).tag(tagKey, tagValue).timer().count()).isEqualTo(1);
	}

	/**
	 * 自定义测试配置类
	 *
	 * 定义一个自定义的 GatewayTagsProvider bean， 返回包含 custom1 和 custom2 两个自定义标签的 Tags。
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	static class CustomConfig {

		@Bean
		GatewayTagsProvider customGatewayTagsProvider() {
			return exchange -> Tags.of("custom1", "tag1", "custom2", "tag2");
		}

	}

}
