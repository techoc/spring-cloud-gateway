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

package org.springframework.cloud.gateway.config;

import java.net.URI;

import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;

/**
 * Gateway 无负载均衡器客户端自动配置类。
 * <p>
 * 当 classpath 中不存在 {@code ReactorLoadBalancer} 类且未配置 {@code ReactiveLoadBalancer} Bean 时，
 * 该配置类会自动创建一个临时的 NoLoadBalancerClientFilter，用于处理 lb:// 协议的请求。
 * <p>
 * 此配置旨在提供向后兼容性，在没有负载均衡器的情况下优雅地处理无法找到服务实例的情况。
 *
 * @author Spencer Gibb
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer")
@ConditionalOnMissingBean(ReactiveLoadBalancer.class)
@EnableConfigurationProperties(GatewayLoadBalancerProperties.class)
@AutoConfigureAfter(GatewayReactiveLoadBalancerClientAutoConfiguration.class)
public class GatewayNoLoadBalancerClientAutoConfiguration {

	/**
	 * 创建无负载均衡器客户端过滤器。
	 * <p>
	 * 当没有配置 ReactiveLoadBalancerClientFilter 时创建该过滤器， 用于处理 lb:// 协议的请求。由于没有负载均衡器可用，
	 * 该过滤器会抛出 NotFoundException 异常。
	 * @param properties 网关负载均衡器配置属性
	 * @return NoLoadBalancerClientFilter 实例
	 */
	@Bean
	@ConditionalOnMissingBean(ReactiveLoadBalancerClientFilter.class)
	public NoLoadBalancerClientFilter noLoadBalancerClientFilter(GatewayLoadBalancerProperties properties) {
		return new NoLoadBalancerClientFilter(properties.isUse404());
	}

	/**
	 * 无负载均衡器客户端过滤器。
	 * <p>
	 * 当系统中没有配置负载均衡器时，该过滤器作为临时替代方案， 拦截所有 lb:// 协议的请求并抛出 NotFoundException。
	 *
	 * @see GlobalFilter
	 * @see Ordered
	 */
	protected static class NoLoadBalancerClientFilter implements GlobalFilter, Ordered {

		/** 是否在找不到实例时返回 404 状态码 */
		private final boolean use404;

		/**
		 * 构造无负载均衡器客户端过滤器。
		 * @param use404 如果为 true，当找不到实例时返回 404 状态码；否则返回 503 状态码
		 */
		public NoLoadBalancerClientFilter(boolean use404) {
			this.use404 = use404;
		}

		@Override
		public int getOrder() {
			return LOAD_BALANCER_CLIENT_FILTER_ORDER;
		}

		/**
		 * 过滤处理方法。
		 * <p>
		 * 检查请求的 URL 是否使用 lb:// 协议。如果使用但没有可用的负载均衡器， 则抛出 NotFoundException 异常；否则继续执行过滤器链。
		 * @param exchange 当前请求的 ServerWebExchange 对象
		 * @param chain 过滤器链
		 * @return 表示处理完成的 Mono
		 * @throws NotFoundException 当使用 lb:// 协议但找不到服务实例时抛出
		 */
		@Override
		@SuppressWarnings("Duplicates")
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
			String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
			if (url == null || (!"lb".equals(url.getScheme()) && !"lb".equals(schemePrefix))) {
				return chain.filter(exchange);
			}

			throw NotFoundException.create(use404, "Unable to find instance for " + url.getHost());
		}

	}

}
