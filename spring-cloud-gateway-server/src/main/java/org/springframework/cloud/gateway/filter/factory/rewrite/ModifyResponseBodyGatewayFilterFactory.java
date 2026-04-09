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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

import static java.util.function.Function.identity;
import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

/**
 * 响应体修改过滤器工厂，用于在响应发送前修改响应体的内容。
 *
 * <p>
 * 该过滤器允许开发者将下游服务返回的响应体转换为不同类型的新响应体， 常见使用场景包括：
 * <ul>
 * <li>响应体格式转换（如 XML 转 JSON、JSON 格式化）</li>
 * <li>响应体数据脱敏（如移除敏感字段）</li>
 * <li>响应体数据增强（如添加额外字段）</li>
 * <li>响应体压缩/解压处理（与 MessageBodyDecoder/MessageBodyEncoder 配合）</li>
 * </ul>
 *
 * <p>
 * 该过滤器支持自动处理 Content-Encoding 头（如 gzip）， 会自动解码后再转换，转换后再编码。
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: modify-response-route
 *           uri: http://example.com
 *           filters:
 *             - name: ModifyResponseBody
 *               args:
 *                 inClass: #{T(com.example.OriginalResponse)}
 *                 outClass: #{T(com.example.ModifiedResponse)}
 *                 rewriteFunction: #{@myRewriteFunction}
 *                 newContentType: application/json
 * </pre>
 *
 * <p>
 * 使用 Java DSL 配置： <pre>
 * {@code
 * &#64;Bean
 * public RouteLocator routes(RouteLocatorBuilder builder) {
 *     return builder.routes()
 *         .route("modify_response", r -> r
 *             .host("*.modify.org")
 *             .filters(f -> f
 *                 .modifyResponseBody(OriginalResponse.class, ModifiedResponse.class,
 *                     (exchange, payload) -> Mono.just(new ModifiedResponse(...))))
 *             .uri("http://example.com"))
 *         .build();
 * }
 * }
 * </pre>
 *
 * @author Spencer Gibb
 * @see RewriteFunction
 * @see MessageBodyDecoder
 * @see MessageBodyEncoder
 * @see CachedBodyOutputMessage
 */
public class ModifyResponseBodyGatewayFilterFactory
		extends AbstractGatewayFilterFactory<ModifyResponseBodyGatewayFilterFactory.Config> {

	/** 消息体解码器映射表，按编码类型索引 */
	private final Map<String, MessageBodyDecoder> messageBodyDecoders;

	/** 消息体编码器映射表，按编码类型索引 */
	private final Map<String, MessageBodyEncoder> messageBodyEncoders;

	/** HTTP 消息读取器列表 */
	private final List<HttpMessageReader<?>> messageReaders;

	/**
	 * 构造函数，初始化消息体解码器和编码器。
	 * @param messageReaders HTTP 消息读取器列表
	 * @param messageBodyDecoders 消息体解码器集合
	 * @param messageBodyEncoders 消息体编码器集合
	 */
	public ModifyResponseBodyGatewayFilterFactory(List<HttpMessageReader<?>> messageReaders,
			Set<MessageBodyDecoder> messageBodyDecoders, Set<MessageBodyEncoder> messageBodyEncoders) {
		super(Config.class);
		this.messageReaders = messageReaders;
		this.messageBodyDecoders = messageBodyDecoders.stream()
				.collect(Collectors.toMap(MessageBodyDecoder::encodingType, identity()));
		this.messageBodyEncoders = messageBodyEncoders.stream()
				.collect(Collectors.toMap(MessageBodyEncoder::encodingType, identity()));
	}

	/**
	 * 应用此过滤器，创建响应体修改过滤器。
	 * @param config 响应体修改配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		ModifyResponseGatewayFilter gatewayFilter = new ModifyResponseGatewayFilter(config);
		gatewayFilter.setFactory(this);
		return gatewayFilter;
	}

	/**
	 * 响应体修改过滤器配置类。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>inClass - 原始响应体的类型</li>
	 * <li>outClass - 修改后响应体的类型</li>
	 * <li>inHints - 输入提示信息</li>
	 * <li>outHints - 输出提示信息</li>
	 * <li>newContentType - 新的 Content-Type</li>
	 * <li>rewriteFunction - 重写函数，用于实现转换逻辑</li>
	 * </ul>
	 */
	public static class Config {

		/** 原始响应体的类型 */
		private Class inClass;

		/** 修改后响应体的类型 */
		private Class outClass;

		/** 输入提示信息 */
		private Map<String, Object> inHints;

		/** 输出提示信息 */
		private Map<String, Object> outHints;

		/** 新的 Content-Type */
		private String newContentType;

		/** 重写函数，用于实现响应体转换逻辑 */
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

		public Map<String, Object> getInHints() {
			return inHints;
		}

		public Config setInHints(Map<String, Object> inHints) {
			this.inHints = inHints;
			return this;
		}

		public Map<String, Object> getOutHints() {
			return outHints;
		}

		public Config setOutHints(Map<String, Object> outHints) {
			this.outHints = outHints;
			return this;
		}

		public String getNewContentType() {
			return newContentType;
		}

		public Config setNewContentType(String newContentType) {
			this.newContentType = newContentType;
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

	}

	/**
	 * 响应修改网关过滤器实现。
	 *
	 * <p>
	 * 该过滤器在响应写入之前修改响应体内容。
	 */
	public class ModifyResponseGatewayFilter implements GatewayFilter, Ordered {

		/** 过滤器配置 */
		private final Config config;

		/** 过滤器工厂引用 */
		private GatewayFilterFactory<Config> gatewayFilterFactory;

		/**
		 * 构造函数。
		 * @param config 过滤器配置
		 */
		public ModifyResponseGatewayFilter(Config config) {
			this.config = config;
		}

		/**
		 * 执行过滤逻辑，修改响应体。
		 * @param exchange 当前请求的 ServerWebExchange 对象
		 * @param chain 过滤器链
		 * @return 表示完成的可发布对象
		 */
		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			// 使用修改后的响应装饰器包装原始响应
			return chain.filter(exchange.mutate().response(new ModifiedServerHttpResponse(exchange, config)).build());
		}

		/**
		 * 获取过滤器执行顺序。
		 *
		 * <p>
		 * 该过滤器需要在 NettyWriteResponseFilter 之前执行， 以确保在响应被写入网络之前完成响应体修改。
		 * @return 过滤器执行顺序
		 */
		@Override
		public int getOrder() {
			return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
		}

		/**
		 * 返回此过滤器的字符串表示形式。
		 * @return 过滤器的字符串描述
		 */
		@Override
		public String toString() {
			Object obj = (this.gatewayFilterFactory != null) ? this.gatewayFilterFactory : this;
			return filterToStringCreator(obj).append("New content type", config.getNewContentType())
					.append("In class", config.getInClass()).append("Out class", config.getOutClass()).toString();
		}

		/**
		 * 设置过滤器工厂引用。
		 * @param gatewayFilterFactory 过滤器工厂
		 */
		public void setFactory(GatewayFilterFactory<Config> gatewayFilterFactory) {
			this.gatewayFilterFactory = gatewayFilterFactory;
		}

	}

	/**
	 * 修改后的 HTTP 响应装饰器。
	 *
	 * <p>
	 * 该类拦截响应体写入操作，在数据发送到客户端之前进行修改。
	 */
	protected class ModifiedServerHttpResponse extends ServerHttpResponseDecorator {

		/** 当前请求的 ServerWebExchange 对象 */
		private final ServerWebExchange exchange;

		/** 过滤器配置 */
		private final Config config;

		/**
		 * 构造函数。
		 * @param exchange 当前请求的 ServerWebExchange 对象
		 * @param config 过滤器配置
		 */
		public ModifiedServerHttpResponse(ServerWebExchange exchange, Config config) {
			super(exchange.getResponse());
			this.exchange = exchange;
			this.config = config;
		}

		/**
		 * 拦截响应体写入操作，进行响应体修改。
		 * @param body 原始响应体数据
		 * @return 表示完成的可发布对象
		 */
		@SuppressWarnings("unchecked")
		@Override
		public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
			Class inClass = config.getInClass();
			Class outClass = config.getOutClass();

			// 获取原始响应 Content-Type
			String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
			HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.add(HttpHeaders.CONTENT_TYPE, originalResponseContentType);

			// 准备客户端响应对象
			ClientResponse clientResponse = prepareClientResponse(body, httpHeaders);

			// 提取响应体，应用重写函数进行转换
			Mono modifiedBody = extractBody(exchange, clientResponse, inClass)
					.flatMap(originalBody -> config.getRewriteFunction().apply(exchange, originalBody))
					.switchIfEmpty(Mono.defer(() -> (Mono) config.getRewriteFunction().apply(exchange, null)));

			// 创建体插入器
			BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, outClass);
			CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange,
					exchange.getResponse().getHeaders());

			return bodyInserter.insert(outputMessage, new BodyInserterContext()).then(Mono.defer(() -> {
				// 写入修改后的响应体
				Mono<DataBuffer> messageBody = writeBody(getDelegate(), outputMessage, outClass);
				HttpHeaders headers = getDelegate().getHeaders();
				// 设置正确的 Content-Length
				if (!headers.containsKey(HttpHeaders.TRANSFER_ENCODING)
						|| headers.containsKey(HttpHeaders.CONTENT_LENGTH)) {
					messageBody = messageBody.doOnNext(data -> headers.setContentLength(data.readableByteCount()));
				}
				return getDelegate().writeWith(messageBody);
			}));
		}

		@Override
		public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
			return writeWith(Flux.from(body).flatMapSequential(p -> p));
		}

		/**
		 * 准备客户端响应对象。
		 * @param body 响应体数据
		 * @param httpHeaders HTTP 头信息
		 * @return 客户端响应对象
		 */
		private ClientResponse prepareClientResponse(Publisher<? extends DataBuffer> body, HttpHeaders httpHeaders) {
			ClientResponse.Builder builder;
			builder = ClientResponse.create(exchange.getResponse().getStatusCode(), messageReaders);
			return builder.headers(headers -> headers.putAll(httpHeaders)).body(Flux.from(body)).build();
		}

		/**
		 * 提取响应体，并根据 Content-Encoding 头进行解码。
		 * @param <T> 响应体类型
		 * @param exchange 当前请求的 ServerWebExchange 对象
		 * @param clientResponse 客户端响应对象
		 * @param inClass 响应体类型
		 * @return 解码后的响应体
		 */
		@SuppressWarnings("unchecked")
		private <T> Mono<T> extractBody(ServerWebExchange exchange, ClientResponse clientResponse, Class<T> inClass) {
			// 如果是 byte[] 类型，直接返回
			if (byte[].class.isAssignableFrom(inClass)) {
				return clientResponse.bodyToMono(inClass);
			}

			// 检查 Content-Encoding 头，如果存在匹配的解码器则进行解码
			List<String> encodingHeaders = exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.CONTENT_ENCODING);
			for (String encoding : encodingHeaders) {
				MessageBodyDecoder decoder = messageBodyDecoders.get(encoding);
				if (decoder != null) {
					return clientResponse.bodyToMono(byte[].class).publishOn(Schedulers.parallel()).map(decoder::decode)
							.map(bytes -> exchange.getResponse().bufferFactory().wrap(bytes))
							.map(buffer -> prepareClientResponse(Mono.just(buffer),
									exchange.getResponse().getHeaders()))
							.flatMap(response -> response.bodyToMono(inClass));
				}
			}

			return clientResponse.bodyToMono(inClass);
		}

		/**
		 * 写入响应体，并根据 Content-Encoding 头进行编码。
		 * @param httpResponse HTTP 响应对象
		 * @param message 缓存的输出消息
		 * @param outClass 输出类型
		 * @return 编码后的响应体
		 */
		private Mono<DataBuffer> writeBody(ServerHttpResponse httpResponse, CachedBodyOutputMessage message,
				Class<?> outClass) {
			Mono<DataBuffer> response = DataBufferUtils.join(message.getBody());
			if (byte[].class.isAssignableFrom(outClass)) {
				return response;
			}

			// 检查 Content-Encoding 头，如果存在匹配的编码器则进行编码
			List<String> encodingHeaders = httpResponse.getHeaders().getOrEmpty(HttpHeaders.CONTENT_ENCODING);
			for (String encoding : encodingHeaders) {
				MessageBodyEncoder encoder = messageBodyEncoders.get(encoding);
				if (encoder != null) {
					DataBufferFactory dataBufferFactory = httpResponse.bufferFactory();
					response = response.publishOn(Schedulers.parallel()).map(buffer -> {
						byte[] encodedResponse = encoder.encode(buffer);
						DataBufferUtils.release(buffer);
						return encodedResponse;
					}).map(dataBufferFactory::wrap);
					break;
				}
			}

			return response;
		}

	}

}
