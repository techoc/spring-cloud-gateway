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

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

/**
 * X-Forwarded-* 请求头过滤器。
 *
 * <p>
 * 此过滤器处理非标准的 X-Forwarded 系列 HTTP 头，包括：
 * </p>
 * <ul>
 * <li>X-Forwarded-For - 客户端真实 IP 地址</li>
 * <li>X-Forwarded-Host - 原始主机名</li>
 * <li>X-Forwarded-Port - 原始端口号</li>
 * <li>X-Forwarded-Proto - 原始协议（http/https）</li>
 * <li>X-Forwarded-Prefix - 原始路径前缀</li>
 * </ul>
 *
 * <p>
 * 这些头信息用于在代理链中传递原始请求信息，使下游服务能够获取客户端的真实连接信息。
 * </p>
 *
 * <p>
 * 配置属性前缀：{@code spring.cloud.gateway.x-forwarded}
 * </p>
 *
 * <p>
 * 安全考虑：此过滤器使用 {@link TrustedProxies} 验证远程地址，防止伪造的 X-Forwarded 信息。
 * </p>
 *
 * @author Spencer Gibb
 * @author Ryan Baxter
 * @see <a href=
 * "https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For">X-Forwarded-For
 * MDN</a>
 */
@ConfigurationProperties("spring.cloud.gateway.x-forwarded")
public class XForwardedHeadersFilter implements HttpHeadersFilter, Ordered {

	/**
	 * 日志记录器。
	 */
	private static final Log log = LogFactory.getLog(XForwardedHeadersFilter.class);

	/**
	 * HTTP 默认端口。
	 */
	public static final int HTTP_PORT = 80;

	/**
	 * HTTPS 默认端口。
	 */
	public static final int HTTPS_PORT = 443;

	/**
	 * HTTP 协议方案。
	 */
	public static final String HTTP_SCHEME = "http";

	/**
	 * HTTPS 协议方案。
	 */
	public static final String HTTPS_SCHEME = "https";

	/**
	 * X-Forwarded-For 头名称。
	 */
	public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

	/**
	 * X-Forwarded-Host 头名称。
	 */
	public static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";

	/**
	 * X-Forwarded-Port 头名称。
	 */
	public static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";

	/**
	 * X-Forwarded-Proto 头名称。
	 */
	public static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";

	/**
	 * X-Forwarded-Prefix 头名称。
	 */
	public static final String X_FORWARDED_PREFIX_HEADER = "X-Forwarded-Prefix";

	/**
	 * 过滤器执行顺序。
	 */
	private int order = 0;

	/**
	 * 是否启用此过滤器。
	 */
	private boolean enabled = true;

	/**
	 * 是否启用 X-Forwarded-For。
	 */
	private boolean forEnabled = true;

	/**
	 * 是否启用 X-Forwarded-Host。
	 */
	private boolean hostEnabled = true;

	/**
	 * 是否启用 X-Forwarded-Port。
	 */
	private boolean portEnabled = true;

	/**
	 * 是否启用 X-Forwarded-Proto。
	 */
	private boolean protoEnabled = true;

	/**
	 * 是否启用 X-Forwarded-Prefix。
	 */
	private boolean prefixEnabled = true;

	/**
	 * X-Forwarded-For 是否使用追加模式。
	 */
	private boolean forAppend = true;

	/**
	 * X-Forwarded-Host 是否使用追加模式。
	 */
	private boolean hostAppend = true;

	/**
	 * X-Forwarded-Port 是否使用追加模式。
	 */
	private boolean portAppend = true;

	/**
	 * X-Forwarded-Proto 是否使用追加模式。
	 */
	private boolean protoAppend = true;

	/**
	 * X-Forwarded-Prefix 是否使用追加模式。
	 */
	private boolean prefixAppend = true;

	/**
	 * 受信任代理验证器。
	 */
	private final TrustedProxies trustedProxies;

	/**
	 * 默认构造函数（已弃用）。
	 * @deprecated 请使用带受信任代理参数的构造函数
	 */
	@Deprecated
	public XForwardedHeadersFilter() {
		trustedProxies = s -> true;
		log.warn(GatewayProperties.PREFIX
				+ ".trusted-proxies is not set. Using deprecated Constructor. Untrusted hosts might be added to Forwarded header.");
	}

	/**
	 * 构造函数，使用受信任代理正则表达式。
	 * @param trustedProxiesRegex 匹配受信任代理地址的正则表达式
	 */
	public XForwardedHeadersFilter(String trustedProxiesRegex) {
		trustedProxies = TrustedProxies.from(trustedProxiesRegex);
	}

	/**
	 * 获取过滤器执行顺序。
	 * @return 顺序值
	 */
	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * 设置过滤器执行顺序。
	 * @param order 顺序值
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * 检查过滤器是否启用。
	 * @return 如果启用返回 true
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * 设置过滤器是否启用。
	 * @param enabled 是否启用
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * 检查 X-Forwarded-For 是否启用。
	 * @return 如果启用返回 true
	 */
	public boolean isForEnabled() {
		return forEnabled;
	}

	/**
	 * 设置 X-Forwarded-For 是否启用。
	 * @param forEnabled 是否启用
	 */
	public void setForEnabled(boolean forEnabled) {
		this.forEnabled = forEnabled;
	}

	/**
	 * 检查 X-Forwarded-Host 是否启用。
	 * @return 如果启用返回 true
	 */
	public boolean isHostEnabled() {
		return hostEnabled;
	}

	/**
	 * 设置 X-Forwarded-Host 是否启用。
	 * @param hostEnabled 是否启用
	 */
	public void setHostEnabled(boolean hostEnabled) {
		this.hostEnabled = hostEnabled;
	}

	/**
	 * 检查 X-Forwarded-Port 是否启用。
	 * @return 如果启用返回 true
	 */
	public boolean isPortEnabled() {
		return portEnabled;
	}

	/**
	 * 设置 X-Forwarded-Port 是否启用。
	 * @param portEnabled 是否启用
	 */
	public void setPortEnabled(boolean portEnabled) {
		this.portEnabled = portEnabled;
	}

	/**
	 * 检查 X-Forwarded-Proto 是否启用。
	 * @return 如果启用返回 true
	 */
	public boolean isProtoEnabled() {
		return protoEnabled;
	}

	/**
	 * 设置 X-Forwarded-Proto 是否启用。
	 * @param protoEnabled 是否启用
	 */
	public void setProtoEnabled(boolean protoEnabled) {
		this.protoEnabled = protoEnabled;
	}

	/**
	 * 检查 X-Forwarded-Prefix 是否启用。
	 * @return 如果启用返回 true
	 */
	public boolean isPrefixEnabled() {
		return prefixEnabled;
	}

	/**
	 * 设置 X-Forwarded-Prefix 是否启用。
	 * @param prefixEnabled 是否启用
	 */
	public void setPrefixEnabled(boolean prefixEnabled) {
		this.prefixEnabled = prefixEnabled;
	}

	/**
	 * 检查 X-Forwarded-For 是否使用追加模式。
	 * @return 如果是追加模式返回 true
	 */
	public boolean isForAppend() {
		return forAppend;
	}

	/**
	 * 设置 X-Forwarded-For 是否使用追加模式。
	 * @param forAppend 是否追加
	 */
	public void setForAppend(boolean forAppend) {
		this.forAppend = forAppend;
	}

	/**
	 * 检查 X-Forwarded-Host 是否使用追加模式。
	 * @return 如果是追加模式返回 true
	 */
	public boolean isHostAppend() {
		return hostAppend;
	}

	/**
	 * 设置 X-Forwarded-Host 是否使用追加模式。
	 * @param hostAppend 是否追加
	 */
	public void setHostAppend(boolean hostAppend) {
		this.hostAppend = hostAppend;
	}

	/**
	 * 检查 X-Forwarded-Port 是否使用追加模式。
	 * @return 如果是追加模式返回 true
	 */
	public boolean isPortAppend() {
		return portAppend;
	}

	/**
	 * 设置 X-Forwarded-Port 是否使用追加模式。
	 * @param portAppend 是否追加
	 */
	public void setPortAppend(boolean portAppend) {
		this.portAppend = portAppend;
	}

	/**
	 * 检查 X-Forwarded-Proto 是否使用追加模式。
	 * @return 如果是追加模式返回 true
	 */
	public boolean isProtoAppend() {
		return protoAppend;
	}

	/**
	 * 设置 X-Forwarded-Proto 是否使用追加模式。
	 * @param protoAppend 是否追加
	 */
	public void setProtoAppend(boolean protoAppend) {
		this.protoAppend = protoAppend;
	}

	/**
	 * 检查 X-Forwarded-Prefix 是否使用追加模式。
	 * @return 如果是追加模式返回 true
	 */
	public boolean isPrefixAppend() {
		return prefixAppend;
	}

	/**
	 * 设置 X-Forwarded-Prefix 是否使用追加模式。
	 * @param prefixAppend 是否追加
	 */
	public void setPrefixAppend(boolean prefixAppend) {
		this.prefixAppend = prefixAppend;
	}

	/**
	 * 过滤 HTTP 头，添加 X-Forwarded-* 信息。
	 *
	 * <p>
	 * 根据配置添加相应的 X-Forwarded 头信息，支持追加和覆盖两种模式。
	 * </p>
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

		for (Map.Entry<String, List<String>> entry : original.entrySet()) {
			updated.addAll(entry.getKey(), entry.getValue());
		}

		if (isForEnabled()) {
			String remoteAddr = null;
			if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
				remoteAddr = request.getRemoteAddress().getHostString();
			}
			// 将 remoteAddr 与受信任代理进行匹配
			write(updated, X_FORWARDED_FOR_HEADER, remoteAddr, isForAppend(), trustedProxies::isTrusted);
		}

		String proto = request.getURI().getScheme();
		if (isProtoEnabled()) {
			write(updated, X_FORWARDED_PROTO_HEADER, proto, isProtoAppend());
		}

		if (isPrefixEnabled()) {
			// 如果网关路由到的 URL 路径是原始 URL 路径的子集（结尾部分），
			// 则差值就是前缀。例如，如果请求 original.com/prefix/get/ 被路由到
			// routedservice:8090/get，则 /prefix 就是前缀。
			// 首先获取 URI，然后提取路径，如果一个是另一个的结尾部分则移除。

			LinkedHashSet<URI> originalUris = exchange.getAttribute(GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
			URI requestUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);

			if (originalUris != null && requestUri != null) {

				originalUris.forEach(originalUri -> {

					if (originalUri != null && originalUri.getPath() != null) {
						String prefix = originalUri.getPath();

						// 在检查请求路径是否是原始路径的结尾部分之前，去除尾部斜杠
						String originalUriPath = stripTrailingSlash(originalUri);
						String requestUriPath = stripTrailingSlash(requestUri);

						updateRequest(updated, originalUri, originalUriPath, requestUriPath);

					}
				});
			}
		}

		if (isPortEnabled()) {
			String port = String.valueOf(request.getURI().getPort());
			if (request.getURI().getPort() < 0) {
				port = String.valueOf(getDefaultPort(proto));
			}
			write(updated, X_FORWARDED_PORT_HEADER, port, isPortAppend());
		}

		if (isHostEnabled()) {
			String host = toHostHeader(request);
			write(updated, X_FORWARDED_HOST_HEADER, host, isHostAppend());
		}

		return updated;
	}

	/**
	 * 更新请求头，添加 X-Forwarded-Prefix。
	 * @param updated 更新后的头
	 * @param originalUri 原始 URI
	 * @param originalUriPath 原始 URI 路径
	 * @param requestUriPath 请求 URI 路径
	 */
	private void updateRequest(HttpHeaders updated, URI originalUri, String originalUriPath, String requestUriPath) {
		String prefix;
		if (requestUriPath != null && (originalUriPath.endsWith(requestUriPath))) {
			prefix = substringBeforeLast(originalUriPath, requestUriPath);
			if (prefix != null && prefix.length() > 0 && prefix.length() <= originalUri.getPath().length()) {
				write(updated, X_FORWARDED_PREFIX_HEADER, prefix, isPrefixAppend());
			}
		}
	}

	/**
	 * 获取字符串中最后一个分隔符之前的子串。
	 * @param str 原始字符串
	 * @param separator 分隔符
	 * @return 分隔符之前的子串，如果未找到分隔符返回原字符串
	 */
	private static String substringBeforeLast(String str, String separator) {
		if (ObjectUtils.isEmpty(str) || ObjectUtils.isEmpty(separator)) {
			return str;
		}
		int pos = str.lastIndexOf(separator);
		if (pos == -1) {
			return str;
		}
		return str.substring(0, pos);
	}

	/**
	 * 写入头信息。
	 * @param headers HTTP 头
	 * @param name 头名称
	 * @param value 头值
	 * @param append 是否追加模式
	 */
	private void write(HttpHeaders headers, String name, String value, boolean append) {
		write(headers, name, value, append, s -> true);
	}

	/**
	 * 写入头信息，带条件判断。
	 *
	 * <p>
	 * 追加模式下，会将所有值用逗号连接成一个头值。
	 * </p>
	 * @param headers HTTP 头
	 * @param name 头名称
	 * @param value 头值
	 * @param append 是否追加模式
	 * @param shouldWrite 是否写入的条件判断
	 */
	private void write(HttpHeaders headers, String name, String value, boolean append, Predicate<String> shouldWrite) {
		if (append) {
			if (value != null) {
				headers.add(name, value);
			}
			// 这些头应该被视为单个逗号分隔的头
			if (headers.containsKey(name)) {
				List<String> values = headers.get(name).stream().filter(shouldWrite).collect(Collectors.toList());
				String delimitedValue = StringUtils.collectionToCommaDelimitedString(values);
				headers.set(name, delimitedValue);
			}
		}
		else if (value != null && shouldWrite.test(value)) {
			headers.set(name, value);
		}
	}

	/**
	 * 获取协议的默认端口。
	 * @param scheme 协议方案
	 * @return 默认端口号
	 */
	private int getDefaultPort(String scheme) {
		return HTTPS_SCHEME.equals(scheme) ? HTTPS_PORT : HTTP_PORT;
	}

	/**
	 * 将请求转换为 Host 头格式。
	 *
	 * <p>
	 * 如果端口是默认端口（HTTP 80 或 HTTPS 443），则不包含端口号。
	 * </p>
	 * @param request HTTP 请求
	 * @return Host 头值
	 */
	private String toHostHeader(ServerHttpRequest request) {
		int port = request.getURI().getPort();
		String host = request.getURI().getHost();
		String scheme = request.getURI().getScheme();
		if (port < 0 || (port == HTTP_PORT && HTTP_SCHEME.equals(scheme))
				|| (port == HTTPS_PORT && HTTPS_SCHEME.equals(scheme))) {
			return host;
		}
		else {
			return host + ":" + port;
		}
	}

	/**
	 * 去除 URI 路径的尾部斜杠。
	 * @param uri URI
	 * @return 去除尾部斜杠的路径
	 */
	private String stripTrailingSlash(URI uri) {
		if (uri.getPath().endsWith("/")) {
			return uri.getPath().substring(0, uri.getPath().length() - 1);
		}
		else {
			return uri.getPath();
		}
	}

}
