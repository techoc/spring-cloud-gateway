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

import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * Cloud Foundry 路由服务断言工厂 - 匹配发往 Cloud Foundry 路由服务的请求。
 *
 * <p>
 * 该断言工厂用于识别请求是否来自 Cloud Foundry 平台的路由服务。 Cloud Foundry 路由服务通过特定的 HTTP 头来传递请求信息。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>检查请求是否包含 X-CF-Forwarded-Url 头（转发的原始 URL）</li>
 * <li>检查请求是否包含 X-CF-Proxy-Signature 头（代理签名）</li>
 * <li>检查请求是否包含 X-CF-Proxy-Metadata 头（代理元数据）</li>
 * <li>三个头都存在时才匹配成功</li>
 * </ul>
 *
 * <p>
 * <b>使用场景：</b>
 * </p>
 * <ul>
 * <li>在 Cloud Foundry 环境中部署的微服务网关</li>
 * <li>需要与 Cloud Foundry 路由服务集成的场景</li>
 * <li>验证请求是否经过 CF 代理</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # YAML 配置方式
 * - id: cloudfoundry_route
 *   uri: https://cf-route-service.example.com
 *   predicates:
 *   - CloudFoundryRouteService
 * }</pre>
 *
 * @author Andrew Fitzgerald
 * @see <a href="https://docs.cloudfoundry.org/services/route-services.html">Cloud Foundry
 * Route Service documentation</a>
 */
public class CloudFoundryRouteServiceRoutePredicateFactory extends AbstractRoutePredicateFactory<Object> {

	/**
	 * Cloud Foundry 转发的原始 URL 头名称。
	 */
	public static final String X_CF_FORWARDED_URL = "X-CF-Forwarded-Url";

	/**
	 * Cloud Foundry 代理签名头名称。
	 */
	public static final String X_CF_PROXY_SIGNATURE = "X-CF-Proxy-Signature";

	/**
	 * Cloud Foundry 代理元数据头名称。
	 */
	public static final String X_CF_PROXY_METADATA = "X-CF-Proxy-Metadata";

	/** 内部使用的 Header 断言工厂 */
	private final HeaderRoutePredicateFactory factory = new HeaderRoutePredicateFactory();

	/**
	 * 构造函数，使用 Object.class 作为配置类（此断言无需配置参数）。
	 */
	public CloudFoundryRouteServiceRoutePredicateFactory() {
		super(Object.class);
	}

	/**
	 * 创建断言，检查请求是否包含 CF 路由服务所需的所有头信息。
	 * @param unused 忽略的参数，此断言无需配置
	 * @return 匹配的断言
	 */
	@Override
	public Predicate<ServerWebExchange> apply(Object unused) {
		// 组合三个头断言，必须全部满足
		return headerPredicate(X_CF_FORWARDED_URL).and(headerPredicate(X_CF_PROXY_SIGNATURE))
				.and(headerPredicate(X_CF_PROXY_METADATA));
	}

	/**
	 * 创建检查特定 HTTP 头存在的断言。
	 * @param header 头名称
	 * @return 检查该头是否存在（正则匹配任意值）的断言
	 */
	private Predicate<ServerWebExchange> headerPredicate(String header) {
		HeaderRoutePredicateFactory.Config config = factory.newConfig();
		config.setHeader(header);
		config.setRegexp(".*"); // 匹配任意值
		return factory.apply(config);
	}

}
