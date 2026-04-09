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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.support.MvcFoundOnClasspathException;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 类路径警告自动配置类。
 *
 * <p>
 * 此类用于在 Spring Cloud Gateway 的类路径配置不正确时，检测并向用户发出警告信息。 主要检测以下两种错误配置场景：
 * <ul>
 * <li>类路径中存在 Spring MVC（Servlet）但缺少 Spring WebFlux，Gateway 需要 WebFlux 运行</li>
 * <li>类路径中完全缺少 Spring WebFlux 依赖</li>
 * </ul>
 *
 * <p>
 * 该自动配置类会在条件满足时生效，通过抛出异常或输出警告日志的方式提示用户配置问题。
 *
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(GatewayAutoConfiguration.class)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
public class GatewayClassPathWarningAutoConfiguration {

	/** Apache Commons Log 日志实例，用于输出警告信息。 */
	private static final Log log = LogFactory.getLog(GatewayClassPathWarningAutoConfiguration.class);

	/** 用于格式化警告信息边框的字符串常量。 */
	private static final String BORDER = "\n\n**********************************************************\n\n";

	/**
	 * 当类路径中存在 Spring MVC 但缺少 WebFlux 时的内部配置类。
	 *
	 * <p>
	 * Spring Cloud Gateway 需要基于 Spring WebFlux 的响应式编程模型运行。 如果在类路径中检测到传统的 Spring
	 * MVC（Servlet）DispatcherServlet， 则会抛出 {@link MvcFoundOnClasspathException} 异常，阻止应用启动，
	 * 以避免运行时出现不可预期的行为。
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
	protected static class SpringMvcFoundOnClasspathConfiguration {

		/**
		 * 默认构造函数，直接抛出异常阻止应用启动。
		 * @throws MvcFoundOnClasspathException 当类路径中同时存在 Spring MVC 时抛出
		 */
		public SpringMvcFoundOnClasspathConfiguration() {
			throw new MvcFoundOnClasspathException();
		}

	}

	/**
	 * 当类路径中缺少 Spring WebFlux 时的内部配置类。
	 *
	 * <p>
	 * Spring Cloud Gateway 依赖于 Spring WebFlux 作为其响应式 Web 框架。 如果在类路径中未检测到
	 * {@code org.springframework.web.reactive.DispatcherHandler}， 则会在构造函数中输出警告日志，提示用户添加
	 * spring-boot-starter-webflux 依赖。
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("org.springframework.web.reactive.DispatcherHandler")
	protected static class WebfluxMissingFromClasspathConfiguration {

		/**
		 * 默认构造函数，输出缺少 WebFlux 依赖的警告信息。
		 *
		 * <p>
		 * 该构造函数会在配置类实例化时自动执行，向日志输出警告信息， 建议用户添加 spring-boot-starter-webflux 依赖。
		 */
		public WebfluxMissingFromClasspathConfiguration() {
			log.warn(BORDER + "Spring Webflux is missing from the classpath, "
					+ "which is required for Spring Cloud Gateway at this time. "
					+ "Please add spring-boot-starter-webflux dependency." + BORDER);
		}

	}

}
