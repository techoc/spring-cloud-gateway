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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.route.builder.Buildable;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.toAsyncPredicate;

/**
 * 路由实体类，表示 Spring Cloud Gateway 中的一条路由规则。
 * <p>
 * 一条路由由以下核心要素组成：
 * <ul>
 * <li>唯一标识（id）</li>
 * <li>目标 URI（uri）—— 请求被转发到的目标地址</li>
 * <li>执行顺序（order）—— 多条路由匹配时按此值升序优先</li>
 * <li>异步断言（predicate）—— 决定请求是否匹配此路由</li>
 * <li>过滤器链（gatewayFilters）—— 对请求/响应进行处理的过滤器列表</li>
 * <li>元数据（metadata）—— 存放路由相关的扩展信息</li>
 * </ul>
 * <p>
 * 该类实现了 {@link Ordered} 接口，支持多路由排序。 推荐通过内部建造者类 {@link Builder} 或 {@link AsyncBuilder}
 * 来构建路由实例。
 *
 * @author Spencer Gibb
 */
public class Route implements Ordered {

	/** 路由的唯一标识符 */
	private final String id;

	/** 路由转发的目标 URI */
	private final URI uri;

	/** 路由的执行顺序，值越小优先级越高 */
	private final int order;

	/** 路由的异步断言，用于判断请求是否匹配此路由 */
	private final AsyncPredicate<ServerWebExchange> predicate;

	/** 路由上绑定的网关过滤器列表 */
	private final List<GatewayFilter> gatewayFilters;

	/** 路由的扩展元数据，可存储自定义的键值对信息 */
	private final Map<String, Object> metadata;

	/**
	 * 私有构造方法，仅由内部建造者调用。
	 * @param id 路由唯一标识
	 * @param uri 目标 URI
	 * @param order 执行顺序
	 * @param predicate 异步断言
	 * @param gatewayFilters 网关过滤器列表
	 * @param metadata 元数据
	 */
	private Route(String id, URI uri, int order, AsyncPredicate<ServerWebExchange> predicate,
			List<GatewayFilter> gatewayFilters, Map<String, Object> metadata) {
		this.id = id;
		this.uri = uri;
		this.order = order;
		this.predicate = predicate;
		this.gatewayFilters = gatewayFilters;
		this.metadata = metadata;
	}

	/**
	 * 创建一个同步断言风格的路由建造者。
	 * @return {@link Builder} 实例
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * 根据已有的路由定义创建一个同步断言风格的路由建造者， 并预填充 id、uri、order 和 metadata 信息。
	 * @param routeDefinition 路由定义对象
	 * @return {@link Builder} 实例
	 */
	public static Builder builder(RouteDefinition routeDefinition) {
		// @formatter:off
		return new Builder().id(routeDefinition.getId())
				.uri(routeDefinition.getUri())
				.order(routeDefinition.getOrder())
				.metadata(routeDefinition.getMetadata());
		// @formatter:on
	}

	/**
	 * 创建一个异步断言风格的路由建造者。
	 * @return {@link AsyncBuilder} 实例
	 */
	public static AsyncBuilder async() {
		return new AsyncBuilder();
	}

	/**
	 * 根据已有的路由定义创建一个异步断言风格的路由建造者， 并预填充 id、uri、order 和 metadata 信息。
	 * @param routeDefinition 路由定义对象
	 * @return {@link AsyncBuilder} 实例
	 */
	public static AsyncBuilder async(RouteDefinition routeDefinition) {
		// @formatter:off
		return new AsyncBuilder().id(routeDefinition.getId())
				.uri(routeDefinition.getUri())
				.order(routeDefinition.getOrder())
				.metadata(routeDefinition.getMetadata());
		// @formatter:on
	}

	/**
	 * 获取路由的唯一标识符。
	 * @return 路由 ID
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * 获取路由转发的目标 URI。
	 * @return 目标 URI
	 */
	public URI getUri() {
		return this.uri;
	}

	/**
	 * 获取路由的执行顺序。
	 * @return 排序值，越小优先级越高
	 */
	public int getOrder() {
		return order;
	}

	/**
	 * 获取路由的异步断言。
	 * @return 异步断言对象
	 */
	public AsyncPredicate<ServerWebExchange> getPredicate() {
		return this.predicate;
	}

	/**
	 * 获取路由绑定的网关过滤器列表（只读视图）。
	 * @return 不可修改的过滤器列表
	 */
	public List<GatewayFilter> getFilters() {
		return Collections.unmodifiableList(this.gatewayFilters);
	}

	/**
	 * 获取路由的元数据（只读视图）。
	 * @return 不可修改的元数据 Map
	 */
	public Map<String, Object> getMetadata() {
		return Collections.unmodifiableMap(metadata);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Route route = (Route) o;
		return this.order == route.order && Objects.equals(this.id, route.id) && Objects.equals(this.uri, route.uri)
				&& Objects.equals(this.predicate, route.predicate)
				&& Objects.equals(this.gatewayFilters, route.gatewayFilters)
				&& Objects.equals(this.metadata, route.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.uri, this.order, this.predicate, this.gatewayFilters, this.metadata);
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("Route{");
		sb.append("id='").append(id).append('\'');
		sb.append(", uri=").append(uri);
		sb.append(", order=").append(order);
		sb.append(", predicate=").append(predicate);
		sb.append(", gatewayFilters=").append(gatewayFilters);
		sb.append(", metadata=").append(metadata);
		sb.append('}');
		return sb.toString();
	}

	/**
	 * 路由建造者的抽象基类，提供路由构建所需的通用属性和方法。
	 * <p>
	 * 子类需实现 {@link #getThis()} 返回自身引用（用于链式调用）， 以及 {@link #getPredicate()} 返回对应类型的断言。
	 *
	 * @param <B> 建造者的具体类型，支持链式调用
	 */
	public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> implements Buildable<Route> {

		/** 路由唯一标识 */
		protected String id;

		/** 路由目标 URI */
		protected URI uri;

		/** 路由执行顺序，默认为 0 */
		protected int order = 0;

		/** 路由绑定的过滤器列表 */
		protected List<GatewayFilter> gatewayFilters = new ArrayList<>();

		/** 路由元数据 */
		protected Map<String, Object> metadata = new HashMap<>();

		protected AbstractBuilder() {
		}

		/**
		 * 返回当前建造者自身，子类实现用于支持链式调用。
		 * @return 当前建造者实例
		 */
		protected abstract B getThis();

		/**
		 * 设置路由 ID。
		 * @param id 路由唯一标识
		 * @return 当前建造者实例（链式调用）
		 */
		public B id(String id) {
			this.id = id;
			return getThis();
		}

		/**
		 * 获取当前设置的路由 ID。
		 * @return 路由 ID
		 */
		public String getId() {
			return id;
		}

		/**
		 * 设置路由执行顺序。
		 * @param order 排序值，越小优先级越高
		 * @return 当前建造者实例（链式调用）
		 */
		public B order(int order) {
			this.order = order;
			return getThis();
		}

		/**
		 * 通过字符串形式设置目标 URI。
		 * <p>
		 * 注意：若 URI scheme 为 http/https 且未指定端口，将自动补全默认端口（80 或 443）。
		 * @param uri 目标 URI 字符串
		 * @return 当前建造者实例（链式调用）
		 */
		public B uri(String uri) {
			return uri(URI.create(uri));
		}

		/**
		 * 设置目标 URI。
		 * <p>
		 * 注意：scheme 不能为空；若为 http/https 协议且未指定端口，将自动补全默认端口。
		 * @param uri 目标 URI 对象
		 * @return 当前建造者实例（链式调用）
		 * @throws IllegalArgumentException URI 格式不正确时抛出
		 */
		public B uri(URI uri) {
			this.uri = uri;
			String scheme = this.uri.getScheme();
			Assert.hasText(scheme, "The parameter [" + this.uri + "] format is incorrect, scheme can not be empty");
			if (this.uri.getPort() < 0 && scheme.startsWith("http")) {
				// 对 http/https 协议自动补全默认端口
				int port = this.uri.getScheme().equals("https") ? 443 : 80;
				this.uri = UriComponentsBuilder.fromUri(this.uri).port(port).build(false).toUri();
			}
			return getThis();
		}

		/**
		 * 替换路由的全部元数据。
		 * @param metadata 新的元数据 Map，将完全替换已有数据
		 * @return 当前建造者实例（链式调用）
		 */
		public B replaceMetadata(Map<String, Object> metadata) {
			this.metadata = metadata;
			return getThis();
		}

		/**
		 * 批量追加元数据。
		 * @param metadata 要追加的元数据 Map
		 * @return 当前建造者实例（链式调用）
		 */
		public B metadata(Map<String, Object> metadata) {
			this.metadata.putAll(metadata);
			return getThis();
		}

		/**
		 * 追加单条元数据。
		 * @param key 元数据键
		 * @param value 元数据值
		 * @return 当前建造者实例（链式调用）
		 */
		public B metadata(String key, Object value) {
			this.metadata.put(key, value);
			return getThis();
		}

		/**
		 * 获取当前建造者关联的异步断言，由子类实现。
		 * @return 异步断言对象
		 */
		public abstract AsyncPredicate<ServerWebExchange> getPredicate();

		/**
		 * 替换路由的全部过滤器。
		 * @param gatewayFilters 新的过滤器列表，将完全替换已有列表
		 * @return 当前建造者实例（链式调用）
		 */
		public B replaceFilters(List<GatewayFilter> gatewayFilters) {
			this.gatewayFilters = gatewayFilters;
			return getThis();
		}

		/**
		 * 向路由添加单个过滤器。
		 * @param gatewayFilter 要添加的网关过滤器
		 * @return 当前建造者实例（链式调用）
		 */
		public B filter(GatewayFilter gatewayFilter) {
			this.gatewayFilters.add(gatewayFilter);
			return getThis();
		}

		/**
		 * 向路由批量添加过滤器集合。
		 * @param gatewayFilters 要添加的过滤器集合
		 * @return 当前建造者实例（链式调用）
		 */
		public B filters(Collection<GatewayFilter> gatewayFilters) {
			this.gatewayFilters.addAll(gatewayFilters);
			return getThis();
		}

		/**
		 * 向路由批量添加过滤器（可变参数形式）。
		 * @param gatewayFilters 要添加的过滤器数组
		 * @return 当前建造者实例（链式调用）
		 */
		public B filters(GatewayFilter... gatewayFilters) {
			return filters(Arrays.asList(gatewayFilters));
		}

		/**
		 * 构建 {@link Route} 实例。
		 * <p>
		 * 构建前会校验 id、uri、predicate 均不为 null。
		 * @return 构建完成的 {@link Route} 对象
		 * @throws IllegalArgumentException id、uri 或 predicate 为 null 时抛出
		 */
		public Route build() {
			Assert.notNull(this.id, "id can not be null");
			Assert.notNull(this.uri, "uri can not be null");
			AsyncPredicate<ServerWebExchange> predicate = getPredicate();
			Assert.notNull(predicate, "predicate can not be null");

			return new Route(this.id, this.uri, this.order, predicate, this.gatewayFilters, this.metadata);
		}

	}

	/**
	 * 异步断言风格的路由建造者。
	 * <p>
	 * 使用 {@link AsyncPredicate} 作为路由断言，支持对断言进行 and / or / negate 等逻辑组合操作。
	 * 适用于需要异步判断的场景（如访问响应式数据源）。
	 */
	public static class AsyncBuilder extends AbstractBuilder<AsyncBuilder> {

		/** 当前设置的异步断言 */
		protected AsyncPredicate<ServerWebExchange> predicate;

		@Override
		protected AsyncBuilder getThis() {
			return this;
		}

		/**
		 * 获取当前的异步断言。
		 * @return 异步断言对象
		 */
		@Override
		public AsyncPredicate<ServerWebExchange> getPredicate() {
			return this.predicate;
		}

		/**
		 * 设置同步断言（内部转换为异步断言）。
		 * @param predicate 同步断言
		 * @return 当前建造者实例（链式调用）
		 */
		public AsyncBuilder predicate(Predicate<ServerWebExchange> predicate) {
			return asyncPredicate(toAsyncPredicate(predicate));
		}

		/**
		 * 设置异步断言。
		 * @param predicate 异步断言
		 * @return 当前建造者实例（链式调用）
		 */
		public AsyncBuilder asyncPredicate(AsyncPredicate<ServerWebExchange> predicate) {
			this.predicate = predicate;
			return this;
		}

		/**
		 * 将当前断言与指定断言进行逻辑与（AND）组合。
		 * <p>
		 * 注意：当前断言不能为 null，否则抛出异常。
		 * @param predicate 要进行 AND 组合的断言
		 * @return 当前建造者实例（链式调用）
		 * @throws IllegalArgumentException 当前断言为 null 时抛出
		 */
		public AsyncBuilder and(AsyncPredicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call and() on null predicate");
			this.predicate = this.predicate.and(predicate);
			return this;
		}

		/**
		 * 将当前断言与指定断言进行逻辑或（OR）组合。
		 * <p>
		 * 注意：当前断言不能为 null，否则抛出异常。
		 * @param predicate 要进行 OR 组合的断言
		 * @return 当前建造者实例（链式调用）
		 * @throws IllegalArgumentException 当前断言为 null 时抛出
		 */
		public AsyncBuilder or(AsyncPredicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call or() on null predicate");
			this.predicate = this.predicate.or(predicate);
			return this;
		}

		/**
		 * 对当前断言进行逻辑取反（NOT）。
		 * <p>
		 * 注意：当前断言不能为 null，否则抛出异常。
		 * @return 当前建造者实例（链式调用）
		 * @throws IllegalArgumentException 当前断言为 null 时抛出
		 */
		public AsyncBuilder negate() {
			Assert.notNull(this.predicate, "can not call negate() on null predicate");
			this.predicate = this.predicate.negate();
			return this;
		}

	}

	/**
	 * 同步断言风格的路由建造者。
	 * <p>
	 * 使用标准的 {@link Predicate} 作为路由断言，内部会自动将其包装为 {@link AsyncPredicate}。 适用于断言逻辑本身是同步的场景。
	 */
	public static class Builder extends AbstractBuilder<Builder> {

		/** 当前设置的同步断言 */
		protected Predicate<ServerWebExchange> predicate;

		@Override
		protected Builder getThis() {
			return this;
		}

		/**
		 * 获取当前同步断言对应的异步断言包装。
		 * @return 包装后的异步断言
		 */
		@Override
		public AsyncPredicate<ServerWebExchange> getPredicate() {
			return ServerWebExchangeUtils.toAsyncPredicate(this.predicate);
		}

		/**
		 * 将当前断言与指定断言进行逻辑与（AND）组合。
		 * @param predicate 要进行 AND 组合的同步断言
		 * @return 当前建造者实例（链式调用）
		 * @throws IllegalArgumentException 当前断言为 null 时抛出
		 */
		public Builder and(Predicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call and() on null predicate");
			this.predicate = this.predicate.and(predicate);
			return this;
		}

		/**
		 * 将当前断言与指定断言进行逻辑或（OR）组合。
		 * @param predicate 要进行 OR 组合的同步断言
		 * @return 当前建造者实例（链式调用）
		 * @throws IllegalArgumentException 当前断言为 null 时抛出
		 */
		public Builder or(Predicate<ServerWebExchange> predicate) {
			Assert.notNull(this.predicate, "can not call or() on null predicate");
			this.predicate = this.predicate.or(predicate);
			return this;
		}

		/**
		 * 对当前断言进行逻辑取反（NOT）。
		 * @return 当前建造者实例（链式调用）
		 * @throws IllegalArgumentException 当前断言为 null 时抛出
		 */
		public Builder negate() {
			Assert.notNull(this.predicate, "can not call negate() on null predicate");
			this.predicate = this.predicate.negate();
			return this;
		}

	}

}
