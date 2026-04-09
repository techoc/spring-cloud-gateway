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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway 负载均衡器配置属性。
 * <p>
 * 绑定 {@code spring.cloud.gateway.loadbalancer} 前缀下的配置属性， 用于控制 Gateway 与 Spring Cloud
 * LoadBalancer 集成时的行为。
 * </p>
 *
 * @author Ryan Baxter
 */
@ConfigurationProperties("spring.cloud.gateway.loadbalancer")
public class GatewayLoadBalancerProperties {

	/**
	 * 是否在负载均衡找不到服务实例时返回 404 状态码。
	 * <p>
	 * 默认为 {@code false}，此时会返回 503 Service Unavailable。
	 * </p>
	 */
	private boolean use404;

	/**
	 * 获取是否使用 404 响应的配置。
	 * @return {@code true} 表示找不到实例时返回 404，{@code false} 返回 503
	 */
	public boolean isUse404() {
		return use404;
	}

	/**
	 * 设置是否使用 404 响应。
	 * @param use404 是否使用 404 响应
	 */
	public void setUse404(boolean use404) {
		this.use404 = use404;
	}

}
