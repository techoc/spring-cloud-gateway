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

package org.springframework.cloud.gateway.handler;

import java.util.function.Function;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.handler.predicate.GatewayPredicate;
import org.springframework.cloud.gateway.support.HasConfig;
import org.springframework.cloud.gateway.support.Visitor;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * 异步断言接口，用于在 Spring Cloud Gateway 中进行异步条件判断。
 *
 * <p>
 * AsyncPredicate 是响应式网关断言的核心接口，继承自 {@link Function} 和 {@link HasConfig}。 与同步的
 * {@link Predicate} 不同，它返回 {@link Publisher}&lt;Boolean&gt;，支持响应式编程模型。
 * </p>
 *
 * <p>
 * <b>核心功能：</b>
 * </p>
 * <ul>
 * <li>支持异步条件判断，适用于需要异步IO操作的场景</li>
 * <li>提供逻辑组合操作（and、or、negate）</li>
 * <li>支持访问者模式，用于断言的遍历和处理</li>
 * </ul>
 *
 * <p>
 * <b>使用示例：</b>
 * </p>
 * <pre>{@code
 * // 从同步 Predicate 转换为异步断言
 * AsyncPredicate<ServerWebExchange> asyncPredicate = AsyncPredicate.from(exchange -> true);
 *
 * // 组合多个断言
 * AsyncPredicate<ServerWebExchange> combined = predicate1.and(predicate2);
 * }</pre>
 *
 * @author Ben Hale
 * @see GatewayPredicate
 * @see java.util.function.Predicate
 */
public interface AsyncPredicate<T> extends Function<T, Publisher<Boolean>>, HasConfig {

	default AsyncPredicate<T> and(AsyncPredicate<? super T> other) {
		return new AndAsyncPredicate<>(this, other);
	}

	default AsyncPredicate<T> negate() {
		return new NegateAsyncPredicate<>(this);
	}

	default AsyncPredicate<T> not(AsyncPredicate<? super T> other) {
		return new NegateAsyncPredicate<>(other);
	}

	default AsyncPredicate<T> or(AsyncPredicate<? super T> other) {
		return new OrAsyncPredicate<>(this, other);
	}

	default void accept(Visitor visitor) {
		visitor.visit(this);
	}

	static AsyncPredicate<ServerWebExchange> from(Predicate<? super ServerWebExchange> predicate) {
		return new DefaultAsyncPredicate<>(GatewayPredicate.wrapIfNeeded(predicate));
	}

	class DefaultAsyncPredicate<T> implements AsyncPredicate<T> {

		private final Predicate<T> delegate;

		public DefaultAsyncPredicate(Predicate<T> delegate) {
			this.delegate = delegate;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.just(delegate.test(t));
		}

		@Override
		public String toString() {
			return this.delegate.toString();
		}

		@Override
		public void accept(Visitor visitor) {
			if (delegate instanceof GatewayPredicate) {
				GatewayPredicate gatewayPredicate = (GatewayPredicate) delegate;
				gatewayPredicate.accept(visitor);
			}
		}

	}

	/**
	 * 取反的异步断言。
	 *
	 * <p>
	 * 将当前断言的结果取反后返回。 如果原断言返回 true，则此断言返回 false；反之亦然。
	 * </p>
	 *
	 * @param <T> 断言输入类型
	 */
	class NegateAsyncPredicate<T> implements AsyncPredicate<T> {

		/** 要取反的原始断言 */
		private final AsyncPredicate<? super T> predicate;

		public NegateAsyncPredicate(AsyncPredicate<? super T> predicate) {
			Assert.notNull(predicate, "predicate AsyncPredicate must not be null");
			this.predicate = predicate;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.from(predicate.apply(t)).map(b -> !b);
		}

		@Override
		public String toString() {
			return String.format("!(%s)", this.predicate);
		}

	}

	class AndAsyncPredicate<T> implements AsyncPredicate<T> {

		private final AsyncPredicate<? super T> left;

		private final AsyncPredicate<? super T> right;

		public AndAsyncPredicate(AsyncPredicate<? super T> left, AsyncPredicate<? super T> right) {
			Assert.notNull(left, "Left AsyncPredicate must not be null");
			Assert.notNull(right, "Right AsyncPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.from(left.apply(t)).flatMap(result -> !result ? Mono.just(false) : Mono.from(right.apply(t)));
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

	class OrAsyncPredicate<T> implements AsyncPredicate<T> {

		private final AsyncPredicate<? super T> left;

		private final AsyncPredicate<? super T> right;

		public OrAsyncPredicate(AsyncPredicate<? super T> left, AsyncPredicate<? super T> right) {
			Assert.notNull(left, "Left AsyncPredicate must not be null");
			Assert.notNull(right, "Right AsyncPredicate must not be null");
			this.left = left;
			this.right = right;
		}

		@Override
		public Publisher<Boolean> apply(T t) {
			return Mono.from(left.apply(t)).flatMap(result -> result ? Mono.just(true) : Mono.from(right.apply(t)));
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
