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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

/**
 * 主机断言工厂 - 根据请求的 Host 头进行路由匹配。
 *
 * <p>
 * 该断言工厂用于根据请求的 Host 头（域名）进行路由匹配。 支持 Ant 风格的路径匹配模式，可以包含通配符。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求的 Host 头是否匹配配置的模式列表</li>
 * <li>支持 Ant 风格的通配符模式（如 *.example.com）</li>
 * <li>支持路径变量提取（{subdomain}.example.com）</li>
 * <li>提取的变量会存入 exchange 属性供后续过滤器使用</li>
 * </ul>
 *
 * <p>
 * <b>模式示例：</b>
 * </p>
 * <ul>
 * <li><code>www.example.com</code> - 精确匹配</li>
 * <li><code>*.example.com</code> - 匹配所有子域名</li>
 * <li><code>**.example.com</code> - 匹配多级子域名</li>
 * <li><code>{subdomain}.example.com</code> - 提取子域名变量</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 多个主机模式
 * - id: host_route
 *   uri: https://example.org
 *   predicates:
 *   - Host=**.example.org,**.example.com
 *
 * # YAML 配置方式 - 使用路径变量
 * - id: tenant_route
 *   uri: https://{tenant}.example.org
 *   predicates:
 *   - Host={tenant}.example.org
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 * @see AntPathMatcher
 */
public class HostRoutePredicateFactory extends AbstractRoutePredicateFactory<HostRoutePredicateFactory.Config> {

	/** 路径匹配器，使用点号作为路径分隔符的 AntPathMatcher */
	private PathMatcher pathMatcher = new AntPathMatcher(".");

	/**
	 * 构造函数，使用默认配置类。
	 */
	public HostRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 设置自定义的路径匹配器。
	 * @param pathMatcher 路径匹配器
	 */
	public void setPathMatcher(PathMatcher pathMatcher) {
		this.pathMatcher = pathMatcher;
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("patterns");
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
	 * 创建断言，检查请求的 Host 头。
	 * @param config 配置对象，包含主机模式列表
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		return new GatewayPredicate() {
			/**
			 * 测试请求的 Host 头是否匹配配置的模式。
			 * @param exchange 服务器 Web 交换对象
			 * @return 如果 Host 匹配任意模式则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				// 获取 Host 头
				String host = exchange.getRequest().getHeaders().getFirst("Host");
				String match = null;

				// 遍历所有模式，查找第一个匹配的
				for (int i = 0; i < config.getPatterns().size(); i++) {
					String pattern = config.getPatterns().get(i);
					if (pathMatcher.match(pattern, host)) {
						match = pattern;
						break;
					}
				}

				// 如果找到匹配的模式
				if (match != null) {
					// 提取 URI 模板变量并存储到 exchange 属性
					Map<String, String> variables = pathMatcher.extractUriTemplateVariables(match, host);
					ServerWebExchangeUtils.putUriTemplateVariables(exchange, variables);
					return true;
				}

				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Hosts: %s", config.getPatterns());
			}
		};
	}

	/**
	 * 配置类，定义 Host 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** 主机模式列表 */
		private List<String> patterns = new ArrayList<>();

		/**
		 * 获取主机模式列表。
		 * @return 主机模式列表
		 */
		public List<String> getPatterns() {
			return patterns;
		}

		/**
		 * 设置主机模式列表。
		 * @param patterns 主机模式列表
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setPatterns(List<String> patterns) {
			this.patterns = patterns;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("patterns", patterns).toString();
		}

	}

}
