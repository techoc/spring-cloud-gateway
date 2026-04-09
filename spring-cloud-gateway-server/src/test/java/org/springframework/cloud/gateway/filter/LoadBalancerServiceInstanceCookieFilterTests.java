/*
 * Copyright 2013-2021 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;

/**
 * LoadBalancerServiceInstanceCookieFilter 单元测试类
 *
 * 本测试类用于验证 LoadBalancerServiceInstanceCookieFilter 的粘性会话功能，包括： - 测试为响应添加服务实例 Cookie 头 -
 * 测试在已有 Cookie 的情况下追加服务实例 Cookie - 测试在无服务实例响应时继续执行过滤器链 - 测试在 Cookie 名称为空或 null 时跳过添加
 *
 * LoadBalancerServiceInstanceCookieFilter 负责实现基于 Cookie 的粘性会话， 将负载均衡选择的服务实例 ID 添加到请求
 * Cookie 中，便于后续请求路由到同一实例。
 *
 * @author Olga Maciaszek-Sharma
 * @author 译者：Spring Cloud Gateway 团队
 */
class LoadBalancerServiceInstanceCookieFilterTests {

	private final LoadBalancerProperties properties = new LoadBalancerProperties();

	private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

	private final ServerWebExchange exchange = MockServerWebExchange
			.from(MockServerHttpRequest.get("http://localhost/get").build());

	private final LoadBalancerServiceInstanceCookieFilter filter = new LoadBalancerServiceInstanceCookieFilter(
			properties);

	@BeforeEach
	void setUp() {
		properties.getStickySession().setAddServiceInstanceCookie(true);
	}

	/**
	 * 测试添加服务实例 Cookie 头 验证：应添加包含服务实例 ID 的 Cookie 头，名称为 sc-lb-instance-id
	 */
	@Test
	void shouldAddServiceInstanceCookieHeader() {
		exchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR,
				new DefaultResponse(new DefaultServiceInstance("test-01", "test", "host", 8080, false)));

		ServerWebExchange filteredExchange = testFilter(exchange);

		assertThat(filteredExchange.getRequest().getHeaders().get(HttpHeaders.COOKIE)).hasSize(1);
		assertThat(filteredExchange.getRequest().getHeaders().get(HttpHeaders.COOKIE))
				.containsExactly("sc-lb-instance-id=test-01");
	}

	/**
	 * 测试在已有 Cookie 时追加服务实例 Cookie 验证：原有的 Cookie 应保留，同时添加服务实例 Cookie
	 */
	@Test
	void shouldAppendServiceInstanceCookieHeaderIfCookiesPresent() {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/get")
				.cookie(new HttpCookie("testCookieName", "testCookieValue")).build());
		exchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR,
				new DefaultResponse(new DefaultServiceInstance("test-01", "test", "host", 8080, false)));

		ServerWebExchange filteredExchange = testFilter(exchange);

		assertThat(filteredExchange.getRequest().getHeaders().get(HttpHeaders.COOKIE))
				.containsExactly("testCookieName=testCookieValue", "sc-lb-instance-id=test-01");
	}

	/**
	 * 测试无服务实例响应时应继续执行过滤器链 验证：不应添加任何 Cookie
	 */
	@Test
	void shouldContinueChainWhenNoServiceInstanceResponse() {
		ServerWebExchange filteredExchange = testFilter(exchange);

		assertThat(filteredExchange.getRequest().getHeaders()).isEmpty();
	}

	/**
	 * 测试 Cookie 名称为 null 时应继续执行过滤器链 验证：不应添加任何 Cookie
	 */
	@Test
	void shouldContinueChainWhenNullServiceInstanceCookieName() {
		exchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR,
				new DefaultResponse(new DefaultServiceInstance("test-01", "test", "host", 8080, false)));
		properties.getStickySession().setInstanceIdCookieName(null);

		ServerWebExchange filteredExchange = testFilter(exchange);

		assertThat(filteredExchange.getRequest().getHeaders()).isEmpty();
	}

	/**
	 * 测试 Cookie 名称为空字符串时应继续执行过滤器链 验证：不应添加任何 Cookie
	 */
	@Test
	void shouldContinueChainWhenEmptyServiceInstanceCookieName() {
		exchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR,
				new DefaultResponse(new DefaultServiceInstance("test-01", "test", "host", 8080, false)));
		properties.getStickySession().setInstanceIdCookieName("");

		ServerWebExchange filteredExchange = testFilter(exchange);

		assertThat(filteredExchange.getRequest().getHeaders()).isEmpty();
	}

	/**
	 * 执行过滤器测试的辅助方法
	 * @param exchange 模拟的 ServerWebExchange
	 * @return 过滤后的 ServerWebExchange
	 */
	private ServerWebExchange testFilter(ServerWebExchange exchange) {
		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
		when(chain.filter(captor.capture())).thenReturn(Mono.empty());

		filter.filter(exchange, chain).block();
		verify(chain).filter(any(ServerWebExchange.class));
		verifyNoMoreInteractions(chain);
		return captor.getValue();
	}

}
