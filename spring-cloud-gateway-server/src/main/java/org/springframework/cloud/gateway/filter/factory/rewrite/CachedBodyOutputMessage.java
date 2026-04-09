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

import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.web.server.ServerWebExchange;

/**
 * 缓存响应体的输出消息实现类。
 *
 * <p>
 * 该类实现了 {@link ReactiveHttpOutputMessage} 接口，用于在修改响应体过滤器中 缓存响应体数据，以便在后续处理中能够多次读取和修改。
 *
 * <p>
 * 主要用途：
 * <ul>
 * <li>在 {@link ModifyResponseBodyGatewayFilterFactory} 中缓存下游服务的响应体</li>
 * <li>支持在响应发送前修改响应体内容</li>
 * <li>将响应体存储为字段，以便后续处理</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see ReactiveHttpOutputMessage
 * @see ModifyResponseBodyGatewayFilterFactory
 */
public class CachedBodyOutputMessage implements ReactiveHttpOutputMessage {

	/** 数据缓冲区工厂，用于创建数据缓冲区 */
	private final DataBufferFactory bufferFactory;

	/** HTTP 头信息 */
	private final HttpHeaders httpHeaders;

	/** 标记响应体是否已被缓存 */
	private boolean cached = false;

	/** 缓存的响应体数据 */
	private Flux<DataBuffer> body = null;

	/**
	 * 构造函数。
	 * @param exchange 当前请求的 ServerWebExchange 对象
	 * @param httpHeaders HTTP 头信息
	 */
	public CachedBodyOutputMessage(ServerWebExchange exchange, HttpHeaders httpHeaders) {
		this.bufferFactory = exchange.getResponse().bufferFactory();
		this.httpHeaders = httpHeaders;
	}

	/**
	 * 在提交响应之前执行的回调方法。
	 * @param action 提交时要执行的操作
	 */
	@Override
	public void beforeCommit(Supplier<? extends Mono<Void>> action) {

	}

	/**
	 * 检查响应是否已提交。
	 * @return 始终返回 false，表示响应尚未提交
	 */
	@Override
	public boolean isCommitted() {
		return false;
	}

	/**
	 * 检查响应体是否已被缓存。
	 * @return 如果已缓存返回 true，否则返回 false
	 */
	boolean isCached() {
		return this.cached;
	}

	/**
	 * 获取 HTTP 头信息。
	 * @return HTTP 头信息对象
	 */
	@Override
	public HttpHeaders getHeaders() {
		return this.httpHeaders;
	}

	/**
	 * 获取数据缓冲区工厂。
	 * @return 数据缓冲区工厂
	 */
	@Override
	public DataBufferFactory bufferFactory() {
		return this.bufferFactory;
	}

	/**
	 * 获取缓存的响应体数据。
	 *
	 * <p>
	 * 如果响应体尚未设置（即从未调用过 {@link #writeWith} 方法）， 则返回一个包含 IllegalStateException 的错误流。
	 * @return 响应体的 DataBuffer 数据流，如果未设置则返回错误流
	 * @see Flux
	 */
	public Flux<DataBuffer> getBody() {
		if (body == null) {
			return Flux
					.error(new IllegalStateException("The body is not set. " + "Did handling complete with success?"));
		}
		return this.body;
	}

	/**
	 * 使用指定的数据缓冲区写入响应体。
	 *
	 * <p>
	 * 此方法会将响应体数据缓存起来，并标记为已缓存状态。
	 * @param body 要写入的数据缓冲区发布者
	 * @return 表示完成的可发布对象
	 */
	public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
		this.body = Flux.from(body);
		this.cached = true;
		return Mono.empty();
	}

	/**
	 * 使用多个数据缓冲区写入并刷新响应。
	 * @param body 数据缓冲区发布者的发布者
	 * @return 表示完成的可发布对象
	 */
	@Override
	public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
		return writeWith(Flux.from(body).flatMap(p -> p));
	}

	/**
	 * 标记响应完成。
	 *
	 * <p>
	 * 通过写入一个空的响应体来标记响应完成。
	 * @return 表示完成的可发布对象
	 */
	@Override
	public Mono<Void> setComplete() {
		return writeWith(Flux.empty());
	}

}
