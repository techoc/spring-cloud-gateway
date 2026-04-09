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

package org.springframework.cloud.gateway.route.builder;

import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.cloud.gateway.route.Route;

/**
 * 路由 URI 配置规格类，用于设置路由的目标 URI。
 * <p>
 * 这是路由配置链的最终阶段，在此设置请求匹配后转发的目标地址。 支持两种形式的 URI：
 * <ul>
 * <li>字符串形式：{@code "http://example.com"} 或 {@code "lb://service-name"}</li>
 * <li>URI 对象形式：{@code URI.create("http://example.com")}</li>
 * </ul>
 * <p>
 * 同时支持自定义路由构建器和元数据配置。
 *
 * @author Spencer Gibb
 */
public class UriSpec {

	/** 异步路由构建器 */
	final Route.AsyncBuilder routeBuilder;

	/** 父构建器 */
	final RouteLocatorBuilder.Builder builder;

	/**
	 * 构造方法。
	 * @param routeBuilder 异步路由构建器
	 * @param builder 父构建器
	 */
	UriSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		this.routeBuilder = routeBuilder;
		this.builder = builder;
	}

	/**
	 * 自定义路由构建器。
	 * <p>
	 * 允许直接操作底层的 {@link Route.AsyncBuilder} 进行高级配置。
	 * @param routeConsumer 路由构建器消费者
	 * @return 当前规格对象（链式调用）
	 */
	public UriSpec customize(Consumer<Route.AsyncBuilder> routeConsumer) {
		routeConsumer.accept(this.routeBuilder);
		return this;
	}

	/**
	 * 替换路由的全部元数据。
	 * <p>
	 * 完全替换已有的元数据，而非追加。
	 * @param metadata 新的元数据 Map
	 * @return 当前规格对象（链式调用）
	 */
	public UriSpec replaceMetadata(Map<String, Object> metadata) {
		this.routeBuilder.replaceMetadata(metadata);
		return this;
	}

	/**
	 * 批量追加元数据。
	 * @param metadata 要追加的元数据 Map
	 * @return 当前规格对象（链式调用）
	 */
	public UriSpec metadata(Map<String, Object> metadata) {
		this.routeBuilder.metadata(metadata);
		return this;
	}

	/**
	 * 追加单条元数据。
	 * @param key 元数据键
	 * @param value 元数据值
	 * @return 当前规格对象（链式调用）
	 */
	public UriSpec metadata(String key, Object value) {
		this.routeBuilder.metadata(key, value);
		return this;
	}

	/**
	 * 设置路由的目标 URI（字符串形式）。
	 * <p>
	 * 支持普通 HTTP URI 和负载均衡 URI（{@code lb://service-name}）。
	 * @param uri 目标 URI 字符串
	 * @return 可构建对象，调用 {@link Buildable#build()} 完成路由构建
	 */
	public Buildable<Route> uri(String uri) {
		return this.routeBuilder.uri(uri);
	}

	/**
	 * 设置路由的目标 URI（URI 对象形式）。
	 * @param uri 目标 URI 对象
	 * @return 可构建对象，调用 {@link Buildable#build()} 完成路由构建
	 */
	public Buildable<Route> uri(URI uri) {
		return this.routeBuilder.uri(uri);
	}

	/**
	 * 从 Spring 容器中获取指定类型的 Bean。
	 * <p>
	 * 用于获取过滤器工厂、断言工厂等组件。
	 * @param type Bean 类型
	 * @param <T> Bean 类型参数
	 * @return 指定类型的 Bean 实例
	 */
	<T> T getBean(Class<T> type) {
		return this.builder.getContext().getBean(type);
	}

}
