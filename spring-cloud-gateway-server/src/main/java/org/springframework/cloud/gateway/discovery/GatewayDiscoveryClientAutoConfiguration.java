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

package org.springframework.cloud.gateway.discovery;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REPLACEMENT_KEY;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.PATTERN_KEY;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeFilterFactoryName;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeRoutePredicateName;

/**
 * 网关服务发现客户端自动配置类。
 * <p>
 * 该类是Spring Cloud Gateway与服务发现组件集成的核心自动配置类。 当检测到项目中存在服务发现客户端（如Eureka、Consul、Nacos等）时，
 * 自动启用基于服务发现的路由定位功能，实现动态路由的自动配置。
 * <p>
 * 主要功能：
 * <ul>
 * <li>初始化默认的路由断言配置（路径匹配）</li>
 * <li>初始化默认的路由过滤器配置（路径重写）</li>
 * <li>创建DiscoveryLocatorProperties配置Bean</li>
 * <li>条件化创建DiscoveryClientRouteDefinitionLocator</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see Configuration
 * @see AutoConfigureBefore
 * @see AutoConfigureAfter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@AutoConfigureBefore(GatewayAutoConfiguration.class)
@AutoConfigureAfter(CompositeDiscoveryClientAutoConfiguration.class)
@ConditionalOnClass({ DispatcherHandler.class, CompositeDiscoveryClientAutoConfiguration.class })
@EnableConfigurationProperties
public class GatewayDiscoveryClientAutoConfiguration {

	/**
	 * 初始化默认的路由断言定义列表。
	 * <p>
	 * 默认配置包含一个路径断言，用于匹配以/serviceId/**格式的请求路径。
	 * 例如：服务ID为"user-service"时，将匹配路径"/user-service/**"的请求。
	 * <p>
	 * 注意：TODO注释中提到未来可能添加直接匹配/serviceId（不带/**）的断言。
	 * @return 默认断言定义列表
	 */
	public static List<PredicateDefinition> initPredicates() {
		ArrayList<PredicateDefinition> definitions = new ArrayList<>();
		// TODO: 添加一个直接匹配/serviceId路径的断言？

		// 添加路径断言，匹配/serviceId/**格式的URL
		PredicateDefinition predicate = new PredicateDefinition();
		predicate.setName(normalizeRoutePredicateName(PathRoutePredicateFactory.class));
		predicate.addArg(PATTERN_KEY, "'/'+serviceId+'/**'");
		definitions.add(predicate);
		return definitions;
	}

	/**
	 * 初始化默认的路由过滤器定义列表。
	 * <p>
	 * 默认配置包含一个路径重写过滤器，用于移除请求路径中的服务ID前缀。
	 * <p>
	 * 例如：
	 * <ul>
	 * <li>原始请求路径：/user-service/api/users</li>
	 * <li>重写后路径：/api/users</li>
	 * </ul>
	 * 这样后端服务接收到的路径就不再包含服务ID前缀。
	 * @return 默认过滤器定义列表
	 */
	public static List<FilterDefinition> initFilters() {
		ArrayList<FilterDefinition> definitions = new ArrayList<>();

		// 添加路径重写过滤器，默认移除/serviceId前缀
		FilterDefinition filter = new FilterDefinition();
		filter.setName(normalizeFilterFactoryName(RewritePathGatewayFilterFactory.class));
		String regex = "'/' + serviceId + '/?(?<remaining>.*)'";
		String replacement = "'/${remaining}'";
		filter.addArg(REGEXP_KEY, regex);
		filter.addArg(REPLACEMENT_KEY, replacement);
		definitions.add(filter);

		return definitions;
	}

	/**
	 * 创建并配置DiscoveryLocatorProperties Bean。
	 * <p>
	 * 该方法初始化服务发现定位器的配置属性，包括：
	 * <ul>
	 * <li>默认的路由断言配置（路径匹配）</li>
	 * <li>默认的路由过滤器配置（路径重写）</li>
	 * </ul>
	 * 用户可以通过application.yml/properties覆盖这些默认配置。
	 * @return 配置好的DiscoveryLocatorProperties实例
	 */
	@Bean
	public DiscoveryLocatorProperties discoveryLocatorProperties() {
		DiscoveryLocatorProperties properties = new DiscoveryLocatorProperties();
		properties.setPredicates(initPredicates());
		properties.setFilters(initFilters());
		return properties;
	}

	/**
	 * 响应式服务发现客户端路由定义定位器配置类。
	 * <p>
	 * 这是一个内部静态配置类，用于条件化地创建DiscoveryClientRouteDefinitionLocator Bean。
	 * 只有当spring.cloud.discovery.reactive.enabled为true（或缺失，默认为true）时才会生效。
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.cloud.discovery.reactive.enabled", matchIfMissing = true)
	public static class ReactiveDiscoveryClientRouteDefinitionLocatorConfiguration {

		/**
		 * 创建DiscoveryClientRouteDefinitionLocator Bean。
		 * <p>
		 * 该Bean负责从ReactiveDiscoveryClient获取服务实例并转换为路由定义。
		 * 只有在spring.cloud.gateway.discovery.locator.enabled=true时才会创建。
		 * <p>
		 * 创建的路由定位器会被Gateway自动加载，用于动态发现服务注册中心中的服务 并自动生成对应的路由规则。
		 * @param discoveryClient 响应式服务发现客户端
		 * @param properties 服务发现定位器配置属性
		 * @return DiscoveryClientRouteDefinitionLocator实例
		 */
		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.discovery.locator.enabled")
		public DiscoveryClientRouteDefinitionLocator discoveryClientRouteDefinitionLocator(
				ReactiveDiscoveryClient discoveryClient, DiscoveryLocatorProperties properties) {
			return new DiscoveryClientRouteDefinitionLocator(discoveryClient, properties);
		}

	}

}
