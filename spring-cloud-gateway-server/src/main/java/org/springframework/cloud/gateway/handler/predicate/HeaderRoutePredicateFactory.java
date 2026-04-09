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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.NotEmpty;

import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * 请求头断言工厂 - 根据 HTTP 请求头进行路由匹配。
 *
 * <p>
 * 该断言工厂用于检查请求是否包含特定名称的请求头， 并且可选地验证请求头的值是否匹配给定的正则表达式。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求中是否存在指定名称的请求头</li>
 * <li>可选：验证请求头的值是否匹配给定的正则表达式</li>
 * <li>支持多值请求头（检查任意一个值匹配即可）</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 仅检查请求头存在
 * - id: header_route
 *   uri: https://example.org
 *   predicates:
 *   - Header=X-Request-Id
 *
 * # YAML 配置方式 - 验证请求头值
 * - id: content_type_route
 *   uri: https://api.example.org
 *   predicates:
 *   - Header=Content-Type,application/json
 *
 * # YAML 配置方式 - 使用正则表达式
 * - id: version_route
 *   uri: https://v2.example.org
 *   predicates:
 *   - Header=X-API-Version,v2\\..+
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 */
public class HeaderRoutePredicateFactory extends AbstractRoutePredicateFactory<HeaderRoutePredicateFactory.Config> {

	/**
	 * 配置键：header，请求头名称。
	 */
	public static final String HEADER_KEY = "header";

	/**
	 * 配置键：regexp，请求头值的正则表达式。
	 */
	public static final String REGEXP_KEY = "regexp";

	/**
	 * 构造函数，使用默认配置类。
	 */
	public HeaderRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(HEADER_KEY, REGEXP_KEY);
	}

	/**
	 * 创建断言，检查请求头。
	 * @param config 配置对象，包含请求头名称和正则表达式
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		// 检查是否配置了正则表达式
		boolean hasRegex = !ObjectUtils.isEmpty(config.regexp);

		return new GatewayPredicate() {
			/**
			 * 测试请求头是否匹配配置。
			 * @param exchange 服务器 Web 交换对象
			 * @return 匹配结果
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				// 获取请求头的所有值
				List<String> values = exchange.getRequest().getHeaders().getOrDefault(config.header,
						Collections.emptyList());
				if (values.isEmpty()) {
					return false;
				}

				// 如果配置了正则表达式，检查是否有值匹配
				if (hasRegex) {
					// 遍历所有请求头值，检查是否有匹配正则表达式的
					for (int i = 0; i < values.size(); i++) {
						String value = values.get(i);
						if (value.matches(config.regexp)) {
							return true;
						}
					}
					return false;
				}

				// 没有配置正则表达式，只检查请求头是否存在
				return true;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Header: %s regexp=%s", config.header, config.regexp);
			}
		};
	}

	/**
	 * 配置类，定义 Header 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** 请求头名称（必须） */
		@NotEmpty
		private String header;

		/** 请求头值的正则表达式（可选） */
		private String regexp;

		/**
		 * 获取请求头名称。
		 * @return 请求头名称
		 */
		public String getHeader() {
			return header;
		}

		/**
		 * 设置请求头名称。
		 * @param header 请求头名称
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setHeader(String header) {
			this.header = header;
			return this;
		}

		/**
		 * 获取正则表达式。
		 * @return 正则表达式字符串
		 */
		public String getRegexp() {
			return regexp;
		}

		/**
		 * 设置正则表达式。
		 * @param regexp 正则表达式字符串
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setRegexp(String regexp) {
			this.regexp = regexp;
			return this;
		}

	}

}
