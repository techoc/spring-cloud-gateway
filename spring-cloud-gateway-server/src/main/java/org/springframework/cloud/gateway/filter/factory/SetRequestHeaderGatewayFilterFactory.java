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
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 设置请求头过滤器工厂，用于在请求转发前设置指定的请求头。
 *
 * <p>
 * 该过滤器会覆盖已有的同名请求头值，或者添加新的请求头。 支持动态值，可以通过 ${variable} 语法引用交换对象中的属性。
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
 *             - name: SetRequestHeader
 *               args:
 *                 name: X-Request-Id
 *                 value: ${server.http.port}-${日期}
 * </pre>
 *
 * <p>
 * 快捷配置方式： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: my-route
 *           uri: http://example.com
 *           filters:
 *             - SetRequestHeader=X-Request-Id,12345
 * </pre>
 *
 * <p>
 * 支持的变量替换：
 * <ul>
 * <li>${header.xxx} - 引用请求头</li>
 * <li>${param.xxx} - 引用查询参数</li>
 * <li>${uri} - 当前请求 URI</li>
 * <li>${route.id} - 路由 ID</li>
 * <li>${日期} - 当前日期（支持格式：${日期;格式}）</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see AbstractNameValueGatewayFilterFactory
 */
public class SetRequestHeaderGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	/**
	 * 应用此过滤器，创建请求头设置过滤器。
	 * @param config 包含请求头名称和值的配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，设置指定的请求头。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 展开配置值中的变量引用
				String value = ServerWebExchangeUtils.expand(exchange, config.getValue());

				// 创建更新了请求头的请求对象
				ServerHttpRequest request = exchange.getRequest().mutate()
						.headers(httpHeaders -> httpHeaders.set(config.name, value)).build();

				// 使用更新后的请求继续执行过滤器链
				return chain.filter(exchange.mutate().request(request).build());
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(SetRequestHeaderGatewayFilterFactory.this)
						.append(config.getName(), config.getValue()).toString();
			}
		};
	}

}
