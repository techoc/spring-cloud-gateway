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
import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.NotEmpty;

import org.springframework.http.HttpCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * Cookie 断言工厂 - 根据请求中的 Cookie 进行路由匹配。
 *
 * <p>
 * 该断言工厂用于检查请求是否携带特定名称且值匹配指定正则表达式的 Cookie。 常用于用户认证状态检查、A/B 测试客户端分组等功能。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求中是否存在指定名称的 Cookie</li>
 * <li>可选：验证 Cookie 的值是否匹配给定的正则表达式</li>
 * <li>支持同一名称的多个 Cookie（取第一个匹配的）</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 仅检查 Cookie 存在
 * - id: cookie_route
 *   uri: https://example.org
 *   predicates:
 *   - Cookie=sessionId,.*
 *
 * # YAML 配置方式 - 验证 Cookie 值
 * - id: user_route
 *   uri: https://user.example.org
 *   predicates:
 *   - Cookie=userType,premium
 *
 * # YAML 配置方式 - 使用正则表达式
 * - id: test_route
 *   uri: https://test.example.org
 *   predicates:
 *   - Cookie=experimentId,(group-a|group-b)
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 */
public class CookieRoutePredicateFactory extends AbstractRoutePredicateFactory<CookieRoutePredicateFactory.Config> {

	/**
	 * 配置键：name，Cookie 名称。
	 */
	public static final String NAME_KEY = "name";

	/**
	 * 配置键：regexp，Cookie 值的正则表达式。
	 */
	public static final String REGEXP_KEY = "regexp";

	/**
	 * 构造函数，使用默认配置类。
	 */
	public CookieRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY, REGEXP_KEY);
	}

	/**
	 * 创建断言，检查请求中的 Cookie。
	 * @param config 配置对象，包含 Cookie 名称和正则表达式
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			/**
			 * 测试请求中的 Cookie 是否匹配配置。
			 * @param exchange 服务器 Web 交换对象
			 * @return 如果找到匹配名称和正则表达式的 Cookie 则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				// 获取指定名称的 Cookie 列表
				List<HttpCookie> cookies = exchange.getRequest().getCookies().get(config.name);
				if (cookies == null) {
					return false;
				}
				// 遍历 Cookie 列表，检查是否有匹配正则表达式的
				for (HttpCookie cookie : cookies) {
					if (cookie.getValue().matches(config.regexp)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Cookie: name=%s regexp=%s", config.name, config.regexp);
			}
		};
	}

	/**
	 * 配置类，定义 Cookie 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** Cookie 名称（必须） */
		@NotEmpty
		private String name;

		/** Cookie 值的正则表达式（必须） */
		@NotEmpty
		private String regexp;

		/**
		 * 获取 Cookie 名称。
		 * @return Cookie 名称
		 */
		public String getName() {
			return name;
		}

		/**
		 * 设置 Cookie 名称。
		 * @param name Cookie 名称
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setName(String name) {
			this.name = name;
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
