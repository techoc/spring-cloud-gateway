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

package org.springframework.cloud.gateway.route.builder;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.XForwardedRemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.http.HttpMethod;
import org.springframework.web.server.ServerWebExchange;

import static java.util.Arrays.stream;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * 路由断言配置规格类，用于配置路由的匹配条件。
 * <p>
 * 该类提供了丰富的断言方法，支持基于以下条件的请求匹配：
 * <ul>
 * <li>时间条件：after、before、between</li>
 * <li>请求属性：cookie、header、host、method、path、query</li>
 * <li>网络条件：remoteAddr、xForwardedRemoteAddr</li>
 * <li>高级功能：readBody（读取请求体）、weight（权重路由）</li>
 * </ul>
 * <p>
 * 多个断言之间默认为逻辑与（AND）关系。 配置完断言后，可通过 {@link BooleanSpec#filters(Function)} 进入过滤器配置， 或直接调用
 * {@link UriSpec#uri(String)} 设置目标 URI。
 *
 * @author Spencer Gibb
 */
public class PredicateSpec extends UriSpec {

	/**
	 * 构造方法。
	 * @param routeBuilder 异步路由构建器
	 * @param builder 父构建器
	 */
	PredicateSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
	}

	/**
	 * 设置路由的执行顺序。
	 * <p>
	 * 值越小优先级越高，默认为 0。
	 * @param order 排序值
	 * @return 当前规格对象（链式调用）
	 */
	public PredicateSpec order(int order) {
		this.routeBuilder.order(order);
		return this;
	}

	/**
	 * 添加自定义同步断言。
	 * @param predicate 同步断言，判断请求是否匹配
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
		return asyncPredicate(toAsyncPredicate(predicate));
	}

	/**
	 * 添加自定义异步断言。
	 * @param predicate 异步断言
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
		this.routeBuilder.asyncPredicate(predicate);
		return new BooleanSpec(this.routeBuilder, this.builder);
	}

	/**
	 * 创建网关过滤器规格对象。
	 * <p>
	 * 用于内部创建过滤器配置链。
	 * @return 网关过滤器规格对象
	 */
	protected GatewayFilterSpec createGatewayFilterSpec() {
		return new GatewayFilterSpec(this.routeBuilder, this.builder);
	}

	/**
	 * 时间断言：请求时间必须在指定时间之后。
	 * <p>
	 * 使用场景：定时上线新功能、限时活动等。
	 * @param datetime 起始时间（ZonedDateTime）
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec after(ZonedDateTime datetime) {
		return asyncPredicate(getBean(AfterRoutePredicateFactory.class).applyAsync(c -> c.setDatetime(datetime)));
	}

	/**
	 * 时间断言：请求时间必须在指定时间之前。
	 * @param datetime 截止时间（ZonedDateTime）
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec before(ZonedDateTime datetime) {
		return asyncPredicate(getBean(BeforeRoutePredicateFactory.class).applyAsync(c -> c.setDatetime(datetime)));
	}

	/**
	 * 时间断言：请求时间必须在两个时间之间。
	 * @param datetime1 起始时间
	 * @param datetime2 截止时间
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec between(ZonedDateTime datetime1, ZonedDateTime datetime2) {
		return asyncPredicate(getBean(BetweenRoutePredicateFactory.class)
				.applyAsync(c -> c.setDatetime1(datetime1).setDatetime2(datetime2)));
	}

	/**
	 * Cookie 断言：检查请求 Cookie 是否匹配指定正则表达式。
	 * @param name Cookie 名称
	 * @param regex Cookie 值匹配的正则表达式
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec cookie(String name, String regex) {
		return asyncPredicate(
				getBean(CookieRoutePredicateFactory.class).applyAsync(c -> c.setName(name).setRegexp(regex)));
	}

	/**
	 * Header 断言：检查请求头是否存在。
	 * @param header 请求头名称
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec header(String header) {
		return asyncPredicate(getBean(HeaderRoutePredicateFactory.class).applyAsync(c -> c.setHeader(header)));
	}

	/**
	 * Header 断言：检查请求头是否存在且值匹配正则表达式。
	 * @param header 请求头名称
	 * @param regex 请求头值匹配的正则表达式
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec header(String header, String regex) {
		return asyncPredicate(
				getBean(HeaderRoutePredicateFactory.class).applyAsync(c -> c.setHeader(header).setRegexp(regex)));
	}

	/**
	 * Host 断言：检查请求 Host 头是否匹配指定模式。
	 * <p>
	 * 支持 Ant 风格模式，使用 {@code .} 作为分隔符。 示例：{@code *.example.com} 匹配所有子域名。
	 * @param pattern Host 匹配模式（可变参数）
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec host(String... pattern) {
		return asyncPredicate(
				getBean(HostRoutePredicateFactory.class).applyAsync(c -> c.setPatterns(Arrays.asList(pattern))));
	}

	/**
	 * Method 断言：检查 HTTP 方法是否匹配（字符串形式）。
	 * @param methods HTTP 方法名称，如 "GET", "POST"
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec method(String... methods) {
		return asyncPredicate(getBean(MethodRoutePredicateFactory.class).applyAsync(c -> {
			HttpMethod[] httpMethods = stream(methods).map(HttpMethod::resolve).toArray(HttpMethod[]::new);
			c.setMethods(httpMethods);
		}));
	}

	/**
	 * Method 断言：检查 HTTP 方法是否匹配（HttpMethod 枚举形式）。
	 * @param methods HTTP 方法枚举
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec method(HttpMethod... methods) {
		return asyncPredicate(getBean(MethodRoutePredicateFactory.class).applyAsync(c -> {
			c.setMethods(methods);
		}));
	}

	/**
	 * Path 断言：检查请求路径是否匹配指定模式。
	 * <p>
	 * 使用 Spring 的 PathMatcher 进行匹配，支持 Ant 风格模式。 示例：{@code /api/**} 匹配所有 /api/ 开头的路径。
	 * @param patterns 路径匹配模式（可变参数）
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec path(String... patterns) {
		return asyncPredicate(
				getBean(PathRoutePredicateFactory.class).applyAsync(c -> c.setPatterns(Arrays.asList(patterns))));
	}

	/**
	 * Path 断言：检查请求路径是否匹配指定模式，可控制是否匹配尾部斜杠。
	 * @param matchTrailingSlash 是否匹配尾部斜杠，false 表示不匹配以 / 结尾的路径
	 * @param patterns 路径匹配模式（可变参数）
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec path(boolean matchTrailingSlash, String... patterns) {
		return asyncPredicate(getBean(PathRoutePredicateFactory.class)
				.applyAsync(c -> c.setPatterns(Arrays.asList(patterns)).setMatchTrailingSlash(matchTrailingSlash)));
	}

	/**
	 * ReadBody 断言：读取并检查请求体内容（BETA 功能）。
	 * <p>
	 * 注意：此功能处于 BETA 阶段，未来版本可能变更。 使用场景：根据请求体内容决定路由，如 JSON 字段值判断。
	 * @param inClass 请求体解析的目标类
	 * @param predicate 请求体内容断言
	 * @param <T> 请求体类型参数
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public <T> BooleanSpec readBody(Class<T> inClass, Predicate<T> predicate) {
		return asyncPredicate(
				getBean(ReadBodyRoutePredicateFactory.class).applyAsync(c -> c.setPredicate(inClass, predicate)));
	}

	/**
	 * Query 断言：检查查询参数是否存在且值匹配正则表达式。
	 * @param param 查询参数名
	 * @param regex 参数值匹配的正则表达式
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec query(String param, String regex) {
		return asyncPredicate(
				getBean(QueryRoutePredicateFactory.class).applyAsync(c -> c.setParam(param).setRegexp(regex)));
	}

	/**
	 * Query 断言：检查查询参数是否存在。
	 * @param param 查询参数名
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec query(String param) {
		return asyncPredicate(getBean(QueryRoutePredicateFactory.class).applyAsync(c -> c.setParam(param)));
	}

	/**
	 * RemoteAddr 断言：检查客户端远程地址是否匹配指定 IP 范围。
	 * <p>
	 * 默认使用请求的远程地址。如果网关位于代理层之后，可能需要使用 {@link #xForwardedRemoteAddr(String...)} 或自定义
	 * {@link RemoteAddressResolver}。
	 * @param addrs IP 地址范围，使用 CIDR 表示法（IPv4 或 IPv6）
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec remoteAddr(String... addrs) {
		return remoteAddr(null, addrs);
	}

	/**
	 * RemoteAddr 断言：检查客户端远程地址是否匹配指定 IP 范围，支持自定义地址解析器。
	 * <p>
	 * 适用于网关位于代理层之后的场景，可通过 {@link RemoteAddressResolver} 自定义 IP 解析逻辑， 如基于
	 * {@code X-Forwarded-For} 头获取真实客户端 IP。
	 * @param resolver 远程地址解析器
	 * @param addrs IP 地址范围，使用 CIDR 表示法
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec remoteAddr(RemoteAddressResolver resolver, String... addrs) {
		return asyncPredicate(getBean(RemoteAddrRoutePredicateFactory.class).applyAsync(c -> {
			c.setSources(addrs);
			if (resolver != null) {
				c.setRemoteAddressResolver(resolver);
			}
		}));
	}

	/**
	 * XForwardedRemoteAddr 断言：基于 {@code X-Forwarded-For} 头检查客户端 IP。
	 * <p>
	 * 适用于网关位于代理层之后的场景，自动从 X-Forwarded-For 头获取真实客户端 IP。
	 * @param addrs IP 地址范围，使用 CIDR 表示法
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec xForwardedRemoteAddr(String... addrs) {
		return asyncPredicate(getBean(XForwardedRemoteAddrRoutePredicateFactory.class).applyAsync(c -> {
			c.setSources(addrs);
		}));
	}

	/**
	 * Weight 断言：基于权重选择路由（灰度发布/金丝雀发布）。
	 * <p>
	 * 将同一组路由按权重分配流量，实现按比例分流。 示例：group="serviceA", weight=80 表示该路由获得 80% 的流量。
	 * @param group 路由分组名称
	 * @param weight 权重值（同组内所有路由权重之和应为 100）
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec weight(String group, int weight) {
		return asyncPredicate(getBean(WeightRoutePredicateFactory.class)
				.applyAsync(c -> c.setGroup(group).setRouteId(routeBuilder.getId()).setWeight(weight)));
	}

	/**
	 * CloudFoundry 路由服务断言。
	 * <p>
	 * 用于识别 CloudFoundry 平台的路由服务请求。
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec cloudFoundryRouteService() {
		return predicate(getBean(CloudFoundryRouteServiceRoutePredicateFactory.class).apply(c -> {
		}));
	}

	/**
	 * 始终为 true 的断言。
	 * <p>
	 * 用于需要无条件匹配的场景，或作为逻辑运算的基础。
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec alwaysTrue() {
		return predicate(exchange -> true);
	}

	/**
	 * 逻辑非（NOT）运算。
	 * <p>
	 * 对指定断言取反。
	 * @param fn 断言配置函数
	 * @return 布尔规格对象，支持逻辑运算符
	 */
	public BooleanSpec not(Function<PredicateSpec, BooleanSpec> fn) {
		return alwaysTrue().and().not(fn);
	}

}
