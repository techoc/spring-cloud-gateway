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

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * SimpleUrlHandlerMapping 全局 CORS 自动配置类。
 * <p>
 * 此配置用于处理 CORS 预检请求（PreFlight Request）。通过在此处添加全局配置， 无需修改现有的路由谓词即可允许 "OPTIONS" HTTP
 * 方法，从而支持跨域请求。
 * <p>
 * 当类路径中存在 {@link SimpleUrlHandlerMapping} 且配置项
 * {@code spring.cloud.gateway.globalcors.add-to-simple-url-handler-mapping} 为 true 时生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(SimpleUrlHandlerMapping.class)
@ConditionalOnProperty(name = "spring.cloud.gateway.globalcors.add-to-simple-url-handler-mapping",
		matchIfMissing = false)
public class SimpleUrlHandlerMappingGlobalCorsAutoConfiguration {

	/**
	 * 全局 CORS 属性配置，包含跨域请求的全局配置信息。
	 */
	@Autowired
	private GlobalCorsProperties globalCorsProperties;

	/**
	 * Spring WebFlux 的简单 URL 处理器映射，用于处理静态资源请求。
	 */
	@Autowired
	private SimpleUrlHandlerMapping simpleUrlHandlerMapping;

	/**
	 * 配置 CORS 跨域配置。
	 * <p>
	 * 在容器初始化完成后，将全局 CORS 配置应用到 SimpleUrlHandlerMapping 中， 使其能够处理所有路径的跨域预检请求。
	 */
	@PostConstruct
	void config() {
		simpleUrlHandlerMapping.setCorsConfigurations(globalCorsProperties.getCorsConfigurations());
	}

}
