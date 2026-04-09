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

package org.springframework.cloud.gateway.filter;

import reactor.core.publisher.Mono;

import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

/**
 * 带有执行顺序的网关过滤器包装类。
 * <p>
 * 该类实现了 {@link GatewayFilter} 和 {@link Ordered} 接口，通过装饰器模式将一个 {@link GatewayFilter}
 * 实例包装，并赋予其执行优先级。过滤器链中的过滤器将按照 {@code order} 值从小到大的顺序执行（值越小，优先级越高）。
 *
 * @author Spencer Gibb
 */
public class OrderedGatewayFilter implements GatewayFilter, Ordered {

	/** 被委托的实际过滤器实例 */
	private final GatewayFilter delegate;

	/** 过滤器执行顺序，值越小优先级越高 */
	private final int order;

	/**
	 * 构造一个带有顺序的网关过滤器。
	 * @param delegate 实际执行过滤逻辑的 {@link GatewayFilter} 实例
	 * @param order 过滤器的执行顺序，值越小优先级越高
	 */
	public OrderedGatewayFilter(GatewayFilter delegate, int order) {
		this.delegate = delegate;
		this.order = order;
	}

	/**
	 * 获取被委托的实际过滤器实例。
	 * @return 被包装的 {@link GatewayFilter} 实例
	 */
	public GatewayFilter getDelegate() {
		return delegate;
	}

	/**
	 * 将过滤请求委托给包装的 {@link GatewayFilter} 执行。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return this.delegate.filter(exchange, chain);
	}

	/**
	 * 返回过滤器的执行顺序。
	 * @return 过滤器执行顺序值，值越小优先级越高
	 */
	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * 返回该过滤器的字符串表示形式，包含委托过滤器信息和执行顺序。
	 * @return 格式为 "[delegate, order = N]" 的字符串
	 */
	@Override
	public String toString() {
		return new StringBuilder("[").append(delegate).append(", order = ").append(order).append("]").toString();
	}

}
