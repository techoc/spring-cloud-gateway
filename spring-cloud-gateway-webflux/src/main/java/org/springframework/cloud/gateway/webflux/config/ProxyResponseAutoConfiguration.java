/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.gateway.webflux.config;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

/**
 * 代理响应自动配置类。
 * 用于在 Spring WebFlux 的 <code>@RequestMapping</code> 方法中自动配置 {@link ProxyExchange} 参数处理器。
 * 该配置类会在检测到 Web 应用环境时自动启用，并注册必要的 Bean。
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@ConditionalOnClass({ HandlerMethodReturnValueHandler.class })
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyResponseAutoConfiguration implements WebFluxConfigurer {

	/** 应用上下文，用于获取已注册的 Bean。 */
	@Autowired
	private ApplicationContext context;

	/**
	 * 创建代理交换参数解析器 Bean。
	 * 如果容器中不存在 WebClient.Builder，则使用默认的构建器。
	 * @param optional 可选的 WebClient 构建器
	 * @param proxy 代理配置属性
	 * @return 配置好的代理交换参数解析器实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public ProxyExchangeArgumentResolver proxyExchangeArgumentResolver(Optional<WebClient.Builder> optional,
			ProxyProperties proxy) {
		WebClient.Builder builder = optional.orElse(WebClient.builder());
		WebClient template = builder.build();
		ProxyExchangeArgumentResolver resolver = new ProxyExchangeArgumentResolver(template);
		resolver.setHeaders(proxy.convertHeaders());
		resolver.setAutoForwardedHeaders(proxy.getAutoForward());
		resolver.setSensitive(proxy.getSensitive()); // can be null
		return resolver;
	}

	/**
	 * 配置自定义参数解析器。
	 * 将代理交换参数解析器添加到 WebFlux 的参数解析器列表中，
	 * 使其能够处理控制器方法中的 ProxyExchange 参数。
	 * @param configurer 参数解析器配置器
	 */
	@Override
	public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
		WebFluxConfigurer.super.configureArgumentResolvers(configurer);
		configurer.addCustomResolver(context.getBean(ProxyExchangeArgumentResolver.class));
	}

}
