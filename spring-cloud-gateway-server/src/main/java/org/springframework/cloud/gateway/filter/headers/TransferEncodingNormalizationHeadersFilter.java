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

package org.springframework.cloud.gateway.filter.headers;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Transfer-Encoding 头规范化过滤器。
 *
 * <p>
 * 此过滤器根据 RFC 7230 第 3.3.3 节的规定，处理 Transfer-Encoding 和 Content-Length 头的冲突问题。当
 * Transfer-Encoding 为 "chunked" 时，应该移除 Content-Length 头， 因为在这种情况下 Content-Length 是无效的。
 * </p>
 *
 * <p>
 * 这是为了解决某些 HTTP 客户端和服务器在处理分块传输编码时可能出现的问题。
 * </p>
 *
 * <p>
 * 处理逻辑：
 * </p>
 * <ul>
 * <li>如果 Transfer-Encoding 头值为 "chunked"</li>
 * <li>并且存在 Content-Length 头</li>
 * <li>则移除 Content-Length 头</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7230#section-3.3.3">RFC 7230 -
 * Message Body Length</a>
 */
public class TransferEncodingNormalizationHeadersFilter implements HttpHeadersFilter, Ordered {

	/**
	 * 获取过滤器执行顺序。
	 *
	 * <p>
	 * 设置为 1000，确保在其他头过滤器之后执行。
	 * </p>
	 * @return 顺序值 1000
	 */
	@Override
	public int getOrder() {
		return 1000;
	}

	/**
	 * 过滤 HTTP 头，规范化 Transfer-Encoding 头。
	 *
	 * <p>
	 * 当 Transfer-Encoding 为 "chunked" 且存在 Content-Length 时， 移除 Content-Length 头以避免协议冲突。
	 * </p>
	 * @param input 原始 HTTP 头
	 * @param exchange 服务器 Web 交换对象
	 * @return 过滤后的 HTTP 头
	 */
	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		String transferEncoding = input.getFirst(HttpHeaders.TRANSFER_ENCODING);
		if (transferEncoding != null && "chunked".equalsIgnoreCase(transferEncoding.trim())
				&& input.containsKey(HttpHeaders.CONTENT_LENGTH)) {

			HttpHeaders filtered = new HttpHeaders();
			// 如果 input 是只读的，则避免直接修改
			filtered.addAll(input);
			filtered.remove(HttpHeaders.CONTENT_LENGTH);
			return filtered;
		}

		return input;
	}

}
