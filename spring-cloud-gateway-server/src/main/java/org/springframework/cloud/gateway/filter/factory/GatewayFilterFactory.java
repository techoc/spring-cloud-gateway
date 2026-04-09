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

package org.springframework.cloud.gateway.filter.factory;

import java.util.function.Consumer;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.support.Configurable;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.cloud.gateway.support.ShortcutConfigurable;

/**
 * 网关过滤器工厂接口。
 * <p>
 * 该接口是创建 {@link GatewayFilter} 实例的工厂契约。它继承自 {@link ShortcutConfigurable} 和
 * {@link Configurable}，支持快捷配置方式和泛型配置类。
 * <p>
 * 主要功能：
 * <ul>
 * <li>通过配置对象创建过滤器实例；</li>
 * <li>支持通过 Consumer 方式快速创建过滤器（用于 Java DSL）；</li>
 * <li>支持路由 ID 注入（实现 {@link HasRouteId} 接口时）；</li>
 * <li>自动从类名推断过滤器名称。</li>
 * </ul>
 *
 * @param <C> 配置类类型
 * @author Spencer Gibb
 */
@FunctionalInterface
public interface GatewayFilterFactory<C> extends ShortcutConfigurable, Configurable<C> {

	/**
	 * 配置类中名称字段的键常量。
	 */
	String NAME_KEY = "name";

	/**
	 * 配置类中值字段的键常量。
	 */
	String VALUE_KEY = "value";

	/**
	 * 通过 Consumer 方式创建过滤器（带路由 ID）。
	 * <p>
	 * 适用于 Java DSL 编程方式，允许在 lambda 表达式中配置过滤器参数。
	 * @param routeId 路由 ID
	 * @param consumer 配置对象的消费者，用于设置参数
	 * @return 创建的 {@link GatewayFilter} 实例
	 */
	// 对 Java DSL 有用
	default GatewayFilter apply(String routeId, Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		return apply(routeId, config);
	}

	/**
	 * 通过 Consumer 方式创建过滤器（不带路由 ID）。
	 * @param consumer 配置对象的消费者
	 * @return 创建的 {@link GatewayFilter} 实例
	 */
	default GatewayFilter apply(Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		return apply(config);
	}

	/**
	 * 获取配置类的类型。
	 * @return 配置类的 Class
	 * @throws UnsupportedOperationException 若未实现此方法
	 */
	default Class<C> getConfigClass() {
		throw new UnsupportedOperationException("getConfigClass() not implemented");
	}

	/**
	 * 创建新的配置对象。
	 * @return 配置类实例
	 * @throws UnsupportedOperationException 若未实现此方法
	 */
	@Override
	default C newConfig() {
		throw new UnsupportedOperationException("newConfig() not implemented");
	}

	/**
	 * 通过配置对象创建过滤器实例。
	 * <p>
	 * 这是核心方法，所有具体过滤器工厂必须实现此方法。
	 * @param config 配置对象
	 * @return 创建的 {@link GatewayFilter} 实例
	 */
	GatewayFilter apply(C config);

	/**
	 * 通过配置对象创建过滤器实例（带路由 ID）。
	 * <p>
	 * 若配置对象实现了 {@link HasRouteId} 接口，则自动设置路由 ID。
	 * @param routeId 路由 ID
	 * @param config 配置对象
	 * @return 创建的 {@link GatewayFilter} 实例
	 */
	default GatewayFilter apply(String routeId, C config) {
		if (config instanceof HasRouteId) {
			HasRouteId hasRouteId = (HasRouteId) config;
			hasRouteId.setRouteId(routeId);
		}
		return apply(config);
	}

	/**
	 * 获取过滤器的名称。
	 * <p>
	 * 默认实现从类名推断，例如 {@code AddRequestHeaderGatewayFilterFactory} 的名称为
	 * {@code AddRequestHeader}。
	 * @return 过滤器名称
	 */
	default String name() {
		// TODO: 处理代理情况
		return NameUtils.normalizeFilterFactoryName(getClass());
	}

}
