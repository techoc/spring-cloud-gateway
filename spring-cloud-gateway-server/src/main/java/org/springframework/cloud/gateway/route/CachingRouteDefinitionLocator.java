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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import reactor.cache.CacheFlux;
import reactor.core.publisher.Flux;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationListener;

/**
 * 带缓存功能的路由定义定位器，装饰器模式实现。
 * <p>
 * 该类作为 {@link RouteDefinitionLocator} 的装饰器，为底层路由定义定位器添加缓存能力：
 * <ul>
 * <li>首次获取路由定义时从被装饰的定位器加载，并缓存结果</li>
 * <li>后续请求直接返回缓存数据，避免重复查询存储介质</li>
 * <li>监听 {@link RefreshRoutesEvent} 事件，异步刷新缓存</li>
 * </ul>
 * <p>
 * 缓存使用 Reactor 的 {@link CacheFlux} 实现，天然支持响应式编程模型。 与 {@link CachingRouteLocator}
 * 不同，该类缓存的是路由定义（配置层），而非运行时的路由对象。
 *
 * @author Spencer Gibb
 */
public class CachingRouteDefinitionLocator implements RouteDefinitionLocator, ApplicationListener<RefreshRoutesEvent> {

	/** 缓存键常量 */
	private static final String CACHE_KEY = "routeDefs";

	/** 被装饰的底层路由定义定位器 */
	private final RouteDefinitionLocator delegate;

	/** 缓存的路由定义流，使用 CacheFlux 实现响应式缓存 */
	private final Flux<RouteDefinition> routeDefinitions;

	/** 内部缓存存储，键为 CACHE_KEY，值为路由定义信号列表 */
	private final Map<String, List> cache = new ConcurrentHashMap<>();

	/**
	 * 构造方法，初始化缓存路由定义流。
	 * @param delegate 被装饰的底层路由定义定位器
	 */
	public CachingRouteDefinitionLocator(RouteDefinitionLocator delegate) {
		this.delegate = delegate;
		routeDefinitions = CacheFlux.lookup(cache, CACHE_KEY, RouteDefinition.class).onCacheMissResume(this::fetch);
	}

	/**
	 * 从底层定位器获取路由定义。
	 * @return 路由定义流
	 */
	private Flux<RouteDefinition> fetch() {
		return this.delegate.getRouteDefinitions();
	}

	/**
	 * 获取路由定义列表。
	 * <p>
	 * 首次调用会从底层定位器加载并缓存，后续调用直接返回缓存数据。
	 * @return 包含所有路由定义的响应式流
	 */
	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		return this.routeDefinitions;
	}

	/**
	 * 手动清除路由定义缓存。
	 * <p>
	 * 下次调用 {@link #getRouteDefinitions()} 时将重新从底层定位器加载。
	 * @return 清除缓存后的路由定义流
	 */
	public Flux<RouteDefinition> refresh() {
		this.cache.clear();
		return this.routeDefinitions;
	}

	/**
	 * 监听路由刷新事件，异步刷新缓存。
	 * <p>
	 * 当收到 {@link RefreshRoutesEvent} 时，会异步从底层定位器重新加载路由定义， 加载成功后更新缓存。
	 * @param event 路由刷新事件
	 */
	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		fetch().materialize().collect(Collectors.toList()).doOnNext(routes -> cache.put(CACHE_KEY, routes)).subscribe();
	}

}
