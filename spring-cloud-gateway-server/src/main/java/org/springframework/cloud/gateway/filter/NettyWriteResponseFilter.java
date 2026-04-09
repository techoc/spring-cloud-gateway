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

import java.util.List;

import io.netty.buffer.ByteBuf;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;

/**
 * Netty 响应回写过滤器。
 * <p>
 * 该全局过滤器在 {@link NettyRoutingFilter} 将下游响应连接存入 exchange 属性后， 负责从 Netty
 * 连接读取下游响应体并将其写回给客户端。
 * <p>
 * 执行顺序为 {@link #WRITE_RESPONSE_FILTER_ORDER}（值为 -1），优先于绝大多数过滤器执行， 确保响应体在过滤器链执行完毕后立即写回。
 * <p>
 * 对于流式媒体类型（如 SSE、流式 JSON），使用 {@code writeAndFlushWith} 逐块写响应； 对于普通媒体类型，使用
 * {@code writeWith} 一次性写响应。
 * <p>
 * 注意：该过滤器在 "pre" 阶段无逻辑，因为 {@code CLIENT_RESPONSE_CONN_ATTR} 属性 在
 * {@link NettyRoutingFilter} 运行后才会存在。
 *
 * @author Spencer Gibb
 */
public class NettyWriteResponseFilter implements GlobalFilter, Ordered {

	/**
	 * 响应回写过滤器的执行顺序，值为 -1。
	 */
	public static final int WRITE_RESPONSE_FILTER_ORDER = -1;

	private static final Log log = LogFactory.getLog(NettyWriteResponseFilter.class);

	/** 流式媒体类型列表，对这些类型使用逐块刷新方式写响应 */
	private final List<MediaType> streamingMediaTypes;

	/**
	 * 构造 NettyWriteResponseFilter。
	 * @param streamingMediaTypes 流式媒体类型列表（如 text/event-stream、application/stream+json）
	 */
	public NettyWriteResponseFilter(List<MediaType> streamingMediaTypes) {
		this.streamingMediaTypes = streamingMediaTypes;
	}

	/**
	 * 返回过滤器执行顺序。
	 * @return {@link #WRITE_RESPONSE_FILTER_ORDER}，值为 -1
	 */
	@Override
	public int getOrder() {
		return WRITE_RESPONSE_FILTER_ORDER;
	}

	/**
	 * 过滤请求，在过滤器链执行完毕后将 Netty 下游响应体写回客户端。
	 * <p>
	 * 处理流程：
	 * <ol>
	 * <li>先执行过滤器链（{@code chain.filter(exchange)}）；</li>
	 * <li>链执行完成后，从 exchange 属性中获取 Netty 连接；</li>
	 * <li>若连接为空（非 Netty 路由），直接完成；</li>
	 * <li>否则从连接读取响应体，根据内容类型决定写响应方式（流式或普通）；</li>
	 * <li>在取消或发生错误时清理 Netty 连接资源。</li>
	 * </ol>
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 注意：pre 阶段无逻辑，CLIENT_RESPONSE_CONN_ATTR 在 NettyRoutingFilter 运行后才存在
		// @formatter:off
		return chain.filter(exchange)
				.then(Mono.defer(() -> {
					Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);

					if (connection == null) {
						return Mono.empty();
					}
					if (log.isTraceEnabled()) {
						log.trace("NettyWriteResponseFilter start inbound: "
								+ connection.channel().id().asShortText() + ", outbound: "
								+ exchange.getLogPrefix());
					}
					ServerHttpResponse response = exchange.getResponse();

					// TODO: 是否必须？
					final Flux<DataBuffer> body = connection
							.inbound()
							.receive()
							.retain()
							.map(byteBuf -> wrap(byteBuf, response));

					MediaType contentType = null;
					try {
						contentType = response.getHeaders().getContentType();
					}
					catch (Exception e) {
						if (log.isTraceEnabled()) {
							log.trace("invalid media type", e);
						}
					}
					// 流式类型逐块刷新，普通类型一次性写
					return (isStreamingMediaType(contentType)
							? response.writeAndFlushWith(body.map(Flux::just))
							: response.writeWith(body));
				})).doOnCancel(() -> cleanup(exchange))
				.doOnError(throwable -> cleanup(exchange));
		// @formatter:on
	}

	/**
	 * 将 Netty {@link ByteBuf} 包装为 Spring {@link DataBuffer}。
	 * <p>
	 * 根据 {@link DataBufferFactory} 类型选择包装方式：
	 * <ul>
	 * <li>{@link NettyDataBufferFactory}：直接包装 ByteBuf，零拷贝；</li>
	 * <li>{@link DefaultDataBufferFactory}（测试环境）：复制数据后释放 ByteBuf。</li>
	 * </ul>
	 * @param byteBuf Netty 字节缓冲区
	 * @param response 当前服务器 HTTP 响应对象（用于获取 DataBufferFactory）
	 * @return 包装后的 {@link DataBuffer}
	 * @throws IllegalArgumentException 若 DataBufferFactory 类型未知
	 */
	protected DataBuffer wrap(ByteBuf byteBuf, ServerHttpResponse response) {
		DataBufferFactory bufferFactory = response.bufferFactory();
		if (bufferFactory instanceof NettyDataBufferFactory) {
			NettyDataBufferFactory factory = (NettyDataBufferFactory) bufferFactory;
			return factory.wrap(byteBuf);
		}
		// MockServerHttpResponse 使用 DefaultDataBufferFactory
		else if (bufferFactory instanceof DefaultDataBufferFactory) {
			DataBuffer buffer = ((DefaultDataBufferFactory) bufferFactory).allocateBuffer(byteBuf.readableBytes());
			buffer.write(byteBuf.nioBuffer());
			byteBuf.release();
			return buffer;
		}
		throw new IllegalArgumentException("Unkown DataBufferFactory type " + bufferFactory.getClass());
	}

	/**
	 * 清理 Netty 连接资源。
	 * <p>
	 * 在请求取消或发生错误时调用，若连接仍处于活跃状态则主动释放。
	 * @param exchange 当前服务器 Web 交换对象
	 */
	private void cleanup(ServerWebExchange exchange) {
		Connection connection = exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR);
		if (connection != null && connection.channel().isActive()) {
			connection.dispose();
		}
	}

	/**
	 * 判断给定的媒体类型是否为流式媒体类型。
	 * <p>
	 * TODO: 如果框架提供了相应支持，可直接使用框架方法。
	 * @param contentType 待判断的媒体类型，可能为 null
	 * @return 若为流式媒体类型则返回 {@code true}，否则返回 {@code false}
	 */
	// TODO: 如有可能，使用框架提供的方法
	private boolean isStreamingMediaType(@Nullable MediaType contentType) {
		if (contentType != null) {
			for (int i = 0; i < streamingMediaTypes.size(); i++) {
				if (streamingMediaTypes.get(i).isCompatibleWith(contentType)) {
					return true;
				}
			}
		}
		return false;
	}

}
