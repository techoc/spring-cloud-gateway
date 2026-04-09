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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.style.ToStringCreator;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway网关核心配置属性类。
 * <p>
 * 用于绑定 spring.cloud.gateway.* 开头的配置属性， 包含路由定义、默认过滤器、流式媒体类型等配置。
 *
 * @author Spencer Gibb
 */
@ConfigurationProperties(GatewayProperties.PREFIX)
@Validated
public class GatewayProperties {

	/**
	 * 配置属性前缀：spring.cloud.gateway
	 */
	public static final String PREFIX = "spring.cloud.gateway";

	private final Log logger = LogFactory.getLog(getClass());

	/**
	 * 路由定义列表。
	 * <p>
	 * 配置网关的所有路由规则，每个路由定义包含路由ID、目标URI、谓词和过滤器等信息。
	 */
	@NotNull
	@Valid
	private List<RouteDefinition> routes = new ArrayList<>();

	/**
	 * 默认过滤器定义列表。
	 * <p>
	 * 应用于所有路由的全局过滤器配置。
	 */
	private List<FilterDefinition> defaultFilters = new ArrayList<>();

	/**
	 * 流式媒体类型列表。
	 * <p>
	 * 定义哪些媒体类型应被视为流式响应，用于特定的网关处理逻辑。 默认包含：SSE、流式JSON、gRPC等类型。
	 */
	private List<MediaType> streamingMediaTypes = Arrays.asList(MediaType.TEXT_EVENT_STREAM,
			MediaType.APPLICATION_STREAM_JSON, new MediaType("application", "grpc"),
			new MediaType("application", "grpc+protobuf"), new MediaType("application", "grpc+json"));

	/**
	 * 是否在路由定义错误时抛出异常。
	 * <p>
	 * 默认为 true，表示遇到路由定义错误时抛出异常； 设为 false 时，仅记录警告日志而不抛出异常。
	 */
	private boolean failOnRouteDefinitionError = true;

	/**
	 * 可信代理的正则表达式。
	 * <p>
	 * 定义出现在 Forwarded 或 X-Forwarded 请求头中的可信代理服务器， 用于安全地解析客户端真实IP等信息。
	 */
	private String trustedProxies;

	/**
	 * 获取路由定义列表。
	 * @return 路由定义列表
	 */
	public List<RouteDefinition> getRoutes() {
		return routes;
	}

	/**
	 * 设置路由定义列表。
	 * @param routes 路由定义列表
	 */
	public void setRoutes(List<RouteDefinition> routes) {
		this.routes = routes;
		if (routes != null && routes.size() > 0 && logger.isDebugEnabled()) {
			logger.debug("Routes supplied from Gateway Properties: " + routes);
		}
	}

	/**
	 * 获取默认过滤器定义列表。
	 * @return 默认过滤器定义列表
	 */
	public List<FilterDefinition> getDefaultFilters() {
		return defaultFilters;
	}

	/**
	 * 设置默认过滤器定义列表。
	 * @param defaultFilters 默认过滤器定义列表
	 */
	public void setDefaultFilters(List<FilterDefinition> defaultFilters) {
		this.defaultFilters = defaultFilters;
	}

	/**
	 * 获取流式媒体类型列表。
	 * @return 流式媒体类型列表
	 */
	public List<MediaType> getStreamingMediaTypes() {
		return streamingMediaTypes;
	}

	/**
	 * 设置流式媒体类型列表。
	 * @param streamingMediaTypes 流式媒体类型列表
	 */
	public void setStreamingMediaTypes(List<MediaType> streamingMediaTypes) {
		this.streamingMediaTypes = streamingMediaTypes;
	}

	/**
	 * 判断是否应在路由定义错误时抛出异常。
	 * @return true表示遇到路由定义错误时抛出异常
	 */
	public boolean isFailOnRouteDefinitionError() {
		return failOnRouteDefinitionError;
	}

	/**
	 * 设置是否在路由定义错误时抛出异常。
	 * @param failOnRouteDefinitionError true表示遇到错误时抛出异常
	 */
	public void setFailOnRouteDefinitionError(boolean failOnRouteDefinitionError) {
		this.failOnRouteDefinitionError = failOnRouteDefinitionError;
	}

	/**
	 * 获取可信代理的正则表达式。
	 * @return 可信代理的正则表达式
	 */
	public String getTrustedProxies() {
		return trustedProxies;
	}

	/**
	 * 设置可信代理的正则表达式。
	 * @param trustedProxies 可信代理的正则表达式
	 */
	public void setTrustedProxies(String trustedProxies) {
		this.trustedProxies = trustedProxies;
	}

	/**
	 * 返回配置属性的字符串表示。
	 * @return 包含所有配置属性的字符串
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("routes", routes).append("defaultFilters", defaultFilters)
				.append("streamingMediaTypes", streamingMediaTypes)
				.append("failOnRouteDefinitionError", failOnRouteDefinitionError)
				.append("trustedProxies", trustedProxies).toString();

	}

}
