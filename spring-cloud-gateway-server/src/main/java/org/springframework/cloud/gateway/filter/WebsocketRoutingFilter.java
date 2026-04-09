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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.PRESERVE_HOST_HEADER_ATTRIBUTE;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.containsEncodedParts;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

/**
 * WebSocket 路由过滤器。
 * <p>
 * 该全局过滤器负责将 WebSocket 请求（{@code ws://} 或 {@code wss://}）代理转发到目标服务。 支持标准 WebSocket
 * 协议和子协议（SubProtocol），能够处理 WebSocket 升级请求和双向消息转发。
 * <p>
 * 主要功能：
 * <ul>
 * <li>检测 HTTP 请求中的 Upgrade 头，自动将 http/ws、https/wss 相互转换；</li>
 * <li>过滤 WebSocket 请求头，移除不必要的头部（如 hop-by-hop 头）；</li>
 * <li>支持 WebSocket 子协议（Sec-WebSocket-Protocol）的透传；</li>
 * <li>处理连接关闭状态码的适配；</li>
 * <li>双向转发客户端和服务端的 WebSocket 消息。</li>
 * </ul>
 * <p>
 * 执行顺序为 {@link Ordered#LOWEST_PRECEDENCE} - 1，在 {@link NettyRoutingFilter} 之前执行， 确保在 HTTP
 * 请求被路由到 NettyRoutingFilter 之前拦截 WebSocket 升级请求。
 *
 * @author Spencer Gibb
 * @author Nikita Konev
 */
public class WebsocketRoutingFilter implements GlobalFilter, Ordered {

	/**
	 * WebSocket 子协议头部名称。
	 */
	public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

	private static final Log log = LogFactory.getLog(WebsocketRoutingFilter.class);

	/** WebSocket 客户端，用于建立到下游服务的 WebSocket 连接 */
	private final WebSocketClient webSocketClient;

	/** WebSocket 服务，用于处理 WebSocket 握手请求 */
	private final WebSocketService webSocketService;

	/** 请求头过滤器列表的 ObjectProvider，延迟获取以避免循环依赖 */
	private final ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider;

	/** 请求头过滤器列表缓存，请勿直接使用，应通过 getHeadersFilters() 方法获取 */
	private volatile List<HttpHeadersFilter> headersFilters;

	/**
	 * 构造 WebsocketRoutingFilter。
	 * @param webSocketClient WebSocket 客户端实现
	 * @param webSocketService WebSocket 服务处理实现
	 * @param headersFiltersProvider 请求头过滤器列表的 ObjectProvider
	 */
	public WebsocketRoutingFilter(WebSocketClient webSocketClient, WebSocketService webSocketService,
			ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider) {
		this.webSocketClient = webSocketClient;
		this.webSocketService = webSocketService;
		this.headersFiltersProvider = headersFiltersProvider;
	}

	/**
	 * 将 HTTP/HTTPS 协议转换为 WebSocket 协议。
	 * <p>
	 * 转换规则：
	 * <ul>
	 * <li>{@code http} → {@code ws}</li>
	 * <li>{@code https} → {@code wss}</li>
	 * <li>其他协议保持不变。</li>
	 * </ul>
	 * @param scheme 原始协议（小写）
	 * @return WebSocket 协议字符串
	 */
	/* 仅用于测试 */
	static String convertHttpToWs(String scheme) {
		scheme = scheme.toLowerCase();
		return "http".equals(scheme) ? "ws" : "https".equals(scheme) ? "wss" : scheme;
	}

	/**
	 * 返回过滤器执行顺序。
	 * @return {@link Ordered#LOWEST_PRECEDENCE} - 1，优先于 NettyRoutingFilter 执行
	 */
	@Override
	public int getOrder() {
		// 在 NettyRoutingFilter 之前执行，因为该过滤器会处理特定的 HTTP 请求
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	/**
	 * 过滤请求，将 WebSocket 请求代理转发到目标服务。
	 * <p>
	 * 处理流程：
	 * <ol>
	 * <li>检查是否为 WebSocket 升级请求，若是则转换协议（http→ws, https→wss）；</li>
	 * <li>若请求已被路由或非 WebSocket 协议，则跳过；</li>
	 * <li>过滤请求头，移除 hop-by-hop 头部；</li>
	 * <li>获取子协议列表；</li>
	 * <li>创建代理 WebSocket 处理器并交由 WebSocketService 处理握手。</li>
	 * </ol>
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		changeSchemeIfIsWebSocketUpgrade(exchange);

		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		String scheme = requestUrl.getScheme();

		if (isAlreadyRouted(exchange) || (!"ws".equals(scheme) && !"wss".equals(scheme))) {
			return chain.filter(exchange);
		}
		setAlreadyRouted(exchange);

		HttpHeaders headers = exchange.getRequest().getHeaders();
		HttpHeaders filtered = filterRequest(getHeadersFilters(), exchange);

		List<String> protocols = getProtocols(headers);

		return this.webSocketService.handleRequest(exchange,
				new ProxyWebSocketHandler(requestUrl, this.webSocketClient, filtered, protocols));
	}

	/**
	 * 从请求头中解析 WebSocket 子协议列表。
	 * <p>
	 * 支持多个协议头部，每个头部可能包含逗号分隔的多个协议名称。
	 * @param headers HTTP 请求头
	 * @return 子协议名称列表
	 */
	/* 仅用于测试 */
	List<String> getProtocols(HttpHeaders headers) {
		List<String> protocols = headers.get(SEC_WEBSOCKET_PROTOCOL);
		if (protocols != null) {
			ArrayList<String> updatedProtocols = new ArrayList<>();
			for (int i = 0; i < protocols.size(); i++) {
				String protocol = protocols.get(i);
				updatedProtocols.addAll(Arrays.asList(StringUtils.tokenizeToStringArray(protocol, ",")));
			}
			protocols = updatedProtocols;
		}
		return protocols;
	}

	/**
	 * 获取请求头过滤器列表。
	 * <p>
	 * 首次调用时从 ObjectProvider 获取并缓存，同时注册两个默认过滤器：
	 * <ol>
	 * <li>移除 HOST 头（除非设置了 preserveHost）；</li>
	 * <li>移除以 "sec-websocket" 开头的所有头部（除子协议外）。</li>
	 * </ol>
	 * @return 请求头过滤器列表
	 */
	/* 仅用于测试 */
	List<HttpHeadersFilter> getHeadersFilters() {
		if (this.headersFilters == null) {
			this.headersFilters = this.headersFiltersProvider.getIfAvailable(ArrayList::new);

			// 移除 HOST 头，除非明确要求保留
			headersFilters.add((headers, exchange) -> {
				HttpHeaders filtered = new HttpHeaders();
				filtered.addAll(headers);
				filtered.remove(HttpHeaders.HOST);
				boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);
				if (preserveHost) {
					String host = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
					filtered.add(HttpHeaders.HOST, host);
				}
				return filtered;
			});

			// 移除 sec-websocket 相关的头部（子协议除外）
			headersFilters.add((headers, exchange) -> {
				HttpHeaders filtered = new HttpHeaders();
				for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
					if (!entry.getKey().toLowerCase().startsWith("sec-websocket")) {
						filtered.addAll(entry.getKey(), entry.getValue());
					}
				}
				return filtered;
			});
		}

		return this.headersFilters;
	}

	/**
	 * 若请求为 WebSocket 升级请求，则将协议从 http/https 转换为 ws/wss。
	 * @param exchange 当前服务器 Web 交换对象
	 */
	static void changeSchemeIfIsWebSocketUpgrade(ServerWebExchange exchange) {
		// 检查 Upgrade 头
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
		String scheme = requestUrl.getScheme().toLowerCase();
		String upgrade = exchange.getRequest().getHeaders().getUpgrade();
		// 若客户端发送了 "http" 或 "https" 请求但请求 WebSocket 升级，则转换协议
		if ("WebSocket".equalsIgnoreCase(upgrade) && ("http".equals(scheme) || "https".equals(scheme))) {
			String wsScheme = convertHttpToWs(scheme);
			boolean encoded = containsEncodedParts(requestUrl);
			URI wsRequestUrl = UriComponentsBuilder.fromUri(requestUrl).scheme(wsScheme).build(encoded).toUri();
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, wsRequestUrl);
			if (log.isTraceEnabled()) {
				log.trace("changeSchemeTo:[" + wsRequestUrl + "]");
			}
		}
	}

	/**
	 * 代理 WebSocket 处理器，负责与下游 WebSocket 服务建立连接并双向转发消息。
	 */
	private static class ProxyWebSocketHandler implements WebSocketHandler {

		/** WebSocket 客户端 */
		private final WebSocketClient client;

		/** 目标 WebSocket 服务 URL */
		private final URI url;

		/** 转发到下游服务的 HTTP 请求头 */
		private final HttpHeaders headers;

		/** WebSocket 子协议列表 */
		private final List<String> subProtocols;

		/**
		 * 构造代理 WebSocket 处理器。
		 * @param url 目标 WebSocket 服务 URL
		 * @param client WebSocket 客户端
		 * @param headers 请求头
		 * @param protocols 子协议列表
		 */
		ProxyWebSocketHandler(URI url, WebSocketClient client, HttpHeaders headers, List<String> protocols) {
			this.client = client;
			this.url = url;
			this.headers = headers;
			if (protocols != null) {
				this.subProtocols = protocols;
			}
			else {
				this.subProtocols = Collections.emptyList();
			}
		}

		/**
		 * 返回支持的 WebSocket 子协议列表。
		 * @return 子协议名称列表
		 */
		@Override
		public List<String> getSubProtocols() {
			return this.subProtocols;
		}

		/**
		 * 处理 WebSocket 会话，建立到下游服务的连接并进行消息双向转发。
		 * @param session 客户端到网关的 WebSocket 会话
		 * @return 反映会话完成的 {@code Mono<Void>}
		 */
		@Override
		public Mono<Void> handle(WebSocketSession session) {
			// 将请求头透传给下游 WebSocket 服务
			return client.execute(url, this.headers, new WebSocketHandler() {

				/**
				 * 适配 WebSocket 关闭状态码。
				 * <p>
				 * 根据 RFC 6455 规范，某些状态码不能在关闭帧中使用，应转换为 PROTOCOL_ERROR。 有效范围为 3000-4999。
				 * @param closeStatus 原始关闭状态
				 * @return 适配后的关闭状态
				 */
				private CloseStatus adaptCloseStatus(CloseStatus closeStatus) {
					int code = closeStatus.getCode();
					if (code > 2999 && code < 5000) {
						return closeStatus;
					}
					switch (code) {
					case 1000:
					case 1001:
					case 1002:
					case 1003:
					case 1007:
					case 1008:
					case 1009:
					case 1010:
					case 1011:
						return closeStatus;
					case 1004:
						// 保留值，不应在关闭帧中使用
					case 1005:
						// 无状态码，不应在关闭帧中使用
					case 1006:
						// 连接异常关闭，不应在关闭帧中使用
					case 1012:
						// 非标准码
					case 1013:
						// 非标准码
					case 1015:
						// TLS 握手失败，不应在关闭帧中使用
					default:
						return CloseStatus.PROTOCOL_ERROR;
					}
				}

				@Override
				public Mono<Void> handle(WebSocketSession proxySession) {
					// 监听两端关闭状态并传播到对端
					Mono<Void> serverClose = proxySession.closeStatus().filter(__ -> session.isOpen())
							.map(this::adaptCloseStatus).flatMap(session::close);
					Mono<Void> proxyClose = session.closeStatus().filter(__ -> proxySession.isOpen())
							.map(this::adaptCloseStatus).flatMap(proxySession::close);
					// 转发客户端消息到服务端（Reactor Netty 需要 retain()）
					Mono<Void> proxySessionSend = proxySession
							.send(session.receive().doOnNext(WebSocketMessage::retain));
					// 转发服务端消息到客户端
					Mono<Void> serverSessionSend = session
							.send(proxySession.receive().doOnNext(WebSocketMessage::retain));
					// 确保一端关闭时传播关闭信号到对端
					Mono.when(serverClose, proxyClose).subscribe();
					// 两端发送完成后整个会话结束
					return Mono.zip(proxySessionSend, serverSessionSend).then();
				}

				/**
				 * 返回子协议列表，供下游使用。
				 * @return 子协议名称列表
				 */
				@Override
				public List<String> getSubProtocols() {
					return ProxyWebSocketHandler.this.subProtocols;
				}
			});
		}

	}

}
