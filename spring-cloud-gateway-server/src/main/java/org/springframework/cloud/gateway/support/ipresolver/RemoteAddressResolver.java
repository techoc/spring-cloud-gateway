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

package org.springframework.cloud.gateway.support.ipresolver;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * 远程地址解析器接口，用于获取客户端的真实 IP 地址。
 * <p>
 * 在网关部署于代理或负载均衡器后面时，直接从请求中获取的远程地址通常是代理的 IP， 而不是真正的客户端 IP。该接口允许实现自定义的地址解析逻辑， 从 HTTP 头（如
 * X-Forwarded-For）中提取真实客户端地址。
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 * <li>IP 白名单/黑名单访问控制</li>
 * <li>基于地理位置的路由</li>
 * <li>访问日志记录真实客户端 IP</li>
 * <li>限流策略基于真实客户端 IP</li>
 * </ul>
 * </p>
 *
 * @author Andrew Fitzgerald
 * @see XForwardedRemoteAddressResolver
 */
public interface RemoteAddressResolver {

	/**
	 * 从请求交换中解析客户端的远程地址。
	 * <p>
	 * 默认实现直接返回
	 * {@link org.springframework.http.server.reactive.ServerHttpRequest#getRemoteAddress()}，
	 * 即底层网络连接中的远程地址。
	 * </p>
	 * @param exchange 当前请求交换对象
	 * @return 客户端的远程地址，包含 IP 和端口
	 */
	default InetSocketAddress resolve(ServerWebExchange exchange) {
		return exchange.getRequest().getRemoteAddress();
	}

}
