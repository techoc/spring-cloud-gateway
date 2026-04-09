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

import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * gRPC 请求头过滤器。
 *
 * <p>
 * 此过滤器为 gRPC 请求添加必要的 HTTP/2 头信息，确保 gRPC 协议正确工作。 根据 RFC 7540 第 8.1.2.2 节的规定，gRPC 请求需要包含
 * "te: trailers" 头。
 * </p>
 *
 * <p>
 * 处理逻辑：
 * </p>
 * <ul>
 * <li>检查请求的 Content-Type 是否以 "application/grpc" 开头</li>
 * <li>如果是 gRPC 请求，添加 "te: trailers" 头</li>
 * </ul>
 *
 * <p>
 * 这是 gRPC over HTTP/2 协议的必要条件，确保服务器知道客户端支持 trailers。
 * </p>
 *
 * @author Alberto C. Ríos
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7540#section-8.1.2.2">RFC 7540 -
 * HTTP/2 Message Exchanges</a>
 */
public class GRPCRequestHeadersFilter implements HttpHeadersFilter, Ordered {

	/**
	 * 过滤 HTTP 头，为 gRPC 请求添加必要的头信息。
	 *
	 * <p>
	 * 复制所有原始头，如果请求是 gRPC 请求（Content-Type 以 "application/grpc" 开头）， 则添加 "te: trailers" 头。
	 * </p>
	 * @param headers 原始 HTTP 头
	 * @param exchange 服务器 Web 交换对象
	 * @return 过滤后的 HTTP 头
	 */
	@Override
	public HttpHeaders filter(HttpHeaders headers, ServerWebExchange exchange) {
		HttpHeaders updated = new HttpHeaders();

		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			updated.addAll(entry.getKey(), entry.getValue());
		}

		// 根据 RFC 7540 第 8.1.2.2 节，gRPC 请求需要 "te: trailers" 头
		if (isGRPC(headers.getFirst(HttpHeaders.CONTENT_TYPE))) {
			updated.add("te", "trailers");
		}
		return updated;
	}

	/**
	 * 检查请求是否为 gRPC 请求。
	 *
	 * <p>
	 * 通过检查 Content-Type 是否以 "application/grpc" 开头来判断。
	 * </p>
	 * @param contentTypeValue Content-Type 头值
	 * @return 如果是 gRPC 请求返回 true，否则返回 false
	 */
	private boolean isGRPC(String contentTypeValue) {
		return StringUtils.startsWithIgnoreCase(contentTypeValue, "application/grpc");
	}

	/**
	 * 检查此过滤器是否支持指定的过滤器类型。
	 *
	 * <p>
	 * 此过滤器只支持请求类型。
	 * </p>
	 * @param type 过滤器类型
	 * @return 如果是请求类型返回 true
	 */
	@Override
	public boolean supports(Type type) {
		return Type.REQUEST.equals(type);
	}

	/**
	 * 获取过滤器执行顺序。
	 *
	 * <p>
	 * 设置为最低优先级，确保在其他过滤器之后执行。
	 * </p>
	 * @return 顺序值 {@link Ordered#LOWEST_PRECEDENCE}
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
