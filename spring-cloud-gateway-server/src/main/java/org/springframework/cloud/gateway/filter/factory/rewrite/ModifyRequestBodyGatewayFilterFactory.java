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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.util.List;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 请求体修改过滤器工厂，用于在请求转发前修改请求体的内容。
 *
 * <p>
 * 该过滤器允许开发者将原始请求体转换为不同类型的新请求体， 常见使用场景包括：
 * <ul>
 * <li>请求体格式转换（如 JSON 转 XML、JSON 转表单）</li>
 * <li>请求体数据脱敏（如移除敏感字段）</li>
 * <li>请求体数据增强（如添加额外字段）</li>
 * <li>请求体压缩/解压处理</li>
 * </ul>
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: modify-request-route
 *           uri: http://example.com
 *           filters:
 *             - name: ModifyRequestBody
 *               args:
 *                 inClass: #{T(com.example.MyRequest)}
 *                 outClass: #{T(com.example.ModifiedRequest)}
 *                 rewriteFunction: #{@myRewriteFunction}
 *                 contentType: application/json
 * </pre>
 *
 * <p>
 * 使用 Java DSL 配置： <pre>
 * {@code
 * &#64;Bean
 * public RouteLocator routes(RouteLocatorBuilder builder) {
 *     return builder.routes()
 *         .route("modify_request", r -> r
 *             .host("*.modify.org")
 *             .filters(f -> f
 *                 .modifyRequestBody(MyRequest.class, ModifiedRequest.class,
 *                     (exchange, payload) -> Mono.just(new ModifiedRequest(...))))
 *             .uri("http://example.com"))
 *         .build();
 * }
 * }
 * </pre>
 *
 * @author Spencer Gibb
 * @see RewriteFunction
 * @see CachedBodyOutputMessage
 */
public class ModifyRequestBodyGatewayFilterFactory
		extends AbstractGatewayFilterFactory<ModifyRequestBodyGatewayFilterFactory.Config> {

	/** HTTP 消息读取器列表，用于读取请求体 */
	private final List<HttpMessageReader<?>> messageReaders;

	/**
	 * 使用默认配置的构造函数。
	 */
	public ModifyRequestBodyGatewayFilterFactory() {
		super(Config.class);
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
	}

	/**
	 * 使用自定义消息读取器的构造函数。
	 * @param messageReaders HTTP 消息读取器列表
	 */
	public ModifyRequestBodyGatewayFilterFactory(List<HttpMessageReader<?>> messageReaders) {
		super(Config.class);
		this.messageReaders = messageReaders;
	}

	/**
	 * 应用此过滤器，创建请求体修改过滤器。
	 * @param config 请求体修改配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	@SuppressWarnings("unchecked")
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，修改请求体。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				Class inClass = config.getInClass();
				ServerRequest serverRequest = ServerRequest.create(exchange, messageReaders);

				// 将请求体转换为指定类型，然后使用重写函数进行处理
				Mono<?> modifiedBody = serverRequest.bodyToMono(inClass)
						.flatMap(originalBody -> config.getRewriteFunction().apply(exchange, originalBody))
						.switchIfEmpty(Mono.defer(() -> (Mono) config.getRewriteFunction().apply(exchange, null)));

				// 创建体插入器，将修改后的请求体写入输出消息
				BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, config.getOutClass());
				HttpHeaders headers = new HttpHeaders();
				headers.putAll(exchange.getRequest().getHeaders());

				// 移除 Content-Length 头，新内容长度将由 bodyInserter 计算
				headers.remove(HttpHeaders.CONTENT_LENGTH);

				// 如果修改了 Content-Type，设置新的 Content-Type
				if (config.getContentType() != null) {
					headers.set(HttpHeaders.CONTENT_TYPE, config.getContentType());
				}

				// 创建缓存的输出消息
				CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
				return bodyInserter.insert(outputMessage, new BodyInserterContext())
						// .log("modify_request", Level.INFO)
						.then(Mono.defer(() -> {
							// 创建请求装饰器，用修改后的请求体替换原始请求体
							ServerHttpRequest decorator = decorate(exchange, headers, outputMessage);
							return chain.filter(exchange.mutate().request(decorator).build());
						})).onErrorResume((Function<Throwable, Mono<Void>>) throwable -> release(exchange,
								outputMessage, throwable));
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(ModifyRequestBodyGatewayFilterFactory.this)
						.append("Content type", config.getContentType()).append("In class", config.getInClass())
						.append("Out class", config.getOutClass()).toString();
			}
		};
	}

	/**
	 * 释放缓存的数据缓冲区资源。
	 * @param exchange 当前请求的 ServerWebExchange 对象
	 * @param outputMessage 缓存的输出消息
	 * @param throwable 发生的异常
	 * @return 表示完成的可发布对象
	 */
	protected Mono<Void> release(ServerWebExchange exchange, CachedBodyOutputMessage outputMessage,
			Throwable throwable) {
		if (outputMessage.isCached()) {
			return outputMessage.getBody().map(DataBufferUtils::release).then(Mono.error(throwable));
		}
		return Mono.error(throwable);
	}

	/**
	 * 创建请求装饰器，用于替换原始请求体。
	 * @param exchange 当前请求的 ServerWebExchange 对象
	 * @param headers HTTP 头信息
	 * @param outputMessage 缓存的输出消息
	 * @return 请求装饰器
	 */
	ServerHttpRequestDecorator decorate(ServerWebExchange exchange, HttpHeaders headers,
			CachedBodyOutputMessage outputMessage) {
		return new ServerHttpRequestDecorator(exchange.getRequest()) {
			@Override
			public HttpHeaders getHeaders() {
				long contentLength = headers.getContentLength();
				HttpHeaders httpHeaders = new HttpHeaders();
				httpHeaders.putAll(headers);
				if (contentLength > 0) {
					httpHeaders.setContentLength(contentLength);
				}
				else {
					// 使用 chunked 传输编码
					httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
				}
				return httpHeaders;
			}

			@Override
			public Flux<DataBuffer> getBody() {
				return outputMessage.getBody();
			}
		};
	}

	/**
	 * 请求体修改过滤器配置类。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>inClass - 原始请求体的类型</li>
	 * <li>outClass - 修改后请求体的类型</li>
	 * <li>contentType - 新的 Content-Type（可选）</li>
	 * <li>rewriteFunction - 重写函数，用于实现转换逻辑</li>
	 * </ul>
	 */
	public static class Config {

		/** 原始请求体的类型 */
		private Class inClass;

		/** 修改后请求体的类型 */
		private Class outClass;

		/** 新的 Content-Type（可选） */
		private String contentType;

		/** 重写函数，用于实现请求体转换逻辑 */
		private RewriteFunction rewriteFunction;

		// Getters and Setters
		public Class getInClass() {
			return inClass;
		}

		public Config setInClass(Class inClass) {
			this.inClass = inClass;
			return this;
		}

		public Class getOutClass() {
			return outClass;
		}

		public Config setOutClass(Class outClass) {
			this.outClass = outClass;
			return this;
		}

		public RewriteFunction getRewriteFunction() {
			return rewriteFunction;
		}

		public Config setRewriteFunction(RewriteFunction rewriteFunction) {
			this.rewriteFunction = rewriteFunction;
			return this;
		}

		/**
		 * 设置重写函数，同时指定输入和输出类型。
		 * @param <T> 输入类型
		 * @param <R> 输出类型
		 * @param inClass 输入类型
		 * @param outClass 输出类型
		 * @param rewriteFunction 重写函数
		 * @return 配置对象，支持链式调用
		 */
		public <T, R> Config setRewriteFunction(Class<T> inClass, Class<R> outClass,
				RewriteFunction<T, R> rewriteFunction) {
			setInClass(inClass);
			setOutClass(outClass);
			setRewriteFunction(rewriteFunction);
			return this;
		}

		public String getContentType() {
			return contentType;
		}

		public Config setContentType(String contentType) {
			this.contentType = contentType;
			return this;
		}

	}

}
