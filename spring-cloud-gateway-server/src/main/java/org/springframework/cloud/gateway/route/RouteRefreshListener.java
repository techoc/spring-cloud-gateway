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

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.HeartbeatMonitor;
import org.springframework.cloud.client.discovery.event.InstanceRegisteredEvent;
import org.springframework.cloud.client.discovery.event.ParentHeartbeatEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.Assert;

/**
 * 路由刷新监听器，监听多种 Spring Cloud 事件并触发路由刷新。
 * <p>
 * 该类类似于 Zuul 的 ZuulDiscoveryRefreshListener，负责在以下场景触发路由刷新：
 * <ul>
 * <li>应用上下文刷新完成（排除管理端口上下文）</li>
 * <li>配置刷新（RefreshScopeRefreshedEvent）</li>
 * <li>服务实例注册（InstanceRegisteredEvent）</li>
 * <li>服务发现心跳（HeartbeatEvent / ParentHeartbeatEvent）</li>
 * </ul>
 * <p>
 * 使用 {@link HeartbeatMonitor} 检测心跳值变化，避免重复刷新。
 * <p>
 * TODO: 考虑在 Spring Cloud Commons 中抽象为基类？
 *
 * @see org.springframework.cloud.client.discovery.event.HeartbeatMonitor
 */
// see ZuulDiscoveryRefreshListener
// TODO: make abstract class in commons?
public class RouteRefreshListener implements ApplicationListener<ApplicationEvent> {

	/** 应用事件发布器，用于发布刷新事件 */
	private final ApplicationEventPublisher publisher;

	/** 心跳监控器，用于检测服务发现心跳值变化 */
	private HeartbeatMonitor monitor = new HeartbeatMonitor();

	/**
	 * 构造方法。
	 * @param publisher 应用事件发布器，用于发布 {@link RefreshRoutesEvent}
	 * @throws IllegalArgumentException publisher 为 null 时抛出
	 */
	public RouteRefreshListener(ApplicationEventPublisher publisher) {
		Assert.notNull(publisher, "publisher may not be null");
		this.publisher = publisher;
	}

	/**
	 * 监听应用事件，根据事件类型决定是否触发路由刷新。
	 * <p>
	 * 处理的事件类型：
	 * <ul>
	 * <li>{@link ContextRefreshedEvent} — 上下文刷新完成（排除管理端口）</li>
	 * <li>{@link RefreshScopeRefreshedEvent} — 配置刷新</li>
	 * <li>{@link InstanceRegisteredEvent} — 服务实例注册</li>
	 * <li>{@link ParentHeartbeatEvent} — 父上下文心跳</li>
	 * <li>{@link HeartbeatEvent} — 服务发现心跳</li>
	 * </ul>
	 * @param event 应用事件
	 */
	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent) {
			ContextRefreshedEvent refreshedEvent = (ContextRefreshedEvent) event;
			// 排除管理端口（如 Actuator）的上下文刷新
			if (!WebServerApplicationContext.hasServerNamespace(refreshedEvent.getApplicationContext(), "management")) {
				reset();
			}
		}
		else if (event instanceof RefreshScopeRefreshedEvent || event instanceof InstanceRegisteredEvent) {
			reset();
		}
		else if (event instanceof ParentHeartbeatEvent) {
			ParentHeartbeatEvent e = (ParentHeartbeatEvent) event;
			resetIfNeeded(e.getValue());
		}
		else if (event instanceof HeartbeatEvent) {
			HeartbeatEvent e = (HeartbeatEvent) event;
			resetIfNeeded(e.getValue());
		}
	}

	/**
	 * 根据心跳值变化决定是否触发刷新。
	 * <p>
	 * 使用 {@link HeartbeatMonitor#update(Object)} 检测心跳值是否发生变化， 仅在值变化时才触发刷新，避免不必要的重复刷新。
	 * @param value 心跳值
	 */
	private void resetIfNeeded(Object value) {
		if (this.monitor.update(value)) {
			reset();
		}
	}

	/**
	 * 触发路由刷新。
	 * <p>
	 * 发布 {@link RefreshRoutesEvent} 事件，通知路由系统重新加载路由配置。
	 */
	private void reset() {
		this.publisher.publishEvent(new RefreshRoutesEvent(this));
	}

}
