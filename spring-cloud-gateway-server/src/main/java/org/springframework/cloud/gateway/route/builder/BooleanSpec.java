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

import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.AND;
import static org.springframework.cloud.gateway.route.builder.BooleanSpec.Operator.OR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * 布尔断言规格类，用于对路由断言进行逻辑运算。
 * <p>
 * 该类支持对断言进行逻辑与（AND）、逻辑或（OR）、逻辑非（NEGATE）运算， 使路由匹配条件可以组合成复杂的布尔表达式。
 * <p>
 * 典型使用方式： <pre>{@code
 * .predicate(...)
 * .and().predicate(...)
 * .or().predicate(...)
 * .negate()
 * }</pre>
 * <p>
 * 完成断言配置后，可通过 {@link #filters(Function)} 方法进入过滤器配置阶段。
 *
 * @author Spencer Gibb
 */
public class BooleanSpec extends UriSpec {

	/** 当前保存的断言，主要用于 Kotlin DSL */
	final AsyncPredicate<ServerWebExchange> predicate;

	/**
	 * 构造方法。
	 * @param routeBuilder 异步路由构建器
	 * @param builder 父构建器
	 */
	public BooleanSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder) {
		super(routeBuilder, builder);
		// 保存当前断言，对 Kotlin DSL 有用
		predicate = routeBuilder.getPredicate();
	}

	/**
	 * 应用逻辑与（AND）运算符。
	 * <p>
	 * 将后续断言与当前断言进行 AND 组合。
	 * @return 布尔运算规格对象
	 */
	public BooleanOpSpec and() {
		return new BooleanOpSpec(routeBuilder, builder, AND);
	}

	/**
	 * 应用逻辑或（OR）运算符。
	 * <p>
	 * 将后续断言与当前断言进行 OR 组合。
	 * @return 布尔运算规格对象
	 */
	public BooleanOpSpec or() {
		return new BooleanOpSpec(routeBuilder, builder, OR);
	}

	/**
	 * 对当前断言进行逻辑取反（NOT）。
	 * @return 新的布尔规格对象
	 */
	public BooleanSpec negate() {
		this.routeBuilder.negate();
		return new BooleanSpec(routeBuilder, builder);
	}

	/**
	 * 进入过滤器配置阶段。
	 * <p>
	 * 完成断言配置后，通过此方法配置路由的过滤器链。
	 * @param fn 过滤器配置函数，接收 {@link GatewayFilterSpec} 返回 {@link UriSpec}
	 * @return URI 规格对象，用于设置目标地址
	 */
	public UriSpec filters(Function<GatewayFilterSpec, UriSpec> fn) {
		return fn.apply(new GatewayFilterSpec(routeBuilder, builder));
	}

	/**
	 * 逻辑运算符枚举。
	 */
	enum Operator {

		/** 逻辑与 */
		AND,
		/** 逻辑或 */
		OR,
		/** 逻辑非 */
		NEGATE

	}

	/**
	 * 布尔运算规格类，继承自 {@link PredicateSpec}，支持逻辑运算符链式调用。
	 * <p>
	 * 用于处理 AND、OR 等二元逻辑运算。
	 */
	public static class BooleanOpSpec extends PredicateSpec {

		/** 当前逻辑运算符 */
		private Operator operator;

		/**
		 * 构造方法。
		 * @param routeBuilder 异步路由构建器
		 * @param builder 父构建器
		 * @param operator 逻辑运算符
		 * @throws IllegalArgumentException operator 为 null 时抛出
		 */
		BooleanOpSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder, Operator operator) {
			super(routeBuilder, builder);
			Assert.notNull(operator, "operator may not be null");
			this.operator = operator;
		}

		/**
		 * 添加自定义同步断言。
		 * @param predicate 同步断言
		 * @return 布尔规格对象
		 */
		public BooleanSpec predicate(Predicate<ServerWebExchange> predicate) {
			return asyncPredicate(toAsyncPredicate(predicate));
		}

		/**
		 * 添加自定义异步断言，并根据当前运算符进行逻辑组合。
		 * @param predicate 异步断言
		 * @return 布尔规格对象
		 */
		@Override
		public BooleanSpec asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
			switch (this.operator) {
			case AND:
				this.routeBuilder.and(predicate);
				break;
			case OR:
				this.routeBuilder.or(predicate);
				break;
			case NEGATE:
				this.routeBuilder.negate();
			}
			return new BooleanSpec(this.routeBuilder, this.builder);
		}

		/**
		 * 对指定断言进行逻辑非（NOT）运算。
		 * <p>
		 * 用于构建如 "A AND NOT B" 这样的复合条件。
		 * @param fn 断言配置函数
		 * @return 布尔规格对象
		 */
		public BooleanSpec not(Function<PredicateSpec, BooleanSpec> fn) {
			return fn.apply(new NotOpSpec(this.routeBuilder, this.builder, this.operator));
		}

	}

	/**
	 * 逻辑非运算规格类，继承自 {@link BooleanOpSpec}。
	 * <p>
	 * 专门处理 NOT 运算，对传入的断言取反后再进行 AND/OR 组合。
	 */
	public static class NotOpSpec extends BooleanOpSpec {

		/**
		 * 构造方法。
		 * @param routeBuilder 异步路由构建器
		 * @param builder 父构建器
		 * @param operator 逻辑运算符（AND 或 OR）
		 */
		NotOpSpec(Route.AsyncBuilder routeBuilder, RouteLocatorBuilder.Builder builder, Operator operator) {
			super(routeBuilder, builder, operator);
		}

		/**
		 * 添加异步断言，并在组合前对断言进行取反。
		 * @param predicate 异步断言
		 * @return 布尔规格对象
		 */
		@Override
		public BooleanSpec asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
			AsyncPredicate<ServerWebExchange> negated = this.routeBuilder.getPredicate().not(predicate);
			return super.asyncPredicate(negated);
		}

	}

}
