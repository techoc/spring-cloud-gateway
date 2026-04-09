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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;

/**
 * Gateway 全局 CORS（跨域资源共享）配置属性。
 * <p>
 * 绑定 {@code spring.cloud.gateway.globalcors} 前缀下的配置属性， 用于配置全局的跨域请求策略。具体参考
 * {@link RoutePredicateHandlerMapping}。
 * </p>
 *
 * @see RoutePredicateHandlerMapping
 */
@ConfigurationProperties("spring.cloud.gateway.globalcors")
public class GlobalCorsProperties {

	/** CORS 配置映射，键为 URL 路径模式，值为对应的 CORS 配置 */
	private final Map<String, CorsConfiguration> corsConfigurations = new LinkedHashMap<>();

	/**
	 * 获取 CORS 配置映射。
	 * @return 路径模式到 CORS 配置的映射
	 */
	public Map<String, CorsConfiguration> getCorsConfigurations() {
		return corsConfigurations;
	}

}
