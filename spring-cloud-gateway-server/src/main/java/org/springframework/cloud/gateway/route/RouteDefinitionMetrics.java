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

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.context.ApplicationListener;

/**
 * 路由定义数量监控指标类。
 * <p>
 * 该类监听 {@link RefreshRoutesEvent} 事件，实时统计并上报当前网关中的路由定义数量。 通过 Micrometer 将指标暴露给监控系统（如
 * Prometheus、Grafana 等）。
 * <p>
 * 指标名称格式：{@code <metricsPrefix>.routes.count} <br>
 * 示例：{@code gateway.routes.count}
 * <p>
 * 使用 {@link AtomicInteger} 保证并发安全，使用 Gauge 类型指标（可增可减）。
 *
 * @author Fredrich Ombico
 */
public class RouteDefinitionMetrics implements ApplicationListener<RefreshRoutesEvent> {

	/** 日志记录器 */
	private static final Log log = LogFactory.getLog(GatewayMetricsFilter.class);

	/** 路由定义定位器，用于获取路由列表 */
	private final RouteDefinitionLocator routeLocator;

	/** 路由数量计数器，Micrometer Gauge 类型指标 */
	private final AtomicInteger routeDefinitionCount;

	/** 指标名称前缀 */
	private final String metricsPrefix;

	/**
	 * 构造方法，初始化监控指标。
	 * @param meterRegistry Micrometer 指标注册表
	 * @param routeLocator 路由定义定位器
	 * @param metricsPrefix 指标名称前缀（自动去除末尾的点号）
	 */
	public RouteDefinitionMetrics(MeterRegistry meterRegistry, RouteDefinitionLocator routeLocator,
			String metricsPrefix) {
		this.routeLocator = routeLocator;

		// 去除指标前缀末尾的点号，确保格式统一
		if (metricsPrefix.endsWith(".")) {
			this.metricsPrefix = metricsPrefix.substring(0, metricsPrefix.length() - 1);
		}
		else {
			this.metricsPrefix = metricsPrefix;
		}
		routeDefinitionCount = meterRegistry.gauge(this.metricsPrefix + ".routes.count", new AtomicInteger(0));
	}

	/**
	 * 获取指标名称前缀。
	 * @return 指标前缀
	 */
	public String getMetricsPrefix() {
		return metricsPrefix;
	}

	/**
	 * 监听路由刷新事件，更新路由数量指标。
	 * <p>
	 * 当收到 {@link RefreshRoutesEvent} 时，异步获取路由定义数量并更新 Gauge 指标。
	 * @param event 路由刷新事件
	 */
	@Override
	public void onApplicationEvent(RefreshRoutesEvent event) {
		routeLocator.getRouteDefinitions().count().subscribe(count -> {
			routeDefinitionCount.set(count.intValue());
			if (log.isDebugEnabled()) {
				log.debug("New routes count: " + routeDefinitionCount);
			}
		});
	}

}
