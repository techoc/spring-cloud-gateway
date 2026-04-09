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

import java.util.Collections;
import java.util.Map;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.StatefulConfigurable;
import org.springframework.util.Assert;

/**
 * 限流器核心接口。
 *
 * <p>
 * 该接口定义了限流器的标准行为，是所有限流器实现必须实现的接口。
 *
 * <p>
 * 功能说明：
 * <ul>
 * <li>继承自StatefulConfigurable，支持状态化配置管理</li>
 * <li>定义isAllowed方法，用于判断请求是否允许通过</li>
 * <li>提供Response内部类封装限流结果</li>
 * </ul>
 *
 * <p>
 * 使用场景：
 * <ul>
 * <li>API接口访问频率控制</li>
 * <li>防止DDoS攻击和爬虫爬取</li>
 * <li>服务间调用的流量控制</li>
 * <li>资源配额管理和计费控制</li>
 * </ul>
 *
 * <p>
 * 实现要求：
 * <ul>
 * <li>实现类应保证限流判断的原子性</li>
 * <li>分布式环境下需保证一致性</li>
 * <li>应提供合理的错误处理机制</li>
 * </ul>
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: limited-route
 *           filters:
 *             - name: RequestRateLimiter
 *               args:
 *                 redis-rate-limiter.replenishRate: 10
 *                 redis-rate-limiter.burstCapacity: 20
 * </pre>
 *
 * @param <C> 配置类型，指定限流器使用的配置类
 * @author Spencer Gibb
 * @see StatefulConfigurable
 * @see Response
 */
public interface RateLimiter<C> extends StatefulConfigurable<C> {

	/**
	 * 判断指定路由的请求是否允许通过限流检查。
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>routeId - 路由标识符，用于获取该路由的限流配置</li>
	 * <li>id - 限流键值，由KeyResolver解析生成</li>
	 * </ul>
	 *
	 * <p>
	 * 返回值说明：
	 * <ul>
	 * <li>返回包含限流结果的Response对象</li>
	 * <li>Response包含是否允许和相关的HTTP响应头</li>
	 * <li>使用Mono包装支持响应式编程</li>
	 * </ul>
	 *
	 * <p>
	 * 业务逻辑：
	 * <ol>
	 * <li>根据routeId获取或创建限流配置</li>
	 * <li>根据id获取或创建该键的令牌桶状态</li>
	 * <li>执行令牌桶算法判断是否允许</li>
	 * <li>更新令牌桶状态并返回结果</li>
	 * </ol>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>该方法应保证线程安全和原子性</li>
	 * <li>Redis等分布式实现需保证脚本原子执行</li>
	 * <li>异常时应考虑是否放行请求</li>
	 * </ul>
	 * @param routeId 路由标识符
	 * @param id 限流键值
	 * @return 限流结果的Mono对象
	 */
	Mono<Response> isAllowed(String routeId, String id);

	/**
	 * 限流响应结果类。
	 *
	 * <p>
	 * 封装限流检查的结果，包含请求是否被允许以及相关的HTTP响应头信息。
	 *
	 * <p>
	 * 响应头说明：
	 * <ul>
	 * <li>X-RateLimit-Remaining: 剩余可用请求数</li>
	 * <li>X-RateLimit-Replenish-Rate: 令牌补充速率</li>
	 * <li>X-RateLimit-Burst-Capacity: 突发容量</li>
	 * <li>X-RateLimit-Requested-Tokens: 单次请求消耗的令牌数</li>
	 * </ul>
	 *
	 * <p>
	 * 使用示例： <pre>
	 * isAllowed(routeId, id).map(response -> {
	 *     if (!response.isAllowed()) {
	 *         // 返回429 Too Many Requests
	 *     }
	 *     return response.getHeaders();
	 * });
	 * </pre>
	 */
	class Response {

		/**
		 * 请求是否被允许通过。
		 * <p>
		 * true表示允许，false表示被限流拦截
		 * </p>
		 */
		private final boolean allowed;

		/**
		 * 剩余令牌数量。
		 * <p>
		 * 注意：此字段当前未在构造函数中设置，固定为-1
		 * </p>
		 * @deprecated 由于实现变更，此字段可能不再准确
		 */
		private final long tokensRemaining;

		/**
		 * HTTP响应头映射。
		 * <p>
		 * 包含限流相关的响应头信息
		 * </p>
		 */
		private final Map<String, String> headers;

		/**
		 * 构造函数，创建限流响应对象。
		 *
		 * <p>
		 * 参数说明：
		 * <ul>
		 * <li>allowed - 请求是否被允许</li>
		 * <li>headers - 限流相关的HTTP响应头</li>
		 * </ul>
		 *
		 * <p>
		 * 注意事项：
		 * <ul>
		 * <li>tokensRemaining固定设置为-1</li>
		 * <li>headers不能为null</li>
		 * </ul>
		 * @param allowed 请求是否被允许
		 * @param headers HTTP响应头映射
		 */
		public Response(boolean allowed, Map<String, String> headers) {
			this.allowed = allowed;
			this.tokensRemaining = -1;
			Assert.notNull(headers, "headers may not be null");
			this.headers = headers;
		}

		/**
		 * 判断请求是否被允许。
		 *
		 * <p>
		 * 返回值说明：
		 * <ul>
		 * <li>true - 请求可以通过，继续处理</li>
		 * <li>false - 请求被限流拦截，应返回429状态码</li>
		 * </ul>
		 * @return 请求是否被允许
		 */
		public boolean isAllowed() {
			return allowed;
		}

		/**
		 * 获取限流相关的HTTP响应头。
		 *
		 * <p>
		 * 返回值说明：
		 * <ul>
		 * <li>返回不可修改的Map副本</li>
		 * <li>包含X-RateLimit-*系列响应头</li>
		 * </ul>
		 * @return HTTP响应头映射
		 */
		public Map<String, String> getHeaders() {
			return Collections.unmodifiableMap(headers);
		}

		/**
		 * 返回响应对象的字符串表示。
		 *
		 * <p>
		 * 返回格式：Response{allowed=..., headers=..., tokensRemaining=...}
		 * @return 响应对象的字符串表示
		 */
		@Override
		public String toString() {
			final StringBuffer sb = new StringBuffer("Response{");
			sb.append("allowed=").append(allowed);
			sb.append(", headers=").append(headers);
			sb.append(", tokensRemaining=").append(tokensRemaining);
			sb.append('}');
			return sb.toString();
		}

	}

}
