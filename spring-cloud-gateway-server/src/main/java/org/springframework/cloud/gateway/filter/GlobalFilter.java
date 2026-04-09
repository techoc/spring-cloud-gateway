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
 * 全局过滤器接口，用于拦截式、链式处理网关请求。
 * <p>
 * 与 {@link GatewayFilter} 不同，全局过滤器作用于所有匹配的路由，无需为每条路由单独配置。
 * 可用于实现横切、应用无关的需求，如安全认证、全局日志、监控指标等。
 * <p>
 * 参照 Spring Framework 的 WebFilter 接口设计，仅作用于已匹配的网关路由。
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 */
public interface GlobalFilter {

	/**
	 * 处理 Web 请求，并可选地通过给定的 {@link GatewayFilterChain} 将请求委托给下一个过滤器。
	 * @param exchange 当前服务器 Web 交换对象，包含请求和响应信息
	 * @param chain 过滤器链，提供将请求委托给下一个过滤器的能力
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain);

}
