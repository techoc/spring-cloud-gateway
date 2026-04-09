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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 响应头重写过滤器工厂，用于在响应发送前修改指定的响应头值。
 *
 * <p>
 * 该过滤器使用正则表达式匹配响应头值并进行替换，常用于：
 * <ul>
 * <li>去除响应头中的敏感信息</li>
 * <li>修改下游服务返回的 URL</li>
 * <li>统一响应头格式</li>
 * </ul>
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: rewrite-response-header-route
 *           uri: http://example.com
 *           filters:
 *             - name: RewriteResponseHeader
 *               args:
 *                 name: X-Request-Id
 *                 regexp: password.*
+
 *                 replacement: password=***
 * </pre>
 *
 * <p>
 * 快捷配置方式： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: rewrite-response-header-route
 *           uri: http://example.com
 *           filters:
 *             - RewriteResponseHeader=X-Request-Id, password.*, password=***
 * </pre>
 *
 * <p>
 * 示例说明：
 * <ul>
 * <li>响应头: X-Request-Id: password123</li>
 * <li>正则表达式: password.*</li>
 * <li>替换值: password=***</li>
 * <li>重写后: X-Request-Id: password=***</li>
 * </ul>
 *
 * @author Vitaliy Pavlyuk
 * @see AbstractGatewayFilterFactory
 */
public class RewriteResponseHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RewriteResponseHeaderGatewayFilterFactory.Config> {

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
	public RewriteResponseHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置的字段顺序。
	 * @return 包含字段名的列表，用于快捷配置解析
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY, REGEXP_KEY, REPLACEMENT_KEY);
	}

	/**
	 * 应用此过滤器，创建响应头重写过滤器。
	 * @param config 包含响应头名称、正则表达式和替换表达式的配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，在响应发送后重写响应头。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 先执行过滤器链，然后在响应发送后重写响应头
				return chain.filter(exchange).then(Mono.fromRunnable(() -> rewriteHeaders(exchange, config)));
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(RewriteResponseHeaderGatewayFilterFactory.this)
						.append("name", config.getName()).append("regexp", config.getRegexp())
						.append("replacement", config.getReplacement()).toString();
			}
		};
	}

	/**
	 * 重写指定的响应头（已弃用，请使用 {@link #rewriteHeaders}）。
	 * @param exchange 当前请求的 ServerWebExchange 对象
	 * @param config 过滤器配置
	 * @deprecated 使用 {@link #rewriteHeaders} 代替
	 */
	@Deprecated
	protected void rewriteHeader(ServerWebExchange exchange, Config config) {
		rewriteHeaders(exchange, config);
	}

	/**
	 * 重写响应头，处理多个值的响应头。
	 * @param exchange 当前请求的 ServerWebExchange 对象
	 * @param config 过滤器配置
	 */
	protected void rewriteHeaders(ServerWebExchange exchange, Config config) {
		final String name = config.getName();
		final HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
		// 仅当响应头存在时才进行重写
		responseHeaders.computeIfPresent(name, (k, v) -> rewriteHeaders(config, v));
	}

	/**
	 * 重写响应头的值列表。
	 * @param config 过滤器配置
	 * @param headers 响应头的值列表
	 * @return 重写后的响应头值列表
	 */
	protected List<String> rewriteHeaders(Config config, List<String> headers) {
		ArrayList<String> rewrittenHeaders = new ArrayList<>();
		for (int i = 0; i < headers.size(); i++) {
			String rewriten = rewrite(headers.get(i), config.getRegexp(), config.getReplacement());
			rewrittenHeaders.add(rewriten);
		}
		return rewrittenHeaders;
	}

	/**
	 * 执行单个值的正则替换。
	 * @param value 原始值
	 * @param regexp 正则表达式
	 * @param replacement 替换表达式
	 * @return 替换后的值
	 */
	String rewrite(String value, String regexp, String replacement) {
		// 处理转义的美元符号
		return value.replaceAll(regexp, replacement.replace("$\\", "$"));
	}

	/**
	 * 响应头重写过滤器配置类。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>name - 要重写的响应头名称</li>
	 * <li>regexp - Java 正则表达式</li>
	 * <li>replacement - 替换表达式</li>
	 * </ul>
	 */
	public static class Config extends AbstractGatewayFilterFactory.NameConfig {

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
		 * @param regexp 正则表达式
		 * @return 配置对象，支持链式调用
		 */
		public Config setRegexp(String regexp) {
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
		 * @param replacement 替换表达式
		 * @return 配置对象，支持链式调用
		 */
		public Config setReplacement(String replacement) {
			this.replacement = replacement;
			return this;
		}

	}

}
