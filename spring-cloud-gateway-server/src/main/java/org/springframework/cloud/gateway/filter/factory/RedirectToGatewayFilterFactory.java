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

package org.springframework.cloud.gateway.filter.factory;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.HttpStatusHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setResponseStatus;

/**
 * 重定向过滤器工厂。
 * <p>
 * 该过滤器终止当前请求，返回指定的重定向响应。 通常用于 HTTP 3xx 状态码重定向。
 * <p>
 * 配置参数：
 * <ul>
 * <li>status：HTTP 状态码（必须是 3xx）</li>
 * <li>url：重定向目标 URL</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - RedirectTo=302, https://example.com
 * </pre>
 *
 * @author Spencer Gibb
 */
public class RedirectToGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RedirectToGatewayFilterFactory.Config> {

	/**
	 * 状态码参数键名。
	 */
	public static final String STATUS_KEY = "status";

	/**
	 * URL 参数键名。
	 */
	public static final String URL_KEY = "url";

	/**
	 * 默认构造方法。
	 */
	public RedirectToGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STATUS_KEY, URL_KEY);
	}

	/**
	 * 创建重定向过滤器（使用配置对象）。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return apply(config.status, config.url);
	}

	/**
	 * 创建重定向过滤器（使用字符串参数）。
	 * @param statusString HTTP 状态码字符串
	 * @param urlString 重定向目标 URL
	 * @return 网关过滤器实例
	 */
	public GatewayFilter apply(String statusString, String urlString) {
		HttpStatusHolder httpStatus = HttpStatusHolder.parse(statusString);
		Assert.isTrue(httpStatus.is3xxRedirection(), "status must be a 3xx code, but was " + statusString);
		final URI url = URI.create(urlString);
		return apply(httpStatus, url);
	}

	/**
	 * 创建重定向过滤器（使用 HttpStatus 和 URI）。
	 * @param httpStatus HTTP 状态码
	 * @param uri 重定向目标 URI
	 * @return 网关过滤器实例
	 */
	public GatewayFilter apply(HttpStatus httpStatus, URI uri) {
		return apply(new HttpStatusHolder(httpStatus, null), uri);
	}

	/**
	 * 创建重定向过滤器（使用 HttpStatusHolder 和 URI）。
	 * @param httpStatus 状态码持有者
	 * @param uri 重定向目标 URI
	 * @return 网关过滤器实例
	 */
	public GatewayFilter apply(HttpStatusHolder httpStatus, URI uri) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 检查响应是否已提交
				if (!exchange.getResponse().isCommitted()) {
					// 设置响应状态码
					setResponseStatus(exchange, httpStatus);

					// 添加 Location 头
					final ServerHttpResponse response = exchange.getResponse();
					response.getHeaders().set(HttpHeaders.LOCATION, uri.toString());
					// 完成响应
					return response.setComplete();
				}
				return Mono.empty();
			}

			@Override
			public String toString() {
				String status;
				if (httpStatus.getHttpStatus() != null) {
					status = String.valueOf(httpStatus.getHttpStatus().value());
				}
				else {
					status = httpStatus.getStatus().toString();
				}
				return filterToStringCreator(RedirectToGatewayFilterFactory.this).append(status, uri).toString();
			}
		};
	}

	/**
	 * 重定向过滤器配置类。
	 */
	public static class Config {

		/** HTTP 状态码（必须是 3xx） */
		String status;

		/** 重定向目标 URL */
		String url;

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

	}

}
