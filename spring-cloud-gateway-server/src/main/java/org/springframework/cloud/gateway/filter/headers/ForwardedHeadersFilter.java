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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * Forwarded 请求头过滤器。
 *
 * <p>
 * 此过滤器实现了 RFC 7239 中定义的 Forwarded HTTP 头处理逻辑。 Forwarded 头用于在代理链中传递客户端连接信息，替代了非标准的
 * X-Forwarded-* 头。
 * </p>
 *
 * <p>
 * 主要功能：
 * </p>
 * <ul>
 * <li>解析现有的 Forwarded 头信息</li>
 * <li>根据受信任代理配置过滤不安全的头信息</li>
 * <li>添加当前代理节点的信息（host、proto、for 等）</li>
 * </ul>
 *
 * <p>
 * Forwarded 头格式示例：
 * </p>
 * <pre>Forwarded: for=192.168.1.1;host=example.com;proto=https</pre>
 *
 * <p>
 * 安全考虑：此过滤器使用 {@link TrustedProxies} 来验证远程地址和现有的 Forwarded 头信息， 防止恶意客户端伪造代理链信息。
 * </p>
 *
 * @author Spencer Gibb
 * @author Ryan Baxter
 * @see <a href="https://tools.ietf.org/html/rfc7239">RFC 7239 - Forwarded HTTP
 * Extension</a>
 */
public class ForwardedHeadersFilter implements HttpHeadersFilter, Ordered {

	/**
	 * 日志记录器。
	 */
	private static final Log log = LogFactory.getLog(ForwardedHeadersFilter.class);

	/**
	 * Forwarded HTTP 头名称。
	 */
	public static final String FORWARDED_HEADER = "Forwarded";

	/**
	 * 受信任代理验证器。
	 */
	private final TrustedProxies trustedProxies;

	/**
	 * 默认构造函数（已弃用）。
	 *
	 * <p>
	 * 此构造函数将所有主机视为受信任，存在安全风险。 请使用 {@link #ForwardedHeadersFilter(String)} 并指定受信任代理正则表达式。
	 * </p>
	 * @deprecated 请使用带受信任代理参数的构造函数
	 */
	@Deprecated
	public ForwardedHeadersFilter() {
		trustedProxies = s -> true;
		log.warn(GatewayProperties.PREFIX
				+ ".trusted-proxies is not set. Using deprecated Constructor. Untrusted hosts might be added to Forwarded header.");
	}

	/**
	 * 构造函数，使用受信任代理正则表达式创建过滤器。
	 * @param trustedProxiesRegex 匹配受信任代理地址的正则表达式
	 */
	public ForwardedHeadersFilter(String trustedProxiesRegex) {
		trustedProxies = TrustedProxies.from(trustedProxiesRegex);
	}

	/**
	 * 解析 Forwarded 头值列表。
	 *
	 * <p>
	 * Forwarded 头可能包含多个值，用逗号分隔。每个值可能包含多个键值对，用分号分隔。
	 * </p>
	 * @param values Forwarded 头的原始值列表
	 * @return 解析后的 Forwarded 对象列表
	 */
	/* for testing */
	static List<Forwarded> parse(List<String> values) {
		ArrayList<Forwarded> forwardeds = new ArrayList<>();
		if (CollectionUtils.isEmpty(values)) {
			return forwardeds;
		}
		for (String value : values) {
			String[] forwardedValues = StringUtils.tokenizeToStringArray(value, ",");
			for (String forwardedValue : forwardedValues) {
				Forwarded forwarded = parse(forwardedValue);
				forwardeds.add(forwarded);
			}
		}
		return forwardeds;
	}

	/**
	 * 解析单个 Forwarded 头值。
	 *
	 * <p>
	 * 将格式为 "key=value;key2=value2" 的字符串解析为 Forwarded 对象。
	 * </p>
	 * @param value Forwarded 头的单个值
	 * @return 解析后的 Forwarded 对象，如果解析失败返回 null
	 */
	/* for testing */
	static Forwarded parse(String value) {
		String[] pairs = StringUtils.tokenizeToStringArray(value, ";");

		LinkedCaseInsensitiveMap<String> result = splitIntoCaseInsensitiveMap(pairs);
		if (result == null) {
			return null;
		}

		Forwarded forwarded = new Forwarded(result);

		return forwarded;
	}

	/**
	 * 将键值对数组分割为大小写不敏感的映射。
	 * @param pairs 键值对数组，格式为 "key=value"
	 * @return 大小写不敏感的映射，如果输入为空返回 null
	 */
	/* for testing */
	static LinkedCaseInsensitiveMap<String> splitIntoCaseInsensitiveMap(String[] pairs) {
		if (ObjectUtils.isEmpty(pairs)) {
			return null;
		}

		LinkedCaseInsensitiveMap<String> result = new LinkedCaseInsensitiveMap<>();
		for (String element : pairs) {
			String[] splittedElement = StringUtils.split(element, "=");
			if (splittedElement == null) {
				continue;
			}
			result.put(splittedElement[0].trim(), splittedElement[1].trim());
		}
		return result;
	}

	/**
	 * 获取过滤器的执行顺序。
	 * @return 顺序值，数值越小优先级越高
	 */
	@Override
	public int getOrder() {
		return 0;
	}

	/**
	 * 过滤 HTTP 头，处理 Forwarded 头信息。
	 *
	 * <p>
	 * 处理逻辑：
	 * </p>
	 * <ol>
	 * <li>验证远程地址是否受信任</li>
	 * <li>复制所有非 Forwarded 头</li>
	 * <li>解析并过滤现有的 Forwarded 头（只保留受信任的）</li>
	 * <li>添加当前代理节点的 Forwarded 信息</li>
	 * </ol>
	 * @param input 原始 HTTP 头
	 * @param exchange 服务器 Web 交换对象
	 * @return 过滤后的 HTTP 头
	 */
	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		ServerHttpRequest request = exchange.getRequest();

		if (request.getRemoteAddress() != null
				&& !trustedProxies.isTrusted(request.getRemoteAddress().getHostString())) {
			log.trace(LogMessage.format("Remote address not trusted. pattern %s remote address %s", trustedProxies,
					request.getRemoteAddress()));
			return input;
		}

		HttpHeaders original = input;
		HttpHeaders updated = new HttpHeaders();

		// 复制所有头，除了 Forwarded 头
		for (Map.Entry<String, List<String>> entry : original.entrySet()) {
			if (!entry.getKey().equalsIgnoreCase(FORWARDED_HEADER)) {
				updated.addAll(entry.getKey(), entry.getValue());
			}
		}

		List<Forwarded> forwardeds = parse(original.get(FORWARDED_HEADER));

		for (Forwarded f : forwardeds) {
			// 只添加 "for" 值匹配受信任代理的 Forwarded 头
			if (trustedProxies.isTrusted(f.get("for"))) {
				updated.add(FORWARDED_HEADER, f.toHeaderValue());
			}
		}

		// 添加新的 Forwarded 信息
		URI uri = request.getURI();
		String host = original.getFirst(HttpHeaders.HOST);
		Forwarded forwarded = new Forwarded().put("host", host).put("proto", uri.getScheme());

		InetSocketAddress remoteAddress = request.getRemoteAddress();
		if (remoteAddress != null) {
			// 如果 remoteAddress 未解析，调用 getHostAddress() 会导致 NullPointerException
			String forValue;
			if (remoteAddress.isUnresolved()) {
				forValue = remoteAddress.getHostName();
			}
			else {
				InetAddress address = remoteAddress.getAddress();
				forValue = remoteAddress.getAddress().getHostAddress();
				if (address instanceof Inet6Address) {
					forValue = "[" + forValue + "]";
				}
			}
			if (trustedProxies.isTrusted(forValue)) {
				// 只有受信任才添加 for 值
				int port = remoteAddress.getPort();
				if (port >= 0) {
					forValue = forValue + ":" + port;
				}
				forwarded.put("for", forValue);
			}
		}

		updated.add(FORWARDED_HEADER, forwarded.toHeaderValue());

		return updated;
	}

	/**
	 * Forwarded 头值对象。
	 *
	 * <p>
	 * 封装 Forwarded 头的键值对数据，提供便捷的构建和序列化方法。
	 * </p>
	 */
	/* for testing */
	static class Forwarded {

		/**
		 * 等号字符，用于键值对分隔。
		 */
		private static final char EQUALS = '=';

		/**
		 * 分号字符，用于键值对之间的分隔。
		 */
		private static final char SEMICOLON = ';';

		/**
		 * 存储 Forwarded 头的键值对。
		 */
		private final Map<String, String> values;

		/**
		 * 默认构造函数，创建空的 Forwarded 对象。
		 */
		Forwarded() {
			this.values = new HashMap<>();
		}

		/**
		 * 使用现有映射创建 Forwarded 对象。
		 * @param values 键值对映射
		 */
		Forwarded(Map<String, String> values) {
			this.values = values;
		}

		/**
		 * 添加键值对。
		 *
		 * <p>
		 * 如果值包含冒号，会自动添加引号。
		 * </p>
		 * @param key 键
		 * @param value 值
		 * @return 当前 Forwarded 对象，支持链式调用
		 */
		public Forwarded put(String key, String value) {
			this.values.put(key, quoteIfNeeded(value));
			return this;
		}

		/**
		 * 如果需要，为值添加引号。
		 *
		 * <p>
		 * 当前实现只在值包含冒号时添加引号。
		 * </p>
		 * @param s 原始值
		 * @return 可能需要引号的值
		 */
		private String quoteIfNeeded(String s) {
			if (s != null && s.contains(":")) { // TODO: 扩大引号条件
				return "\"" + s + "\"";
			}
			return s;
		}

		/**
		 * 获取指定键的值。
		 * @param key 键
		 * @return 值，如果不存在返回 null
		 */
		public String get(String key) {
			return this.values.get(key);
		}

		/**
		 * 获取所有键值对。
		 * @return 键值对映射
		 */
		/* for testing */
		Map<String, String> getValues() {
			return this.values;
		}

		/**
		 * 返回对象的字符串表示。
		 * @return 字符串表示
		 */
		@Override
		public String toString() {
			return "Forwarded{" + "values=" + this.values + '}';
		}

		/**
		 * 将 Forwarded 对象序列化为 HTTP 头值格式。
		 *
		 * <p>
		 * 格式：key1=value1;key2=value2
		 * </p>
		 * @return 序列化后的头值
		 */
		public String toHeaderValue() {
			StringBuilder builder = new StringBuilder();
			for (Map.Entry<String, String> entry : this.values.entrySet()) {
				if (builder.length() > 0) {
					builder.append(SEMICOLON);
				}
				builder.append(entry.getKey()).append(EQUALS).append(entry.getValue());
			}
			return builder.toString();
		}

	}

}
