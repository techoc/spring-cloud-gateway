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

import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Arrays.stream;

/**
 * HTTP 方法断言工厂 - 根据请求的 HTTP 方法进行路由匹配。
 *
 * <p>
 * 该断言工厂用于根据请求的 HTTP 方法（GET、POST、PUT、DELETE 等） 进行路由匹配。支持匹配多种 HTTP 方法。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求的 HTTP 方法是否在配置的方法列表中</li>
 * <li>支持多个 HTTP 方法的匹配</li>
 * <li>支持所有标准 HTTP 方法（GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS）</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 单一方法
 * - id: get_route
 *   uri: https://example.org
 *   predicates:
 *   - Method=GET
 *
 * # YAML 配置方式 - 多个方法
 * - id: api_route
 *   uri: https://api.example.org
 *   predicates:
 *   - Method=GET,POST,PUT
 * }</pre>
 *
 * @author Spencer Gibb
 * @author Dennis Menge
 * @see AbstractRoutePredicateFactory
 * @see org.springframework.http.HttpMethod
 */
public class MethodRoutePredicateFactory extends AbstractRoutePredicateFactory<MethodRoutePredicateFactory.Config> {

	/**
	 * 配置键：methods，HTTP 方法列表。
	 */
	public static final String METHODS_KEY = "methods";

	/**
	 * 构造函数，使用默认配置类。
	 */
	public MethodRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(METHODS_KEY);
	}

	/**
	 * 返回快捷配置类型。
	 * @return 快捷配置类型为收集列表模式
	 */
	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	/**
	 * 创建断言，检查请求的 HTTP 方法。
	 * @param config 配置对象，包含 HTTP 方法列表
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			/**
			 * 测试请求的 HTTP 方法是否在配置的方法列表中。
			 * @param exchange 服务器 Web 交换对象
			 * @return 如果请求方法在列表中则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				HttpMethod requestMethod = exchange.getRequest().getMethod();
				// 检查请求方法是否与配置的方法列表中的任意一个匹配
				return stream(config.getMethods()).anyMatch(httpMethod -> httpMethod == requestMethod);
			}

			@Override
			public String toString() {
				return String.format("Methods: %s", Arrays.toString(config.getMethods()));
			}
		};
	}

	/**
	 * 配置类，定义 Method 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** HTTP 方法数组 */
		private HttpMethod[] methods;

		/**
		 * 获取 HTTP 方法数组。
		 * @return HTTP 方法数组
		 */
		public HttpMethod[] getMethods() {
			return methods;
		}

		/**
		 * 设置 HTTP 方法数组。
		 * @param methods HTTP 方法数组
		 */
		public void setMethods(HttpMethod... methods) {
			this.methods = methods;
		}

	}

}
