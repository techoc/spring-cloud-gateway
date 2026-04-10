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

import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Set;

/**
 * 代理交换参数解析器，用于解析 Spring WebFlux 控制器方法中的 {@link ProxyExchange} 参数。
 * <p>
 * 该类实现了 {@link HandlerMethodArgumentResolver} 接口，是 Spring WebFlux 参数解析机制的一部分。
 * 当控制器方法的参数类型为 {@link ProxyExchange} 时，Spring 会自动调用此解析器创建并配置 {@link ProxyExchange}
 * 实例，使开发者能够在控制器中方便地实现请求代理转发功能。
 * <p>
 * 主要功能：
 * <ul>
 * <li>识别并支持 {@link ProxyExchange} 类型的方法参数</li>
 * <li>从方法参数中提取泛型类型信息</li>
 * <li>创建并配置 {@link ProxyExchange} 实例</li>
 * <li>应用配置的请求头、自动转发头和敏感头设置</li>
 * </ul>
 * <p>
 * 配置属性：
 * <ul>
 * <li>headers - 固定请求头，添加到所有下游请求</li>
 * <li>autoForwardedHeaders - 自动转发的请求头名称集合</li>
 * <li>sensitive - 敏感请求头，不会被转发到下游</li>
 * </ul>
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 * @see HandlerMethodArgumentResolver
 * @see ProxyExchange
 */
public class ProxyExchangeArgumentResolver implements HandlerMethodArgumentResolver {

	/**
	 * WebClient 实例，用于执行 HTTP 请求。
	 * <p>
	 * 该实例会被传递给创建的 {@link ProxyExchange} 对象，用于实际的后端服务调用。
	 */
	private WebClient rest;

	/**
	 * 固定 HTTP 请求头信息。
	 * <p>
	 * 这些请求头会被添加到所有通过 {@link ProxyExchange} 转发的请求中。
	 */
	private HttpHeaders headers;

	/**
	 * 需要自动转发的请求头名称集合。
	 * <p>
	 * 当接收到客户端请求时，这些指定的请求头会自动从原始请求中提取并转发到下游服务。
	 */
	private Set<String> autoForwardedHeaders;

	/**
	 * 敏感的请求头名称集合。
	 * <p>
	 * 被标记为敏感的请求头（如 Authorization、Cookie 等）出于安全考虑不会被转发到下游服务。 如果未设置，则使用
	 * {@link ProxyExchange#DEFAULT_SENSITIVE} 默认值。
	 */
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
	 * <p>
	 * 这些请求头会被添加到所有通过 {@link ProxyExchange} 转发的请求中。
	 * @param headers 要设置的 HTTP 请求头
	 */
	public void setHeaders(HttpHeaders headers) {
		this.headers = headers;
	}

	/**
	 * 设置需要自动转发的请求头名称集合。
	 * <p>
	 * 当接收到请求时，这些指定的请求头会自动从原始请求中提取并转发到目标服务。
	 * @param autoForwardedHeaders 需要自动转发的请求头名称集合
	 */
	public void setAutoForwardedHeaders(Set<String> autoForwardedHeaders) {
		this.autoForwardedHeaders = autoForwardedHeaders;
	}

	/**
	 * 设置敏感的请求头名称集合。
	 * <p>
	 * 被标记为敏感的请求头将不会自动转发到下游服务，用于保护安全相关信息。
	 * @param sensitive 敏感请求头名称集合
	 */
	public void setSensitive(Set<String> sensitive) {
		this.sensitive = sensitive;
	}

	/**
	 * 判断是否支持解析给定的方法参数。
	 * <p>
	 * 如果参数类型是 {@link ProxyExchange} 或其子类，则返回 true，表示该解析器可以处理此参数。
	 * @param parameter 方法参数信息，包含参数类型、泛型信息等元数据
	 * @return 如果支持该参数类型则返回 true，否则返回 false
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return ProxyExchange.class.isAssignableFrom(parameter.getParameterType());
	}

	/**
	 * 从方法参数中提取泛型类型信息。
	 * <p>
	 * 该方法解析 {@link ProxyExchange}{@code <T>} 中的类型参数 T，用于确定响应体的目标类型。 如果参数类型是
	 * {@link ParameterizedType}，则提取第一个实际类型参数； 如果是 {@link TypeVariable} 或
	 * {@link WildcardType}（如 {@code ProxyExchange<?>}）， 则返回 {@link Object}.class 作为默认类型。
	 * @param parameter 方法参数信息
	 * @return 提取的类型信息，默认为 {@link Object}.class
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
	 * 解析方法参数，创建并配置 {@link ProxyExchange} 实例。
	 * <p>
	 * 该方法执行以下步骤：
	 * <ol>
	 * <li>创建 {@link ProxyExchange} 实例，传入 WebClient、请求交换对象、绑定上下文和响应类型</li>
	 * <li>应用配置的固定请求头</li>
	 * <li>如果配置了自动转发头，从当前请求中提取并应用</li>
	 * <li>应用敏感头配置</li>
	 * </ol>
	 * @param parameter 方法参数信息，包含泛型类型
	 * @param bindingContext 绑定上下文，用于数据绑定和验证
	 * @param exchange 服务器 Web 交换对象，包含当前 HTTP 请求和响应信息
	 * @return 包含配置好的 {@link ProxyExchange} 实例的 Mono 对象
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
	 * 从当前请求中提取需要自动转发的请求头。
	 * <p>
	 * 遍历请求中的所有请求头，只保留在 {@link #autoForwardedHeaders} 集合中指定的请求头。 这允许选择性地将某些请求头（如
	 * X-Request-ID、X-User-ID 等）从客户端请求传递到后端服务。
	 * @param exchange 服务器 Web 交换对象，用于获取请求信息
	 * @return 包含自动转发请求头的 {@link HttpHeaders} 对象
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
