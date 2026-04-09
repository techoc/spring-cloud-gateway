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
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 移除响应头过滤器工厂。
 * <p>
 * 该过滤器在响应返回客户端之前，移除指定的 HTTP 响应头。
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - RemoveResponseHeader=X-Remove-Header
 * </pre>
 *
 * @author Spencer Gibb
 */
public class RemoveResponseHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

	/**
	 * 默认构造方法。
	 */
	public RemoveResponseHeaderGatewayFilterFactory() {
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
	 * 创建移除响应头过滤器。
	 * @param config 名称配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(NameConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 在过滤器链执行完成后移除响应头
				return chain.filter(exchange)
						.then(Mono.fromRunnable(() -> exchange.getResponse().getHeaders().remove(config.getName())));
			}

			@Override
			public String toString() {
				return filterToStringCreator(RemoveResponseHeaderGatewayFilterFactory.this)
						.append("name", config.getName()).toString();
			}
		};
	}

}
