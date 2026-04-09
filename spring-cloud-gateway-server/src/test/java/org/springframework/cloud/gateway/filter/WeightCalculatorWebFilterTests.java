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

package org.springframework.cloud.gateway.filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnJre;

import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter.GroupWeightConfig;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.JRE.JAVA_17;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WeightCalculatorWebFilter 单元测试类
 *
 * 本测试类用于验证 WeightCalculatorWebFilter 的权重计算功能，包括： - 测试权重值的规范化计算 - 测试基于权重的路由选择 - 测试
 * PredicateArgsEvent 事件的接收和处理
 *
 * WeightCalculatorWebFilter 是 Spring Cloud Gateway 的权重计算过滤器， 根据配置的权重值在同组路由中进行加权负载均衡。
 *
 * @author 译者：Spring Cloud Gateway 团队
 */
public class WeightCalculatorWebFilterTests {

	/**
	 * 测试权重计算逻辑 验证：权重值应被正确规范化为 0-1 之间的概率值
	 */
	@Test
	public void testWeightCalculation() {
		WeightCalculatorWebFilter filter = createFilter();

		String grp1 = "group1";
		String grp2 = "group2";
		int grp1idx = 1;
		int grp2idx = 1;

		assertWeightCalculation(filter, grp1, grp1idx++, 1, asList(1.0));
		assertWeightCalculation(filter, grp2, grp2idx++, 1, asList(1.0));
		assertWeightCalculation(filter, grp1, grp1idx++, 3, asList(0.25, 0.75), 0.25);
		assertWeightCalculation(filter, grp2, grp2idx++, 1, asList(0.5, 0.5), 0.5);
		assertWeightCalculation(filter, grp1, grp1idx++, 6, asList(0.1, 0.3, 0.6), 0.1, 0.4);
		assertWeightCalculation(filter, grp2, grp2idx++, 2, asList(0.25, 0.25, 0.5), 0.25, 0.5);
		assertWeightCalculation(filter, grp2, grp2idx++, 4, asList(0.125, 0.125, 0.25, 0.5), 0.125, 0.25, 0.5);
	}

	/**
	 * 创建 WeightCalculatorWebFilter 实例
	 * @return WeightCalculatorWebFilter 实例
	 */
	private WeightCalculatorWebFilter createFilter() {
		return new WeightCalculatorWebFilter(null, new ConfigurationService(null, () -> null, () -> null));
	}

	/**
	 * 断言权重计算的正确性
	 * @param filter WeightCalculatorWebFilter 实例
	 * @param group 权重组名
	 * @param item 当前路由项编号
	 * @param weight 权重值
	 * @param normalized 预期的规范化权重列表
	 * @param middleRanges 预期的中间范围值
	 */
	private void assertWeightCalculation(WeightCalculatorWebFilter filter, String group, int item, int weight,
			List<Double> normalized, Double... middleRanges) {
		String routeId = route(item);

		filter.addWeightConfig(new WeightConfig(group, routeId, weight));

		Map<String, GroupWeightConfig> groupWeights = filter.getGroupWeights();
		assertThat(groupWeights).containsKey(group);

		GroupWeightConfig config = groupWeights.get(group);
		assertThat(config.group).isEqualTo(group);
		assertThat(config.weights).hasSize(item).containsEntry(routeId, weight);
		assertThat(config.normalizedWeights).hasSize(item);

		for (int i = 0; i < normalized.size(); i++) {
			assertThat(config.normalizedWeights).containsEntry(route(i + 1), normalized.get(i));
		}

		for (int i = 0; i < normalized.size(); i++) {
			assertThat(config.rangeIndexes).containsEntry(i, route(i + 1));
		}

		assertThat(config.ranges).hasSize(item + 1).startsWith(0.0).endsWith(1.0);

		if (middleRanges.length > 0) {
			assertThat(config.ranges).contains(middleRanges);
		}
	}

	/**
	 * 生成路由 ID
	 * @param i 路由编号
	 * @return 路由 ID 字符串
	 */
	private String route(int i) {
		return "route" + i;
	}

	/**
	 * 测试使用随机数选择路由 验证：根据权重计算的范围，随机数应正确映射到对应路由 注意：此测试在 JDK 17 上被禁用
	 */
	// TODO: modify implementation for testability on JDK17 for Spring 6
	@Test
	@DisabledOnJre(JAVA_17)
	public void testChooseRouteWithRandom() {
		WeightCalculatorWebFilter filter = createFilter();
		filter.addWeightConfig(new WeightConfig("groupa", "route1", 1));
		filter.addWeightConfig(new WeightConfig("groupa", "route2", 3));
		filter.addWeightConfig(new WeightConfig("groupa", "route3", 6));

		Random random = mock(Random.class);

		when(random.nextDouble()).thenReturn(0.05).thenReturn(0.2).thenReturn(0.6);

		filter.setRandom(random);

		MockServerWebExchange exchange = MockServerWebExchange
				.from(MockServerHttpRequest.get("http://localhost").build());

		WebFilterChain filterChain = mock(WebFilterChain.class);
		filter.filter(exchange, filterChain);
		Map<String, String> weights = WeightCalculatorWebFilter.getWeights(exchange);
		assertThat(weights).containsEntry("groupa", "route1");

		filter.filter(exchange, filterChain);
		weights = WeightCalculatorWebFilter.getWeights(exchange);
		assertThat(weights).containsEntry("groupa", "route2");

		filter.filter(exchange, filterChain);
		weights = WeightCalculatorWebFilter.getWeights(exchange);
		assertThat(weights).containsEntry("groupa", "route3");
	}

	/**
	 * 测试接收 PredicateArgsEvent 事件 验证：事件中的权重配置应被正确解析并添加到过滤器
	 */
	@Test
	public void receivesPredicateArgsEvent() {
		TestWeightCalculatorWebFilter filter = new TestWeightCalculatorWebFilter();

		HashMap<String, Object> args = new HashMap<>();
		args.put("weight.group", "group1");
		args.put("weight.weight", "1");
		PredicateArgsEvent event = new PredicateArgsEvent(this, "routeA", args);
		filter.handle(event);

		WeightConfig weightConfig = filter.weightConfig;
		assertThat(weightConfig.getGroup()).isEqualTo("group1");
		assertThat(weightConfig.getRouteId()).isEqualTo("routeA");
		assertThat(weightConfig.getWeight()).isEqualTo(1);
	}

	/**
	 * 用于测试的 WeightCalculatorWebFilter 子类
	 *
	 * 重写 addWeightConfig 方法以捕获添加的权重配置， 便于验证事件处理逻辑。
	 */
	class TestWeightCalculatorWebFilter extends WeightCalculatorWebFilter {

		private WeightConfig weightConfig;

		TestWeightCalculatorWebFilter() {
			super(null, new ConfigurationService(null, () -> null, () -> null));
		}

		@Override
		void addWeightConfig(WeightConfig weightConfig) {
			this.weightConfig = weightConfig;
		}

	}

}
