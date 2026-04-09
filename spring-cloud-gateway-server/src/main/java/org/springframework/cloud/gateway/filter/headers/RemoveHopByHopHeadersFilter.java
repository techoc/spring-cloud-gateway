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

package org.springframework.cloud.gateway.filter.headers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Hop-by-Hop 请求头移除过滤器。
 *
 * <p>
 * 此过滤器根据 RFC 2616 规范移除 Hop-by-Hop 头信息。这些头信息是单跳有效的， 不应该被转发到下游服务或返回给客户端。
 * </p>
 *
 * <p>
 * 默认移除的头包括：
 * </p>
 * <ul>
 * <li>Connection - 连接管理</li>
 * <li>Keep-Alive - 持久连接参数</li>
 * <li>Transfer-Encoding - 传输编码</li>
 * <li>TE - 传输编码能力</li>
 * <li>Trailer - 尾部头字段</li>
 * <li>Proxy-Authorization - 代理认证</li>
 * <li>Proxy-Authenticate - 代理认证挑战</li>
 * <li>X-Application-Context - 应用上下文</li>
 * <li>Upgrade - 协议升级</li>
 * </ul>
 *
 * <p>
 * 配置属性前缀：{@code spring.cloud.gateway.filter.remove-hop-by-hop}
 * </p>
 *
 * @author Spencer Gibb
 * @author Ryan Baxter
 * @see <a href="https://tools.ietf.org/html/rfc2616#section-13.5.1">RFC 2616 - Hop-by-Hop
 * Headers</a>
 */
@ConfigurationProperties("spring.cloud.gateway.filter.remove-hop-by-hop")
public class RemoveHopByHopHeadersFilter implements HttpHeadersFilter, Ordered {

	/**
	 * 请求中默认需要移除的 Hop-by-Hop 头集合。
	 *
	 * <p>
	 * 根据 RFC 2616 和实际应用场景定义。
	 * </p>
	 */
	public static final Set<String> HEADERS_REMOVED_ON_REQUEST = new HashSet<>(
			Arrays.asList("connection", "keep-alive", "transfer-encoding", "te", "trailer", "proxy-authorization",
					"proxy-authenticate", "x-application-context", "upgrade"
			// 以下两个未在 RFC 中列出
			// "proxy-connection",
			// "content-length",
			));

	/**
	 * 过滤器执行顺序。
	 */
	private int order = Ordered.LOWEST_PRECEDENCE - 1;

	/**
	 * 需要移除的头集合，默认为 {@link #HEADERS_REMOVED_ON_REQUEST}。
	 */
	private Set<String> headers = HEADERS_REMOVED_ON_REQUEST;

	/**
	 * 获取需要移除的头集合。
	 * @return 头名称集合
	 */
	public Set<String> getHeaders() {
		return headers;
	}

	/**
	 * 设置需要移除的头集合。
	 * @param headers 头名称集合
	 */
	public void setHeaders(Set<String> headers) {
		this.headers = headers;
	}

	/**
	 * 获取过滤器执行顺序。
	 * @return 顺序值
	 */
	@Override
	public int getOrder() {
		return order;
	}

	/**
	 * 设置过滤器执行顺序。
	 * @param order 顺序值
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * 过滤 HTTP 头，移除 Hop-by-Hop 头。
	 *
	 * <p>
	 * 遍历所有头，只保留不在移除列表中的头。头名称比较不区分大小写。
	 * </p>
	 * @param input 原始 HTTP 头
	 * @param exchange 服务器 Web 交换对象
	 * @return 过滤后的 HTTP 头
	 */
	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		HttpHeaders filtered = new HttpHeaders();

		for (Map.Entry<String, List<String>> entry : input.entrySet()) {
			if (!this.headers.contains(entry.getKey().toLowerCase())) {
				filtered.addAll(entry.getKey(), entry.getValue());
			}
		}

		return filtered;
	}

	/**
	 * 检查此过滤器是否支持指定的过滤器类型。
	 *
	 * <p>
	 * 此过滤器同时支持请求和响应两种类型。
	 * </p>
	 * @param type 过滤器类型
	 * @return 始终返回 true
	 */
	@Override
	public boolean supports(Type type) {
		return type.equals(Type.REQUEST) || type.equals(Type.RESPONSE);
	}

}
