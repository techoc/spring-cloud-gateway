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

import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 移除请求头过滤器工厂。
 * <p>
 * 该过滤器在请求转发前移除指定的 HTTP 请求头。
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - RemoveRequestHeader=X-Remove-Header
 * </pre>
 *
 * @author Spencer Gibb
 */
public class RemoveRequestHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

	/**
	 * 默认构造方法。
	 */
	public RemoveRequestHeaderGatewayFilterFactory() {
		super(NameConfig.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY);
	}

	/**
	 * 创建移除请求头过滤器。
	 * @param config 名称配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(NameConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 创建新请求，移除指定的请求头
				ServerHttpRequest request = exchange.getRequest().mutate()
						.headers(httpHeaders -> httpHeaders.remove(config.getName())).build();

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(RemoveRequestHeaderGatewayFilterFactory.this)
						.append("name", config.getName()).toString();
			}
		};
	}

}
