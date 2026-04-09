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

package org.springframework.cloud.gateway.support.tagsprovider;

import java.util.function.Function;

import io.micrometer.core.instrument.Tags;

import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网关指标标签提供者接口，用于为 Micrometer 指标生成标签。
 * <p>
 * 该接口继承自 {@link Function}{@code <ServerWebExchange, Tags>}， 接收一个请求交换对象，返回用于该请求的指标标签。
 * 标签通常包含路由 ID、路径、HTTP 状态码等维度信息， 用于在 Micrometer 中对指标进行多维度分组和分析。
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 * <li>为每个路由生成独立的指标</li>
 * <li>按 HTTP 方法、状态码分组统计</li>
 * <li>记录请求路径用于分析流量分布</li>
 * </ul>
 * </p>
 * <p>
 * 实现类可以使用 {@link #and(GatewayTagsProvider)} 方法组合多个提供者， 生成合并后的标签。
 * </p>
 *
 * @author Ingyu Hwang
 * @see io.micrometer.core.instrument.Tags
 * @see ServerWebExchange
 */
public interface GatewayTagsProvider extends Function<ServerWebExchange, Tags> {

	/**
	 * 组合两个标签提供者，生成合并的标签。
	 * <p>
	 * 返回一个新的提供者，对同一请求应用两个提供者并将标签合并。 合并顺序：先应用当前提供者，再应用 other 提供者。
	 * </p>
	 * @param other 另一个标签提供者，不能为 null
	 * @return 组合后的标签提供者
	 * @throws IllegalArgumentException 当 other 为 null 时抛出
	 */
	default GatewayTagsProvider and(GatewayTagsProvider other) {
		Assert.notNull(other, "other must not be null");

		return exchange -> other.apply(exchange).and(apply(exchange));
	}

}
