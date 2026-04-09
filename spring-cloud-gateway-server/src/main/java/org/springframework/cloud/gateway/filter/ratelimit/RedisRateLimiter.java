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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.validation.constraints.Min;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.style.ToStringCreator;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

/**
 * 基于Redis的令牌桶限流器实现。
 *
 * <p>
 * 该类使用Redis和Lua脚本实现高效的分布式限流，采用令牌桶算法。
 *
 * <p>
 * 功能说明：
 * <ul>
 * <li>基于令牌桶算法的流量控制</li>
 * <li>使用Redis保证分布式环境下的一致性</li>
 * <li>Lua脚本保证限流判断的原子性</li>
 * <li>支持按用户、IP、接口等维度的限流</li>
 * </ul>
 *
 * <p>
 * 算法原理（令牌桶）：
 * <ul>
 * <li>桶的容量代表最大突发流量</li>
 * <li>以固定速率向桶中添加令牌</li>
 * <li>每个请求消耗一个或多个令牌</li>
 * <li>令牌不足时请求被拒绝</li>
 * </ul>
 *
 * <p>
 * 使用场景：
 * <ul>
 * <li>API接口的访问频率限制</li>
 * <li>防止恶意爬虫和DDoS攻击</li>
 * <li>付费API的配额管理</li>
 * <li>微服务间的流量控制</li>
 * </ul>
 *
 * <p>
 * 参考文档：
 * <ul>
 * <li><a href="https://stripe.com/blog/rate-limiters">Stripe Rate Limiters</a></li>
 * <li><a href="https://gist.github.com/ptarjan/e38f45f2dfe601419ca3af937fff574d">Redis
 * Rate Limiter Script</a></li>
 * </ul>
 *
 * @author Spencer Gibb
 * @author Ronny Bräunlich
 * @author Denis Cutic
 * @see AbstractRateLimiter
 * @see RateLimiter
 */
@ConfigurationProperties("spring.cloud.gateway.redis-rate-limiter")
public class RedisRateLimiter extends AbstractRateLimiter<RedisRateLimiter.Config> implements ApplicationContextAware {

	/**
	 * Redis限流器配置属性名称。
	 * <p>
	 * 用于在配置文件中指定限流器相关属性，前缀格式：spring.cloud.gateway.redis-rate-limiter.*
	 * </p>
	 */
	public static final String CONFIGURATION_PROPERTY_NAME = "redis-rate-limiter";

	/**
	 * Redis Lua脚本的Bean名称。
	 * <p>
	 * 定义了令牌桶算法的具体实现脚本
	 * </p>
	 */
	public static final String REDIS_SCRIPT_NAME = "redisRequestRateLimiterScript";

	/**
	 * 剩余请求数响应头名称。
	 * <p>
	 * 告诉客户端当前剩余的可请求次数
	 * </p>
	 */
	public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

	/**
	 * 令牌补充速率响应头名称。
	 * <p>
	 * 告诉客户端每秒补充多少令牌
	 * </p>
	 */
	public static final String REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";

	/**
	 * 突发容量响应头名称。
	 * <p>
	 * 告诉客户端桶的最大容量
	 * </p>
	 */
	public static final String BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";

	/**
	 * 请求令牌数响应头名称。
	 * <p>
	 * 告诉客户端每个请求消耗多少令牌
	 * </p>
	 */
	public static final String REQUESTED_TOKENS_HEADER = "X-RateLimit-Requested-Tokens";

	/**
	 * 日志记录器。
	 * <p>
	 * 用于记录限流器的运行日志，便于问题排查
	 * </p>
	 */
	private Log log = LogFactory.getLog(getClass());

	/**
	 * Redis响应式字符串模板。
	 * <p>
	 * 用于执行Redis命令，支持响应式编程
	 * </p>
	 */
	private ReactiveStringRedisTemplate redisTemplate;

	/**
	 * Redis Lua脚本。
	 * <p>
	 * 定义了原子性的令牌桶操作
	 * </p>
	 */
	private RedisScript<List<Long>> script;

	/**
	 * 初始化状态标志。
	 * <p>
	 * 使用原子布尔值确保初始化只执行一次
	 * </p>
	 */
	private AtomicBoolean initialized = new AtomicBoolean(false);

	/**
	 * 默认限流配置。
	 * <p>
	 * 当特定路由无配置时使用此默认值
	 * </p>
	 */
	private Config defaultConfig;

	// configuration properties

	/**
	 * 是否在响应头中包含限流信息。
	 * <p>
	 * 默认为true，客户端可以获取限流详情
	 * </p>
	 * <p>
	 * 设置为false可减少响应头大小
	 * </p>
	 */
	private boolean includeHeaders = true;

	/**
	 * 剩余请求数响应头名称。
	 * <p>
	 * 可通过配置覆盖默认值 X-RateLimit-Remaining
	 * </p>
	 */
	private String remainingHeader = REMAINING_HEADER;

	/**
	 * 令牌补充速率响应头名称。
	 * <p>
	 * 可通过配置覆盖默认值 X-RateLimit-Replenish-Rate
	 * </p>
	 */
	private String replenishRateHeader = REPLENISH_RATE_HEADER;

	/**
	 * 突发容量响应头名称。
	 * <p>
	 * 可通过配置覆盖默认值 X-RateLimit-Burst-Capacity
	 * </p>
	 */
	private String burstCapacityHeader = BURST_CAPACITY_HEADER;

	/**
	 * 请求令牌数响应头名称。
	 * <p>
	 * 可通过配置覆盖默认值 X-RateLimit-Requested-Tokens
	 * </p>
	 */
	private String requestedTokensHeader = REQUESTED_TOKENS_HEADER;

	/**
	 * 全参数构造函数。
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>redisTemplate - Redis响应式模板，用于执行Redis命令</li>
	 * <li>script - Lua脚本，定义令牌桶算法</li>
	 * <li>configurationService - 配置服务，用于绑定配置属性</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>构造时将initialized设为true</li>
	 * <li>需要确保Redis和脚本Bean已正确配置</li>
	 * </ul>
	 * @param redisTemplate Redis响应式模板
	 * @param script Redis Lua脚本
	 * @param configurationService 配置服务
	 */
	public RedisRateLimiter(ReactiveStringRedisTemplate redisTemplate, RedisScript<List<Long>> script,
			ConfigurationService configurationService) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
		this.redisTemplate = redisTemplate;
		this.script = script;
		this.initialized.compareAndSet(false, true);
	}

	/**
	 * 简化的构造函数，用于Java DSL静态配置。
	 *
	 * <p>
	 * 适用场景：
	 * <ul>
	 * <li>在Java代码中直接定义限流规则</li>
	 * <li>不依赖Spring Boot的配置属性</li>
	 * <li>所有路由使用相同的限流配置</li>
	 * </ul>
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>defaultReplenishRate - 每秒补充的令牌数</li>
	 * <li>defaultBurstCapacity - 令牌桶的最大容量</li>
	 * </ul>
	 *
	 * <p>
	 * 示例： <pre>
	 * RedisRateLimiter limiter = new RedisRateLimiter(10, 20);
	 * </pre>
	 * @param defaultReplenishRate 默认令牌补充速率（每秒）
	 * @param defaultBurstCapacity 默认突发容量（令牌数）
	 */
	public RedisRateLimiter(int defaultReplenishRate, int defaultBurstCapacity) {
		super(Config.class, CONFIGURATION_PROPERTY_NAME, (ConfigurationService) null);
		this.defaultConfig = new Config().setReplenishRate(defaultReplenishRate).setBurstCapacity(defaultBurstCapacity);
	}

	/**
	 * 带请求令牌数的构造函数，用于Java DSL静态配置。
	 *
	 * <p>
	 * 适用场景：
	 * <ul>
	 * <li>每个请求消耗多个令牌的情况</li>
	 * <li>资源密集型API的限流</li>
	 * </ul>
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>defaultReplenishRate - 每秒补充的令牌数</li>
	 * <li>defaultBurstCapacity - 令牌桶的最大容量</li>
	 * <li>defaultRequestedTokens - 每个请求消耗的令牌数</li>
	 * </ul>
	 * @param defaultReplenishRate 默认令牌补充速率（每秒）
	 * @param defaultBurstCapacity 默认突发容量（令牌数）
	 * @param defaultRequestedTokens 默认请求令牌数
	 */
	public RedisRateLimiter(int defaultReplenishRate, int defaultBurstCapacity, int defaultRequestedTokens) {
		this(defaultReplenishRate, defaultBurstCapacity);
		this.defaultConfig.setRequestedTokens(defaultRequestedTokens);
	}

	/**
	 * 生成Redis键列表。
	 *
	 * <p>
	 * 核心逻辑：
	 * <ul>
	 * <li>使用Redis Hash Tag语法 {} 包裹键值</li>
	 * <li>确保Redis集群环境下同一用户的键在同一槽</li>
	 * <li>生成两个键：令牌数键和时间戳键</li>
	 * </ul>
	 *
	 * <p>
	 * 键结构：
	 * <ul>
	 * <li>request_rate_limiter.{用户ID}.tokens - 剩余令牌数</li>
	 * <li>request_rate_limiter.{用户ID}.timestamp - 最后更新时间</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>id参数由KeyResolver生成，应保证唯一性</li>
	 * <li>Hash Tag确保集群环境下键的正确分布</li>
	 * </ul>
	 * @param id 限流键值（通常为用户ID或IP地址）
	 * @return Redis键列表，包含令牌键和时间戳键
	 */
	static List<String> getKeys(String id) {
		// use `{}` around keys to use Redis Key hash tags
		// this allows for using redis cluster

		// Make a unique key per user.
		String prefix = "request_rate_limiter.{" + id;

		// You need two Redis keys for Token Bucket.
		String tokenKey = prefix + "}.tokens";
		String timestampKey = prefix + "}.timestamp";
		return Arrays.asList(tokenKey, timestampKey);
	}

	/**
	 * 检查是否包含限流响应头。
	 * @return true表示包含响应头，false表示不包含
	 */
	public boolean isIncludeHeaders() {
		return includeHeaders;
	}

	/**
	 * 设置是否包含限流响应头。
	 * @param includeHeaders 是否包含响应头
	 */
	public void setIncludeHeaders(boolean includeHeaders) {
		this.includeHeaders = includeHeaders;
	}

	/**
	 * 获取剩余请求数响应头名称。
	 * @return 响应头名称
	 */
	public String getRemainingHeader() {
		return remainingHeader;
	}

	/**
	 * 设置剩余请求数响应头名称。
	 * @param remainingHeader 响应头名称
	 */
	public void setRemainingHeader(String remainingHeader) {
		this.remainingHeader = remainingHeader;
	}

	/**
	 * 获取令牌补充速率响应头名称。
	 * @return 响应头名称
	 */
	public String getReplenishRateHeader() {
		return replenishRateHeader;
	}

	/**
	 * 设置令牌补充速率响应头名称。
	 * @param replenishRateHeader 响应头名称
	 */
	public void setReplenishRateHeader(String replenishRateHeader) {
		this.replenishRateHeader = replenishRateHeader;
	}

	/**
	 * 获取突发容量响应头名称。
	 * @return 响应头名称
	 */
	public String getBurstCapacityHeader() {
		return burstCapacityHeader;
	}

	/**
	 * 设置突发容量响应头名称。
	 * @param burstCapacityHeader 响应头名称
	 */
	public void setBurstCapacityHeader(String burstCapacityHeader) {
		this.burstCapacityHeader = burstCapacityHeader;
	}

	/**
	 * 获取请求令牌数响应头名称。
	 * @return 响应头名称
	 */
	public String getRequestedTokensHeader() {
		return requestedTokensHeader;
	}

	/**
	 * 设置请求令牌数响应头名称。
	 * @param requestedTokensHeader 响应头名称
	 */
	public void setRequestedTokensHeader(String requestedTokensHeader) {
		this.requestedTokensHeader = requestedTokensHeader;
	}

	/**
	 * 设置应用上下文，用于初始化依赖的Bean。
	 *
	 * <p>
	 * 核心逻辑：
	 * <ul>
	 * <li>检查是否已初始化，防止重复初始化</li>
	 * <li>如果redisTemplate为空，从上下文获取</li>
	 * <li>获取Lua脚本Bean</li>
	 * <li>获取配置服务Bean（如果存在）</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>使用compareAndSet确保线程安全</li>
	 * <li>只执行一次初始化</li>
	 * <li>异常会被Spring框架处理</li>
	 * </ul>
	 * @param context Spring应用上下文
	 * @throws BeansException 如果初始化失败
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		if (initialized.compareAndSet(false, true)) {
			if (this.redisTemplate == null) {
				this.redisTemplate = context.getBean(ReactiveStringRedisTemplate.class);
			}
			this.script = context.getBean(REDIS_SCRIPT_NAME, RedisScript.class);
			if (context.getBeanNamesForType(ConfigurationService.class).length > 0) {
				setConfigurationService(context.getBean(ConfigurationService.class));
			}
		}
	}

	/**
	 * 获取默认配置。
	 * <p>
	 * 主要用于测试目的
	 * </p>
	 * @return 默认限流配置
	 */
	/* for testing */ Config getDefaultConfig() {
		return defaultConfig;
	}

	/**
	 * 执行限流检查，判断请求是否允许通过。
	 *
	 * <p>
	 * 核心业务逻辑：
	 * <ol>
	 * <li>检查初始化状态</li>
	 * <li>加载限流配置</li>
	 * <li>获取Redis键</li>
	 * <li>执行Lua脚本进行令牌桶操作</li>
	 * <li>解析结果并返回响应</li>
	 * </ol>
	 *
	 * <p>
	 * 令牌桶算法说明：
	 * <ul>
	 * <li>使用Redis存储令牌桶状态</li>
	 * <li>Lua脚本保证判断的原子性</li>
	 * <li>replenishRate控制每秒补充的令牌数</li>
	 * <li>burstCapacity控制最大可突发请求数</li>
	 * </ul>
	 *
	 * <p>
	 * 错误处理策略：
	 * <ul>
	 * <li>Redis执行失败时记录日志</li>
	 * <li>返回allowed=true允许请求通过</li>
	 * <li>避免Redis故障导致服务完全不可用</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>未初始化时会抛出IllegalStateException</li>
	 * <li>异常情况下默认放行请求</li>
	 * <li>应监控异常情况的发生频率</li>
	 * </ul>
	 * @param routeId 路由标识符
	 * @param id 限流键值
	 * @return 限流响应
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Mono<Response> isAllowed(String routeId, String id) {
		if (!this.initialized.get()) {
			throw new IllegalStateException("RedisRateLimiter is not initialized");
		}

		Config routeConfig = loadConfiguration(routeId);

		// How many requests per second do you want a user to be allowed to do?
		int replenishRate = routeConfig.getReplenishRate();

		// How much bursting do you want to allow?
		int burstCapacity = routeConfig.getBurstCapacity();

		// How many tokens are requested per request?
		int requestedTokens = routeConfig.getRequestedTokens();

		try {
			List<String> keys = getKeys(id);

			// The arguments to the LUA script. time() returns unixtime in seconds.
			List<String> scriptArgs = Arrays.asList(replenishRate + "", burstCapacity + "", "", requestedTokens + "");
			// allowed, tokens_left = redis.eval(SCRIPT, keys, args)
			Flux<List<Long>> flux = this.redisTemplate.execute(this.script, keys, scriptArgs);
			// .log("redisratelimiter", Level.FINER);
			return flux.onErrorResume(throwable -> {
				if (log.isDebugEnabled()) {
					log.debug("Error calling rate limiter lua", throwable);
				}
				return Flux.just(Arrays.asList(1L, -1L));
			}).reduce(new ArrayList<Long>(), (longs, l) -> {
				longs.addAll(l);
				return longs;
			}).map(results -> {
				boolean allowed = results.get(0) == 1L;
				Long tokensLeft = results.get(1);

				Response response = new Response(allowed, getHeaders(routeConfig, tokensLeft));

				if (log.isDebugEnabled()) {
					log.debug("response: " + response);
				}
				return response;
			});
		}
		catch (Exception e) {
			/*
			 * We don't want a hard dependency on Redis to allow traffic. Make sure to set
			 * an alert so you know if this is happening too much. Stripe's observed
			 * failure rate is 0.01%.
			 */
			log.error("Error determining if user allowed from redis", e);
		}
		return Mono.just(new Response(true, getHeaders(routeConfig, -1L)));
	}

	/**
	 * 加载指定路由的限流配置。
	 *
	 * <p>
	 * 配置查找顺序：
	 * <ol>
	 * <li>查找路由ID对应的配置</li>
	 * <li>查找默认过滤器配置</li>
	 * <li>抛出异常（无配置）</li>
	 * </ol>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>必须为每个路由配置限流参数</li>
	 * <li>可以使用defaultFilters为所有路由设置默认值</li>
	 * </ul>
	 * @param routeId 路由标识符
	 * @return 该路由的限流配置
	 * @throws IllegalArgumentException 如果找不到配置
	 */
	/* for testing */ Config loadConfiguration(String routeId) {
		Config routeConfig = getConfig().getOrDefault(routeId, defaultConfig);

		if (routeConfig == null) {
			routeConfig = getConfig().get(RouteDefinitionRouteLocator.DEFAULT_FILTERS);
		}

		if (routeConfig == null) {
			throw new IllegalArgumentException("No Configuration found for route " + routeId + " or defaultFilters");
		}
		return routeConfig;
	}

	/**
	 * 生成限流相关的HTTP响应头。
	 *
	 * <p>
	 * 响应头内容：
	 * <ul>
	 * <li>X-RateLimit-Remaining: 剩余令牌数</li>
	 * <li>X-RateLimit-Replenish-Rate: 令牌补充速率</li>
	 * <li>X-RateLimit-Burst-Capacity: 突发容量</li>
	 * <li>X-RateLimit-Requested-Tokens: 请求令牌数</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>可通过配置自定义响应头名称</li>
	 * <li>includeHeaders=false时不包含任何响应头</li>
	 * </ul>
	 * @param config 限流配置
	 * @param tokensLeft 剩余令牌数
	 * @return HTTP响应头映射
	 */
	public Map<String, String> getHeaders(Config config, Long tokensLeft) {
		Map<String, String> headers = new HashMap<>();
		if (isIncludeHeaders()) {
			headers.put(this.remainingHeader, tokensLeft.toString());
			headers.put(this.replenishRateHeader, String.valueOf(config.getReplenishRate()));
			headers.put(this.burstCapacityHeader, String.valueOf(config.getBurstCapacity()));
			headers.put(this.requestedTokensHeader, String.valueOf(config.getRequestedTokens()));
		}
		return headers;
	}

	/**
	 * Redis限流器的配置类。
	 *
	 * <p>
	 * 定义了令牌桶算法的三个核心参数：
	 * <ul>
	 * <li>replenishRate - 令牌补充速率</li>
	 * <li>burstCapacity - 突发容量</li>
	 * <li>requestedTokens - 请求令牌数</li>
	 * </ul>
	 *
	 * <p>
	 * 配置示例： <pre>
	 * spring:
	 *   cloud:
	 *     gateway:
	 *       redis-rate-limiter:
	 *         includeHeaders: true
	 *         remainingHeader: X-RateLimit-Remaining
	 * </pre>
	 *
	 * <p>
	 * 路由配置示例： <pre>
	 * - id: my-route
	 *   uri: http://example.com
	 *   filters:
	 *     - name: RequestRateLimiter
	 *       args:
	 *         redis-rate-limiter.replenishRate: 10
	 *         redis-rate-limiter.burstCapacity: 20
	 *         redis-rate-limiter.requestedTokens: 1
	 * </pre>
	 */
	@Validated
	public static class Config {

		/**
		 * 令牌补充速率（每秒）。
		 * <p>
		 * 表示每秒向桶中添加的令牌数量
		 * </p>
		 *
		 * <p>
		 * 使用场景：
		 * <ul>
		 * <li>控制用户的平均请求速率</li>
		 * <li>例如设置为10表示每秒允许10个请求</li>
		 * </ul>
		 *
		 * <p>
		 * 约束：
		 * <ul>
		 * <li>最小值为1</li>
		 * <li>必须为正整数</li>
		 * </ul>
		 */
		@Min(1)
		private int replenishRate;

		/**
		 * 突发容量（令牌数）。
		 * <p>
		 * 令牌桶能够容纳的最大令牌数
		 * </p>
		 *
		 * <p>
		 * 使用场景：
		 * <ul>
		 * <li>允许短时间内超过平均速率的请求</li>
		 * <li>例如设置为50，允许最多连续50个请求</li>
		 * </ul>
		 *
		 * <p>
		 * 约束：
		 * <ul>
		 * <li>最小值为0</li>
		 * <li>必须大于或等于replenishRate</li>
		 * </ul>
		 *
		 * <p>
		 * 默认值：1
		 */
		@Min(0)
		private int burstCapacity = 1;

		/**
		 * 单次请求消耗的令牌数。
		 * <p>
		 * 每个请求从桶中消耗的令牌数量
		 * </p>
		 *
		 * <p>
		 * 使用场景：
		 * <ul>
		 * <li>资源密集型操作消耗更多令牌</li>
		 * <li>例如文件下载可能消耗5个令牌</li>
		 * </ul>
		 *
		 * <p>
		 * 约束：
		 * <ul>
		 * <li>最小值为1</li>
		 * <li>必须为正整数</li>
		 * </ul>
		 *
		 * <p>
		 * 默认值：1
		 */
		@Min(1)
		private int requestedTokens = 1;

		/**
		 * 获取令牌补充速率。
		 * @return 每秒补充的令牌数
		 */
		public int getReplenishRate() {
			return replenishRate;
		}

		/**
		 * 设置令牌补充速率。
		 * @param replenishRate 每秒补充的令牌数
		 * @return 配置对象，支持链式调用
		 */
		public Config setReplenishRate(int replenishRate) {
			this.replenishRate = replenishRate;
			return this;
		}

		/**
		 * 获取突发容量。
		 * @return 令牌桶最大容量
		 */
		public int getBurstCapacity() {
			return burstCapacity;
		}

		/**
		 * 设置突发容量。
		 *
		 * <p>
		 * 校验规则：
		 * <ul>
		 * <li>burstCapacity必须大于或等于replenishRate</li>
		 * <li>否则抛出IllegalStateException</li>
		 * </ul>
		 * @param burstCapacity 令牌桶最大容量
		 * @return 配置对象，支持链式调用
		 * @throws IllegalStateException 如果burstCapacity小于replenishRate
		 */
		public Config setBurstCapacity(int burstCapacity) {
			Assert.isTrue(burstCapacity >= this.replenishRate, "BurstCapacity(" + burstCapacity
					+ ") must be greater than or equal than replenishRate(" + this.replenishRate + ")");
			this.burstCapacity = burstCapacity;
			return this;
		}

		/**
		 * 获取请求令牌数。
		 * @return 单次请求消耗的令牌数
		 */
		public int getRequestedTokens() {
			return requestedTokens;
		}

		/**
		 * 设置请求令牌数。
		 * @param requestedTokens 单次请求消耗的令牌数
		 * @return 配置对象，支持链式调用
		 */
		public Config setRequestedTokens(int requestedTokens) {
			this.requestedTokens = requestedTokens;
			return this;
		}

		/**
		 * 返回配置对象的字符串表示。
		 * @return 字符串表示
		 */
		@Override
		public String toString() {
			return new ToStringCreator(this).append("replenishRate", replenishRate)
					.append("burstCapacity", burstCapacity).append("requestedTokens", requestedTokens).toString();

		}

	}

}
