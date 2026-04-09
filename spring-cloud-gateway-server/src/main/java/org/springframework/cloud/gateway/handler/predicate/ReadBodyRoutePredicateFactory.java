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

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * 请求体读取断言工厂 - 读取并验证请求体内容的路由匹配。
 *
 * <p>
 * 该断言工厂允许在路由匹配阶段读取并检查请求体内容。 它支持将请求体反序列化为指定类型，并应用用户提供的断言进行验证。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>读取请求体并反序列化为指定类型</li>
 * <li>对请求体内容应用自定义断言进行验证</li>
 * <li>请求体在内存中缓存，避免重复反序列化</li>
 * <li>仅支持异步模式（返回 AsyncPredicate）</li>
 * </ul>
 *
 * <p>
 * <b>注意事项：</b>
 * </p>
 * <ul>
 * <li>读取请求体会消耗请求体流，后续过滤器无法再次读取原始流</li>
 * <li>适用于请求体较小的场景</li>
 * <li>必须在其他读取请求体的过滤器之前使用</li>
 * </ul>
 *
 * <p>
 * <b>使用场景：</b>
 * </p>
 * <ul>
 * <li>根据请求体内容（如 JWT 声明）进行路由</li>
 * <li>验证请求体是否符合特定格式</li>
 * <li>API 版本控制</li>
 * </ul>
 *
 * <p>
 * <b>配置示例：</b>
 * </p>
 * <pre>{@code
 * # 此断言通常通过 Java DSL 配置
 * &#64;Bean
 * public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
 *     return builder.routes()
 *         .route("read_body_route", r -> r
 *             .readBody(String.class, s -> s.contains("premium"))
 *             .uri("https://premium.example.org"))
 *         .build();
 * }
 * }</pre>
 *
 * @author Spencer Gibb
 * @see AsyncPredicate
 */
public class ReadBodyRoutePredicateFactory extends AbstractRoutePredicateFactory<ReadBodyRoutePredicateFactory.Config> {

	/** 日志记录器 */
	protected static final Log log = LogFactory.getLog(ReadBodyRoutePredicateFactory.class);

	/** 测试结果属性键 */
	private static final String TEST_ATTRIBUTE = "read_body_predicate_test_attribute";

	/** 缓存请求体对象的属性键 */
	private static final String CACHE_REQUEST_BODY_OBJECT_KEY = "cachedRequestBodyObject";

	/** HTTP 消息读取器列表 */
	private final List<HttpMessageReader<?>> messageReaders;

	/**
	 * 默认构造函数，使用默认的 HTTP 消息读取器。
	 */
	public ReadBodyRoutePredicateFactory() {
		super(Config.class);
		this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
	}

	/**
	 * 构造函数，使用自定义的 HTTP 消息读取器。
	 * @param messageReaders HTTP 消息读取器列表
	 */
	public ReadBodyRoutePredicateFactory(List<HttpMessageReader<?>> messageReaders) {
		super(Config.class);
		this.messageReaders = messageReaders;
	}

	/**
	 * 创建异步断言，用于读取并验证请求体。
	 *
	 * <p>
	 * 该方法：
	 * </p>
	 * <ol>
	 * <li>检查是否已有缓存的请求体</li>
	 * <li>如果有，使用缓存的请求体进行断言测试</li>
	 * <li>如果没有，读取请求体并缓存，然后进行断言测试</li>
	 * </ol>
	 * @param config 配置对象，包含目标类类型和断言
	 * @return 异步断言
	 */
	@Override
	@SuppressWarnings("unchecked")
	public AsyncPredicate<ServerWebExchange> applyAsync(Config config) {
		return new AsyncPredicate<ServerWebExchange>() {
			@Override
			public Publisher<Boolean> apply(ServerWebExchange exchange) {
				Class inClass = config.getInClass();

				// 检查是否有缓存的请求体
				Object cachedBody = exchange.getAttribute(CACHE_REQUEST_BODY_OBJECT_KEY);

				// 请求体只能读取一次，缓存机制确保多次调用不会重复读取
				if (cachedBody != null) {
					try {
						boolean test = config.predicate.test(cachedBody);
						exchange.getAttributes().put(TEST_ATTRIBUTE, test);
						return Mono.just(test);
					}
					catch (ClassCastException e) {
						if (log.isDebugEnabled()) {
							log.debug("Predicate test failed because class in predicate "
									+ "does not match the cached body object", e);
						}
					}
					return Mono.just(false);
				}
				else {
					// 读取请求体并缓存
					return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange,
							(serverHttpRequest) -> ServerRequest
									.create(exchange.mutate().request(serverHttpRequest).build(), messageReaders)
									.bodyToMono(inClass).doOnNext(objectValue -> exchange.getAttributes()
											.put(CACHE_REQUEST_BODY_OBJECT_KEY, objectValue))
									.map(objectValue -> config.getPredicate().test(objectValue)));
				}
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("ReadBody: %s", config.getInClass());
			}
		};
	}

	/**
	 * 同步版本不支持，抛出异常。
	 * @param config 配置对象
	 * @return 不返回
	 * @throws UnsupportedOperationException 始终抛出此异常
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Predicate<ServerWebExchange> apply(Config config) {
		throw new UnsupportedOperationException("ReadBodyPredicateFactory is only async.");
	}

	/**
	 * 配置类，定义 ReadBody 断言所需的配置参数。
	 */
	public static class Config {

		/** 请求体反序列化的目标类型 */
		private Class inClass;

		/** 用于验证请求体的断言 */
		private Predicate predicate;

		/** HTTP 消息解码提示 */
		private Map<String, Object> hints;

		/**
		 * 获取请求体目标类型。
		 * @return 目标类型 Class
		 */
		public Class getInClass() {
			return inClass;
		}

		/**
		 * 设置请求体目标类型。
		 * @param inClass 目标类型 Class
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setInClass(Class inClass) {
			this.inClass = inClass;
			return this;
		}

		/**
		 * 获取验证断言。
		 * @return 断言
		 */
		public Predicate getPredicate() {
			return predicate;
		}

		/**
		 * 设置验证断言。
		 * @param predicate 断言
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setPredicate(Predicate predicate) {
			this.predicate = predicate;
			return this;
		}

		/**
		 * 设置目标类型和断言（便捷方法）。
		 * @param inClass 目标类型
		 * @param predicate 断言
		 * @param <T> 目标类型泛型
		 * @return 当前配置对象，用于链式调用
		 */
		public <T> Config setPredicate(Class<T> inClass, Predicate<T> predicate) {
			setInClass(inClass);
			this.predicate = predicate;
			return this;
		}

		/**
		 * 获取 HTTP 消息解码提示。
		 * @return 解码提示 Map
		 */
		public Map<String, Object> getHints() {
			return hints;
		}

		/**
		 * 设置 HTTP 消息解码提示。
		 * @param hints 解码提示 Map
		 * @return 当前配置对象，用于链式调用
		 */
		public Config setHints(Map<String, Object> hints) {
			this.hints = hints;
			return this;
		}

	}

}
