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

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 添加请求参数过滤器工厂。
 * <p>
 * 该过滤器向请求的查询字符串中添加指定的参数。 支持 SpEL 表达式动态取值，通过 {@link ServerWebExchangeUtils#expand} 方法解析。
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - AddRequestParameter=name, value
 *   - AddRequestParameter=debug, true
 * </pre>
 * <p>
 * 注意：参数值未进行 URL 编码，使用时需确保值不包含特殊字符。
 *
 * @author Spencer Gibb
 */
public class AddRequestParameterGatewayFilterFactory extends AbstractNameValueGatewayFilterFactory {

	/**
	 * 创建添加请求参数过滤器。
	 * <p>
	 * 过滤器将配置的参数追加到请求的查询字符串中。 保留原有的查询参数，在末尾添加新参数。
	 * @param config 名称-值配置对象
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(NameValueConfig config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				URI uri = exchange.getRequest().getURI();
				StringBuilder query = new StringBuilder();
				String originalQuery = uri.getRawQuery();

				// 保留原有查询参数
				if (StringUtils.hasText(originalQuery)) {
					query.append(originalQuery);
					// 若原有参数不以 & 结尾，添加分隔符
					if (originalQuery.charAt(originalQuery.length() - 1) != '&') {
						query.append('&');
					}
				}

				// 解析值中的 SpEL 表达式
				String value = ServerWebExchangeUtils.expand(exchange, config.getValue());
				// TODO: 是否需要进行 URL 编码？
				query.append(config.getName());
				query.append('=');
				query.append(value);

				try {
					// 构建新的 URI
					URI newUri = UriComponentsBuilder.fromUri(uri).replaceQuery(query.toString()).build(true).toUri();

					ServerHttpRequest request = exchange.getRequest().mutate().uri(newUri).build();

					return chain.filter(exchange.mutate().request(request).build());
				}
				catch (RuntimeException ex) {
					throw new IllegalStateException("Invalid URI query: \"" + query.toString() + "\"");
				}
			}

			@Override
			public String toString() {
				return filterToStringCreator(AddRequestParameterGatewayFilterFactory.this)
						.append(config.getName(), config.getValue()).toString();
			}
		};
	}

}
