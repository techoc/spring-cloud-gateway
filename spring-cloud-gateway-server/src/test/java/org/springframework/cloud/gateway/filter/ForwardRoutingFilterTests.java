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

import java.net.URI;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ALREADY_ROUTED_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * ForwardRoutingFilter 单元测试类
 *
 * 本测试类用于验证 ForwardRoutingFilter 的功能行为，包括： - 转发请求到内部端点的路由逻辑 - 对于非 forward:// 协议的请求不进行过滤处理
 * - 对于已标记为已路由的请求不再重复转发 - 保持主机路径与请求中指定的一致
 *
 * ForwardRoutingFilter 是 Spring Cloud Gateway 的内部转发过滤器， 用于处理 forward://
 * 协议的请求，将请求转发到网关本地的其他端点。
 *
 * @author Arjun Curat
 * @author 译者：Spring Cloud Gateway 团队
 */

@RunWith(MockitoJUnitRunner.class)
public class ForwardRoutingFilterTests {

	private ServerWebExchange exchange;

	@Mock
	private GatewayFilterChain chain;

	@Mock
	private ObjectProvider<DispatcherHandler> objectProvider;

	@Mock
	private DispatcherHandler dispatcherHandler;

	@InjectMocks
	private ForwardRoutingFilter forwardRoutingFilter;

	@Before
	public void setup() {
		exchange = MockServerWebExchange.from(MockServerHttpRequest.get("localendpoint").build());
		when(objectProvider.getIfAvailable()).thenReturn(this.dispatcherHandler);
	}

	/**
	 * 测试当请求URL协议不是 forward 时不应进行过滤 验证：对于 https:// 协议的请求，过滤器应继续调用过滤器链而不进行转发
	 */
	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsNotForward() {
		URI uri = UriComponentsBuilder.fromUriString("https://endpoint").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		forwardRoutingFilter.filter(exchange, chain);

		verifyNoInteractions(dispatcherHandler);
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	/**
	 * 测试当请求URL协议是 forward 时应进行过滤和转发 验证：对于 forward:// 协议的请求，过滤器应调用 DispatcherHandler
	 * 进行内部转发
	 */
	@Test
	public void shouldFilterWhenGatewayRequestUrlSchemeIsForward() {
		URI uri = UriComponentsBuilder.fromUriString("forward://endpoint").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isNull();

		forwardRoutingFilter.filter(exchange, chain);

		verifyNoMoreInteractions(chain);
		verify(dispatcherHandler).handle(exchange);

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isNull();
	}

	/**
	 * 测试转发时保持主机和路径与请求中指定的一致 验证：转发后的请求URL应保持 forward://host/outage 的完整路径信息
	 */
	@Test
	public void shouldFilterAndKeepHostPathAsSpecified() {

		URI uri = UriComponentsBuilder.fromUriString("forward://host/outage").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

		ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);

		forwardRoutingFilter.filter(exchange, chain);

		verify(dispatcherHandler).handle(captor.capture());

		assertThat(exchange.getAttributes().get(GATEWAY_ALREADY_ROUTED_ATTR)).isNull();

		ServerWebExchange webExchange = captor.getValue();

		URI forwardedUrl = webExchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		assertThat(forwardedUrl).hasScheme("forward").hasHost("host").hasPath("/outage");
	}

	/**
	 * 测试当请求URL协议是 forward 但已经被标记为已路由时应跳过转发 验证：对于已设置 GATEWAY_ALREADY_ROUTED_ATTR 为 true
	 * 的请求，不应重复转发
	 */
	@Test
	public void shouldNotFilterWhenGatewayRequestUrlSchemeIsForwardButAlreadyRouted() {
		URI uri = UriComponentsBuilder.fromUriString("forward://host").build().toUri();
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
		exchange.getAttributes().put(GATEWAY_ALREADY_ROUTED_ATTR, true);

		forwardRoutingFilter.filter(exchange, chain);

		verifyNoInteractions(dispatcherHandler);
		verify(chain).filter(exchange);
		verifyNoMoreInteractions(chain);
	}

	/**
	 * 测试过滤器的执行顺序是否为最低优先级 验证：ForwardRoutingFilter 的优先级应与 Ordered.LOWEST_PRECEDENCE 一致
	 */
	@Test
	public void orderIsLowestPrecedence() {
		assertThat(forwardRoutingFilter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
	}

}
