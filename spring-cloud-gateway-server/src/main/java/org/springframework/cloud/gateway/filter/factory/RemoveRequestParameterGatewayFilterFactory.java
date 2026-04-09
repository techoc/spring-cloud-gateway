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
import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.util.CollectionUtils.unmodifiableMultiValueMap;

/**
 * 请求参数移除过滤器工厂，用于从请求 URL 中移除指定的查询参数。
 *
 * <p>
 * 该过滤器允许在请求转发之前从查询字符串中删除特定的参数， 常用于隐藏敏感信息、统一参数格式或去除不必要的参数。
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
 *             - name: RemoveRequestParameter
 *               args:
 *                 name: token  # 移除名为 token 的参数
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
 *             - RemoveRequestParameter=token
 * </pre>
 *
 * <p>
 * 示例说明：
 * <ul>
 * <li>请求 URL: /api/users?id=123&token=abc123</li>
 * <li>过滤器移除参数: token</li>
 * <li>转发 URL: /api/users?id=123</li>
 * </ul>
 *
 * @author Thirunavukkarasu Ravichandran
 * @see AbstractGatewayFilterFactory
 */
public class RemoveRequestParameterGatewayFilterFactory
		extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

	/**
	 * 构造函数，使用 NameConfig 配置类初始化过滤器工厂。
	 */
	public RemoveRequestParameterGatewayFilterFactory() {
		super(NameConfig.class);
	}

	/**
	 * 返回快捷配置的字段顺序。
	 * @return 包含字段名的列表，用于快捷配置解析
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY);
	}

	/**
	 * 应用此过滤器，创建参数移除过滤器。
	 * @param config 包含要移除的参数名称的配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(NameConfig config) {
		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，移除指定的查询参数。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();

				// 创建查询参数的可变副本
				MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>(request.getQueryParams());
				// 移除指定的参数
				queryParams.remove(config.getName());

				// 构建新的 URI，替换查询参数
				URI newUri = UriComponentsBuilder.fromUri(request.getURI())
						.replaceQueryParams(unmodifiableMultiValueMap(queryParams)).build().toUri();

				// 创建更新了 URI 的请求对象
				ServerHttpRequest updatedRequest = exchange.getRequest().mutate().uri(newUri).build();

				// 使用更新后的请求继续执行过滤器链
				return chain.filter(exchange.mutate().request(updatedRequest).build());
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(RemoveRequestParameterGatewayFilterFactory.this)
						.append("name", config.getName()).toString();
			}
		};
	}

}
