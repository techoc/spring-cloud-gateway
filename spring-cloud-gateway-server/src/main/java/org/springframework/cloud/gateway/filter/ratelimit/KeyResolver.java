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

import reactor.core.publisher.Mono;

import org.springframework.web.server.ServerWebExchange;

/**
 * 限流键(Key)解析器接口。
 *
 * <p>
 * 该接口定义了从请求上下文中提取限流键的核心方法，是限流系统的关键组件。
 *
 * <p>
 * 功能说明：
 * <ul>
 * <li>用于确定限流的粒度和维度</li>
 * <li>键值决定了限流的作用范围</li>
 * <li>支持多种限流策略的实现</li>
 * </ul>
 *
 * <p>
 * 使用场景：
 * <ul>
 * <li>基于用户/IP的限流：通过解析用户身份或IP地址作为键</li>
 * <li>基于接口的限流：以API路径作为键实现按接口限流</li>
 * <li>基于服务实例的限流：以服务ID作为键实现按服务限流</li>
 * <li>混合策略：结合多种信息生成复合键</li>
 * </ul>
 *
 * <p>
 * 返回值说明：
 * <ul>
 * <li>返回Mono{@code <String>}，支持响应式编程</li>
 * <li>返回空Mono表示该请求不参与限流</li>
 * <li>返回null也会被转换为空Mono处理</li>
 * </ul>
 *
 * <p>
 * 实现建议：
 * <ul>
 * <li>实现类应考虑空值和异常情况的处理</li>
 * <li>注意键的生成逻辑应保持幂等性</li>
 * <li>对于分布式限流，键值应具备唯一性</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see ServerWebExchange
 * @see Mono
 */
public interface KeyResolver {

	/**
	 * 从请求上下文中解析出限流键。
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>exchange - 服务器Web交换对象，包含完整的请求和响应信息</li>
	 * </ul>
	 *
	 * <p>
	 * 返回值说明：
	 * <ul>
	 * <li>返回限流键的Mono对象</li>
	 * <li>键值格式由具体实现决定</li>
	 * <li>可能返回空Mono表示跳过限流</li>
	 * </ul>
	 *
	 * <p>
	 * 业务逻辑：
	 * <ul>
	 * <li>从exchange中提取需要的请求信息</li>
	 * <li>根据策略生成唯一的限流键</li>
	 * <li>返回响应式的键值对象</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>实现应处理未认证请求的情况</li>
	 * <li>键值长度应适中，避免存储和比较开销</li>
	 * <li>对于异常情况，建议返回空Mono而非抛出异常</li>
	 * </ul>
	 * @param exchange 服务器Web交换对象
	 * @return 限流键的Mono对象
	 */
	Mono<String> resolve(ServerWebExchange exchange);

}
