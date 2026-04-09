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

import java.util.Collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Gateway 环境属性后置处理器。
 *
 * <p>
 * 该类实现了 Spring Boot 的 {@link EnvironmentPostProcessor} 接口， 用于在应用上下文初始化之前，对 Spring
 * Environment 进行后置处理。
 *
 * <p>
 * 主要功能：将 {@code spring.webflux.hiddenmethod.filter.enabled} 属性设置为 {@code false}，
 * 以禁用隐藏方法过滤器。这是 Spring Cloud Gateway 运行所需的必要配置， 因为 Gateway 使用不同的机制处理 HTTP 方法。
 *
 * @author Ryan Baxter
 */
public class GatewayEnvironmentPostProcessor implements EnvironmentPostProcessor {

	/**
	 * 后置处理 Spring Environment。
	 *
	 * <p>
	 * 该方法会在应用上下文初始化之前被调用，向环境属性源中添加 Gateway 所需的默认配置。 通过 {@code addFirst} 方法将属性源置于最高优先级，确保
	 * Gateway 的配置优先于其他来源。
	 * @param env 可配置的 Spring 环境对象，用于访问和管理属性源
	 * @param application Spring 应用实例，用于获取上下文信息
	 */
	@Override
	public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
		env.getPropertySources().addFirst(new MapPropertySource("gateway-properties",
				Collections.singletonMap("spring.webflux.hiddenmethod.filter.enabled", "false")));
	}

}
