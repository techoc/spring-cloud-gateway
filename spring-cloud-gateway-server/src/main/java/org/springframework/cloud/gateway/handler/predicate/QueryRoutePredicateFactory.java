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

import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * 查询参数断言工厂 - 根据 URL 查询参数进行路由匹配。
 *
 * <p>
 * 该断言工厂用于检查请求是否包含特定名称的查询参数， 并且可选地验证查询参数的值是否匹配给定的正则表达式。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求中是否存在指定名称的查询参数</li>
 * <li>可选：验证查询参数的值是否匹配给定的正则表达式</li>
 * <li>支持多值查询参数（检查任意一个值匹配即可）</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 仅检查查询参数存在
 * - id: query_route
 *   uri: https://example.org
 *   predicates:
 *   - Query=debug
 *
 * # YAML 配置方式 - 验证查询参数值
 * - id: status_route
 *   uri: https://example.org
 *   predicates:
 *   - Query=status,active
 *
 * # YAML 配置方式 - 使用正则表达式
 * - id: id_route
 *   uri: https://example.org
 *   predicates:
 *   - Query=id,\\d+
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 */
public class QueryRoutePredicateFactory extends AbstractRoutePredicateFactory<QueryRoutePredicateFactory.Config> {

	/**
	 * 配置键：param，查询参数名称。
	 */
	public static final String PARAM_KEY = "param";

	/**
	 * 配置键：regexp，查询参数值的正则表达式。
	 */
	public static final String REGEXP_KEY = "regexp";

	/**
	 * 构造函数，使用默认配置类。
	 */
	public QueryRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(PARAM_KEY, REGEXP_KEY);
	}

	/**
	 * 创建断言，检查查询参数。
	 * @param config 配置对象，包含查询参数名称和正则表达式
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			/**
			 * 测试查询参数是否匹配配置。
			 * @param exchange 服务器 Web 交换对象
			 * @return 匹配结果
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				// 如果没有配置正则表达式，只检查查询参数是否存在
				if (!StringUtils.hasText(config.regexp)) {
					return exchange.getRequest().getQueryParams().containsKey(config.param);
				}

				// 获取查询参数的值列表
				List<String> values = exchange.getRequest().getQueryParams().get(config.param);
				if (values == null) {
					return false;
				}
				// 遍历所有值，检查是否有匹配正则表达式的
				for (String value : values) {
					if (value != null && value.matches(config.regexp)) {
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
				return String.format("Query: param=%s regexp=%s", config.getParam(), config.getRegexp());
			}
		};
	}

	/**
	 * 配置类，定义 Query 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** 查询参数名称（必须） */
		@NotEmpty
		private String param;

		/** 查询参数值的正则表达式（可选） */
		private String regexp;

		/**
		 * 获取查询参数名称。
		 * @return 查询参数名称
		 */
		public String getParam() {
			return param;
		}

		/**
		 * 设置查询参数名称。
		 * @param param 查询参数名称
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setParam(String param) {
			this.param = param;
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
