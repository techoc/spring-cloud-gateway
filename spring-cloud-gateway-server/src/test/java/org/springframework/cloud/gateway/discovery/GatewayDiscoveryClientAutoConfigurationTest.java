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

package org.springframework.cloud.gateway.discovery;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REPLACEMENT_KEY;

/**
 * GatewayDiscoveryClientAutoConfigurationTest - 服务发现自动配置测试类
 *
 * 本测试类验证GatewayDiscoveryClientAutoConfiguration的默认RewritePath过滤器配置
 * 确保自动生成的路由能够正确处理不同路径格式的请求转发
 *
 * @author test
 */
public class GatewayDiscoveryClientAutoConfigurationTest {

	/** 测试用服务ID */
	private static final String SERVICE_ID = "foo";

	/** 基础URI路径 */
	private static final String BASE_URI = "/" + SERVICE_ID;

	/** SpEL表达式解析器 */
	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	/** SpEL评估上下文 */
	private static final SimpleEvaluationContext CONTEXT = SimpleEvaluationContext.forReadOnlyDataBinding()
			.withInstanceMethods().build();

	/** 默认的RewritePath过滤器定义 */
	private static final FilterDefinition DEFAULT_FILTER = GatewayDiscoveryClientAutoConfiguration.initFilters().get(0);

	/** 模拟的服务实例 */
	private ServiceInstance serviceInstance;

	/**
	 * buildServiceInstance - 构建模拟服务实例
	 *
	 * 在每个测试方法前创建模拟的ServiceInstance
	 */
	@BeforeEach
	public void buildServiceInstance() {
		this.serviceInstance = mock(ServiceInstance.class);
		when(this.serviceInstance.getServiceId()).thenReturn(SERVICE_ID);
	}

	/**
	 * defaultRewritePathShouldHandleEmptyRemainingWithoutSlash - 测试无剩余路径（无尾部斜杠）
	 *
	 * 验证请求路径 "/foo" 会被正确重写为 "/"
	 */
	@Test
	public void defaultRewritePathShouldHandleEmptyRemainingWithoutSlash() {
		String expectedRemotePath = "/";

		final String result = replace(BASE_URI);

		assertThat(result).isEqualTo(expectedRemotePath);
	}

	/**
	 * defaultRewritePathShouldHandleEmptyRemainingWithSlash - 测试无剩余路径（有尾部斜杠）
	 *
	 * 验证请求路径 "/foo/" 会被保留为 "/foo/"
	 */
	@Test
	public void defaultRewritePathShouldHandleEmptyRemainingWithSlash() {
		String extraUri = "/";

		final String result = replace(BASE_URI + extraUri);

		assertThat(result).isEqualTo(extraUri);
	}

	/**
	 * defaultRewritePathShouldHandleNonEmptyRemainingPath - 测试有剩余路径
	 *
	 * 验证请求路径 "/foo/some/additional/uri" 会被正确重写为 "/some/additional/uri"
	 */
	@Test
	public void defaultRewritePathShouldHandleNonEmptyRemainingPath() {
		String extraUri = "/some/additional/uri";

		final String result = replace(BASE_URI + extraUri);

		assertThat(result).isEqualTo(extraUri);
	}

	/**
	 * replace - 执行路径重写
	 * @param enteringPath 进入的请求路径
	 * @return 重写后的路径
	 */
	private String replace(String enteringPath) {
		return enteringPath.replaceAll(evaluateExpression(DEFAULT_FILTER.getArgs().get(REGEXP_KEY)),
				evaluateExpression(DEFAULT_FILTER.getArgs().get(REPLACEMENT_KEY)));
	}

	/**
	 * evaluateExpression - 评估SpEL表达式
	 * @param expression SpEL表达式字符串
	 * @return 表达式计算结果
	 */
	private String evaluateExpression(String expression) {
		return Objects
				.requireNonNull(PARSER.parseExpression(expression).getValue(CONTEXT, serviceInstance, String.class));
	}

}
