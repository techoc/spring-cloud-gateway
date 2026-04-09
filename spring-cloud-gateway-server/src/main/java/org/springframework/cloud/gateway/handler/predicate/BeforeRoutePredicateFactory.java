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
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * 时间前置断言工厂 - 匹配在指定时间之前发生的请求。
 *
 * <p>
 * 该断言工厂用于匹配在配置的时间点之前发生的请求。 常用于预热期限制、维护前切换路由、限时活动等场景。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>当请求时间早于配置的时间点时，断言匹配成功</li>
 * <li>支持时区的 ZonedDateTime 类型</li>
 * <li>时间点使用 ISO-8601 格式</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式
 * - id: before_route
 *   uri: https://example.org
 *   predicates:
 *   - Before=2024-06-01T00:00:00+08:00[Asia/Shanghai]
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 * @see AfterRoutePredicateFactory
 */
public class BeforeRoutePredicateFactory extends AbstractRoutePredicateFactory<BeforeRoutePredicateFactory.Config> {

	/**
	 * 配置键：datetime，指定时间点。
	 */
	public static final String DATETIME_KEY = "datetime";

	/**
	 * 构造函数，使用默认配置类。
	 */
	public BeforeRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList(DATETIME_KEY);
	}

	/**
	 * 创建断言，检查当前时间是否在配置时间之前。
	 * @param config 配置对象，包含目标时间点
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			/**
			 * 测试请求是否在配置时间之前。
			 * @param serverWebExchange 服务器 Web 交换对象
			 * @return 如果当前时间早于配置时间则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange serverWebExchange) {
				final ZonedDateTime now = ZonedDateTime.now();
				return now.isBefore(config.getDatetime());
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Before: %s", config.getDatetime());
			}
		};
	}

	/**
	 * 配置类，定义 Before 断言所需的配置参数。
	 */
	public static class Config {

		/** 目标时间点 */
		private ZonedDateTime datetime;

		/**
		 * 获取配置的时间点。
		 * @return 配置的 ZonedDateTime 对象
		 */
		public ZonedDateTime getDatetime() {
			return datetime;
		}

		/**
		 * 设置配置的时间点。
		 * @param datetime 目标 ZonedDateTime 时间点
		 */
		public void setDatetime(ZonedDateTime datetime) {
			this.datetime = datetime;
		}

	}

}
