/*
 * Copyright 2013-2022 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriTemplate;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ALREADY_PREFIXED_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.getUriTemplateVariables;

/**
 * 前缀路径过滤器工厂。
 * <p>
 * 该过滤器为请求路径添加指定的前缀。 支持 SpEL 表达式动态取值，通过 UriTemplate 进行路径模板扩展。
 * <p>
 * 特性：
 * <ul>
 * <li>使用属性标记防止重复添加前缀；</li>
 * <li>保存原始请求 URL 供后续使用；</li>
 * <li>支持 URI 变量替换。</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - PrefixPath=/api/v1
 *   - PrefixPath=/services/{serviceId}
 * </pre>
 *
 * @author Spencer Gibb
 */
public class PrefixPathGatewayFilterFactory
		extends AbstractGatewayFilterFactory<PrefixPathGatewayFilterFactory.Config> {

	/**
	 * 前缀参数键名。
	 */
	public static final String PREFIX_KEY = "prefix";

	private static final Log log = LogFactory.getLog(PrefixPathGatewayFilterFactory.class);

	/**
	 * 默认构造方法。
	 */
	public PrefixPathGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PREFIX_KEY);
	}

	/**
	 * 创建前缀路径过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			final UriTemplate uriTemplate = new UriTemplate(config.prefix);

			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 检查是否已添加过前缀，防止重复
				boolean alreadyPrefixed = exchange.getAttributeOrDefault(GATEWAY_ALREADY_PREFIXED_ATTR, false);
				if (alreadyPrefixed) {
					return chain.filter(exchange);
				}
				exchange.getAttributes().put(GATEWAY_ALREADY_PREFIXED_ATTR, true);

				ServerHttpRequest req = exchange.getRequest();
				// 保存原始请求 URL
				addOriginalRequestUrl(exchange, req.getURI());

				// 扩展 URI 模板变量
				Map<String, String> uriVariables = getUriTemplateVariables(exchange);
				URI uri = uriTemplate.expand(uriVariables);

				// 拼接新路径：前缀 + 原始路径
				String newPath = uri.getRawPath() + req.getURI().getRawPath();
				exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
				ServerHttpRequest request = req.mutate().path(newPath).build();

				if (log.isTraceEnabled()) {
					log.trace("Prefixed URI with: " + config.prefix + " -> " + request.getURI());
				}

				return chain.filter(exchange.mutate().request(request).build());
			}

			@Override
			public String toString() {
				return filterToStringCreator(PrefixPathGatewayFilterFactory.this).append("prefix", config.getPrefix())
						.toString();
			}
		};
	}

	/**
	 * 前缀路径过滤器配置类。
	 */
	public static class Config {

		/** 路径前缀 */
		private String prefix;

		/**
		 * 获取路径前缀。
		 * @return 路径前缀
		 */
		public String getPrefix() {
			return prefix;
		}

		/**
		 * 设置路径前缀。
		 * @param prefix 路径前缀
		 */
		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}

	}

}
