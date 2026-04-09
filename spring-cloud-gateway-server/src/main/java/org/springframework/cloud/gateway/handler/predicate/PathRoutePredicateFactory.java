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

import org.springframework.core.style.ToStringCreator;
import org.springframework.http.server.PathContainer;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPattern.PathMatchInfo;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_PATH_CONTAINER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.putUriTemplateVariables;
import static org.springframework.http.server.PathContainer.parsePath;

/**
 * 路径断言工厂 - 根据请求的 URL 路径进行路由匹配。
 *
 * <p>
 * 该断言工厂是 Spring Cloud Gateway 最常用和核心的断言之一， 用于根据请求的 URL 路径进行路由匹配。支持多个路径模式。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求路径是否匹配配置的模式列表</li>
 * <li>支持路径变量提取（如 /user/{id}）</li>
 * <li>支持 Ant 风格的路径通配符（**、*、?）</li>
 * <li>可选：是否匹配尾部斜杠</li>
 * <li>提取的路径变量会存入 exchange 属性供后续过滤器使用</li>
 * </ul>
 *
 * <p>
 * <b>路径模式示例：</b>
 * </p>
 * <ul>
 * <li><code>/user</code> - 精确匹配 /user</li>
 * <li><code>/user/**</code> - 匹配 /user 下的所有路径</li>
 * <li><code>/user/*</code> - 匹配 /user 下的单级路径</li>
 * <li><code>/user/{id}</code> - 提取 id 变量</li>
 * <li><code>/user/{id:\\d+}</code> - 提取数字类型的 id</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式 - 多个路径模式
 * - id: path_route
 *   uri: https://example.org
 *   predicates:
 *   - Path=/user/**,/order/**
 *
 * # YAML 配置方式 - 带变量提取
 * - id: user_detail
 *   uri: https://user.example.org
 *   predicates:
 *   - Path=/user/{id}
 *
 * # YAML 配置方式 - 使用正则变量
 * - id: product_detail
 *   uri: https://product.example.org
 *   predicates:
 *   - Path=/product/{category}/{id}
 *
 * # YAML 配置方式 - 禁用尾部斜杠匹配
 * - id: exact_path
 *   uri: https://exact.example.org
 *   predicates:
 *   - Path=/api, matchTrailingSlash=false
 * }</pre>
 *
 * @author Spencer Gibb
 * @author Dhawal Kapil
 * @see AbstractRoutePredicateFactory
 * @see PathPattern
 */
public class PathRoutePredicateFactory extends AbstractRoutePredicateFactory<PathRoutePredicateFactory.Config> {

	/** 日志记录器 */
	private static final Log log = LogFactory.getLog(PathRoutePredicateFactory.class);

	/** 尾部斜杠匹配配置键 */
	private static final String MATCH_TRAILING_SLASH = "matchTrailingSlash";

	/** 路径模式解析器 */
	private PathPatternParser pathPatternParser = new PathPatternParser();

	/**
	 * 构造函数，使用默认配置类。
	 */
	public PathRoutePredicateFactory() {
		super(Config.class);
	}

	/**
	 * 记录路径匹配跟踪日志。
	 * @param prefix 日志前缀
	 * @param desired 期望的模式
	 * @param actual 实际的值
	 * @param match 是否匹配
	 */
	private static void traceMatch(String prefix, Object desired, Object actual, boolean match) {
		if (log.isTraceEnabled()) {
			String message = String.format("%s \"%s\" %s against value \"%s\"", prefix, desired,
					match ? "matches" : "does not match", actual);
			log.trace(message);
		}
	}

	/**
	 * 设置自定义的路径模式解析器。
	 * @param pathPatternParser 路径模式解析器
	 */
	public void setPathPatternParser(PathPatternParser pathPatternParser) {
		this.pathPatternParser = pathPatternParser;
	}

	/**
	 * 返回快捷配置字段顺序，用于 YAML 简写配置解析。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("patterns", MATCH_TRAILING_SLASH);
	}

	/**
	 * 返回快捷配置类型。
	 * @return 快捷配置类型为收集列表尾部标志模式
	 */
	@Override
	public ShortcutType shortcutType() {
		return ShortcutType.GATHER_LIST_TAIL_FLAG;
	}

	/**
	 * 创建断言，检查请求路径。
	 * @param config 配置对象，包含路径模式列表
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		final ArrayList<PathPattern> pathPatterns = new ArrayList<>();
		synchronized (this.pathPatternParser) {
			// 设置尾部斜杠匹配策略
			pathPatternParser.setMatchOptionalTrailingSeparator(config.isMatchTrailingSlash());
			// 解析所有路径模式
			config.getPatterns().forEach(pattern -> {
				PathPattern pathPattern = this.pathPatternParser.parse(pattern);
				pathPatterns.add(pathPattern);
			});
		}

		return new GatewayPredicate() {
			/**
			 * 测试请求路径是否匹配配置的模式。
			 * @param exchange 服务器 Web 交换对象
			 * @return 如果路径匹配任意模式则返回 true
			 */
			@Override
			public boolean test(ServerWebExchange exchange) {
				// 获取或创建路径容器
				PathContainer path = (PathContainer) exchange.getAttributes().computeIfAbsent(
						GATEWAY_PREDICATE_PATH_CONTAINER_ATTR,
						s -> parsePath(exchange.getRequest().getURI().getRawPath()));

				// 遍历所有路径模式，查找第一个匹配的
				PathPattern match = null;
				for (int i = 0; i < pathPatterns.size(); i++) {
					PathPattern pathPattern = pathPatterns.get(i);
					if (pathPattern.matches(path)) {
						match = pathPattern;
						break;
					}
				}

				if (match != null) {
					traceMatch("Pattern", match.getPatternString(), path, true);
					// 提取路径变量并存储
					PathMatchInfo pathMatchInfo = match.matchAndExtract(path);
					putUriTemplateVariables(exchange, pathMatchInfo.getUriVariables());
					// 存储匹配的模式字符串
					exchange.getAttributes().put(GATEWAY_PREDICATE_MATCHED_PATH_ATTR, match.getPatternString());
					// 存储匹配路径的路由ID
					String routeId = (String) exchange.getAttributes().get(GATEWAY_PREDICATE_ROUTE_ATTR);
					if (routeId != null) {
						exchange.getAttributes().put(GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR, routeId);
					}
					return true;
				}
				else {
					traceMatch("Pattern", config.getPatterns(), path, false);
					return false;
				}
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Paths: %s, match trailing slash: %b", config.getPatterns(),
						config.isMatchTrailingSlash());
			}
		};
	}

	/**
	 * 配置类，定义 Path 断言所需的配置参数。
	 */
	@Validated
	public static class Config {

		/** 路径模式列表 */
		private List<String> patterns = new ArrayList<>();

		/** 是否匹配尾部斜杠，默认 true */
		private boolean matchTrailingSlash = true;

		/**
		 * 获取路径模式列表。
		 * @return 路径模式列表
		 */
		public List<String> getPatterns() {
			return patterns;
		}

		/**
		 * 设置路径模式列表。
		 * @param patterns 路径模式列表
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setPatterns(List<String> patterns) {
			this.patterns = patterns;
			return this;
		}

		/**
		 * @deprecated 使用 {@link #isMatchTrailingSlash()}
		 */
		@Deprecated
		public boolean isMatchOptionalTrailingSeparator() {
			return isMatchTrailingSlash();
		}

		/**
		 * @deprecated 使用 {@link #setMatchTrailingSlash(boolean)}
		 */
		@Deprecated
		public Config setMatchOptionalTrailingSeparator(boolean matchOptionalTrailingSeparator) {
			setMatchTrailingSlash(matchOptionalTrailingSeparator);
			return this;
		}

		/**
		 * 获取是否匹配尾部斜杠。
		 * @return true 表示 /user 和 /user/ 都能匹配，false 表示精确匹配
		 */
		public boolean isMatchTrailingSlash() {
			return matchTrailingSlash;
		}

		/**
		 * 设置是否匹配尾部斜杠。
		 * @param matchTrailingSlash 是否匹配尾部斜杠
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setMatchTrailingSlash(boolean matchTrailingSlash) {
			this.matchTrailingSlash = matchTrailingSlash;
			return this;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("patterns", patterns)
					.append(MATCH_TRAILING_SLASH, matchTrailingSlash).toString();
		}

	}

}
