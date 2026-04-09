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

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 请求大小限制过滤器工厂。
 * <p>
 * 该过滤器限制请求体的大小，若请求超过指定大小则返回 413 Payload Too Large 响应。 默认最大允许大小为 5 MB。
 * <p>
 * 配置参数：
 * <ul>
 * <li>maxSize：允许的最大请求大小（默认 5MB）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - RequestSize=5MB
 *   - RequestSize=10MB
 * </pre>
 *
 * @author Arpan
 */
public class RequestSizeGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestSizeGatewayFilterFactory.RequestSizeConfig> {

	/** 大小单位前缀（k, M, G, T, P, E） */
	private static String PREFIX = "kMGTPE";

	/** 错误消息模板 */
	private static String ERROR = "Request size is larger than permissible limit."
			+ " Request size is %s where permissible limit is %s";

	/**
	 * 默认构造方法。
	 */
	public RequestSizeGatewayFilterFactory() {
		super(RequestSizeGatewayFilterFactory.RequestSizeConfig.class);
	}

	/**
	 * 格式化错误消息。
	 * @param currentRequestSize 当前请求大小（字节）
	 * @param maxSize 最大允许大小（字节）
	 * @return 格式化的错误消息
	 */
	private static String getErrorMessage(Long currentRequestSize, Long maxSize) {
		return String.format(ERROR, getReadableByteCount(currentRequestSize), getReadableByteCount(maxSize));
	}

	/**
	 * 将字节数转换为可读的大小字符串。
	 * @param bytes 字节数
	 * @return 可读的大小字符串（如 "1.5 MB"）
	 */
	private static String getReadableByteCount(long bytes) {
		int unit = 1000;
		if (bytes < unit) {
			return bytes + " B";
		}
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = Character.toString(PREFIX.charAt(exp - 1));
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	/**
	 * 创建请求大小限制过滤器。
	 * @param requestSizeConfig 请求大小配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(RequestSizeGatewayFilterFactory.RequestSizeConfig requestSizeConfig) {
		// 验证配置
		requestSizeConfig.validate();
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				// 从请求头获取 Content-Length
				String contentLength = request.getHeaders().getFirst("content-length");
				if (!ObjectUtils.isEmpty(contentLength)) {
					Long currentRequestSize = Long.valueOf(contentLength);
					// 检查是否超过限制
					if (currentRequestSize > requestSizeConfig.getMaxSize().toBytes()) {
						// 设置 413 状态码
						exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
						if (!exchange.getResponse().isCommitted()) {
							// 添加错误消息到响应头
							exchange.getResponse().getHeaders().add("errorMessage",
									getErrorMessage(currentRequestSize, requestSizeConfig.getMaxSize().toBytes()));
						}
						// 完成响应，不再继续过滤链
						return exchange.getResponse().setComplete();
					}
				}
				return chain.filter(exchange);
			}

			@Override
			public String toString() {
				return filterToStringCreator(RequestSizeGatewayFilterFactory.this)
						.append("max", requestSizeConfig.getMaxSize()).toString();
			}
		};
	}

	/**
	 * 请求大小配置类。
	 */
	public static class RequestSizeConfig {

		/** 最大请求大小，默认 5 MB */
		// TODO: 使用 Spring Boot 的 DataSize 类型
		private DataSize maxSize = DataSize.ofBytes(5000000L);

		/**
		 * 获取最大请求大小。
		 * @return 最大请求大小
		 */
		public DataSize getMaxSize() {
			return maxSize;
		}

		/**
		 * 设置最大请求大小。
		 * @param maxSize 最大请求大小
		 * @return 自身，用于链式调用
		 */
		public RequestSizeGatewayFilterFactory.RequestSizeConfig setMaxSize(DataSize maxSize) {
			this.maxSize = maxSize;
			return this;
		}

		/**
		 * 验证配置。 // TODO: 使用验证注解
		 */
		public void validate() {
			Assert.notNull(this.maxSize, "maxSize may not be null");
			Assert.isTrue(this.maxSize.toBytes() > 0, "maxSize must be greater than 0");
		}

	}

}
