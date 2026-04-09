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

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.NotNull;

import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * 时间范围断言工厂 - 匹配在两个指定时间之间发生的请求。
 *
 * <p>
 * 该断言工厂用于匹配在配置的时间范围内发生的请求。 时间范围是左闭右开区间 (datetime1, datetime2)。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>当请求时间在 datetime1 之后、datetime2 之前时，断言匹配成功</li>
 * <li>datetime1 必须早于 datetime2，否则抛出异常</li>
 * <li>支持时区的 ZonedDateTime 类型</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式
 * - id: between_route
 *   uri: https://example.org
 *   predicates:
 *   - Between=2024-01-01T00:00:00+08:00[Asia/Shanghai],2024-12-31T23:59:59+08:00[Asia/Shanghai]
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 * @see AfterRoutePredicateFactory
 * @see BeforeRoutePredicateFactory
 */
public class BetweenRoutePredicateFactory extends AbstractRoutePredicateFactory<BetweenRoutePredicateFactory.Config> {

	/**
	 * 配置键：datetime1，范围起始时间。
	 */
	public static final String DATETIME1_KEY = "datetime1";

	/**
	 * 配置键：datetime2，范围结束时间。
	 */
	public static final String DATETIME2_KEY = "datetime2";

	/**
	 * 构造函数，使用默认配置类。
	 */
	public BetweenRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(DATETIME1_KEY, DATETIME2_KEY);
	}

	/**
	 * 创建断言，检查当前时间是否在配置的时间范围内。
	 * @param config 配置对象，包含起始时间和结束时间
	 * @return 匹配的断言
	 * @throws IllegalArgumentException 如果 datetime1 不早于 datetime2
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		// 验证时间范围的合法性
		Assert.isTrue(config.getDatetime1().isBefore(config.getDatetime2()),
				config.getDatetime1() + " must be before " + config.getDatetime2());

		return new GatewayPredicate() {
			/**
			 * 测试请求是否在配置的时间范围内。
			 * @param serverWebExchange 服务器 Web 交换对象
			 * @return 如果当前时间在范围内则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange serverWebExchange) {
				final ZonedDateTime now = ZonedDateTime.now();
				// 使用左闭右开区间：after(datetime1) && before(datetime2)
				return now.isAfter(config.getDatetime1()) && now.isBefore(config.getDatetime2());
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Between: %s and %s", config.getDatetime1(), config.getDatetime2());
			}
		};
	}

	/**
	 * 配置类，定义 Between 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** 范围起始时间（必须） */
		@NotNull
		private ZonedDateTime datetime1;

		/** 范围结束时间（必须） */
		@NotNull
		private ZonedDateTime datetime2;

		/**
		 * 获取范围起始时间。
		 * @return 起始 ZonedDateTime 对象
		 */
		public ZonedDateTime getDatetime1() {
			return datetime1;
		}

		/**
		 * 设置范围起始时间。
		 * @param datetime1 起始 ZonedDateTime 时间点
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setDatetime1(ZonedDateTime datetime1) {
			this.datetime1 = datetime1;
			return this;
		}

		/**
		 * 获取范围结束时间。
		 * @return 结束 ZonedDateTime 对象
		 */
		public ZonedDateTime getDatetime2() {
			return datetime2;
		}

		/**
		 * 设置范围结束时间。
		 * @param datetime2 结束 ZonedDateTime 时间点
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setDatetime2(ZonedDateTime datetime2) {
			this.datetime2 = datetime2;
			return this;
		}

	}

}
