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

package org.springframework.cloud.gateway.filter.headers;

import reactor.netty.http.server.HttpServerResponse;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * gRPC 响应头过滤器。
 *
 * <p>
 * 此过滤器处理 gRPC 响应的头信息，确保 gRPC 状态信息正确传递给客户端。 gRPC 使用 HTTP/2 trailers 来传递状态码和状态消息。
 * </p>
 *
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>设置 Trailer 头，声明 grpc-status 和 grpc-message</li>
 * <li>将 gRPC 状态信息添加到 HTTP/2 trailers</li>
 * <li>处理 gRPC 状态码和消息的默认值</li>
 * </ul>
 *
 * <p>
 * gRPC trailers 是 HTTP/2 协议的一部分，允许在响应体发送完毕后发送额外的元数据。
 * </p>
 *
 * @author Alberto C. Ríos
 * @see <a href="https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-HTTP2.md">gRPC over
 * HTTP2</a>
 */
public class GRPCResponseHeadersFilter implements HttpHeadersFilter, Ordered {

	/**
	 * gRPC 状态头名称。
	 */
	private static final String GRPC_STATUS_HEADER = "grpc-status";

	/**
	 * gRPC 消息头名称。
	 */
	private static final String GRPC_MESSAGE_HEADER = "grpc-message";

	/**
	 * 过滤 HTTP 头，处理 gRPC 响应头。
	 *
	 * <p>
	 * 如果请求是 gRPC 请求，执行以下操作：
	 * </p>
	 * <ol>
	 * <li>设置 Trailer 头，包含 grpc-status 和 grpc-message</li>
	 * <li>获取底层的 HTTP 服务器响应</li>
	 * <li>将 gRPC 状态码和消息添加到 trailers</li>
	 * </ol>
	 * @param headers 原始 HTTP 头
	 * @param exchange 服务器 Web 交换对象
	 * @return 过滤后的 HTTP 头
	 */
	@Override
	public HttpHeaders filter(HttpHeaders headers, ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		HttpHeaders responseHeaders = response.getHeaders();
		if (isGRPC(exchange)) {
			String trailerHeaderValue = GRPC_STATUS_HEADER + "," + GRPC_MESSAGE_HEADER;
			String originalTrailerHeaderValue = responseHeaders.getFirst(HttpHeaders.TRAILER);
			if (originalTrailerHeaderValue != null) {
				trailerHeaderValue += "," + originalTrailerHeaderValue;
			}
			responseHeaders.set(HttpHeaders.TRAILER, trailerHeaderValue);

			// 解包装响应装饰器，获取底层响应
			while (response instanceof ServerHttpResponseDecorator) {
				response = ((ServerHttpResponseDecorator) response).getDelegate();
			}
			if (response instanceof AbstractServerHttpResponse) {
				String grpcStatus = getGrpcStatus(headers);
				String grpcMessage = getGrpcMessage(headers);
				((HttpServerResponse) ((AbstractServerHttpResponse) response).getNativeResponse()).trailerHeaders(h -> {
					h.set(GRPC_STATUS_HEADER, grpcStatus);
					h.set(GRPC_MESSAGE_HEADER, grpcMessage);
				});
			}

		}
		return headers;
	}

	/**
	 * 检查请求是否为 gRPC 请求。
	 *
	 * <p>
	 * 通过检查请求的 Content-Type 是否以 "application/grpc" 开头来判断。
	 * </p>
	 * @param exchange 服务器 Web 交换对象
	 * @return 如果是 gRPC 请求返回 true，否则返回 false
	 */
	private boolean isGRPC(ServerWebExchange exchange) {
		String contentTypeValue = exchange.getRequest().getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
		return StringUtils.startsWithIgnoreCase(contentTypeValue, "application/grpc");
	}

	/**
	 * 获取 gRPC 状态码。
	 *
	 * <p>
	 * 从响应头中提取 grpc-status 值，如果不存在则返回默认值 "0"（OK）。
	 * </p>
	 * @param headers HTTP 头
	 * @return gRPC 状态码
	 */
	private String getGrpcStatus(HttpHeaders headers) {
		final String grpcStatusValue = headers.getFirst(GRPC_STATUS_HEADER);
		return StringUtils.hasText(grpcStatusValue) ? grpcStatusValue : "0";
	}

	/**
	 * 获取 gRPC 状态消息。
	 *
	 * <p>
	 * 从响应头中提取 grpc-message 值，如果不存在则返回空字符串。
	 * </p>
	 * @param headers HTTP 头
	 * @return gRPC 状态消息
	 */
	private String getGrpcMessage(HttpHeaders headers) {
		final String grpcStatusValue = headers.getFirst(GRPC_MESSAGE_HEADER);
		return StringUtils.hasText(grpcStatusValue) ? grpcStatusValue : "";
	}

	/**
	 * 检查此过滤器是否支持指定的过滤器类型。
	 *
	 * <p>
	 * 此过滤器只支持响应类型。
	 * </p>
	 * @param type 过滤器类型
	 * @return 如果是响应类型返回 true
	 */
	@Override
	public boolean supports(Type type) {
		return Type.RESPONSE.equals(type);
	}

	/**
	 * 获取过滤器执行顺序。
	 * @return 顺序值 0
	 */
	@Override
	public int getOrder() {
		return 0;
	}

}
