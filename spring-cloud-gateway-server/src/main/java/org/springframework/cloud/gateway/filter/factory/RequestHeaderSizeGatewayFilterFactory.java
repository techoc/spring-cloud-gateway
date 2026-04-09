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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 请求头大小限制过滤器工厂，用于验证和限制 HTTP 请求头的大小。
 *
 * <p>
 * 该过滤器检查每个请求头（包括键名和值）的大小总和，如果超过配置的最大值， 则拒绝请求并返回 431 REQUEST_HEADER_FIELDS_TOO_LARGE 状态码。
 *
 * <p>
 * 这是防止拒绝服务攻击（DoS）的重要安全措施，可以阻止恶意客户端发送过大的请求头。
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: my-route
 *           uri: http://example.com
 *           filters:
 *             - name: RequestHeaderSize
 *               args:
 *                 maxSize: 16KB  # 默认值为 16KB
 *                 errorHeaderName: X-Request-Header-Size-Error
 * </pre>
 *
 * <p>
 * 也可以使用快捷配置方式： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: my-route
 *           uri: http://example.com
 *           filters:
 *             - RequestHeaderSize=16KB
 * </pre>
 *
 * @author Sakalya Deshpande
 * @author Marta Medio
 * @see <a href="https://tools.ietf.org/html/rfc6585#section-5">RFC 6585 - Additional HTTP
 * Status Codes</a>
 */
public class RequestHeaderSizeGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RequestHeaderSizeGatewayFilterFactory.Config> {

	/** 错误消息前缀模板 */
	private static String ERROR_PREFIX = "Request Header/s size is larger than permissible limit (%s).";

	/** 错误消息格式模板 */
	private static String ERROR = " Request Header/s size for '%s' is %s.";

	/**
	 * 构造函数，使用默认配置类初始化过滤器工厂。
	 */
	public RequestHeaderSizeGatewayFilterFactory() {
		super(RequestHeaderSizeGatewayFilterFactory.Config.class);
	}

	/**
	 * 返回快捷配置的字段顺序。
	 * @return 包含字段名的列表，用于快捷配置解析
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("maxSize");
	}

	/**
	 * 应用此过滤器，创建请求头大小限制过滤器。
	 * @param config 请求头大小限制配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(RequestHeaderSizeGatewayFilterFactory.Config config) {
		// 获取错误头名称，默认为 "errorMessage"
		String errorHeaderName = config.getErrorHeaderName() != null ? config.getErrorHeaderName() : "errorMessage";

		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，验证请求头大小。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				HttpHeaders headers = request.getHeaders();
				// 存储超过限制的请求头及其大小
				HashMap<String, Long> longHeaders = new HashMap<>();

				// 遍历所有请求头，计算每个请求头的总大小
				for (Map.Entry<String, List<String>> headerEntry : headers.entrySet()) {
					long headerSizeInBytes = 0L;
					// 计算请求头键的字节长度
					headerSizeInBytes += headerEntry.getKey().getBytes().length;
					// 计算请求头值（可能有多个）的总字节长度
					List<String> values = headerEntry.getValue();
					for (String value : values) {
						headerSizeInBytes += value.getBytes().length;
					}
					// 如果超过配置的最大值，记录该请求头
					if (headerSizeInBytes > config.getMaxSize().toBytes()) {
						longHeaders.put(headerEntry.getKey(), headerSizeInBytes);
					}
				}

				// 如果存在超过限制的请求头，拒绝请求
				if (!longHeaders.isEmpty()) {
					exchange.getResponse().setStatusCode(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE);
					exchange.getResponse().getHeaders().add(errorHeaderName,
							getErrorMessage(longHeaders, config.getMaxSize()));
					return exchange.getResponse().setComplete();
				}

				// 请求头大小正常，继续执行过滤器链
				return chain.filter(exchange);
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(RequestHeaderSizeGatewayFilterFactory.this)
						.append("maxSize", config.getMaxSize()).toString();
			}
		};
	}

	/**
	 * 生成错误消息，包含所有超过限制的请求头信息。
	 * @param longHeaders 超过限制的请求头映射（名称 -> 大小）
	 * @param maxSize 配置的最大大小
	 * @return 格式化的错误消息
	 */
	private static String getErrorMessage(HashMap<String, Long> longHeaders, DataSize maxSize) {
		StringBuilder msg = new StringBuilder(String.format(ERROR_PREFIX, maxSize));
		longHeaders
				.forEach((header, size) -> msg.append(String.format(ERROR, header, DataSize.of(size, DataUnit.BYTES))));
		return msg.toString();
	}

	/**
	 * 请求头大小限制配置类。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>maxSize - 允许的最大请求头大小，默认 16KB</li>
	 * <li>errorHeaderName - 错误消息响应头的名称</li>
	 * </ul>
	 */
	public static class Config {

		/** 允许的最大请求头大小，默认为 16KB */
		private DataSize maxSize = DataSize.ofBytes(16000L);

		/** 错误消息响应头的名称 */
		private String errorHeaderName;

		/**
		 * 获取允许的最大请求头大小。
		 * @return 最大请求头大小
		 */
		public DataSize getMaxSize() {
			return maxSize;
		}

		/**
		 * 设置允许的最大请求头大小。
		 * @param maxSize 最大请求头大小
		 */
		public void setMaxSize(DataSize maxSize) {
			this.maxSize = maxSize;
		}

		/**
		 * 获取错误消息响应头的名称。
		 * @return 错误头名称
		 */
		public String getErrorHeaderName() {
			return errorHeaderName;
		}

		/**
		 * 设置错误消息响应头的名称。
		 * @param errorHeaderName 错误头名称
		 */
		public void setErrorHeaderName(String errorHeaderName) {
			this.errorHeaderName = errorHeaderName;
		}

	}

}
