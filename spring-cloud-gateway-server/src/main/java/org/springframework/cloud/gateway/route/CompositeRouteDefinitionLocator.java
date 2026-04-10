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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.function.Function;

/**
 * 组合路由定义定位器，将多个路由定义定位器的结果合并为一个流。
 * <p>
 * 该类实现了组合模式（Composite Pattern），用于聚合多个 {@link RouteDefinitionLocator} 的路由定义结果。 主要功能包括：
 * <ul>
 * <li>合并多个定位器的路由定义流</li>
 * <li>为未设置 ID 的路由定义自动生成 UUID</li>
 * </ul>
 * <p>
 * 使用 {@link Flux#flatMapSequential(Function)} 保证路由定义的顺序性。 默认使用
 * {@link AlternativeJdkIdGenerator} 生成路由 ID，可通过构造方法自定义。
 *
 * @author Spencer Gibb
 */
public class CompositeRouteDefinitionLocator implements RouteDefinitionLocator {

	/**
	 * 日志记录器
	 */
	private static final Log log = LogFactory.getLog(CompositeRouteDefinitionLocator.class);

	/**
	 * 被组合的多个路由定义定位器流
	 */
	private final Flux<RouteDefinitionLocator> delegates;

	/**
	 * ID 生成器，用于为未设置 ID 的路由定义生成唯一标识
	 */
	private final IdGenerator idGenerator;

	/**
	 * 构造方法，使用默认的 ID 生成器。
	 * @param delegates 多个路由定义定位器的响应式流
	 */
	public CompositeRouteDefinitionLocator(Flux<RouteDefinitionLocator> delegates) {
		this(delegates, new AlternativeJdkIdGenerator());
	}

	/**
	 * 构造方法，允许自定义 ID 生成器。
	 * @param delegates 多个路由定义定位器的响应式流
	 * @param idGenerator 自定义 ID 生成器
	 */
	public CompositeRouteDefinitionLocator(Flux<RouteDefinitionLocator> delegates, IdGenerator idGenerator) {
		this.delegates = delegates;
		this.idGenerator = idGenerator;
	}

	/**
	 * 获取所有路由定义定位器的合并路由定义列表。
	 * <p>
	 * 按顺序合并多个定位器的路由定义，并为未设置 ID 的路由定义自动生成 UUID。
	 * @return 包含所有路由定义的响应式流
	 */
	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return this.delegates.flatMapSequential(RouteDefinitionLocator::getRouteDefinitions)
				.flatMap(routeDefinition -> {
					// 如果路由定义没有 ID，自动生成一个 UUID
					if (routeDefinition.getId() == null) {
						return randomId().map(id -> {
							routeDefinition.setId(id);
							if (log.isDebugEnabled()) {
								log.debug("Id set on route definition: " + routeDefinition);
							}
							return routeDefinition;
						});
					}
					return Mono.just(routeDefinition);
				});
	}

	/**
	 * 生成随机 UUID 作为路由 ID。
	 * <p>
	 * 使用配置的 ID 生成器，并在 boundedElastic 调度器上执行以避免阻塞。
	 * @return 包含 UUID 字符串的 Mono
	 */
	protected Mono<String> randomId() {
		return Mono.fromSupplier(idGenerator::generateId).map(UUID::toString).publishOn(Schedulers.boundedElastic());
	}

}
