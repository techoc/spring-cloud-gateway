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
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * 权重断言工厂 - 基于权重进行请求分组的路由匹配。
 *
 * <p>
 * 该断言工厂用于实现基于权重的流量分配， 允许将请求按指定比例分发到不同的目标服务。 常用于灰度发布、金丝雀发布、A/B 测试等场景。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>将请求按配置的权重比例分配到同一组内的多个路由</li>
 * <li>同一组的路由共享权重计算</li>
 * <li>权重计算由 WeightCalculatorWebFilter 完成</li>
 * <li>支持配置多个权重组</li>
 * </ul>
 *
 * <p>
 * <b>权重计算机制：</b>
 * </p>
 * <ul>
 * <li>使用随机数与权重总和进行比较</li>
 * <li>WeightCalculatorWebFilter 在断言执行前计算并存储结果</li>
 * <li>同一请求在同一权重组内的所有路由中只会匹配一个</li>
 * </ul>
 *
 * <p>
 * <b>使用场景：</b>
 * </p>
 * <ul>
 * <li>灰度发布：新版本接收少量流量进行验证</li>
 * <li>金丝雀发布：逐步将流量切换到新版本</li>
 * <li>A/B 测试：不同版本接收不同比例的请求</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 金丝雀发布
 * - id: canary_v1
 *   uri: https://v1.example.org
 *   predicates:
 *   - Weight=canary,90  # 90% 流量到 v1
 *
 * - id: canary_v2
 *   uri: https://v2.example.org
 *   predicates:
 *   - Weight=canary,10  # 10% 流量到 v2
 *
 * # YAML 配置方式 - A/B 测试
 * - id: ab_a
 *   uri: https://a.example.org
 *   predicates:
 *   - Weight=ab_test,50
 *
 * - id: ab_b
 *   uri: https://b.example.org
 *   predicates:
 *   - Weight=ab_test,50
 * }</pre>
 *
 * @author Spencer Gibb
 * @see WeightConfig
 * @see org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter
 */
public class WeightRoutePredicateFactory extends AbstractRoutePredicateFactory<WeightConfig>
		implements ApplicationEventPublisherAware {

	/**
	 * 权重组配置键。
	 */
	public static final String GROUP_KEY = WeightConfig.CONFIG_PREFIX + ".group";

	/**
	 * 权重值配置键。
	 */
	public static final String WEIGHT_KEY = WeightConfig.CONFIG_PREFIX + ".weight";

	/** 日志记录器 */
	private static final Log log = LogFactory.getLog(WeightRoutePredicateFactory.class);

	/** 事件发布器，用于发布权重定义事件 */
	private ApplicationEventPublisher publisher;

	/**
	 * 默认构造函数，使用 WeightConfig 作为配置类。
	 */
	public WeightRoutePredicateFactory() {
		super(WeightConfig.class);
	}

	/**
	 * 设置应用事件发布器。
	 * @param publisher 应用事件发布器
	 */
	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(GROUP_KEY, WEIGHT_KEY);
	}

	/**
	 * 返回快捷配置字段前缀。
	 * @return 配置前缀
	 */
	@Override
	public String shortcutFieldPrefix() {
		return WeightConfig.CONFIG_PREFIX;
	}

	/**
	 * 应用配置前的回调方法。
	 *
	 * <p>
	 * 发布 WeightDefinedEvent 事件，通知权重配置已定义。
	 * </p>
	 * @param config 权重配置
	 */
	@Override
	public void beforeApply(WeightConfig config) {
		if (publisher != null) {
			publisher.publishEvent(new WeightDefinedEvent(this, config));
		}
	}

	/**
	 * 创建断言，检查请求是否属于被选中的路由。
	 *
	 * <p>
	 * 实际的权重计算和路由选择由 WeightCalculatorWebFilter 完成。 此断言仅检查当前路由是否为权重计算的获胜者。
	 * </p>
	 * @param config 权重配置
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(WeightConfig config) {
		return new GatewayPredicate() {
			/**
			 * 测试当前路由是否为权重组中被选中的路由。
			 * @param exchange 服务器 Web 交换对象
			 * @return 如果当前路由是获胜者则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				// 获取权重计算结果
				Map<String, String> weights = exchange.getAttributeOrDefault(WEIGHT_ATTR, Collections.emptyMap());

				// 获取当前路由 ID
				String routeId = exchange.getAttribute(GATEWAY_PREDICATE_ROUTE_ATTR);

				// 所有权重计算和与随机数的比较都在 WeightCalculatorWebFilter 中完成
				String group = config.getGroup();
				if (weights.containsKey(group)) {
					String chosenRoute = weights.get(group);
					if (log.isTraceEnabled()) {
						log.trace("in group weight: " + group + ", current route: " + routeId + ", chosen route: "
								+ chosenRoute);
					}

					// 检查当前路由是否为获胜者
					return routeId.equals(chosenRoute);
				}
				else if (log.isTraceEnabled()) {
					log.trace("no weights found for group: " + group + ", current route: " + routeId);
				}

				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Weight: %s %s", config.getGroup(), config.getWeight());
			}
		};
	}

}
