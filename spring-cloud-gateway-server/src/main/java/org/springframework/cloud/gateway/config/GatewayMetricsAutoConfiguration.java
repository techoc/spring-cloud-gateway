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

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionMetrics;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayHttpTagsProvider;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayPathTagsProvider;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayRouteTagsProvider;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.cloud.gateway.support.tagsprovider.PropertiesTagsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".enabled", matchIfMissing = true)
@EnableConfigurationProperties(GatewayMetricsProperties.class)
@AutoConfigureBefore(HttpHandlerAutoConfiguration.class)
@AutoConfigureAfter({ MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class, MeterRegistry.class, MetricsAutoConfiguration.class })
/**
 * Gateway 指标自动配置类。
 * <p>
 * 当 Gateway 启用且存在 Micrometer 的 MeterRegistry 时，该配置类会自动注册 相关的指标提供者和过滤器，用于收集和导出 Gateway
 * 的运行指标数据。
 *
 * @author team
 */
public class GatewayMetricsAutoConfiguration {

	/**
	 * 创建 HTTP 标签提供者，用于提供与 HTTP 请求相关的指标标签。
	 * @return GatewayHttpTagsProvider 实例
	 */
	@Bean
	public GatewayHttpTagsProvider gatewayHttpTagsProvider() {
		return new GatewayHttpTagsProvider();
	}

	/**
	 * 创建路径标签提供者，用于提供与请求路径相关的指标标签。
	 * <p>
	 * 仅当配置项 {@code spring.cloud.gateway.metrics.tags.path.enabled} 为 true 时创建。
	 * @return GatewayPathTagsProvider 实例
	 */
	@Bean
	@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.tags.path.enabled")
	public GatewayPathTagsProvider gatewayPathTagsProvider() {
		return new GatewayPathTagsProvider();
	}

	/**
	 * 创建路由标签提供者，用于提供与网关路由相关的指标标签。
	 * @return GatewayRouteTagsProvider 实例
	 */
	@Bean
	public GatewayRouteTagsProvider gatewayRouteTagsProvider() {
		return new GatewayRouteTagsProvider();
	}

	/**
	 * 创建属性标签提供者，用于提供在配置文件中自定义的指标标签。
	 * @param properties Gateway 指标配置属性
	 * @return PropertiesTagsProvider 实例
	 */
	@Bean
	public PropertiesTagsProvider propertiesTagsProvider(GatewayMetricsProperties properties) {
		return new PropertiesTagsProvider(properties.getTags());
	}

	/**
	 * 创建网关指标过滤器，用于收集和记录 HTTP 请求的指标数据。
	 * <p>
	 * 该过滤器会拦截所有经过网关的请求，收集包括请求方法、状态码、URI 等相关指标。 仅当存在 MeterRegistry 且配置项
	 * {@code spring.cloud.gateway.metrics.enabled} 未设置为 false 时创建（默认为 true）。
	 * @param meterRegistry Micrometer 的指标注册表，用于注册和导出指标
	 * @param tagsProviders 标签提供者列表，用于提供额外的指标标签
	 * @param properties Gateway 指标配置属性
	 * @return GatewayMetricsFilter 实例
	 */
	@Bean
	@ConditionalOnBean(MeterRegistry.class)
	@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.enabled", matchIfMissing = true)
	// don't use @ConditionalOnEnabledGlobalFilter as the above property may
	// encompass more than just the filter
	public GatewayMetricsFilter gatewayMetricFilter(MeterRegistry meterRegistry,
			List<GatewayTagsProvider> tagsProviders, GatewayMetricsProperties properties) {
		return new GatewayMetricsFilter(meterRegistry, tagsProviders, properties.getPrefix());
	}

	/**
	 * 创建路由定义指标对象，用于收集和记录路由定义相关的指标数据。
	 * <p>
	 * 该指标对象会监控网关中注册的路由数量和状态变化。 仅当存在 MeterRegistry 且配置项
	 * {@code spring.cloud.gateway.metrics.enabled} 未设置为 false 时创建（默认为 true）。
	 * @param meterRegistry Micrometer 的指标注册表，用于注册和导出指标
	 * @param routeDefinitionLocator 路由定义定位器，用于获取当前注册的路由信息
	 * @param properties Gateway 指标配置属性
	 * @return RouteDefinitionMetrics 实例
	 */
	@Bean
	@ConditionalOnBean(MeterRegistry.class)
	@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.enabled", matchIfMissing = true)
	public RouteDefinitionMetrics routeDefinitionMetrics(MeterRegistry meterRegistry,
			RouteDefinitionLocator routeDefinitionLocator, GatewayMetricsProperties properties) {
		return new RouteDefinitionMetrics(meterRegistry, routeDefinitionLocator, properties.getPrefix());
	}

}
