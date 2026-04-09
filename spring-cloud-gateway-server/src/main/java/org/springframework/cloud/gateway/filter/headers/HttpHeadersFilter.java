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

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * HTTP 请求头过滤器接口。
 *
 * <p>
 * 此接口定义了过滤 HTTP 请求头的契约，允许在请求被转发到下游服务之前或响应返回给客户端之前 对 HTTP
 * 头进行修改。实现类可以实现特定的头过滤逻辑，如添加、删除或修改头信息。
 * </p>
 *
 * <p>
 * 过滤器类型：
 * </p>
 * <ul>
 * <li>{@link Type#REQUEST} - 请求头过滤器，在请求转发前执行</li>
 * <li>{@link Type#RESPONSE} - 响应头过滤器，在响应返回前执行</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 * </p>
 * <pre>{@code
 * public class CustomHeadersFilter implements HttpHeadersFilter {
 *     &#64;Override
 *     public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
 *         HttpHeaders filtered = new HttpHeaders();
 *         filtered.addAll(input);
 *         filtered.add("X-Custom-Header", "value");
 *         return filtered;
 *     }
 * }
 * }</pre>
 *
 * @author Ryan Baxter
 * @author Spencer Gibb
 */
public interface HttpHeadersFilter {

	/**
	 * 过滤请求头。
	 *
	 * <p>
	 * 便捷方法，用于从交换对象中提取请求头并应用过滤器链。
	 * </p>
	 * @param filters 过滤器列表
	 * @param exchange 服务器 Web 交换对象
	 * @return 过滤后的请求头
	 */
	static HttpHeaders filterRequest(List<HttpHeadersFilter> filters, ServerWebExchange exchange) {
		HttpHeaders headers = exchange.getRequest().getHeaders();
		return filter(filters, headers, exchange, Type.REQUEST);
	}

	/**
	 * 应用过滤器链过滤 HTTP 头。
	 *
	 * <p>
	 * 遍历所有过滤器，对每个支持指定类型的过滤器依次应用过滤逻辑。 过滤器按列表顺序执行，每个过滤器的输出作为下一个过滤器的输入。
	 * </p>
	 * @param filters 过滤器列表
	 * @param input 原始 HTTP 头
	 * @param exchange 服务器 Web 交换对象
	 * @param type 过滤器类型（请求或响应）
	 * @return 过滤后的 HTTP 头
	 */
	static HttpHeaders filter(List<HttpHeadersFilter> filters, HttpHeaders input, ServerWebExchange exchange,
			Type type) {
		if (filters != null) {
			HttpHeaders filtered = input;
			for (int i = 0; i < filters.size(); i++) {
				HttpHeadersFilter filter = filters.get(i);
				if (filter.supports(type)) {
					filtered = filter.filter(filtered, exchange);
				}
			}
			return filtered;
		}

		return input;
	}

	/**
	 * 过滤 HTTP 头。
	 *
	 * <p>
	 * 实现类应此方法定义具体的头过滤逻辑。可以修改、添加或删除头信息。
	 * </p>
	 * @param input 原始 HTTP 头
	 * @param exchange 服务器 Web 交换对象，包含请求和响应信息
	 * @return 过滤后的 HTTP 头
	 */
	HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange);

	/**
	 * 检查此过滤器是否支持指定的过滤器类型。
	 *
	 * <p>
	 * 默认实现只支持请求头过滤（{@link Type#REQUEST}）。 子类可以重写此方法以支持响应头过滤或其他类型。
	 * </p>
	 * @param type 过滤器类型
	 * @return 如果支持该类型返回 true，否则返回 false
	 */
	default boolean supports(Type type) {
		return type.equals(Type.REQUEST);
	}

	/**
	 * 过滤器类型枚举。
	 *
	 * <p>
	 * 定义了过滤器可以应用的阶段：
	 * </p>
	 * <ul>
	 * <li>REQUEST - 请求阶段，在请求转发到下游服务之前</li>
	 * <li>RESPONSE - 响应阶段，在响应返回给客户端之前</li>
	 * </ul>
	 */
	enum Type {

		/**
		 * 请求类型，表示过滤器应用于请求头。
		 */
		REQUEST,

		/**
		 * 响应类型，表示过滤器应用于响应头。
		 */
		RESPONSE

	}

}
