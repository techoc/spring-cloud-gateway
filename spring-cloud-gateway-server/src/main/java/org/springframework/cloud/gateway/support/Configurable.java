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

package org.springframework.cloud.gateway.support;

/**
 * 可配置对象的通用接口，定义了配置类信息的获取和新配置实例的创建方法。
 * <p>
 * 该接口是 Spring Cloud Gateway 中配置体系的基础接口，被各类过滤器工厂
 * （GatewayFilterFactory）和路由谓词工厂（RoutePredicateFactory）等组件实现。 通过实现此接口，组件能够动态创建配置对象实例，支持
 * Spring Boot 的配置绑定机制。
 * </p>
 *
 * @param <C> 配置对象的类型参数
 */
public interface Configurable<C> {

	/**
	 * 返回配置对象的 Class 类型。
	 * <p>
	 * 该方法用于获取配置类的元信息，配合 {@link #newConfig()} 方法可实现 动态创建配置实例的能力。
	 * </p>
	 * @return 配置对象的 Class 类型
	 */
	Class<C> getConfigClass();

	/**
	 * 创建并返回一个新的配置对象实例。
	 * <p>
	 * 该方法通过反射机制实例化配置对象，要求配置类必须有无参构造函数。 返回的实例为"纯净"状态，不包含任何配置值，需要通过配置绑定填充属性。
	 * </p>
	 * @return 新创建的配置对象实例
	 */
	C newConfig();

}
