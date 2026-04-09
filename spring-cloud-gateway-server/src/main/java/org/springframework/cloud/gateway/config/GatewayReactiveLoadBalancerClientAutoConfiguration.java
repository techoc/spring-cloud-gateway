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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledGlobalFilter;
import org.springframework.cloud.gateway.filter.LoadBalancerServiceInstanceCookieFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * Gateway响应式负载均衡客户端自动配置类。
 * <p>
 * 负责自动配置 {@link ReactiveLoadBalancerClientFilter}， 使网关具备负载均衡能力，能够将请求分发到多个服务实例。
 *
 * @author Spencer Gibb
 * @author Olga Maciaszek-Sharma
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ ReactiveLoadBalancer.class, LoadBalancerAutoConfiguration.class, DispatcherHandler.class })
@AutoConfigureAfter(LoadBalancerAutoConfiguration.class)
@EnableConfigurationProperties(GatewayLoadBalancerProperties.class)
public class GatewayReactiveLoadBalancerClientAutoConfiguration {

	/**
	 * 创建并配置负载均衡客户端过滤器。
	 * <p>
	 * 该过滤器负责在网关转发请求时进行负载均衡， 根据服务名称选择合适的服务实例。
	 * @param clientFactory 负载均衡客户端工厂
	 * @param properties 负载均衡配置属性
	 * @return 响应式负载均衡客户端过滤器
	 */
	@Bean
	@ConditionalOnBean(LoadBalancerClientFactory.class)
	@ConditionalOnMissingBean(ReactiveLoadBalancerClientFilter.class)
	@ConditionalOnEnabledGlobalFilter
	public ReactiveLoadBalancerClientFilter gatewayLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory,
			GatewayLoadBalancerProperties properties) {
		return new ReactiveLoadBalancerClientFilter(clientFactory, properties);
	}

	/**
	 * 创建并配置负载均衡服务实例Cookie过滤器。
	 * <p>
	 * 该过滤器用于在Cookie中保存选中的服务实例信息， 实现会话粘滞（sticky session）功能。
	 * @param loadBalancerClientFactory 负载均衡客户端工厂
	 * @return 负载均衡服务实例Cookie过滤器
	 */
	@Bean
	@ConditionalOnBean({ ReactiveLoadBalancerClientFilter.class, LoadBalancerClientFactory.class })
	@ConditionalOnMissingBean
	@ConditionalOnEnabledGlobalFilter
	public LoadBalancerServiceInstanceCookieFilter loadBalancerServiceInstanceCookieFilter(
			LoadBalancerClientFactory loadBalancerClientFactory) {
		return new LoadBalancerServiceInstanceCookieFilter(loadBalancerClientFactory);
	}

}
