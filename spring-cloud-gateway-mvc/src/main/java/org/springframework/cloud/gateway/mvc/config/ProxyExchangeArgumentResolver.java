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

import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * ProxyExchange参数解析器，用于解析@RequestMapping方法中的ProxyExchange参数。
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 */
public class ProxyExchangeArgumentResolver implements HandlerMethodArgumentResolver {

	private final RestTemplate rest;

	/**
	 * 固定头部信息。
	 */
	private HttpHeaders headers;

	/**
	 * 自动转发的头部名称集合。
	 */
	private Set<String> autoForwardedHeaders;

	/**
	 * 敏感头部名称集合。
	 */
	private Set<String> sensitive;

	/**
	 * 构造ProxyExchange参数解析器。
	 * @param builder RestTemplate构建器
	 */
	public ProxyExchangeArgumentResolver(RestTemplate builder) {
		this.rest = builder;
	}

	/**
	 * 设置固定头部信息。
	 * @param headers HTTP头部信息
	 */
	public void setHeaders(HttpHeaders headers) {
		this.headers = headers;
	}

	/**
	 * 设置自动转发的头部名称集合。
	 * @param autoForwardedHeaders 自动转发的头部名称集合
	 */
	public void setAutoForwardedHeaders(Set<String> autoForwardedHeaders) {
		this.autoForwardedHeaders = autoForwardedHeaders == null ? null
				: autoForwardedHeaders.stream().map(String::toLowerCase).collect(toSet());
	}

	/**
	 * 设置敏感头部名称集合。
	 * @param sensitive 敏感头部名称集合
	 */
	public void setSensitive(Set<String> sensitive) {
		this.sensitive = sensitive;
	}

	/**
	 * 判断是否支持该参数类型。
	 * @param parameter 方法参数
	 * @return 如果参数类型是ProxyExchange则返回true
	 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return ProxyExchange.class.isAssignableFrom(parameter.getParameterType());
	}

	/**
	 * 解析方法参数，创建并配置ProxyExchange对象。
	 * @param parameter 方法参数
	 * @param mavContainer ModelAndView容器
	 * @param webRequest Web请求
	 * @param binderFactory 数据绑定工厂
	 * @return 配置好的ProxyExchange对象
	 * @throws Exception 异常
	 */
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
		ProxyExchange<?> proxy = new ProxyExchange<>(rest, webRequest, mavContainer, binderFactory, type(parameter));
		configureHeaders(proxy);
		configureAutoForwardedHeaders(proxy, webRequest);
		configureSensitive(proxy);
		return proxy;
	}

	/**
	 * 获取参数的泛型类型。
	 * @param parameter 方法参数
	 * @return 泛型类型
	 */
	private Type type(MethodParameter parameter) {
		Type type = parameter.getGenericParameterType();
		if (type instanceof ParameterizedType) {
			ParameterizedType param = (ParameterizedType) type;
			type = param.getActualTypeArguments()[0];
		}
		return type;
	}

	/**
	 * 从Web请求中提取需要自动转发的头部信息。
	 * @param webRequest Web请求
	 * @return 需要自动转发的HTTP头部信息
	 */
	private HttpHeaders extractAutoForwardedHeaders(NativeWebRequest webRequest) {
		HttpServletRequest nativeRequest = webRequest.getNativeRequest(HttpServletRequest.class);
		Enumeration<String> headerNames = nativeRequest.getHeaderNames();
		HttpHeaders headers = new HttpHeaders();
		while (headerNames.hasMoreElements()) {
			String header = headerNames.nextElement();
			if (this.autoForwardedHeaders.contains(header.toLowerCase())) {
				headers.addAll(header, Collections.list(nativeRequest.getHeaders(header)));
			}
		}
		return headers;
	}

	/**
	 * 配置固定头部信息到ProxyExchange。
	 * @param proxy ProxyExchange对象
	 */
	private void configureHeaders(final ProxyExchange<?> proxy) {
		if (headers != null) {
			proxy.headers(headers);
		}
	}

	/**
	 * 配置自动转发头部信息到ProxyExchange。
	 * @param proxy ProxyExchange对象
	 * @param webRequest Web请求
	 */
	private void configureAutoForwardedHeaders(final ProxyExchange<?> proxy, final NativeWebRequest webRequest) {
		if ((autoForwardedHeaders != null) && (autoForwardedHeaders.size() > 0)) {
			proxy.headers(extractAutoForwardedHeaders(webRequest));
		}
	}

	/**
	 * 配置敏感头部信息到ProxyExchange。
	 * @param proxy ProxyExchange对象
	 */
	private void configureSensitive(final ProxyExchange<?> proxy) {
		if (sensitive != null) {
			proxy.sensitive(sensitive.toArray(new String[0]));
		}
	}

}
