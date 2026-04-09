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
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * 剥离前缀路径过滤器工厂。
 * <p>
 * 该过滤器移除请求路径的前 N 个路径段（前缀）。 例如路径 /api/v1/users，使用 parts=2 会变成 /users。
 * <p>
 * 配置参数：
 * <ul>
 * <li>parts：需要剥离的路径段数量（默认 1）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - StripPrefix=1
 *   - StripPrefix=2
 * </pre>
 *
 * @author Ryan Baxter
 */
public class StripPrefixGatewayFilterFactory
		extends AbstractGatewayFilterFactory<StripPrefixGatewayFilterFactory.Config> {

	/**
	 * 路径段数参数键名。
	 */
	public static final String PARTS_KEY = "parts";

	/**
	 * 默认构造方法。
	 */
	public StripPrefixGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PARTS_KEY);
	}

	/**
	 * 创建剥离前缀路径过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest request = exchange.getRequest();
				// 保存原始请求 URL
				addOriginalRequestUrl(exchange, request.getURI());
				String path = request.getURI().getRawPath();
				String[] originalParts = StringUtils.tokenizeToStringArray(path, "/");

				// 构建新路径，移除前缀段
				StringBuilder newPath = new StringBuilder("/");
				for (int i = 0; i < originalParts.length; i++) {
					if (i >= config.getParts()) {
						// 仅在第二个或更高段时添加斜杠
						if (newPath.length() > 1) {
							newPath.append('/');
						}
						newPath.append(originalParts[i]);
					}
				}
				// 保留尾部斜杠
				if (newPath.length() > 1 && path.endsWith("/")) {
					newPath.append('/');
				}

				ServerHttpRequest newRequest = request.mutate().path(newPath.toString()).build();

				exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newRequest.getURI());

				return chain.filter(exchange.mutate().request(newRequest).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(StripPrefixGatewayFilterFactory.this).append("parts", config.getParts())
						.toString();
			}
		};
	}

	/**
	 * 剥离前缀路径过滤器配置类。
	 */
	public static class Config {

		/** 需要剥离的路径段数量，默认 1 */
		private int parts = 1;

		/**
		 * 获取路径段数。
		 * @return 路径段数
		 */
		public int getParts() {
			return parts;
		}

		/**
		 * 设置路径段数。
		 * @param parts 路径段数
		 */
		public void setParts(int parts) {
			this.parts = parts;
		}

	}

}
