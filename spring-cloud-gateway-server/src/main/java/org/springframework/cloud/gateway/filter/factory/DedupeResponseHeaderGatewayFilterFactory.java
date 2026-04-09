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
import java.util.LinkedHashSet;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 去重响应头过滤器工厂。
 * <p>
 * 当网关和后端服务都添加了相同的响应头（如 CORS 头）时，响应头可能出现重复值。 该过滤器用于去除重复的响应头值。
 * <p>
 * 使用场景示例： <pre>
 * 后端和网关都添加了 CORS 头，导致：
 * Access-Control-Allow-Credentials: true, true
 * Access-Control-Allow-Origin: https://musk.mars, https://musk.mars
 * </pre>
 * <p>
 * 配置参数：
 * <ul>
 * <li>name：响应头名称，多个用空格分隔（必填）</li>
 * <li>strategy：去重策略（可选，默认 RETAIN_FIRST）</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * default-filters:
 *   - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
 *   - DedupeResponseHeader=Access-Control-Allow-Credentials, RETAIN_LAST
 * </pre>
 *
 * @author Vitaliy Pavlyuk
 */
public class DedupeResponseHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<DedupeResponseHeaderGatewayFilterFactory.Config> {

	/** 策略字段的键名 */
	private static final String STRATEGY_KEY = "strategy";

	/**
	 * 默认构造方法。
	 */
	public DedupeResponseHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY, STRATEGY_KEY);
	}

	/**
	 * 创建去重响应头过滤器。
	 * @param config 去重配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				return chain.filter(exchange)
						.then(Mono.fromRunnable(() -> dedupe(exchange.getResponse().getHeaders(), config)));
			}

			@Override
			public String toString() {
				return filterToStringCreator(DedupeResponseHeaderGatewayFilterFactory.this)
						.append(config.getName(), config.getStrategy()).toString();
			}
		};
	}

	/**
	 * 去重策略枚举。
	 */
	public enum Strategy {

		/**
		 * 默认策略：保留第一个值。
		 */
		RETAIN_FIRST,

		/**
		 * 保留最后一个值。
		 */
		RETAIN_LAST,

		/**
		 * 保留首次出现的不重复值（按出现顺序）。
		 */
		RETAIN_UNIQUE

	}

	/**
	 * 对响应头进行去重处理。
	 * @param headers 响应头对象
	 * @param config 去重配置
	 */
	void dedupe(HttpHeaders headers, Config config) {
		String names = config.getName();
		Strategy strategy = config.getStrategy();
		if (headers == null || names == null || strategy == null) {
			return;
		}
		// 支持多个响应头名称（空格分隔）
		for (String name : names.split(" ")) {
			dedupe(headers, name.trim(), strategy);
		}
	}

	/**
	 * 对单个响应头进行去重。
	 * @param headers 响应头对象
	 * @param name 响应头名称
	 * @param strategy 去重策略
	 */
	private void dedupe(HttpHeaders headers, String name, Strategy strategy) {
		List<String> values = headers.get(name);
		// 若值列表为空或只有一个值，无需去重
		if (values == null || values.size() <= 1) {
			return;
		}
		switch (strategy) {
		case RETAIN_FIRST:
			// 保留第一个值
			headers.set(name, values.get(0));
			break;
		case RETAIN_LAST:
			// 保留最后一个值
			headers.set(name, values.get(values.size() - 1));
			break;
		case RETAIN_UNIQUE:
			// 使用 LinkedHashSet 保留首次出现的顺序
			headers.put(name, new ArrayList<>(new LinkedHashSet<>(values)));
			break;
		default:
			break;
		}
	}

	/**
	 * 去重配置类。
	 */
	public static class Config extends AbstractGatewayFilterFactory.NameConfig {

		/** 去重策略，默认为 RETAIN_FIRST */
		private Strategy strategy = Strategy.RETAIN_FIRST;

		/**
		 * 获取去重策略。
		 * @return 去重策略
		 */
		public Strategy getStrategy() {
			return strategy;
		}

		/**
		 * 设置去重策略。
		 * @param strategy 去重策略
		 * @return 自身，用于链式调用
		 */
		public Config setStrategy(Strategy strategy) {
			this.strategy = strategy;
			return this;
		}

	}

}
