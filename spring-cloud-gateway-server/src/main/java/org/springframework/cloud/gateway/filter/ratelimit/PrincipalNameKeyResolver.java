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
 * 基于认证主体(Principal)名称的限流键解析器实现。
 *
 * <p>
 * 该类是KeyResolver接口的典型实现，以请求认证主体的名称作为限流键。
 *
 * <p>
 * 功能说明：
 * <ul>
 * <li>从ServerWebExchange中获取认证主体信息</li>
 * <li>使用主体的名称作为限流键</li>
 * <li>实现基于用户身份的限流策略</li>
 * </ul>
 *
 * <p>
 * 使用场景：
 * <ul>
 * <li>用户级别的接口访问限流</li>
 * <li>付费用户和普通用户的差异化限流</li>
 * <li>基于用户ID的API调用频率控制</li>
 * <li>防止单一用户过度消耗系统资源</li>
 * </ul>
 *
 * <p>
 * 工作原理：
 * <ol>
 * <li>从exchange获取认证主体(Principal)</li>
 * <li>使用flatMap处理可能不存在的Principal</li>
 * <li>调用getName()获取主体名称</li>
 * <li>使用Mono.justOrEmpty处理可能的空值</li>
 * </ol>
 *
 * <p>
 * 注意事项：
 * <ul>
 * <li>未认证的请求将返回空Mono，该请求不会被限流</li>
 * <li>如果Principal存在但name为空，也会返回空Mono</li>
 * <li>适用于需要用户登录的API限流场景</li>
 * <li>对于公开API，建议使用其他基于IP或路径的KeyResolver</li>
 * </ul>
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       routes:
 *         - id: limited-route
 *           uri: http://example.com
 *           filters:
 *             - name: RequestRateLimiter
 *               args:
 *                 key-resolver: '#{@principalNameKeyResolver}'
 * </pre>
 *
 * @author Spencer Gibb
 * @see KeyResolver
 * @see ServerWebExchange
 */
public class PrincipalNameKeyResolver implements KeyResolver {

	/**
	 * PrincipalNameKeyResolver的Spring Bean名称。
	 * <p>
	 * 用于在配置中引用此Bean，格式为 '#{@principalNameKeyResolver}'
	 * </p>
	 *
	 * <p>
	 * 使用场景：
	 * <ul>
	 * <li>在Spring配置中作为key-resolver的引用值</li>
	 * <li>在Java DSL中通过Bean名称注入</li>
	 * </ul>
	 */
	public static final String BEAN_NAME = "principalNameKeyResolver";

	/**
	 * 解析限流键，使用请求的认证主体名称。
	 *
	 * <p>
	 * 核心逻辑：
	 * <ul>
	 * <li>从ServerWebExchange中获取认证主体</li>
	 * <li>提取主体的名称作为限流键</li>
	 * <li>处理主体不存在或名称为空的情况</li>
	 * </ul>
	 *
	 * <p>
	 * 返回值说明：
	 * <ul>
	 * <li>成功：返回主体名称作为限流键</li>
	 * <li>失败：返回空Mono（无认证主体或名称为空）</li>
	 * </ul>
	 *
	 * <p>
	 * 示例：
	 * <ul>
	 * <li>用户"admin"登录后访问API，返回键"admin"</li>
	 * <li>匿名请求（无认证）返回空Mono</li>
	 * </ul>
	 * @param exchange 服务器Web交换对象
	 * @return 主体名称的Mono对象，如果无主体或名称为空则返回空Mono
	 */
	@Override
	public Mono<String> resolve(ServerWebExchange exchange) {
		return exchange.getPrincipal().flatMap(p -> Mono.justOrEmpty(p.getName()));
	}

}
