/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.gateway.filter;

import java.util.ArrayList;
import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;

/**
 * 负载均衡服务实例 Cookie 过滤器。
 * <p>
 * 该全局过滤器配合 {@link ReactiveLoadBalancerClientFilter} 工作，当负载均衡器选定 某个服务实例后，将该实例的
 * {@code instanceId} 以 Cookie 的形式添加到后续请求头中， 实现粘性会话（Sticky Session）功能。只有在
 * {@link LoadBalancerProperties} 配置中 启用了 {@code addServiceInstanceCookie} 选项且配置了 Cookie
 * 名称时，该过滤器才会生效。
 * <p>
 * 执行顺序为 {@link ReactiveLoadBalancerClientFilter#LOAD_BALANCER_CLIENT_FILTER_ORDER} + 1，
 * 确保在负载均衡器选择实例之后执行。
 *
 * @author Olga Maciaszek-Sharma
 * @since 3.0.2
 */
public class LoadBalancerServiceInstanceCookieFilter implements GlobalFilter, Ordered {

	/** 负载均衡属性，用于获取粘性会话配置（旧版构造方式） */
	private LoadBalancerProperties loadBalancerProperties;

	/** 负载均衡客户端工厂，用于按服务 ID 获取负载均衡属性（推荐使用） */
	private ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerClientFactory;

	/**
	 * @deprecated 请使用
	 * {@link #LoadBalancerServiceInstanceCookieFilter(ReactiveLoadBalancer.Factory)} 代替。
	 * 该构造方法无法按服务 ID 获取个性化的负载均衡属性。
	 * @param loadBalancerProperties 全局负载均衡属性配置
	 */
	@Deprecated
	public LoadBalancerServiceInstanceCookieFilter(LoadBalancerProperties loadBalancerProperties) {
		this.loadBalancerProperties = loadBalancerProperties;
	}

	/**
	 * 构造负载均衡服务实例 Cookie 过滤器。
	 * @param loadBalancerClientFactory 负载均衡客户端工厂，用于按服务 ID 获取负载均衡配置
	 */
	public LoadBalancerServiceInstanceCookieFilter(
			ReactiveLoadBalancer.Factory<ServiceInstance> loadBalancerClientFactory) {
		this.loadBalancerClientFactory = loadBalancerClientFactory;
	}

	/**
	 * 过滤请求，若满足粘性会话条件则将服务实例 ID 添加为 Cookie。
	 * <p>
	 * 处理逻辑：
	 * <ol>
	 * <li>若负载均衡响应为空或未选到服务实例，跳过处理；</li>
	 * <li>若负载均衡配置未启用实例 Cookie，跳过处理；</li>
	 * <li>若实例 Cookie 名称未配置，跳过处理；</li>
	 * <li>否则，将实例 ID 以 Cookie 形式追加到请求头并继续过滤链。</li>
	 * </ol>
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Response<ServiceInstance> serviceInstanceResponse = exchange.getAttribute(GATEWAY_LOADBALANCER_RESPONSE_ATTR);
		if (serviceInstanceResponse == null || !serviceInstanceResponse.hasServer()) {
			return chain.filter(exchange);
		}
		LoadBalancerProperties properties = loadBalancerClientFactory != null
				? loadBalancerClientFactory.getProperties(serviceInstanceResponse.getServer().getServiceId())
				: loadBalancerProperties;
		if (!properties.getStickySession().isAddServiceInstanceCookie()) {
			return chain.filter(exchange);
		}
		String instanceIdCookieName = properties.getStickySession().getInstanceIdCookieName();
		if (!StringUtils.hasText(instanceIdCookieName)) {
			return chain.filter(exchange);
		}
		// 将服务实例 ID 以 Cookie 形式追加到请求头的 Cookie 列表中
		ServerWebExchange newExchange = exchange.mutate().request(exchange.getRequest().mutate().headers((headers) -> {
			List<String> cookieHeaders = new ArrayList<>(headers.getOrEmpty(HttpHeaders.COOKIE));
			String serviceInstanceCookie = new HttpCookie(instanceIdCookieName,
					serviceInstanceResponse.getServer().getInstanceId()).toString();
			cookieHeaders.add(serviceInstanceCookie);
			headers.put(HttpHeaders.COOKIE, cookieHeaders);
		}).build()).build();
		return chain.filter(newExchange);
	}

	/**
	 * 返回过滤器执行顺序。
	 * <p>
	 * 执行顺序为 {@link ReactiveLoadBalancerClientFilter#LOAD_BALANCER_CLIENT_FILTER_ORDER} +
	 * 1， 确保在负载均衡器选择服务实例后执行。
	 * @return 过滤器执行顺序值
	 */
	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER + 1;
	}

}
