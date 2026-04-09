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

import java.util.function.Predicate;

import org.springframework.cloud.gateway.support.HasConfig;
import org.springframework.cloud.gateway.support.Visitor;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网关断言接口，继承自标准 Predicate 并扩展了配置和访问者模式支持。
 *
 * <p>
 * GatewayPredicate 是 Spring Cloud Gateway 中所有路由断言的基础接口， 它扩展了 Java 标准库的
 * {@link Predicate}，并添加了配置支持和访问者模式。
 * </p>
 *
 * <p>
 * <b>核心功能：</b>
 * </p>
 * <ul>
 * <li>提供逻辑组合操作（and、or、negate）</li>
 * <li>支持访问者模式用于遍历和处理断言</li>
 * <li>持有配置信息用于断言的初始化</li>
 * <li>支持将普通 Predicate 包装为 GatewayPredicate</li>
 * </ul>
 *
 * <p>
 * <b>使用示例：</b>
 * </p>
 * <pre>{@code
 * // 组合多个断言
 * GatewayPredicate combined = predicate1.and(predicate2);
 *
 * // 取反断言
 * GatewayPredicate negated = predicate.negate();
 * }</pre>
 *
 * @author Spencer Gibb
 * @see java.util.function.Predicate
 * @see HasConfig
 */
public interface GatewayPredicate extends Predicate<ServerWebExchange>, HasConfig {

	/**
	 * 逻辑与操作，组合两个断言。
	 * @param other 要组合的另一个断言
	 * @return 组合后的新断言
	 */
	@Override
	default Predicate<ServerWebExchange> and(Predicate<? super ServerWebExchange> other) {
		return new AndGatewayPredicate(this, wrapIfNeeded(other));
	}

	/**
	 * 取反操作。
	 * @return 取反后的新断言
	 */
	@Override
	default Predicate<ServerWebExchange> negate() {
		return new NegateGatewayPredicate(this);
	}

	/**
	 * 逻辑或操作，组合两个断言。
	 * @param other 要组合的另一个断言
	 * @return 组合后的新断言
	 */
	@Override
	default Predicate<ServerWebExchange> or(Predicate<? super ServerWebExchange> other) {
		return new OrGatewayPredicate(this, wrapIfNeeded(other));
	}

	/**
	 * 接受访问者，用于遍历和处理断言。
	 * @param visitor 访问者对象
	 */
	default void accept(Visitor visitor) {
		visitor.visit(this);
	}

	/**
	 * 将普通的 Predicate 包装为 GatewayPredicate。
	 * @param other 要包装的 Predicate
	 * @return 包装后的 GatewayPredicate
	 */
	static GatewayPredicate wrapIfNeeded(Predicate<? super ServerWebExchange> other) {
		GatewayPredicate right;

		if (other instanceof GatewayPredicate) {
			right = (GatewayPredicate) other;
		}
		else {
			right = new GatewayPredicateWrapper(other);
		}
		return right;
	}

	/**
	 * 包装普通 Predicate 的适配器类。
	 *
	 * <p>
	 * 将实现 Predicate 接口但未实现 GatewayPredicate 的类 包装为 GatewayPredicate，以便统一处理。
	 * </p>
	 */
	class GatewayPredicateWrapper implements GatewayPredicate {

		/** 被包装的原始 Predicate */
		private final Predicate<? super ServerWebExchange> delegate;

		public GatewayPredicateWrapper(Predicate<? super ServerWebExchange> delegate) {
			Assert.notNull(delegate, "delegate GatewayPredicate must not be null");
			this.delegate = delegate;
		}

		@Override
		public boolean test(ServerWebExchange exchange) {
			return this.delegate.test(exchange);
		}

		@Override
		public void accept(Visitor visitor) {
			if (delegate instanceof GatewayPredicate) {
				GatewayPredicate gatewayPredicate = (GatewayPredicate) delegate;
				gatewayPredicate.accept(visitor);
			}
		}

		@Override
		public String toString() {
			return this.delegate.getClass().getSimpleName();
		}

	}

	class NegateGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate predicate;

		public NegateGatewayPredicate(GatewayPredicate predicate) {
			Assert.notNull(predicate, "predicate GatewayPredicate must not be null");
			this.predicate = predicate;
		}

		@Override
		public boolean test(ServerWebExchange t) {
			return !this.predicate.test(t);
		}

		@Override
		public void accept(Visitor visitor) {
			predicate.accept(visitor);
		}

		@Override
		public String toString() {
			return String.format("!%s", this.predicate);
		}

	}

	/**
	 * 逻辑与网关断言。
	 *
	 * <p>
	 * 短路求值：左侧为 false 时不计算右侧。
	 * </p>
	 */
	class AndGatewayPredicate implements GatewayPredicate {

		/** 左侧断言 */
		private final GatewayPredicate left;

		/** 右侧断言 */
		private final GatewayPredicate right;

		public AndGatewayPredicate(GatewayPredicate left, GatewayPredicate right) {
			Assert.notNull(left, "Left GatewayPredicate must not be null");
			Assert.notNull(right, "Right GatewayPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean test(ServerWebExchange t) {
			return (this.left.test(t) && this.right.test(t));
		}

		@Override
		public void accept(Visitor visitor) {
			left.accept(visitor);
			right.accept(visitor);
		}

		@Override
		public String toString() {
			return String.format("(%s && %s)", this.left, this.right);
		}

	}

	class OrGatewayPredicate implements GatewayPredicate {

		private final GatewayPredicate left;

		private final GatewayPredicate right;

		public OrGatewayPredicate(GatewayPredicate left, GatewayPredicate right) {
			Assert.notNull(left, "Left GatewayPredicate must not be null");
			Assert.notNull(right, "Right GatewayPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean test(ServerWebExchange t) {
			return (this.left.test(t) || this.right.test(t));
		}

		@Override
		public void accept(Visitor visitor) {
			left.accept(visitor);
			right.accept(visitor);
		}

		@Override
		public String toString() {
			return String.format("(%s || %s)", this.left, this.right);
		}

	}

}
