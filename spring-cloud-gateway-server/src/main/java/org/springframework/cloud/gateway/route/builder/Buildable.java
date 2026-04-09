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

/**
 * 可构建对象的通用接口，用于 Fluent API 建造者模式。
 * <p>
 * 该接口定义了构建操作的契约，实现类负责完成具体的构建逻辑。 在 Spring Cloud Gateway 的路由构建体系中，主要用于：
 * <ul>
 * <li>构建 {@link org.springframework.cloud.gateway.route.Route} 对象</li>
 * <li>支持链式调用中的最终构建操作</li>
 * </ul>
 * <p>
 * 典型使用场景： <pre>{@code
 * Buildable<Route> routeBuilder = ...;
 * Route route = routeBuilder.build();
 * }</pre>
 *
 * @param <T> 构建目标对象的类型
 */
public interface Buildable<T> {

	/**
	 * 执行构建操作，返回构建完成的对象。
	 * <p>
	 * 实现类应在此方法中完成所有必要的初始化、校验和组装工作。
	 * @return 构建完成的目标对象
	 */
	T build();

}
