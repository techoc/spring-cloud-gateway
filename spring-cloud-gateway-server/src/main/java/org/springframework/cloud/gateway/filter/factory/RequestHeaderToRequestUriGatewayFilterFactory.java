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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 请求头转请求 URI 过滤器工厂。
 * <p>
 * 该过滤器从指定的请求头中获取 URL，并将其作为目标请求 URI。 用于动态路由场景，请求的目标地址由客户端在请求头中指定。
 * <p>
 * 配置参数：
 * <ul>
 * <li>name：包含目标 URL 的请求头名称</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - RequestHeaderToRequestUri=X-Destination-Url
 * </pre>
 *
 * @author Toshiaki Maki
 */
public class RequestHeaderToRequestUriGatewayFilterFactory
		extends AbstractChangeRequestUriGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

	private final Logger log = LoggerFactory.getLogger(RequestHeaderToRequestUriGatewayFilterFactory.class);

	/**
	 * 默认构造方法。
	 */
	public RequestHeaderToRequestUriGatewayFilterFactory() {
		super(NameConfig.class);
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(NAME_KEY);
	}

	/**
	 * 创建请求头转 URI 过滤器。
	 * @param config 名称配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(NameConfig config) {
		// AbstractChangeRequestUriGatewayFilterFactory.apply() 返回 OrderedGatewayFilter
		OrderedGatewayFilter gatewayFilter = (OrderedGatewayFilter) super.apply(config);
		return new OrderedGatewayFilter(gatewayFilter, gatewayFilter.getOrder()) {
			@Override
			public String toString() {
				return filterToStringCreator(RequestHeaderToRequestUriGatewayFilterFactory.this)
						.append("name", config.getName()).toString();
			}
		};
	}

	/**
	 * 从请求头中获取目标 URI。
	 * @param exchange 当前交换对象
	 * @param config 名称配置
	 * @return 目标 URI，若请求头不存在或格式错误则返回空
	 */
	@Override
	protected Optional<URI> determineRequestUri(ServerWebExchange exchange, NameConfig config) {
		// 从指定请求头获取 URL
		String requestUrl = exchange.getRequest().getHeaders().getFirst(config.getName());
		return Optional.ofNullable(requestUrl).map(url -> {
			try {
				return new URL(url).toURI();
			}
			catch (MalformedURLException | URISyntaxException e) {
				log.info("Request url is invalid : url={}, error={}", requestUrl, e.getMessage());
				return null;
			}
		});
	}

}
