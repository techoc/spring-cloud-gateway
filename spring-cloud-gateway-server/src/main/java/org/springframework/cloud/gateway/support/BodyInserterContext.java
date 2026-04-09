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

package org.springframework.cloud.gateway.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

/**
 * {@link BodyInserter.Context} 接口的实现类，用于在 Spring Cloud Gateway 中为
 * 请求体写入器（BodyInserter）提供上下文信息。
 * <p>
 * 该类封装了 {@link ExchangeStrategies}，使得 BodyInserter 能够获取到可用的 HTTP
 * 消息写入器列表。通常用于网关过滤器中对请求体进行重新编码或修改的场景。
 * </p>
 * <p>
 * 注意：当前实现不支持自定义 Exchange 策略提示（hints）， 也不关联实际的服务端请求对象。
 * </p>
 */
public class BodyInserterContext implements BodyInserter.Context {

	/** HTTP 交换策略，包含消息读写器等配置 */
	private final ExchangeStrategies exchangeStrategies;

	/**
	 * 使用默认的 {@link ExchangeStrategies} 构造 BodyInserterContext。
	 */
	public BodyInserterContext() {
		this.exchangeStrategies = ExchangeStrategies.withDefaults();
	}

	/**
	 * 使用自定义的 {@link ExchangeStrategies} 构造 BodyInserterContext。
	 * <p>
	 * 注意：当前版本尚未完整支持自定义策略，TODO 标记处待后续完善。
	 * </p>
	 * @param exchangeStrategies 自定义的交换策略，包含消息写入器等配置
	 */
	public BodyInserterContext(ExchangeStrategies exchangeStrategies) {
		this.exchangeStrategies = exchangeStrategies; // TODO: support custom strategies
	}

	/**
	 * 返回可用的 HTTP 消息写入器列表。
	 * <p>
	 * 该列表由 {@link ExchangeStrategies} 提供，用于将 Java 对象序列化为 HTTP 响应体。
	 * </p>
	 * @return HTTP 消息写入器列表，不会为 null
	 */
	@Override
	public List<HttpMessageWriter<?>> messageWriters() {
		return exchangeStrategies.messageWriters();
	}

	/**
	 * 返回关联的服务端请求，当前实现始终返回空 Optional。
	 * <p>
	 * 在网关转发场景中，此上下文不绑定具体的服务端请求对象。
	 * </p>
	 * @return 始终返回 {@link Optional#empty()}
	 */
	@Override
	public Optional<ServerHttpRequest> serverRequest() {
		return Optional.empty();
	}

	/**
	 * 返回写入提示（hints）Map，当前实现始终返回空 Map。
	 * <p>
	 * TODO：后续版本将支持自定义提示信息。
	 * </p>
	 * @return 空的不可修改 Map
	 */
	@Override
	public Map<String, Object> hints() {
		return Collections.emptyMap(); // TODO: support hints
	}

}
