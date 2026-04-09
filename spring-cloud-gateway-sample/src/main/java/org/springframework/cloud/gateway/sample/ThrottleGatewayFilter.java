/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.isomorphism.util.TokenBucket;
import org.isomorphism.util.TokenBuckets;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * 限流网关过滤器示例实现。
 * <p>
 * 本过滤器基于 Token Bucket（令牌桶）算法实现请求限流功能。
 * 令牌桶算法是一种常用的流量控制算法，它允许一定程度的突发流量，
 * 同时保持长期的平均速率限制。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>令牌桶以固定速率生成令牌，直到达到容量上限</li>
 *   <li>每个请求需要消耗一个令牌才能通过</li>
 *   <li>如果桶中有可用令牌，请求被放行</li>
 *   <li>如果桶中无可用令牌，返回 429（Too Many Requests）状态码</li>
 * </ol>
 * <p>
 * 可配置参数：
 * <ul>
 *   <li>capacity - 桶的容量，决定最大突发流量</li>
 *   <li>refillTokens - 每次补充的令牌数量</li>
 *   <li>refillPeriod - 补充周期的时间长度</li>
 *   <li>refillUnit - 补充周期的时间单位</li>
 * </ul>
 * <p>
 * 依赖库：token-bucket（https://github.com/bbeck/token-bucket）
 *
 * @see GatewayFilter
 * @see TokenBucket
 */
public class ThrottleGatewayFilter implements GatewayFilter {

	/**
	 * 日志记录器，用于记录限流相关的调试信息。
	 */
	private static final Log log = LogFactory.getLog(ThrottleGatewayFilter.class);

	/**
	 * 令牌桶的容量，表示桶中最多可以存储的令牌数量。
	 * 容量越大，允许的突发流量越高。
	 */
	int capacity;

	/**
	 * 每次补充的令牌数量，决定补充速率。
	 */
	int refillTokens;

	/**
	 * 补充周期的时间长度，与 refillUnit 一起决定补充频率。
	 */
	int refillPeriod;

	/**
	 * 补充周期的时间单位，如秒、分钟等。
	 */
	TimeUnit refillUnit;

	/**
	 * 获取令牌桶容量。
	 *
	 * @return 桶的容量
	 */
	public int getCapacity() {
		return capacity;
	}

	/**
	 * 设置令牌桶容量，支持链式调用。
	 *
	 * @param capacity 桶的容量
	 * @return 当前过滤器实例，支持链式调用
	 */
	public ThrottleGatewayFilter setCapacity(int capacity) {
		this.capacity = capacity;
		return this;
	}

	/**
	 * 获取每次补充的令牌数量。
	 *
	 * @return 补充令牌数
	 */
	public int getRefillTokens() {
		return refillTokens;
	}

	/**
	 * 设置每次补充的令牌数量，支持链式调用。
	 *
	 * @param refillTokens 补充令牌数
	 * @return 当前过滤器实例，支持链式调用
	 */
	public ThrottleGatewayFilter setRefillTokens(int refillTokens) {
		this.refillTokens = refillTokens;
		return this;
	}

	/**
	 * 获取补充周期的时间长度。
	 *
	 * @return 补充周期
	 */
	public int getRefillPeriod() {
		return refillPeriod;
	}

	/**
	 * 设置补充周期的时间长度，支持链式调用。
	 *
	 * @param refillPeriod 补充周期
	 * @return 当前过滤器实例，支持链式调用
	 */
	public ThrottleGatewayFilter setRefillPeriod(int refillPeriod) {
		this.refillPeriod = refillPeriod;
		return this;
	}

	/**
	 * 获取补充周期的时间单位。
	 *
	 * @return 时间单位
	 */
	public TimeUnit getRefillUnit() {
		return refillUnit;
	}

	/**
	 * 设置补充周期的时间单位，支持链式调用。
	 *
	 * @param refillUnit 时间单位
	 * @return 当前过滤器实例，支持链式调用
	 */
	public ThrottleGatewayFilter setRefillUnit(TimeUnit refillUnit) {
		this.refillUnit = refillUnit;
		return this;
	}

	/**
	 * 执行限流过滤逻辑。
	 * <p>
	 * 每次请求时创建一个新的令牌桶（实际生产环境应该按 key 缓存令牌桶），
	 * 尝试从桶中消费一个令牌。如果消费成功，请求继续传递到过滤器链；
	 * 如果消费失败（桶中无令牌），返回 429 状态码并结束请求。
	 * <p>
	 * 注意：当前实现每次请求都创建新的令牌桶，这只是示例代码。
	 * 生产环境应该根据客户端标识（如 IP、用户 ID 等）缓存和复用令牌桶。
	 *
	 * @param exchange 当前的服务器 Web 交换对象，包含请求和响应信息
	 * @param chain    网关过滤器链，用于将请求传递给下一个过滤器
	 * @return Mono<Void> 表示异步处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

		TokenBucket tokenBucket = TokenBuckets.builder().withCapacity(capacity)
				.withFixedIntervalRefillStrategy(refillTokens, refillPeriod, refillUnit).build();

		// TODO: 应该根据 key（如客户端 IP）获取对应的令牌桶，而不是每次都创建新的
		log.debug("TokenBucket capacity: " + tokenBucket.getCapacity());
		boolean consumed = tokenBucket.tryConsume();
		if (consumed) {
			return chain.filter(exchange);
		}
		exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		return exchange.getResponse().setComplete();
	}

}
