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

package org.springframework.cloud.gateway.route;

import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 组合路由定位器，将多个路由定位器的结果合并为一个流。
 * <p>
 * 该类实现了组合模式（Composite Pattern），用于聚合多个 {@link RouteLocator} 的路由结果。
 * 这在需要从多个来源（如配置文件、服务发现、数据库等）加载路由时非常有用。
 * <p>
 * 使用 {@link Flux#flatMapSequential(Function)}} 保证路由的顺序性：
 * 先返回第一个定位器的所有路由，再返回第二个定位器的所有路由，以此类推。
 *
 * @author Spencer Gibb
 */
public class CompositeRouteLocator implements RouteLocator {

	/**
	 * 被组合的多个路由定位器流
	 */
	private final Flux<RouteLocator> delegates;

	/**
	 * 构造方法。
	 * @param delegates 多个路由定位器的响应式流
	 */
	public CompositeRouteLocator(Flux<RouteLocator> delegates) {
		this.delegates = delegates;
	}

	/**
	 * 获取所有路由定位器的合并路由列表。
	 * <p>
	 * 按顺序合并多个定位器的路由，保持定位器之间的顺序关系。
	 * @return 包含所有路由的响应式流
	 */
	@Override
	public Flux<Route> getRoutes() {
		return this.delegates.flatMapSequential(RouteLocator::getRoutes);
	}

}
