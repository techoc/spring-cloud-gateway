/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.mvc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Spring MVC @RequestMapping方法中{@link ProxyExchange}参数处理器的自动配置。
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@ConditionalOnClass({ HandlerMethodReturnValueHandler.class })
@EnableConfigurationProperties(ProxyProperties.class)
public class ProxyResponseAutoConfiguration implements WebMvcConfigurer {

	@Autowired
	private ApplicationContext context;

	/**
	 * 创建ProxyExchange参数解析器Bean。
	 *
	 * @param optional RestTemplate构建器（可选）
	 * @param proxy    代理配置属性
	 * @return ProxyExchange参数解析器
	 */
	@Bean
	@ConditionalOnMissingBean
	public ProxyExchangeArgumentResolver proxyExchangeArgumentResolver(Optional<RestTemplateBuilder> optional,
			ProxyProperties proxy) {
		RestTemplateBuilder builder = optional.orElse(new RestTemplateBuilder());
		RestTemplate template = builder.build();
		template.setErrorHandler(new NoOpResponseErrorHandler());
		template.getMessageConverters().add(new ByteArrayHttpMessageConverter() {
			@Override
			public boolean supports(Class<?> clazz) {
				return true;
			}
		});
		ProxyExchangeArgumentResolver resolver = new ProxyExchangeArgumentResolver(template);
		resolver.setHeaders(proxy.convertHeaders());
		resolver.setAutoForwardedHeaders(proxy.getAutoForward());
		resolver.setSensitive(proxy.getSensitive()); // 可以为null
		return resolver;
	}

	/**
	 * 添加参数解析器到Spring MVC配置中。
	 * @param argumentResolvers 参数解析器列表
	 */
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
		argumentResolvers.add(context.getBean(ProxyExchangeArgumentResolver.class));
	}

	/**
	 * 无操作响应错误处理器，不处理任何错误。
	 */
	private static class NoOpResponseErrorHandler extends DefaultResponseErrorHandler {

		/**
		 * 空实现，不处理任何错误响应。
		 * @param response 客户端HTTP响应
		 * @throws IOException IO异常
		 */
		@Override
		public void handleError(ClientHttpResponse response) throws IOException {
		}

	}

}
