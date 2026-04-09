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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.SEC_WEBSOCKET_PROTOCOL;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.changeSchemeIfIsWebSocketUpgrade;
import static org.springframework.cloud.gateway.filter.WebsocketRoutingFilter.convertHttpToWs;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.http.HttpHeaders.HOST;
import static org.springframework.http.HttpHeaders.UPGRADE;

/**
 * WebsocketRoutingFilter 单元测试类
 *
 * 本测试类用于验证 WebsocketRoutingFilter 的 WebSocket 路由功能，包括： - 测试 WebSocket 协议解析功能 - 测试 HTTP 到
 * WebSocket 协议的转换（http->ws, https->wss） - 测试编码 URL 的正确处理 - 测试请求头过滤器的行为 - 测试 Host 头保留功能
 *
 * WebsocketRoutingFilter 负责处理 WebSocket 升级请求， 将 HTTP/WebSocket 请求路由到后端服务。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
public class WebsocketRoutingFilterTests {

	/**
	 * 测试 WebSocket 协议解析功能 验证：多个空格分隔的协议名称应被正确解析和规范化
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testProtocolParsing() {
		ObjectProvider<List<HttpHeadersFilter>> headersFilters = mock(ObjectProvider.class);
		WebsocketRoutingFilter filter = new WebsocketRoutingFilter(mock(WebSocketClient.class),
				mock(WebSocketService.class), headersFilters);

		HttpHeaders headers = new HttpHeaders();
		headers.put(SEC_WEBSOCKET_PROTOCOL, Arrays.asList(" p1,p2", "p3 , p4 "));
		List<String> protocols = filter.getProtocols(headers);
		assertThat(protocols).containsExactly("p1", "p2", "p3", "p4");
	}

	/**
	 * 测试 HTTP 到 WebSocket 协议的转换 验证：http->ws, https->wss, tcp->tcp（不变）， 同时验证大小写不敏感性
	 */
	@Test
	public void testConvertHttpToWs() {
		assertThat(convertHttpToWs("http")).isEqualTo("ws");
		assertThat(convertHttpToWs("HTTP")).isEqualTo("ws");
		assertThat(convertHttpToWs("https")).isEqualTo("wss");
		assertThat(convertHttpToWs("HTTPS")).isEqualTo("wss");
		assertThat(convertHttpToWs("tcp")).isEqualTo("tcp");
	}

	/**
	 * 测试编码 URL 的正确处理 验证：包含编码空格（%20）的 WebSocket URL 应正确处理
	 */
	@Test
	public void testEncodedUrl() {
		MockServerHttpRequest request = MockServerHttpRequest.get("http://not-matters-that")
				.header(UPGRADE, "WebSocket").build();
		ServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR,
				URI.create("http://microservice/my-service/websocket%20upgrade"));
		changeSchemeIfIsWebSocketUpgrade(exchange);
		URI wsRequestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		assertThat(wsRequestUrl).isEqualTo(URI.create("ws://microservice/my-service/websocket%20upgrade"));
	}

	/**
	 * 测试默认的请求头过滤器行为 验证：非 WebSocket 特定的请求头应被保留，特定请求头应被移除
	 */
	@Test
	public void testHeadersFilter() {
		assertDefaultHeadersFilters(false);
	}

	/**
	 * 测试保留 Host 请求头的过滤器行为 验证：当启用 Host 头保留时，Host 请求头应被保留
	 */
	@Test
	public void testHeadersFilterPreserveHost() {
		assertDefaultHeadersFilters(true);
	}

	/**
	 * 验证默认请求头过滤器的行为
	 * @param preserveHostHeader 是否保留 Host 请求头
	 */
	@SuppressWarnings("unchecked")
	private void assertDefaultHeadersFilters(boolean preserveHostHeader) {
		ObjectProvider<List<HttpHeadersFilter>> headersFilters = mock(ObjectProvider.class);
		when(headersFilters.getIfAvailable(any())).thenReturn(new ArrayList<>());
		WebsocketRoutingFilter filter = new WebsocketRoutingFilter(mock(WebSocketClient.class),
				mock(WebSocketService.class), headersFilters);
		List<HttpHeadersFilter> filters = filter.getHeadersFilters();
		MockServerHttpRequest request = MockServerHttpRequest.get("ws://not-matters-that").header(HOST, "MyHost")
				.header("Sec-Websocket-Something", "someval").header("x-foo", "bar").build();
		MockServerWebExchange exchange = MockServerWebExchange.from(request);
		exchange.getAttributes().put(PRESERVE_HOST_HEADER_ATTRIBUTE, preserveHostHeader);
		HttpHeaders httpHeaders = HttpHeadersFilter.filterRequest(filters, exchange);
		assertThat(httpHeaders).doesNotContainKeys("Sec-Websocket-Something").containsKey("x-foo");
		if (preserveHostHeader) {
			assertThat(httpHeaders).containsKey(HOST);
		}
		else {
			assertThat(httpHeaders).doesNotContainKeys(HOST);
		}
	}

}
