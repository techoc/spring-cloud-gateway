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
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriTemplate;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.getUriTemplateVariables;

/**
 * 设置路径过滤器工厂。
 * <p>
 * 该过滤器使用 URI 模板重新设置请求路径，支持变量替换。 与 {@link PrefixPathGatewayFilterFactory}
 * 不同，该过滤器会完全替换路径而非追加。
 * <p>
 * 配置参数：
 * <ul>
 * <li>template：路径模板，支持 URI 变量（如 {segment}）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - SetPath=/api/{segment}
 * </pre>
 *
 * @author Spencer Gibb
 */
public class SetPathGatewayFilterFactory extends AbstractGatewayFilterFactory<SetPathGatewayFilterFactory.Config> {

	/**
	 * 模板参数键名。
	 */
	public static final String TEMPLATE_KEY = "template";

	/**
	 * 默认构造方法。
	 */
	public SetPathGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(TEMPLATE_KEY);
	}

	/**
	 * 创建设置路径过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		UriTemplate uriTemplate = new UriTemplate(config.template);

		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest req = exchange.getRequest();
				// 保存原始请求 URL
				addOriginalRequestUrl(exchange, req.getURI());

				// 获取 URI 变量进行替换
				Map<String, String> uriVariables = getUriTemplateVariables(exchange);

				URI uri = uriTemplate.expand(uriVariables);
				String newPath = uri.getRawPath();

				// 更新请求 URL
				exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);

				ServerHttpRequest request = req.mutate().path(newPath).build();

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(SetPathGatewayFilterFactory.this).append("template", config.getTemplate())
						.toString();
			}
		};
	}

	/**
	 * 设置路径过滤器配置类。
	 */
	public static class Config {

		/** 路径模板 */
		private String template;

		/**
		 * 获取路径模板。
		 * @return 路径模板字符串
		 */
		public String getTemplate() {
			return template;
		}

		/**
		 * 设置路径模板。
		 * @param template 路径模板字符串
		 */
		public void setTemplate(String template) {
			this.template = template;
		}

	}

}
