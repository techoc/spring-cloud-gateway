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

package org.springframework.cloud.gateway.webflux.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Set;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

/**
 * 代理交换参数解析器，用于解析控制器方法中的 ProxyExchange 参数。 该类实现了 HandlerMethodArgumentResolver 接口，负责在
 * WebFlux 环境中 创建和配置 ProxyExchange 实例，支持请求转发和代理功能
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 */
public class ProxyExchangeArgumentResolver implements HandlerMethodArgumentResolver {

	/** WebClient 实例，用于执行 HTTP 请求。 */
	private WebClient rest;

	/** HTTP 请求头信息。 */
	private HttpHeaders headers;

	/** 需要自动转发的请求头名称集合。 */
	private Set<String> autoForwardedHeaders;

	/** 敏感的请求头名称集合，这些请求头不会被转发。 */
	private Set<String> sensitive;

	/**
	 * 构造函数，使用给定的 WebClient 构建器初始化参数解析器。
	 * @param builder WebClient 构建器，用于创建 HTTP 客户端实例
	 */
	public ProxyExchangeArgumentResolver(WebClient builder) {
		this.rest = builder;
	}

	/**
	 * 设置默认的 HTTP 请求头信息。
	 * @param headers 要设置的 HTTP 请求头
	 */
	public void setHeaders(HttpHeaders headers) {
		this.headers = headers;
	}

	/**
	 * 设置需要自动转发的请求头名称集合。 当接收到请求时，这些指定的请求头会自动转发到目标服务。
	 * @param autoForwardedHeaders 需要自动转发的请求头名称集合
	 */
	public void setAutoForwardedHeaders(Set<String> autoForwardedHeaders) {
		this.autoForwardedHeaders = autoForwardedHeaders;
	}

	/**
	 * 设置敏感的请求头名称集合。 被标记为敏感的请求头将不会自动转发到下游服务。
	 * @param sensitive 敏感请求头名称集合
	 */
	public void setSensitive(Set<String> sensitive) {
		this.sensitive = sensitive;
	}

	/**
	 * 判断是否支持解析给定的方法参数。 如果参数类型是 ProxyExchange 或其子类，则返回 true。
	 * @param parameter 方法参数信息
	 * @return 如果支持该参数类型则返回 true，否则返回 false
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return ProxyExchange.class.isAssignableFrom(parameter.getParameterType());
	}

	/**
	 * 从方法参数中提取泛型类型信息。 如果参数类型是 ParameterizedType，则提取第一个实际类型参数； 如果是 TypeVariable 或
	 * WildcardType，则返回 Object.class。
	 * @param parameter 方法参数信息
	 * @return 提取的类型信息，默认为 Object.class
	 */
	private Type type(MethodParameter parameter) {
		Type type = parameter.getGenericParameterType();
		if (type instanceof ParameterizedType) {
			ParameterizedType param = (ParameterizedType) type;
			type = param.getActualTypeArguments()[0];
		}
		if (type instanceof TypeVariable || type instanceof WildcardType) {
			type = Object.class;
		}
		return type;
	}

	/**
	 * 解析方法参数，创建并配置 ProxyExchange 实例。 该方法会根据配置的请求头、自动转发头和敏感头信息来初始化 ProxyExchange。
	 * @param parameter 方法参数信息
	 * @param bindingContext 绑定上下文，用于数据绑定和验证
	 * @param exchange 服务器 Web 交换对象，包含请求和响应信息
	 * @return 包含 ProxyExchange 实例的 Mono 对象
	 */
	@Override
	public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
			ServerWebExchange exchange) {
		ProxyExchange<?> proxy = new ProxyExchange<>(rest, exchange, bindingContext, type(parameter));
		proxy.headers(headers);
		if (this.autoForwardedHeaders.size() > 0) {
			proxy.headers(extractAutoForwardedHeaders(exchange));
		}
		if (sensitive != null) {
			proxy.sensitive(sensitive.toArray(new String[0]));
		}
		return Mono.just(proxy);
	}

	/**
	 * 从当前请求中提取需要自动转发的请求头。 遍历请求中的所有请求头，只保留在 autoForwardedHeaders 集合中指定的请求头。
	 * @param exchange 服务器 Web 交换对象，用于获取请求信息
	 * @return 包含自动转发请求头的 HttpHeaders 对象
	 */
	private HttpHeaders extractAutoForwardedHeaders(ServerWebExchange exchange) {
		HttpHeaders headers = new HttpHeaders();
		exchange.getRequest().getHeaders().forEach((header, values) -> {
			if (this.autoForwardedHeaders.contains(header)) {
				headers.addAll(header, values);
			}
		});
		return headers;
	}

}
