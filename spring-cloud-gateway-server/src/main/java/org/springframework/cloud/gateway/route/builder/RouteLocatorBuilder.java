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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 路由定位器构建器，用于通过 Fluent API 方式构建 {@link RouteLocator}。
 * <p>
 * 该类提供了声明式的路由配置方式，支持链式调用，使路由配置更加直观易读。 典型使用方式： <pre>{@code
 * RouteLocator locator = builder.routes()
 *     .route("route1", r -> r.path("/api/**").uri("http://example.com"))
 *     .route(r -> r.host("*.example.com").uri("lb://my-service"))
 *     .build();
 * }</pre>
 * <p>
 * 需要传入 {@link ConfigurableApplicationContext} 以支持从 Spring 容器中获取过滤器工厂和断言工厂。
 *
 * @author Spencer Gibb
 */
public class RouteLocatorBuilder {

	/** Spring 应用上下文，用于获取过滤器/断言工厂 Bean */
	private ConfigurableApplicationContext context;

	/**
	 * 构造方法。
	 * @param context Spring 应用上下文
	 */
	public RouteLocatorBuilder(ConfigurableApplicationContext context) {
		this.context = context;
	}

	/**
	 * 创建一个新的路由构建器。
	 * @return 新的 {@link Builder} 实例
	 */
	public Builder routes() {
		return new Builder(context);
	}

	/**
	 * 内部路由构建器类，用于收集和构建多条路由。
	 * <p>
	 * 支持添加多条路由配置，最后通过 {@link #build()} 方法生成 {@link RouteLocator}。
	 */
	public static class Builder {

		/** 待构建的路由列表 */
		private List<Buildable<Route>> routes = new ArrayList<>();

		/** Spring 应用上下文 */
		private ConfigurableApplicationContext context;

		/**
		 * 构造方法。
		 * @param context Spring 应用上下文
		 */
		public Builder(ConfigurableApplicationContext context) {
			this.context = context;
		}

		/**
		 * 创建一条指定 ID 的路由。
		 * <p>
		 * 使用函数式接口配置路由的断言、过滤器和目标 URI。
		 * @param id 路由唯一标识
		 * @param fn 路由配置函数，接收 {@link PredicateSpec} 返回 {@link Buildable<Route>}
		 * @return 当前构建器实例（链式调用）
		 */
		public Builder route(String id, Function<PredicateSpec, Buildable<Route>> fn) {
			Buildable<Route> routeBuilder = fn.apply(new RouteSpec(this).id(id));
			add(routeBuilder);
			return this;
		}

		/**
		 * 创建一条自动生成 ID 的路由。
		 * <p>
		 * 路由 ID 将自动生成 UUID，适用于不需要显式指定 ID 的场景。
		 * @param fn 路由配置函数，接收 {@link PredicateSpec} 返回 {@link Buildable<Route>}
		 * @return 当前构建器实例（链式调用）
		 */
		public Builder route(Function<PredicateSpec, Buildable<Route>> fn) {
			Buildable<Route> routeBuilder = fn.apply(new RouteSpec(this).randomId());
			add(routeBuilder);
			return this;
		}

		/**
		 * 构建并返回 {@link RouteLocator}。
		 * <p>
		 * 将所有配置的路由构建为 {@link Route} 对象，并包装为 {@link RouteLocator}。
		 * @return 包含所有配置路由的 {@link RouteLocator}
		 */
		public RouteLocator build() {
			return () -> Flux.fromIterable(this.routes).map(routeBuilder -> routeBuilder.build());
		}

		/**
		 * 获取 Spring 应用上下文。
		 * @return 应用上下文
		 */
		ConfigurableApplicationContext getContext() {
			return context;
		}

		/**
		 * 添加路由构建器到列表。
		 * @param route 路由构建器
		 */
		void add(Buildable<Route> route) {
			routes.add(route);
		}

	}

	/**
	 * 路由规格类，用于设置路由 ID 并进入断言配置阶段。
	 * <p>
	 * 这是路由配置的入口，负责创建 {@link Route.AsyncBuilder} 并设置路由 ID。
	 */
	public static class RouteSpec {

		/** 异步路由构建器 */
		private final Route.AsyncBuilder routeBuilder = Route.async();

		/** 父构建器 */
		private final Builder builder;

		/**
		 * 构造方法。
		 * @param builder 父构建器
		 */
		RouteSpec(Builder builder) {
			this.builder = builder;
		}

		/**
		 * 设置路由 ID 并进入断言配置。
		 * @param id 路由唯一标识
		 * @return 断言规格对象 {@link PredicateSpec}
		 */
		public PredicateSpec id(String id) {
			this.routeBuilder.id(id);
			return predicateBuilder();
		}

		/**
		 * 生成随机 UUID 作为路由 ID 并进入断言配置。
		 * @return 断言规格对象 {@link PredicateSpec}
		 */
		public PredicateSpec randomId() {
			return id(UUID.randomUUID().toString());
		}

		/**
		 * 创建断言构建器。
		 * @return 断言规格对象
		 */
		private PredicateSpec predicateBuilder() {
			return new PredicateSpec(this.routeBuilder, this.builder);
		}

	}

}
