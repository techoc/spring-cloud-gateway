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
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * {@link SaveSessionGatewayFilterFactory} 用于在执行网关过滤器链之前保存当前的 {@link WebSession}。
 *
 * <p>
 * 此过滤器在以下场景特别有用：当 WebSession 是延迟加载的（例如使用 Spring Session MongoDB 存储），
 * 而需要进行远程调用时，必须在远程调用之前调用 {@link WebSession#save()} 方法来确保会话数据被持久化。
 *
 * <p>
 * 使用示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       default-filters:
 *         - SaveSession
 * </pre>
 *
 * @author Greg Turnquist
 * @see WebSession
 * @see org.springframework.cloud.gateway.filter.GatewayFilterChain
 */
public class SaveSessionGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

	/**
	 * 应用此过滤器，创建用于保存 WebSession 的 GatewayFilter。
	 * @param config 过滤器配置对象（此过滤器不使用配置参数，传入的值会被忽略）
	 * @return 新的 GatewayFilter 实例，用于在过滤器链执行前保存 WebSession
	 */
	@Override
	public GatewayFilter apply(Object config) {
		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑：先获取 WebSession 并保存，然后继续执行过滤器链。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链，用于继续处理请求
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 先保存 WebSession，然后继续执行过滤器链
				return exchange.getSession().map(WebSession::save).then(chain.filter(exchange));
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(SaveSessionGatewayFilterFactory.this).toString();
			}
		};
	}

}
