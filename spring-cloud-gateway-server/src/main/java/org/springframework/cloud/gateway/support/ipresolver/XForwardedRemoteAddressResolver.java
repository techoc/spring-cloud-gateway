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

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * X-Forwarded-For 头解析器，用于从 HTTP 请求头中提取真实客户端 IP 地址。
 * <p>
 * 当网关部署在负载均衡器或反向代理后面时，客户端的真实 IP 通常被附加在 X-Forwarded-For 头中。该头包含一个 IP 列表，按请求经过的顺序排列：
 * </p>
 * <pre>
 * X-Forwarded-For: client, proxy1, proxy2
 * </pre>
 * <p>
 * 列表中最左边的 IP 是最原始的客户端 IP。
 * </p>
 * <p>
 * 该解析器提供两种安全级别：
 * <ul>
 * <li>{@link #trustAll()} - 信任所有 X-Forwarded-For 值，存在被欺骗风险</li>
 * <li>{@link #maxTrustedIndex(int)} - 只信任固定数量的代理后面的客户端 IP</li>
 * </ul>
 * </p>
 *
 * @author Andrew Fitzgerald
 * @see <a href=
 * "https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For">X-Forwarded-For
 * reference</a>
 * @see RemoteAddressResolver
 */
public class XForwardedRemoteAddressResolver implements RemoteAddressResolver {

	/**
	 * X-Forwarded-For HTTP 头名称。
	 */
	public static final String X_FORWARDED_FOR = "X-Forwarded-For";

	private static final Logger log = LoggerFactory.getLogger(XForwardedRemoteAddressResolver.class);

	/** 默认解析器，当 X-Forwarded-For 头不存在时使用 */
	private final RemoteAddressResolver defaultRemoteIpResolver = new RemoteAddressResolver() {
	};

	/**
	 * 最大可信索引，表示从右向左数可信的 IP 数量。
	 * <p>
	 * 例如，值为 1 表示只信任最右边的 IP（即最后一个代理的 IP）， 然后获取其左边的 IP 作为客户端 IP。
	 */
	private final int maxTrustedIndex;

	/**
	 * 私有构造函数。
	 * @param maxTrustedIndex 最大可信索引
	 */
	private XForwardedRemoteAddressResolver(int maxTrustedIndex) {
		this.maxTrustedIndex = maxTrustedIndex;
	}

	/**
	 * 创建信任所有的解析器。
	 * <p>
	 * 安全警告：此配置存在安全风险，恶意用户可能通过伪造 X-Forwarded-For 头 来伪装自己的 IP 地址。仅在完全信任所有请求来源的环境中使用。
	 * </p>
	 * <p>
	 * 等同于调用 {@link #maxTrustedIndex(int)} 并传入 {@link Integer#MAX_VALUE}。
	 * </p>
	 * @return 信任所有的 X-Forwarded-For 解析器
	 */
	public static XForwardedRemoteAddressResolver trustAll() {
		return new XForwardedRemoteAddressResolver(Integer.MAX_VALUE);
	}

	/**
	 * 创建指定可信索引的解析器。
	 * <p>
	 * 此配置存在是为了防止恶意用户伪造 X-Forwarded-For 头。 如果你确定网关应用只能通过可信的负载均衡器访问， 那么可以信任负载均衡器会正确附加客户端
	 * IP， 此时应使用 {@code maxTrustedIndex = 1}。
	 * </p>
	 * <p>
	 * 假设 X-Forwarded-For 值为 [0.0.0.1, 0.0.0.2, 0.0.0.3]： <pre>
	 * maxTrustedIndex -> 返回的 IP
	 *
	 * [MIN_VALUE, 0] -> IllegalArgumentException
	 * 1 -> 0.0.0.3  (最右边，信任1个代理)
	 * 2 -> 0.0.0.2  (从右数第二个，信任2个代理)
	 * 3 -> 0.0.0.1  (最左边，信任3个代理)
	 * [4, MAX_VALUE] -> 0.0.0.1  (索引超出范围，返回最左边)
	 * </pre>
	 * </p>
	 * @param maxTrustedIndex 可信代理数量（从 1 开始计数）
	 * @return 配置好的 X-Forwarded-For 解析器
	 * @throws IllegalArgumentException 当索引小于等于 0 时抛出
	 */
	public static XForwardedRemoteAddressResolver maxTrustedIndex(int maxTrustedIndex) {
		Assert.isTrue(maxTrustedIndex > 0, "An index greater than 0 is required");
		return new XForwardedRemoteAddressResolver(maxTrustedIndex);
	}

	/**
	 * 解析请求中的远程地址。
	 * <p>
	 * 如果 X-Forwarded-For 头存在，从其中提取可信的客户端 IP； 否则回退到默认的远程地址解析逻辑。
	 * </p>
	 * <p>
	 * IP 选择逻辑：
	 * <ol>
	 * <li>反转 IP 列表顺序（从 [client, proxy1, proxy2] 变为 [proxy2, proxy1, client]）</li>
	 * <li>根据 maxTrustedIndex 选择对应位置的 IP</li>
	 * <li>如果列表为空或不完整，使用默认值</li>
	 * </ol>
	 * </p>
	 * @param exchange 当前请求交换对象
	 * @return 解析出的远程地址，端口固定为 0
	 */
	@Override
	public InetSocketAddress resolve(ServerWebExchange exchange) {
		List<String> xForwardedValues = extractXForwardedValues(exchange);
		Collections.reverse(xForwardedValues);
		if (!xForwardedValues.isEmpty()) {
			// 计算实际索引，取列表大小和 maxTrustedIndex 的较小值再减 1
			int index = Math.min(xForwardedValues.size(), maxTrustedIndex) - 1;
			return new InetSocketAddress(xForwardedValues.get(index), 0);
		}
		return defaultRemoteIpResolver.resolve(exchange);
	}

	/**
	 * 提取 X-Forwarded-For 头中的值列表。
	 * <p>
	 * 处理逻辑：
	 * <ul>
	 * <li>如果头不存在或为空，返回空列表</li>
	 * <li>如果存在多个 X-Forwarded-For 头，为安全起见返回空列表并记录警告</li>
	 * <li>逗号分隔的值被解析为独立的 IP 地址</li>
	 * </ul>
	 * </p>
	 * @param exchange 当前请求交换对象
	 * @return IP 地址列表
	 */
	private List<String> extractXForwardedValues(ServerWebExchange exchange) {
		List<String> xForwardedValues = exchange.getRequest().getHeaders().get(X_FORWARDED_FOR);
		if (xForwardedValues == null || xForwardedValues.isEmpty()) {
			return Collections.emptyList();
		}
		if (xForwardedValues.size() > 1) {
			log.warn("Multiple X-Forwarded-For headers found, discarding all");
			return Collections.emptyList();
		}
		String[] values = StringUtils.tokenizeToStringArray(xForwardedValues.get(0), ",");
		if (values.length == 1 && !StringUtils.hasText(values[0])) {
			return Collections.emptyList();
		}
		return Arrays.asList(values);
	}

}
