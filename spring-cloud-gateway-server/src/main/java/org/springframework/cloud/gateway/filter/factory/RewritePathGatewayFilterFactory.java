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
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * 路径重写过滤器工厂，使用正则表达式在请求转发前修改请求路径。
 *
 * <p>
 * 该过滤器基于 Java 正则表达式实现路径的重写功能， 可以将请求路径按照指定的模式进行转换后转发到下游服务。
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: rewrite-path-route
 *           uri: http://example.com
 *           predicates:
 *             - Path=/api/**
 *           filters:
 *             - RewritePath=/api(?{@code <segment>}/?.*), $\{segment}
 * </pre>
 *
 * <p>
 * 示例说明：
 * <ul>
 * <li>原始请求路径: /api/users</li>
 * <li>正则表达式: /api(?{@code <segment>}/?.*)</li>
 * <li>替换表达式: ${segment}</li>
 * <li>重写后路径: /users</li>
 * </ul>
 *
 * <p>
 * 注意事项：
 * <ul>
 * <li>替换表达式中的 $ 需要转义为 $\</li>
 * <li>支持命名捕获组 (?{@code <name>pattern}) 语法</li>
 * <li>正则表达式使用 Java 标准正则表达式语法</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see AbstractGatewayFilterFactory
 * @see java.util.regex.Pattern
 */
public class RewritePathGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RewritePathGatewayFilterFactory.Config> {

	/**
	 * 配置参数名称：正则表达式。
	 */
	public static final String REGEXP_KEY = "regexp";

	/**
	 * 配置参数名称：替换表达式。
	 */
	public static final String REPLACEMENT_KEY = "replacement";

	/**
	 * 构造函数，使用配置类初始化过滤器工厂。
	 */
	public RewritePathGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置的字段顺序。
	 * @return 包含字段名的列表，用于快捷配置解析
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(REGEXP_KEY, REPLACEMENT_KEY);
	}

	/**
	 * 应用此过滤器，创建路径重写过滤器。
	 * @param config 包含正则表达式和替换表达式的配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		// 处理转义的美元符号（$ → $\）
		String replacement = config.replacement.replace("$\\", "$");

		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，重写请求路径。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				ServerHttpRequest req = exchange.getRequest();

				// 保存原始请求 URL，用于调试和日志
				addOriginalRequestUrl(exchange, req.getURI());

				// 获取当前请求路径
				String path = req.getURI().getRawPath();
				// 使用正则表达式替换路径
				String newPath = path.replaceAll(config.regexp, replacement);

				// 创建更新了路径的请求对象
				ServerHttpRequest request = req.mutate().path(newPath).build();

				// 更新网关请求 URL 属性
				exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, request.getURI());

				// 使用更新后的请求继续执行过滤器链
				return chain.filter(exchange.mutate().request(request).build());
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(RewritePathGatewayFilterFactory.this)
						.append(config.getRegexp(), replacement).toString();
			}
		};
	}

	/**
	 * 路径重写过滤器配置类。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>regexp - Java 正则表达式</li>
	 * <li>replacement - 替换表达式，支持捕获组引用</li>
	 * </ul>
	 */
	public static class Config {

		/** Java 正则表达式 */
		private String regexp;

		/** 替换表达式 */
		private String replacement;

		/**
		 * 获取正则表达式。
		 * @return 正则表达式字符串
		 */
		public String getRegexp() {
			return regexp;
		}

		/**
		 * 设置正则表达式。
		 * @param regexp 正则表达式，不能为空
		 * @return 配置对象，支持链式调用
		 */
		public Config setRegexp(String regexp) {
			Assert.hasText(regexp, "regexp must have a value");
			this.regexp = regexp;
			return this;
		}

		/**
		 * 获取替换表达式。
		 * @return 替换表达式字符串
		 */
		public String getReplacement() {
			return replacement;
		}

		/**
		 * 设置替换表达式。
		 * @param replacement 替换表达式，不能为 null
		 * @return 配置对象，支持链式调用
		 */
		public Config setReplacement(String replacement) {
			Assert.notNull(replacement, "replacement must not be null");
			this.replacement = replacement;
			return this;
		}

	}

}
