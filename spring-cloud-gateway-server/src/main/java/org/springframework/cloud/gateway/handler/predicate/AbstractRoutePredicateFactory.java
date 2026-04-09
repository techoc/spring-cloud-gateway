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

package org.springframework.cloud.gateway.handler.predicate;

import org.springframework.cloud.gateway.support.AbstractConfigurable;

/**
 * 路由断言工厂的抽象基类。
 *
 * <p>
 * 该类为所有路由断言工厂提供基础实现，继承自 {@link AbstractConfigurable} 并实现 {@link RoutePredicateFactory}
 * 接口。
 * </p>
 *
 * <p>
 * <b>主要功能：</b>
 * </p>
 * <ul>
 * <li>提供配置类的泛型支持</li>
 * <li>封装配置对象的创建和管理</li>
 * <li>简化具体断言工厂的实现</li>
 * </ul>
 *
 * <p>
 * <b>使用示例：</b>
 * </p>
 * <pre>{@code
 * public class PathRoutePredicateFactory extends AbstractRoutePredicateFactory<PathRoutePredicateFactory.Config> {
 *     public PathRoutePredicateFactory() {
 *         super(Config.class);
 *     }
 *     // ...
 * }
 * }</pre>
 *
 * @param <C> 配置类的类型
 * @author Spencer Gibb
 * @see RoutePredicateFactory
 */
public abstract class AbstractRoutePredicateFactory<C> extends AbstractConfigurable<C>
		implements RoutePredicateFactory<C> {

	/**
	 * 构造函数，传入配置类的 Class 对象。
	 * @param configClass 配置类的 Class 对象，用于反射创建配置实例
	 */
	public AbstractRoutePredicateFactory(Class<C> configClass) {
		super(configClass);
	}

}
