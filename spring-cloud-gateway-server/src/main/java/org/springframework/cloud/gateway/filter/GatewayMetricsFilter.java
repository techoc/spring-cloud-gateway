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

package org.springframework.cloud.gateway.filter;

import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网关监控指标过滤器。
 * <p>
 * 该全局过滤器集成 Micrometer，用于采集每个请求的处理耗时，并以 Timer 指标的形式 上报到
 * {@link MeterRegistry}。指标名称由可配置的前缀加上 {@code ".requests"} 组成， 并附带由
 * {@link GatewayTagsProvider} 提供的多维度标签（如路由 ID、HTTP 方法、响应状态等）。
 * <p>
 * 执行顺序为 {@link NettyWriteResponseFilter#WRITE_RESPONSE_FILTER_ORDER} + 1，
 * 确保在写响应之前尽早启动计时器，并在响应提交前记录指标。
 *
 * @author Tony Clarke
 * @author Ingyu Hwang
 */
public class GatewayMetricsFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(GatewayMetricsFilter.class);

	/** Micrometer 指标注册表，用于记录 Timer 等监控指标 */
	private final MeterRegistry meterRegistry;

	/** 组合的标签提供者，将多个 GatewayTagsProvider 合并为一个 */
	private GatewayTagsProvider compositeTagsProvider;

	/** 指标名称前缀，最终指标名为 "{metricsPrefix}.requests" */
	private final String metricsPrefix;

	/**
	 * 构造网关监控指标过滤器。
	 * @param meterRegistry Micrometer 指标注册表
	 * @param tagsProviders 标签提供者列表，用于为请求指标附加多维度标签
	 * @param metricsPrefix 指标名称前缀（若以 "." 结尾会自动去除）
	 */
	public GatewayMetricsFilter(MeterRegistry meterRegistry, List<GatewayTagsProvider> tagsProviders,
			String metricsPrefix) {
		this.meterRegistry = meterRegistry;
		this.compositeTagsProvider = tagsProviders.stream().reduce(exchange -> Tags.empty(), GatewayTagsProvider::and);
		if (metricsPrefix.endsWith(".")) {
			this.metricsPrefix = metricsPrefix.substring(0, metricsPrefix.length() - 1);
		}
		else {
			this.metricsPrefix = metricsPrefix;
		}
	}

	/**
	 * 获取指标名称前缀。
	 * @return 指标名称前缀字符串
	 */
	public String getMetricsPrefix() {
		return metricsPrefix;
	}

	/**
	 * 返回过滤器执行顺序。
	 * <p>
	 * 执行顺序设置为 {@link NettyWriteResponseFilter#WRITE_RESPONSE_FILTER_ORDER} + 1，
	 * 确保计时器尽早启动并在写响应前完成指标记录。
	 * @return 过滤器执行顺序值
	 */
	@Override
	public int getOrder() {
		// 尽早启动计时器，并在向客户端写响应之前上报指标
		return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER + 1;
	}

	/**
	 * 过滤请求，记录请求处理耗时指标。
	 * <p>
	 * 在请求开始时启动 Timer 采样，在请求成功或失败时停止采样并上报指标。 指标上报尊重响应提交状态：若响应已提交则立即上报，否则注册提交前回调。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Sample sample = Timer.start(meterRegistry);

		return chain.filter(exchange).doOnSuccess(aVoid -> endTimerRespectingCommit(exchange, sample))
				.doOnError(throwable -> endTimerRespectingCommit(exchange, sample));
	}

	/**
	 * 尊重响应提交状态地停止计时器。
	 * <p>
	 * 若响应已提交，直接调用计时器终止逻辑；否则注册响应提交前回调，确保在提交时记录指标。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param sample 计时器采样对象
	 */
	private void endTimerRespectingCommit(ServerWebExchange exchange, Sample sample) {

		ServerHttpResponse response = exchange.getResponse();
		if (response.isCommitted()) {
			endTimerInner(exchange, sample);
		}
		else {
			response.beforeCommit(() -> {
				endTimerInner(exchange, sample);
				return Mono.empty();
			});
		}
	}

	/**
	 * 实际停止计时器并上报指标。
	 * <p>
	 * 从复合标签提供者获取标签，并将 Timer 样本记录到指标注册表。
	 * @param exchange 当前服务器 Web 交换对象，用于生成指标标签
	 * @param sample 待停止的计时器采样对象
	 */
	private void endTimerInner(ServerWebExchange exchange, Sample sample) {
		Tags tags = compositeTagsProvider.apply(exchange);

		if (log.isTraceEnabled()) {
			log.trace(metricsPrefix + ".requests tags: " + tags);
		}
		sample.stop(meterRegistry.timer(metricsPrefix + ".requests", tags));
	}

}
