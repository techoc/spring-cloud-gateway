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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ShortcutConfigurable.ShortcutType.GATHER_LIST;

/**
 * 远程地址断言工厂 - 根据客户端 IP 地址进行路由匹配。
 *
 * <p>
 * 该断言工厂用于根据请求的远程 IP 地址进行路由匹配。 支持单个 IP 地址和 CIDR 网段（如 192.168.1.0/24）。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求的远程 IP 地址是否在配置的 IP 列表/网段中</li>
 * <li>支持单个 IP 地址（如 192.168.1.100）</li>
 * <li>支持 CIDR 网段（如 192.168.1.0/24）</li>
 * <li>支持自定义的远程地址解析器（如 X-Forwarded-For）</li>
 * </ul>
 *
 * <p>
 * <b>使用场景：</b>
 * </p>
 * <ul>
 * <li>IP 白名单/黑名单访问控制</li>
 * <li>基于地理位置的路由</li>
 * <li>内网服务访问限制</li>
 * <li>CDN/负载均衡器后的真实客户端 IP 获取</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 单一 IP 地址
 * - id: ip_route
 *   uri: https://internal.example.org
 *   predicates:
 *   - RemoteAddr=192.168.1.100
 *
 * # YAML 配置方式 - 多个 IP 地址
 * - id: office_route
 *   uri: https://office.example.org
 *   predicates:
 *   - RemoteAddr=10.0.0.1,10.0.0.2,10.0.0.3
 *
 * # YAML 配置方式 - CIDR 网段
 * - id: internal_route
 *   uri: https://internal.example.org
 *   predicates:
 *   - RemoteAddr=192.168.0.0/16
 *
 * # YAML 配置方式 - 内网地址
 * - id: local_route
 *   uri: https://local.example.org
 *   predicates:
 *   - RemoteAddr=127.0.0.1,192.168.0.0/16,10.0.0.0/8
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 * @see RemoteAddressResolver
 */
public class RemoteAddrRoutePredicateFactory
		extends AbstractRoutePredicateFactory<RemoteAddrRoutePredicateFactory.Config> {

	/** 日志记录器 */
	private static final Log log = LogFactory.getLog(RemoteAddrRoutePredicateFactory.class);

	/**
	 * 默认构造函数，使用默认配置类。
	 */
	public RemoteAddrRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 返回快捷配置类型。
	 * @return 快捷配置类型为收集列表模式
	 */
	@Override
	public ShortcutType shortcutType() {
		return GATHER_LIST;
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("sources");
	}

	/**
	 * 将字符串列表转换为 IP 子网过滤规则列表。
	 * @param values IP 地址或 CIDR 网段字符串列表
	 * @return IP 子网过滤规则列表
	 */
	@NotNull
	private List<IpSubnetFilterRule> convert(List<String> values) {
		List<IpSubnetFilterRule> sources = new ArrayList<>();
		for (String arg : values) {
			addSource(sources, arg);
		}
		return sources;
	}

	/**
	 * 创建断言，检查请求的远程 IP 地址。
	 * @param config 配置对象，包含 IP 地址列表和地址解析器
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		List<IpSubnetFilterRule> sources = convert(config.sources);

		return new GatewayPredicate() {
			/**
			 * 测试请求的远程 IP 地址是否在配置的列表中。
			 * @param exchange 服务器 Web 交换对象
			 * @return 如果 IP 地址匹配任意配置则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				// 使用配置的地址解析器获取远程地址
				InetSocketAddress remoteAddress = config.remoteAddressResolver.resolve(exchange);
				if (remoteAddress != null && remoteAddress.getAddress() != null) {
					String hostAddress = remoteAddress.getAddress().getHostAddress();
					String host = exchange.getRequest().getURI().getHost();

					if (log.isDebugEnabled() && !hostAddress.equals(host)) {
						log.debug("Remote addresses didn't match " + hostAddress + " != " + host);
					}

					// 检查是否匹配任意一个 IP 网段规则
					for (IpSubnetFilterRule source : sources) {
						if (source.matches(remoteAddress)) {
							return true;
						}
					}
				}

				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("RemoteAddrs: %s", config.getSources());
			}
		};
	}

	/**
	 * 添加一个 IP 地址或网段到列表中。
	 * @param sources IP 子网过滤规则列表
	 * @param source IP 地址字符串（如 192.168.1.1 或 192.168.1.0/24）
	 */
	private void addSource(List<IpSubnetFilterRule> sources, String source) {
		// 如果没有指定子网掩码，默认使用 /32（单 IP）
		if (!source.contains("/")) {
			source = source + "/32";
		}

		// 解析 IP 地址和 CIDR 前缀
		String[] ipAddressCidrPrefix = source.split("/", 2);
		String ipAddress = ipAddressCidrPrefix[0];
		int cidrPrefix = Integer.parseInt(ipAddressCidrPrefix[1]);

		// 添加 IP 子网过滤规则
		sources.add(new IpSubnetFilterRule(ipAddress, cidrPrefix, IpFilterRuleType.ACCEPT));
	}

	/**
	 * 配置类，定义 RemoteAddr 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** IP 地址或 CIDR 网段列表（必须） */
		@NotEmpty
		private List<String> sources = new ArrayList<>();

		/** 远程地址解析器，默认使用直接解析 */
		@NotNull
		private RemoteAddressResolver remoteAddressResolver = new RemoteAddressResolver() {
		};

		/**
		 * 获取 IP 地址列表。
		 * @return IP 地址/网段列表
		 */
		public List<String> getSources() {
			return sources;
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

		/**
		 * 设置远程地址解析器。
		 *
		 * <p>
		 * 默认解析器直接从请求获取远程地址。 可以设置为 XForwardedRemoteAddressResolver 以支持代理场景。
		 * </p>
		 * @param remoteAddressResolver 远程地址解析器
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setRemoteAddressResolver(RemoteAddressResolver remoteAddressResolver) {
			this.remoteAddressResolver = remoteAddressResolver;
			return this;
		}

	}

}
