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

import java.util.Optional;

/**
 * 代理响应自动配置类。
 * <p>
 * 用于在 Spring WebFlux 的 <code>@RequestMapping</code> 方法中自动配置 {@link ProxyExchange}
 * 参数处理器。该配置类会在检测到 Web 应用环境时自动启用， 并注册必要的 Bean 以支持 {@link ProxyExchange} 参数解析功能。
 * <p>
 * 自动配置条件：
 * <ul>
 * <li>应用必须是 Web 应用（{@link ConditionalOnWebApplication}）</li>
 * <li>类路径中必须存在 {@link HandlerMethodReturnValueHandler} 类（{@link ConditionalOnClass}）</li>
 * </ul>
 * <p>
 * 该配置类会：
 * <ol>
 * <li>创建并注册 {@link ProxyExchangeArgumentResolver} Bean</li>
 * <li>将参数解析器添加到 WebFlux 的参数解析器链中</li>
 * <li>启用 {@link ProxyProperties} 配置属性绑定</li>
 * </ol>
 * <p>
 * 使用示例： <pre>
 * &#64;RestController
 * public class ProxyController {
 *     &#64;GetMapping("/proxy/{id}")
 *     public Mono&lt;ResponseEntity&lt;?&gt;&gt; proxy(@PathVariable Integer id, ProxyExchange&lt;?&gt; proxy) {
 *         return proxy.uri("http://backend-service/" + id).get();
 *     }
 * }
 * </pre>
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 * @see Configuration
 * @see ConditionalOnWebApplication
 * @see ConditionalOnClass
 * @see EnableConfigurationProperties
 * @see WebFluxConfigurer
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@ConditionalOnClass({ HandlerMethodReturnValueHandler.class })
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyResponseAutoConfiguration implements WebFluxConfigurer {

	/**
	 * 应用上下文，用于获取已注册的 Bean。
	 * <p>
	 * 主要用于在 {@link #configureArgumentResolvers} 方法中获取
	 * {@link ProxyExchangeArgumentResolver} 实例并添加到配置器中。
	 */
	@Autowired
	private ApplicationContext context;

	/**
	 * 创建代理交换参数解析器 Bean。
	 * <p>
	 * 如果容器中不存在 {@link WebClient.Builder}，则使用默认的构建器创建 WebClient。 创建的解析器会应用
	 * {@link ProxyProperties} 中配置的请求头、自动转发头和敏感头设置。
	 * <p>
	 * 该 Bean 的创建条件是容器中不存在 {@link ProxyExchangeArgumentResolver} 类型的 Bean
	 * （{@link ConditionalOnMissingBean}）。
	 * @param optional 可选的 WebClient 构建器，如果容器中不存在则为空
	 * @param proxy 代理配置属性，包含请求头、自动转发头和敏感头配置
	 * @return 配置好的 {@link ProxyExchangeArgumentResolver} 实例
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
		resolver.setSensitive(proxy.getSensitive()); // 可以为 null
		return resolver;
	}

	/**
	 * 配置自定义参数解析器。
	 * <p>
	 * 将 {@link ProxyExchangeArgumentResolver} 添加到 WebFlux 的参数解析器列表中， 使其能够处理控制器方法中的
	 * {@link ProxyExchange} 参数。
	 * <p>
	 * 这是 {@link WebFluxConfigurer} 接口的方法实现，Spring 会在初始化 WebFlux 配置时调用。
	 * @param configurer 参数解析器配置器，用于添加自定义参数解析器
	 */
	@Override
	public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
		WebFluxConfigurer.super.configureArgumentResolvers(configurer);
		configurer.addCustomResolver(context.getBean(ProxyExchangeArgumentResolver.class));
	}

}
