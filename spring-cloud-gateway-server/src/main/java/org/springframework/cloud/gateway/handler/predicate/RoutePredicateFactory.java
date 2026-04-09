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

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.support.Configurable;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.cloud.gateway.support.ShortcutConfigurable;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * 路由断言工厂接口，定义创建路由断言的规范。
 *
 * <p>
 * RoutePredicateFactory 是 Spring Cloud Gateway 中所有路由断言工厂的根接口。 它继承自
 * {@link ShortcutConfigurable} 和 {@link Configurable}，提供了 快捷配置和配置类管理的功能。
 * </p>
 *
 * <p>
 * <b>核心职责：</b>
 * </p>
 * <ul>
 * <li>定义创建断言的标准方法</li>
 * <li>支持同步和异步两种断言类型</li>
 * <li>提供快捷配置支持</li>
 * <li>支持 Java DSL 和 YAML 配置</li>
 * </ul>
 *
 * <p>
 * <b>使用示例：</b>
 * </p>
 * <pre>{@code
 * // 实现自定义断言工厂
 * public class MyRoutePredicateFactory extends AbstractRoutePredicateFactory<MyRoutePredicateFactory.Config> {
 *     public MyRoutePredicateFactory() {
 *         super(Config.class);
 *     }
 *
 *     &#64;Override
 *     public Predicate<ServerWebExchange> apply(Config config) {
 *         return exchange -> {
 *             // 自定义匹配逻辑
 *             return true;
 *         };
 *     }
 *
 *     public static class Config {
 *         // 配置属性
 *     }
 * }
 * }</pre>
 *
 * @param <C> 配置类的类型
 * @author Spencer Gibb
 * @see AbstractRoutePredicateFactory
 * @see ShortcutConfigurable
 * @see Configurable
 */
@FunctionalInterface
public interface RoutePredicateFactory<C> extends ShortcutConfigurable, Configurable<C> {

	/**
	 * 模式键常量，用于快捷配置。
	 */
	String PATTERN_KEY = "pattern";

	/**
	 * 使用 Consumer 创建断言（适用于 Java DSL）。
	 *
	 * <p>
	 * 该方法允许通过 Consumer 模式配置断言， 提供了更流畅的 API 使用体验。
	 * </p>
	 * @param consumer 配置消费者
	 * @return 创建的断言
	 */
	// useful for javadsl
	default Predicate<ServerWebExchange> apply(Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		beforeApply(config);
		return apply(config);
	}

	/**
	 * 使用 Consumer 创建异步断言（适用于 Java DSL）。
	 * @param consumer 配置消费者
	 * @return 创建的异步断言
	 */
	default AsyncPredicate<ServerWebExchange> applyAsync(Consumer<C> consumer) {
		C config = newConfig();
		consumer.accept(config);
		beforeApply(config);
		return applyAsync(config);
	}

	/**
	 * 获取配置类的类型。
	 * @return 配置类的 Class
	 * @throws UnsupportedOperationException 如果未实现
	 */
	default Class<C> getConfigClass() {
		throw new UnsupportedOperationException("getConfigClass() not implemented");
	}

	/**
	 * 创建新的配置实例。
	 * @return 配置实例
	 * @throws UnsupportedOperationException 如果未实现
	 */
	@Override
	default C newConfig() {
		throw new UnsupportedOperationException("newConfig() not implemented");
	}

	/**
	 * 应用配置前的回调方法。
	 *
	 * <p>
	 * 子类可以重写此方法，在断言创建前进行配置验证或预处理。
	 * </p>
	 * @param config 配置对象
	 */
	default void beforeApply(C config) {
	}

	/**
	 * 使用配置创建同步断言。
	 * @param config 配置对象
	 * @return 创建的断言
	 */
	Predicate<ServerWebExchange> apply(C config);

	/**
	 * 使用配置创建异步断言。
	 *
	 * <p>
	 * 默认实现将同步断言转换为异步断言。 如果需要原生异步支持，应重写此方法。
	 * </p>
	 * @param config 配置对象
	 * @return 创建的异步断言
	 */
	default AsyncPredicate<ServerWebExchange> applyAsync(C config) {
		return toAsyncPredicate(apply(config));
	}

	/**
	 * 获取断言工厂的名称。
	 *
	 * <p>
	 * 默认实现根据类名自动生成标准化的名称。 例如：PathRoutePredicateFactory -> path
	 * </p>
	 * @return 断言工厂名称
	 */
	default String name() {
		return NameUtils.normalizeRoutePredicateName(getClass());
	}

}
