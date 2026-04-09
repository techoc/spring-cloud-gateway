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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/*
 * Location 响应头重写过滤器工厂，用于修改后端服务返回的 Location 响应头。
 *
 * <p>典型使用场景：
 * <ul>
 *   <li>后端服务返回的 Location 头包含内部主机名，需要替换为网关的公共主机名</li>
 *   <li>需要去除响应 Location 中的版本号（如 /v1/, /v2/）</li>
 *   <li>需要统一 Location 头的 host 部分</li>
 * </ul>
 *
 * <p>配置参数：
 * <ul>
 *   <li>stripVersion - 版本号剥离策略
 *       <ul>
 *         <li>NEVER_STRIP - 从不剥离版本号</li>
 *         <li>AS_IN_REQUEST - 仅当请求路径不包含版本号时剥离（默认）</li>
 *         <li>ALWAYS_STRIP - 始终剥离版本号</li>
 *       </ul>
 *   </li>
 *   <li>locationHeaderName - Location 头名称，默认为 "Location"</li>
 *   <li>hostValue - 替换后的主机名，默认为请求的 Host 头值</li>
 *   <li>protocols - 协议匹配正则，默认为 "https?|ftps?"</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       default-filters:
 *         - RewriteLocationResponseHeader
 * </pre>
 *
 * <p>高级配置示例：
 * <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       default-filters:
 *         - name: RewriteLocationResponseHeader
 *           args:
 *             stripVersion: ALWAYS_STRIP
 *             locationHeaderName: Link
 *             hostValue: example-api.com
 *             protocols: https|ftps
 * </pre>
 *
 * @author Vitaliy Pavlyuk
 * @see AbstractGatewayFilterFactory
 */

/**
 * Location 响应头重写过滤器工厂。
 *
 * <p>
 * 该过滤器用于修改 HTTP 响应中的 Location 头，移除后端特定的细节信息， 如内部主机名、端口和版本号，使返回的 URL 更加通用和安全。
 *
 * <p>
 * 注意：该过滤器不会替换 URL 的协议部分。如需替换协议， 可以在此过滤器之后添加通用的 RewriteResponseHeader 过滤器。
 *
 * @author Vitaliy Pavlyuk
 */
public class RewriteLocationResponseHeaderGatewayFilterFactory
		extends AbstractGatewayFilterFactory<RewriteLocationResponseHeaderGatewayFilterFactory.Config> {

	/** 配置参数名称：版本剥离策略 */
	private static final String STRIP_VERSION_KEY = "stripVersion";

	/** 配置参数名称：Location 头名称 */
	private static final String LOCATION_HEADER_NAME_KEY = "locationHeaderName";

	/** 配置参数名称：主机值 */
	private static final String HOST_VALUE_KEY = "hostValue";

	/** 配置参数名称：协议正则 */
	private static final String PROTOCOLS_KEY = "protocols";

	/** 版本化路径的正则表达式匹配模式 */
	private static final Pattern VERSIONED_PATH = Pattern.compile("^/v\\d+/.*");

	/** 默认支持的协议正则 */
	private static final String DEFAULT_PROTOCOLS = "https?|ftps?";

	/** 默认的主机名+端口匹配模式 */
	private static final Pattern DEFAULT_HOST_PORT = compileHostPortPattern(DEFAULT_PROTOCOLS);

	/** 默认的主机名+端口+版本匹配模式 */
	private static final Pattern DEFAULT_HOST_PORT_VERSION = compileHostPortVersionPattern(DEFAULT_PROTOCOLS);

	/**
	 * 构造函数，使用配置类初始化过滤器工厂。
	 */
	public RewriteLocationResponseHeaderGatewayFilterFactory() {
		super(Config.class);
	}

	/**
	 * 编译主机名和端口的正则表达式模式。
	 * @param protocols 协议正则表达式
	 * @return 编译后的正则表达式模式
	 */
	private static Pattern compileHostPortPattern(String protocols) {
		return Pattern.compile("(?<=^(?:" + protocols + ")://)[^:/]+(?::\\d+)?(?=/)");
	}

	/**
	 * 编译主机名、端口和版本号的正则表达式模式。
	 * @param protocols 协议正则表达式
	 * @return 编译后的正则表达式模式
	 */
	private static Pattern compileHostPortVersionPattern(String protocols) {
		return Pattern.compile("(?<=^(?:" + protocols + ")://)[^:/]+(?::\\d+)?(?:/v\\d+)?(?=/)");
	}

	/**
	 * 返回快捷配置的字段顺序。
	 * @return 包含字段名的列表，用于快捷配置解析
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(STRIP_VERSION_KEY, LOCATION_HEADER_NAME_KEY, HOST_VALUE_KEY, PROTOCOLS_KEY);
	}

	/**
	 * 应用此过滤器，创建 Location 响应头重写过滤器。
	 * @param config 过滤器配置
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，在响应发送后重写 Location 头。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				// 先执行过滤器链，然后在响应发送后重写 Location 头
				return chain.filter(exchange).then(Mono.fromRunnable(() -> rewriteLocation(exchange, config)));
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				// @formatter:off
				return filterToStringCreator(
						RewriteLocationResponseHeaderGatewayFilterFactory.this)
						.append("stripVersion", config.stripVersion)
						.append("locationHeaderName", config.locationHeaderName)
						.append("hostValue", config.hostValue)
						.append("protocols", config.protocols)
						.toString();
				// @formatter:on
			}
		};
	}

	/**
	 * 重写 Location 响应头。
	 * @param exchange 当前请求的 ServerWebExchange 对象
	 * @param config 过滤器配置
	 */
	void rewriteLocation(ServerWebExchange exchange, Config config) {
		// 获取 Location 头的值
		final String location = exchange.getResponse().getHeaders().getFirst(config.getLocationHeaderName());
		// 获取要替换的主机名（优先使用配置的 hostValue，否则使用请求的 Host 头）
		final String host = config.getHostValue() != null ? config.getHostValue()
				: exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
		// 获取请求路径，用于判断版本剥离策略
		final String path = exchange.getRequest().getURI().getPath();

		if (location != null && host != null) {
			// 修复 Location 头
			final String fixedLocation = fixedLocation(location, host, path, config.getStripVersion(),
					config.getHostPortPattern(), config.getHostPortVersionPattern());
			exchange.getResponse().getHeaders().set(config.getLocationHeaderName(), fixedLocation);
		}
	}

	/**
	 * 修复 Location URL，替换 host:port 部分。
	 * @param location 原始 Location 值
	 * @param host 替换后的主机名
	 * @param path 请求路径
	 * @param stripVersion 版本剥离策略
	 * @param hostPortPattern host:port 匹配模式
	 * @param hostPortVersionPattern host:port:version 匹配模式
	 * @return 修复后的 Location 值
	 */
	String fixedLocation(String location, String host, String path, StripVersion stripVersion, Pattern hostPortPattern,
			Pattern hostPortVersionPattern) {
		// 根据版本剥离策略决定使用哪个模式
		final boolean doStrip = StripVersion.ALWAYS_STRIP.equals(stripVersion)
				|| (StripVersion.AS_IN_REQUEST.equals(stripVersion) && !VERSIONED_PATH.matcher(path).matches());
		final Pattern pattern = doStrip ? hostPortVersionPattern : hostPortPattern;
		// 执行替换
		return pattern.matcher(location).replaceFirst(host);
	}

	/**
	 * 版本剥离策略枚举。
	 *
	 * <p>
	 * 控制是否从 Location URL 中剥离版本号（如 /v1/, /v2/）。
	 */
	public enum StripVersion {

		/**
		 * 从不剥离版本号。 即使原始请求路径不包含版本号，也不会从 Location 中剥离版本。
		 */
		NEVER_STRIP,

		/**
		 * 根据请求路径决定是否剥离版本号（默认）。 仅当原始请求路径不包含版本号时，才从 Location 中剥离版本号。
		 */
		AS_IN_REQUEST,

		/**
		 * 始终剥离版本号。 即使原始请求路径包含版本号，也会从 Location 中剥离版本号。
		 */
		ALWAYS_STRIP

	}

	/**
	 * Location 响应头重写过滤器配置类。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>stripVersion - 版本剥离策略</li>
	 * <li>locationHeaderName - Location 头名称</li>
	 * <li>hostValue - 替换后的主机名</li>
	 * <li>protocols - 协议匹配正则</li>
	 * </ul>
	 */
	public static class Config {

		/** 版本剥离策略，默认为 AS_IN_REQUEST */
		private StripVersion stripVersion = StripVersion.AS_IN_REQUEST;

		/** Location 头名称，默认为 "Location" */
		private String locationHeaderName = HttpHeaders.LOCATION;

		/** 替换后的主机名，为 null 时使用请求的 Host 头 */
		private String hostValue;

		/** 协议匹配正则，默认为 "https?|ftps?" */
		private String protocols = DEFAULT_PROTOCOLS;

		/** 编译后的 host:port 匹配模式 */
		private Pattern hostPortPattern = DEFAULT_HOST_PORT;

		/** 编译后的 host:port:version 匹配模式 */
		private Pattern hostPortVersionPattern = DEFAULT_HOST_PORT_VERSION;

		// Getters and Setters
		public StripVersion getStripVersion() {
			return stripVersion;
		}

		public Config setStripVersion(StripVersion stripVersion) {
			this.stripVersion = stripVersion;
			return this;
		}

		public String getLocationHeaderName() {
			return locationHeaderName;
		}

		public Config setLocationHeaderName(String locationHeaderName) {
			this.locationHeaderName = locationHeaderName;
			return this;
		}

		public String getHostValue() {
			return hostValue;
		}

		public Config setHostValue(String hostValue) {
			this.hostValue = hostValue;
			return this;
		}

		public String getProtocols() {
			return protocols;
		}

		public Config setProtocols(String protocols) {
			this.protocols = protocols;
			this.hostPortPattern = compileHostPortPattern(protocols);
			this.hostPortVersionPattern = compileHostPortVersionPattern(protocols);
			return this;
		}

		public Pattern getHostPortPattern() {
			return hostPortPattern;
		}

		public Pattern getHostPortVersionPattern() {
			return hostPortVersionPattern;
		}

	}

}
