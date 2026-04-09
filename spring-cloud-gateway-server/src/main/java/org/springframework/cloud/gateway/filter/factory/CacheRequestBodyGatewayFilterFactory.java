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
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR;

/**
 * 缓存请求体过滤器工厂。
 * <p>
 * 该过滤器将 HTTP 请求体缓存到内存中，使得请求体可以被多次读取。 主要用于以下场景：
 * <ul>
 * <li>谓词（Predicate）需要读取请求体进行匹配判断；</li>
 * <li>后续过滤器需要再次访问请求体数据。</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - CacheRequestBody=java.lang.String
 *   - CacheRequestBody=org.example.MyRequest
 * </pre>
 *
 * @author weizibin
 */
public class CacheRequestBodyGatewayFilterFactory
		extends AbstractGatewayFilterFactory<CacheRequestBodyGatewayFilterFactory.Config> {

	/**
	 * 原始请求体备份属性名，用于处理嵌套缓存场景。
	 */
	static final String CACHED_ORIGINAL_REQUEST_BODY_BACKUP_ATTR = "cachedOriginalRequestBodyBackup";

	/** HTTP 消息读取器列表，用于解析请求体 */
	private final List<HttpMessageReader<?>> messageReaders;

	/**
	 * 默认构造方法，使用默认的 HTTP 消息读取器。
	 */
	public CacheRequestBodyGatewayFilterFactory() {
		super(CacheRequestBodyGatewayFilterFactory.Config.class);
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
	}

	/**
	 * 构造方法，指定自定义的 HTTP 消息读取器列表。
	 * @param messageReaders HTTP 消息读取器列表
	 */
	public CacheRequestBodyGatewayFilterFactory(List<HttpMessageReader<?>> messageReaders) {
		super(CacheRequestBodyGatewayFilterFactory.Config.class);
		this.messageReaders = messageReaders;
	}

	/**
	 * 创建缓存请求体过滤器。
	 * <p>
	 * 处理流程：
	 * <ol>
	 * <li>仅处理 HTTP/HTTPS 请求；</li>
	 * <li>若请求体已缓存，直接继续过滤链；</li>
	 * <li>否则，通过 {@link ServerWebExchangeUtils#cacheRequestBodyAndRequest} 缓存请求体；</li>
	 * <li>将请求体反序列化为配置的类型并存储到 exchange 属性中。</li>
	 * </ol>
	 * @param config 缓存配置，包含要反序列成的目标类
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(CacheRequestBodyGatewayFilterFactory.Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				URI requestUri = request.getURI();
				String scheme = requestUri.getScheme();

				// 仅记录 HTTP 请求（包括 HTTPS）
				if ((!"http".equals(scheme) && !"https".equals(scheme))) {
					return chain.filter(exchange);
				}

				// 若请求体已缓存，直接继续
				Object cachedBody = exchange.getAttribute(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR);
				if (cachedBody != null) {
					return chain.filter(exchange);
				}

				// 缓存请求体
				return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, (serverHttpRequest) -> {
					final ServerRequest serverRequest = ServerRequest
							.create(exchange.mutate().request(serverHttpRequest).build(), messageReaders);
					// 将请求体反序列化为配置的类型并缓存
					return serverRequest.bodyToMono((config.getBodyClass())).doOnNext(objectValue -> {
						Object previousCachedBody = exchange.getAttributes()
								.put(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR, objectValue);
						if (previousCachedBody != null) {
							// 保存之前的缓存体（嵌套场景）
							exchange.getAttributes().put(CACHED_ORIGINAL_REQUEST_BODY_BACKUP_ATTR, previousCachedBody);
						}
					}).then(Mono.defer(() -> {
						// 使用缓存的请求对象继续过滤链
						ServerHttpRequest cachedRequest = exchange
								.getAttribute(CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR);
						Assert.notNull(cachedRequest, "cache request shouldn't be null");
						exchange.getAttributes().remove(CACHED_SERVER_HTTP_REQUEST_DECORATOR_ATTR);
						return chain.filter(exchange.mutate().request(cachedRequest).build()).doFinally(s -> {
							// 清理原始缓存体
							Object backupCachedBody = exchange.getAttributes()
									.get(CACHED_ORIGINAL_REQUEST_BODY_BACKUP_ATTR);
							if (backupCachedBody instanceof DataBuffer) {
								DataBufferUtils.release((DataBuffer) backupCachedBody);
							}
						});
					}));
				});
			}

			@Override
			public String toString() {
				return filterToStringCreator(CacheRequestBodyGatewayFilterFactory.this)
						.append("Body class", config.getBodyClass()).toString();
			}
		};
	}

	/**
	 * 缓存请求体过滤器配置类。
	 */
	public static class Config {

		/** 请求体反序列化的目标类型 */
		private Class<?> bodyClass;

		/**
		 * 获取请求体类。
		 * @return 目标类型 Class
		 */
		public Class<?> getBodyClass() {
			return bodyClass;
		}

		/**
		 * 设置请求体类。
		 * @param bodyClass 目标类型 Class
		 */
		public void setBodyClass(Class<?> bodyClass) {
			this.bodyClass = bodyClass;
		}

	}

}
