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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/*

该路由断言允许基于 "X-Forwarded-For" HTTP 头来过滤请求。

此断言可与负载均衡器或 Web 应用防火墙等反向代理配合使用，
请求只有在来自这些反向代理使用的可信 IP 地址列表时才会被允许。

该实现提供了一个独立的 "XForwardedRemoteAddr" 路由断言，
可以配置允许的 IP 地址列表，默认 "maxTrustedIndex" 设置为 1。
这个值表示我们信任 "X-Forwarded-For" 头中最右边（最后一个）的值，
它代表调用网关时使用的最后一个反向代理。
然后检查该 IP 地址是否在允许的 IP 地址列表中，以确定是否允许请求。

参见 https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#modifying-the-way-remote-addresses-are-resolved

注意：此断言实现本身不包含核心逻辑，它将
"RemoteAddrRoutePredicateFactory" 和 "XForwardedRemoteAddressResolver" 类聚合到一个断言中，
可以直接从应用配置启用，无需在自定义代码中指定任何内容。

application.yml 中的使用示例，信任两个反向代理（一个使用 IPv6 范围）：

  ...
  - predicates:
    - XForwardedRemoteAddr="20.103.252.85", "2a01:111:2050::/44"

*/

/**
 * X-Forwarded-For 远程地址断言工厂 - 基于代理转发的真实客户端 IP 进行路由匹配。
 *
 * <p>
 * 该断言工厂用于识别通过反向代理（如负载均衡器、CDN）转发的请求的真实客户端 IP。 它从 X-Forwarded-For 头中获取 IP 地址，并与配置的允许列表进行匹配。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>从 X-Forwarded-For 头中获取真实客户端 IP</li>
 * <li>支持配置信任的代理层级（maxTrustedIndex）</li>
 * <li>支持单个 IP 地址和 CIDR 网段</li>
 * <li>内部委托给 RemoteAddrRoutePredicateFactory</li>
 * </ul>
 *
 * <p>
 * <b>X-Forwarded-For 头说明：</b>
 * </p>
 * <ul>
 * <li>格式：client, proxy1, proxy2, ...</li>
 * <li>maxTrustedIndex=1：信任最后一个 IP（最后一个代理）</li>
 * <li>maxTrustedIndex=2：信任最后两个 IP（倒数第二个为客户端）</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 默认信任级别（1）
 * - id: xff_route
 *   uri: https://example.org
 *   predicates:
 *   - XForwardedRemoteAddr=192.168.1.0/24
 *
 * # YAML 配置方式 - 多个 IP/CIDR
 * - id: xff_trusted
 *   uri: https://trusted.example.org
 *   predicates:
 *   - XForwardedRemoteAddr=20.103.252.85, 2a01:111:2050::/44
 *
 * # YAML 配置方式 - 自定义信任级别
 * # 适用于有多层代理的场景
 * - id: xff_multi_proxy
 *   uri: https://multi.example.org
 *   predicates:
 *   - XForwardedRemoteAddr=10.0.0.0/8, maxTrustedIndex=3
 * }</pre>
 *
 * @author Jelle Druyts
 * @see RemoteAddrRoutePredicateFactory
 * @see XForwardedRemoteAddressResolver
 */
public class XForwardedRemoteAddrRoutePredicateFactory
		extends AbstractRoutePredicateFactory<XForwardedRemoteAddrRoutePredicateFactory.Config> {

	/** 日志记录器 */
	private static final Log log = LogFactory.getLog(XForwardedRemoteAddrRoutePredicateFactory.class);

	/**
	 * 默认构造函数，使用默认配置类。
	 */
	public XForwardedRemoteAddrRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置类型。
	 * @return 快捷配置类型为收集列表模式
	 */
	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST;
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("sources");
	}

	/**
	 * 创建断言，检查 X-Forwarded-For 头中的 IP 地址。
	 *
	 * <p>
	 * 该方法内部委托给 RemoteAddrRoutePredicateFactory， 但使用 XForwardedRemoteAddressResolver
	 * 解析真实客户端 IP。
	 * </p>
	 * @param config 配置对象，包含 IP 地址列表和信任级别
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		if (log.isDebugEnabled()) {
			log.debug("Applying XForwardedRemoteAddr route predicate with maxTrustedIndex of "
					+ config.getMaxTrustedIndex() + " for " + config.getSources().size() + " source(s)");
		}

		// 复用标准的 RemoteAddrRoutePredicateFactory
		// 但使用 XForwardedRemoteAddressResolver 代替默认的 RemoteAddressResolver
		RemoteAddrRoutePredicateFactory.Config wrappedConfig = new RemoteAddrRoutePredicateFactory.Config();
		wrappedConfig.setSources(config.getSources());
		wrappedConfig
				.setRemoteAddressResolver(XForwardedRemoteAddressResolver.maxTrustedIndex(config.getMaxTrustedIndex()));
		RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory = new RemoteAddrRoutePredicateFactory();
		Predicate<ServerWebExchange> wrappedPredicate = remoteAddrRoutePredicateFactory.apply(wrappedConfig);

		return exchange -> {
			Boolean isAllowed = wrappedPredicate.test(exchange);

			if (log.isDebugEnabled()) {
				ServerHttpRequest request = exchange.getRequest();
				log.debug("Request for \"" + request.getURI() + "\" from client \""
						+ request.getRemoteAddress().getAddress().getHostAddress() + "\" with \""
						+ XForwardedRemoteAddressResolver.X_FORWARDED_FOR + "\" header value of \""
						+ request.getHeaders().get(XForwardedRemoteAddressResolver.X_FORWARDED_FOR) + "\" is "
						+ (isAllowed ? "ALLOWED" : "NOT ALLOWED"));
			}

			return isAllowed;
		};
	}

	/**
	 * 配置类，定义 XForwardedRemoteAddr 断言所需的配置参数。
	 */
	public static class Config {

		/**
		 * 信任的 X-Forwarded-For 头值位置。
		 * <p>
		 * 默认值为 1，表示信任 "X-Forwarded-For" 头中最右边（最后）的值， 这代表调用网关时使用的最后一个反向代理的 IP。
		 * </p>
		 * <ul>
		 * <li>1：信任最后一个 IP（最后一个代理）</li>
		 * <li>2：信任最后两个 IP，倒数第二个为客户端</li>
		 * <li>以此类推...</li>
		 * </ul>
		 */
		// 默认信任最后一个（最近的）代理
		private int maxTrustedIndex = 1;

		/** IP 地址或 CIDR 网段列表 */
		private List<String> sources = new ArrayList<>();

		/**
		 * 获取信任级别。
		 * @return maxTrustedIndex 值
		 */
		public int getMaxTrustedIndex() {
			return this.maxTrustedIndex;
		}

		/**
		 * 设置信任级别。
		 * @param maxTrustedIndex 信任的 IP 位置数量
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setMaxTrustedIndex(int maxTrustedIndex) {
			this.maxTrustedIndex = maxTrustedIndex;
			return this;
		}

		/**
		 * 获取 IP 地址列表。
		 * @return IP 地址/网段列表
		 */
		public List<String> getSources() {
			return this.sources;
		}

		/**
		 * 设置 IP 地址列表。
		 * @param sources IP 地址/网段列表
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setSources(List<String> sources) {
			this.sources = sources;
			return this;
		}

		/**
		 * 设置 IP 地址列表（便捷方法）。
		 * @param sources IP 地址/网段数组
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setSources(String... sources) {
			this.sources = Arrays.asList(sources);
			return this;
		}

	}

}
