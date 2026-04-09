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

import org.springframework.web.server.ServerWebExchange;

/**
 * 网关过滤器链接口，允许 {@link GatewayFilter} 将请求委托给链中的下一个过滤器。
 * <p>
 * 参照 Spring Framework 的 WebFilterChain 设计，该接口是 Gateway 过滤器链式调用的核心契约。
 * 当一个过滤器完成处理后，可通过调用此接口的 {@code filter} 方法将控制权传递给后续过滤器。
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface GatewayFilterChain {

	/**
	 * 将请求委托给链中的下一个 {@link GatewayFilter} 进行处理。
	 * @param exchange 当前服务器 Web 交换对象，包含请求和响应信息
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	Mono<Void> filter(ServerWebExchange exchange);

}
