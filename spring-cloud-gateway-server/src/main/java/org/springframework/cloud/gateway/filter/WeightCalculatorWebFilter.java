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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.style.ToStringCreator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * 基于权重的路由计算过滤器。
 *
 * <p>
 * 该过滤器实现基于权重的负载均衡策略，支持按权重比例分配请求到不同路由。
 *
 * <p>
 * 功能说明：
 * <ul>
 * <li>实现WebFilter接口，作为Web过滤器</li>
 * <li>实现SmartApplicationListener，监听配置变更事件</li>
 * <li>支持基于权重组的路由分配</li>
 * <li>权重计算结果存储在exchange属性中</li>
 * <li>支持动态刷新路由配置</li>
 * </ul>
 *
 * <p>
 * 使用场景：
 * <ul>
 * <li>A/B测试：根据权重分配流量到不同版本</li>
 * <li>灰度发布：按比例将流量引导到新版本</li>
 * <li>金丝雀发布：少量流量先到新版本</li>
 * <li>多版本并存：不同版本处理不同比例请求</li>
 * </ul>
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: weight_high
 *           uri: http://service-a-v2
 *           predicates:
 *             - Weight=group1, 90
 *         - id: weight_low
 *           uri: http://service-a-v1
 *           predicates:
 *             - Weight=group1, 10
 * </pre>
 *
 * <p>
 * 算法原理：
 * <ul>
 * <li>将权重归一化为0-1之间的小数</li>
 * <li>计算每个路由的权重区间</li>
 * <li>生成0-1之间的随机数</li>
 * <li>根据随机数落在的区间确定目标路由</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @author Alexey Nakidkin
 * @see WebFilter
 * @see Ordered
 * @see SmartApplicationListener
 * @see WeightConfig
 */
public class WeightCalculatorWebFilter implements WebFilter, Ordered, SmartApplicationListener {

	/**
	 * 权重计算过滤器的执行顺序。
	 * <p>
	 * 值为10001，在其他过滤器之后执行
	 * </p>
	 */
	public static final int WEIGHT_CALC_FILTER_ORDER = 10001;

	/**
	 * 日志记录器。
	 * <p>
	 * 用于记录权重计算过程
	 * </p>
	 */
	private static final Log log = LogFactory.getLog(WeightCalculatorWebFilter.class);

	/**
	 * 路由定位器提供者。
	 * <p>
	 * 用于获取路由信息，支持延迟解析
	 * </p>
	 */
	private final ObjectProvider<RouteLocator> routeLocator;

	/**
	 * 配置服务。
	 * <p>
	 * 用于绑定和处理配置属性
	 * </p>
	 */
	private final ConfigurationService configurationService;

	/**
	 * 随机数生成器。
	 * <p>
	 * 用于权重路由的随机选择。null时使用ThreadLocalRandom
	 * </p>
	 */
	private Random random = null;

	/**
	 * 过滤器执行顺序。
	 * <p>
	 * 可通过setOrder方法自定义
	 * </p>
	 */
	private int order = WEIGHT_CALC_FILTER_ORDER;

	/**
	 * 权重组配置缓存。
	 * <p>
	 * 键为组名，值为该组的权重配置
	 * </p>
	 */
	private Map<String, GroupWeightConfig> groupWeights = new ConcurrentHashMap<>();

	/**
	 * 路由定位器初始化标志。
	 * <p>
	 * 使用原子布尔保证只初始化一次
	 * </p>
	 */
	private final AtomicBoolean routeLocatorInitialized = new AtomicBoolean();

	/**
	 * 构造函数，创建WeightCalculatorWebFilter实例。
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>routeLocator - 路由定位器提供者</li>
	 * <li>configurationService - 配置服务</li>
	 * </ul>
	 * @param routeLocator 路由定位器提供者
	 * @param configurationService 配置服务
	 */
	public WeightCalculatorWebFilter(ObjectProvider<RouteLocator> routeLocator,
			ConfigurationService configurationService) {
		this.routeLocator = routeLocator;
		this.configurationService = configurationService;
	}

	/**
	 * 获取请求的权重映射。
	 *
	 * <p>
	 * 从exchange属性中获取或创建权重映射。 用于在同一次请求中共享权重计算结果。
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>exchange - 服务器Web交换对象</li>
	 * </ul>
	 *
	 * <p>
	 * 返回值说明：
	 * <ul>
	 * <li>返回权重映射，键为组名，值为选中的路由ID</li>
	 * </ul>
	 * @param exchange 服务器Web交换对象
	 * @return 权重映射
	 */
	/* for testing */
	static Map<String, String> getWeights(ServerWebExchange exchange) {
		Map<String, String> weights = exchange.getAttribute(WEIGHT_ATTR);

		if (weights == null) {
			weights = new ConcurrentHashMap<>();
			exchange.getAttributes().put(WEIGHT_ATTR, weights);
		}
		return weights;
	}

	/**
	 * 获取过滤器执行顺序。
	 * @return 过滤器顺序值
	 */
	@Override
	public int getOrder() {
		return order;
	}

	/**
	 * 设置过滤器执行顺序。
	 * @param order 新的顺序值
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * 设置随机数生成器。
	 *
	 * <p>
	 * 用于测试场景，支持注入可控的随机数生成器。
	 * @param random 随机数生成器
	 */
	public void setRandom(Random random) {
		this.random = random;
	}

	/**
	 * 判断是否支持该事件类型。
	 *
	 * <p>
	 * 支持的事件类型：
	 * <ul>
	 * <li>PredicateArgsEvent - 来自配置文件</li>
	 * <li>WeightDefinedEvent - 来自Java DSL</li>
	 * <li>RefreshRoutesEvent - 强制初始化</li>
	 * </ul>
	 * @param eventType 事件类型Class
	 * @return 如果支持返回true
	 */
	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		// from config file
		return PredicateArgsEvent.class.isAssignableFrom(eventType) ||
		// from java dsl
				WeightDefinedEvent.class.isAssignableFrom(eventType) ||
				// force initialization
				RefreshRoutesEvent.class.isAssignableFrom(eventType);
	}

	/**
	 * 判断是否支持该源类型。
	 *
	 * <p>
	 * 返回true，表示支持所有源类型。
	 * @param sourceType 源类型Class
	 * @return 始终返回true
	 */
	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	/**
	 * 处理应用事件。
	 *
	 * <p>
	 * 事件处理逻辑：
	 * <ul>
	 * <li>PredicateArgsEvent：调用handle处理配置参数</li>
	 * <li>WeightDefinedEvent：直接添加权重配置</li>
	 * <li>RefreshRoutesEvent：触发路由初始化</li>
	 * </ul>
	 *
	 * <p>
	 * RefreshRoutesEvent处理：
	 * <ul>
	 * <li>首次刷新时阻塞初始化，确保应用启动失败时能看到错误</li>
	 * <li>后续刷新时非阻塞订阅</li>
	 * </ul>
	 * @param event 应用事件
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof PredicateArgsEvent) {
			handle((PredicateArgsEvent) event);
		}
		else if (event instanceof WeightDefinedEvent) {
			addWeightConfig(((WeightDefinedEvent) event).getWeightConfig());
		}
		else if (event instanceof RefreshRoutesEvent && routeLocator != null) {
			// forces initialization
			if (routeLocatorInitialized.compareAndSet(false, true)) {
				// on first time, block so that app fails to start if there are errors in
				// routes
				// see gh-1574
				routeLocator.ifAvailable(locator -> locator.getRoutes().blockLast());
			}
			else {
				// this preserves previous behaviour on refresh, this could likely go away
				routeLocator.ifAvailable(locator -> locator.getRoutes().subscribe());
			}
		}

	}

	/**
	 * 处理PredicateArgsEvent事件。
	 *
	 * <p>
	 * 核心逻辑：
	 * <ol>
	 * <li>检查参数是否与权重配置相关</li>
	 * <li>创建WeightConfig对象</li>
	 * <li>绑定配置属性</li>
	 * <li>添加权重配置</li>
	 * </ol>
	 * @param event 谓词参数事件
	 */
	public void handle(PredicateArgsEvent event) {
		Map<String, Object> args = event.getArgs();

		if (args.isEmpty() || !hasRelevantKey(args)) {
			return;
		}

		WeightConfig config = new WeightConfig(event.getRouteId());

		this.configurationService.with(config).name(WeightConfig.CONFIG_PREFIX).normalizedProperties(args).bind();

		addWeightConfig(config);
	}

	/**
	 * 检查参数是否包含权重相关配置。
	 *
	 * <p>
	 * 判断逻辑：参数键以"weight."开头
	 * @param args 参数映射
	 * @return 如果包含权重配置返回true
	 */
	private boolean hasRelevantKey(Map<String, Object> args) {
		return args.keySet().stream().anyMatch(key -> key.startsWith(WeightConfig.CONFIG_PREFIX + "."));
	}

	/**
	 * 添加权重配置到组中。
	 *
	 * <p>
	 * 核心逻辑：
	 * <ol>
	 * <li>检查组是否已存在</li>
	 * <li>创建或复制GroupWeightConfig</li>
	 * <li>添加路由权重</li>
	 * <li>重新计算归一化权重</li>
	 * <li>更新组配置</li>
	 * </ol>
	 *
	 * <p>
	 * 线程安全：
	 * <ul>
	 * <li>使用复制而非修改策略</li>
	 * <li>计算完成后才更新引用</li>
	 * <li>避免并发问题</li>
	 * </ul>
	 *
	 * <p>
	 * 权重归一化：
	 * <ul>
	 * <li>所有权重相加得到总和</li>
	 * <li>每个权重除以总和得到归一化值</li>
	 * <li>计算每个路由的权重区间</li>
	 * </ul>
	 * @param weightConfig 权重配置
	 */
	/* for testing */
	void addWeightConfig(WeightConfig weightConfig) {
		String group = weightConfig.getGroup();
		GroupWeightConfig config;
		// only create new GroupWeightConfig rather than modify
		// and put at end of calculations. This avoids concurency problems
		// later during filter execution.
		if (groupWeights.containsKey(group)) {
			config = new GroupWeightConfig(groupWeights.get(group));
		}
		else {
			config = new GroupWeightConfig(group);
		}

		config.weights.put(weightConfig.getRouteId(), weightConfig.getWeight());

		// recalculate

		// normalize weights
		int weightsSum = 0;

		for (Integer weight : config.weights.values()) {
			weightsSum += weight;
		}

		final AtomicInteger index = new AtomicInteger(0);
		for (Map.Entry<String, Integer> entry : config.weights.entrySet()) {
			String routeId = entry.getKey();
			Integer weight = entry.getValue();
			Double nomalizedWeight = weight / (double) weightsSum;
			config.normalizedWeights.put(routeId, nomalizedWeight);

			// recalculate rangeIndexes
			config.rangeIndexes.put(index.getAndIncrement(), routeId);
		}

		// TODO: calculate ranges
		config.ranges.clear();

		config.ranges.add(0.0);

		List<Double> values = new ArrayList<>(config.normalizedWeights.values());
		for (int i = 0; i < values.size(); i++) {
			Double currentWeight = values.get(i);
			Double previousRange = config.ranges.get(i);
			Double range = previousRange + currentWeight;
			config.ranges.add(range);
		}

		if (log.isTraceEnabled()) {
			log.trace("Recalculated group weight config " + config);
		}
		// only update after all calculations
		groupWeights.put(group, config);
	}

	/**
	 * 获取所有权重组配置。
	 * @return 权重组配置映射
	 */
	/* for testing */
	Map<String, GroupWeightConfig> getGroupWeights() {
		return groupWeights;
	}

	/**
	 * 执行权重计算过滤。
	 *
	 * <p>
	 * 核心逻辑：
	 * <ol>
	 * <li>获取或创建权重映射</li>
	 * <li>遍历所有权重组</li>
	 * <li>为每个组生成随机数</li>
	 * <li>根据随机数确定目标路由</li>
	 * <li>将结果存储到exchange属性</li>
	 * </ol>
	 *
	 * <p>
	 * 随机数策略：
	 * <ul>
	 * <li>默认使用ThreadLocalRandom</li>
	 * <li>可注入自定义Random用于测试</li>
	 * <li>每次请求生成新的随机数</li>
	 * </ul>
	 *
	 * <p>
	 * 路由选择算法：
	 * <ul>
	 * <li>生成[0,1)区间的随机数</li>
	 * <li>遍历权重区间列表</li>
	 * <li>找到第一个r小于等于当前区间上限的区间</li>
	 * <li>该区间对应的路由即为目标</li>
	 * </ul>
	 * @param exchange 服务器Web交换对象
	 * @param chain Web过滤器链
	 * @return 完成信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		Map<String, String> weights = getWeights(exchange);

		for (String group : groupWeights.keySet()) {
			GroupWeightConfig config = groupWeights.get(group);

			if (config == null) {
				if (log.isDebugEnabled()) {
					log.debug("No GroupWeightConfig found for group: " + group);
				}
				continue; // nothing we can do, but this is odd
			}

			/*
			 * Usually, multiple threads accessing the same random object will have some
			 * performance problems, so we can use ThreadLocalRandom by default
			 */
			Random useRandom = this.random;
			useRandom = useRandom == null ? ThreadLocalRandom.current() : useRandom;
			double r = useRandom.nextDouble();

			List<Double> ranges = config.ranges;

			if (log.isTraceEnabled()) {
				log.trace("Weight for group: " + group + ", ranges: " + ranges + ", r: " + r);
			}

			for (int i = 0; i < ranges.size() - 1; i++) {
				if (r >= ranges.get(i) && r < ranges.get(i + 1)) {
					String routeId = config.rangeIndexes.get(i);
					weights.put(group, routeId);
					break;
				}
			}
		}

		if (log.isTraceEnabled()) {
			log.trace("Weights attr: " + weights);
		}

		return chain.filter(exchange);
	}

	/**
	 * 权重组配置类。
	 *
	 * <p>
	 * 存储单个权重组的所有路由权重信息。
	 *
	 * <p>
	 * 配置内容：
	 * <ul>
	 * <li>group - 组名称</li>
	 * <li>weights - 路由ID到权重的映射</li>
	 * <li>normalizedWeights - 归一化后的权重</li>
	 * <li>rangeIndexes - 区间索引到路由ID的映射</li>
	 * <li>ranges - 权重区间列表</li>
	 * </ul>
	 *
	 * <p>
	 * 使用LinkedHashMap保持插入顺序，确保区间计算的一致性。
	 */
	/* for testing */
	static class GroupWeightConfig {

		/**
		 * 权重组名称。
		 */
		String group;

		/**
		 * 原始权重映射。
		 * <p>
		 * 键为路由ID，值为配置的权重值
		 * </p>
		 */
		LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();

		/**
		 * 归一化权重映射。
		 * <p>
		 * 键为路由ID，值为0-1之间的归一化权重
		 * </p>
		 */
		LinkedHashMap<String, Double> normalizedWeights = new LinkedHashMap<>();

		/**
		 * 区间索引映射。
		 * <p>
		 * 键为区间索引，值为路由ID
		 * </p>
		 */
		LinkedHashMap<Integer, String> rangeIndexes = new LinkedHashMap<>();

		/**
		 * 权重区间列表。
		 * <p>
		 * 每个元素表示权重区间上界
		 * </p>
		 */
		List<Double> ranges = new ArrayList<>();

		/**
		 * 构造函数，创建新的组配置。
		 * @param group 组名称
		 */
		GroupWeightConfig(String group) {
			this.group = group;
		}

		/**
		 * 复制构造函数。
		 * @param other 被复制的配置对象
		 */
		GroupWeightConfig(GroupWeightConfig other) {
			this.group = other.group;
			this.weights = new LinkedHashMap<>(other.weights);
			this.normalizedWeights = new LinkedHashMap<>(other.normalizedWeights);
			this.rangeIndexes = new LinkedHashMap<>(other.rangeIndexes);
		}

		/**
		 * 返回配置对象的字符串表示。
		 * @return 字符串表示
		 */
		@Override
		public String toString() {
			return new ToStringCreator(this).append("group", group).append("weights", weights)
					.append("normalizedWeights", normalizedWeights).append("rangeIndexes", rangeIndexes).toString();
		}

	}

}
