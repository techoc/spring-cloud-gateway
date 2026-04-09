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

import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;

/**
 * 网关超时异常，表示请求处理时间超过配置的超时时间。
 * <p>
 * 当网关路由请求时，如果连接超时或响应超时，会抛出此异常。 该异常自动关联 HTTP 504 (Gateway Timeout) 状态码。
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 * <li>与后端服务建立连接超时</li>
 * <li>后端服务响应时间超过配置的最大等待时间</li>
 * <li>断路器熔断时的超时处理</li>
 * </ul>
 * </p>
 *
 * @see ResponseStatus
 * @see ServiceUnavailableException
 */
@ResponseStatus(value = GATEWAY_TIMEOUT, reason = "Response took longer than configured timeout")
public class TimeoutException extends Exception {

	/**
	 * 默认构造函数，创建一个无详细信息的异常。
	 */
	public TimeoutException() {
	}

	/**
	 * 带错误消息的构造函数。
	 * @param message 异常描述信息
	 */
	public TimeoutException(String message) {
		super(message);
	}

	/**
	 * 禁用堆栈跟踪生成以提升性能。
	 * <p>
	 * 网关场景中异常可能频繁触发，填充完整堆栈跟踪会带来性能开销。 通过覆盖此方法直接返回 this，避免不必要的堆栈填充操作。
	 * </p>
	 * @return 当前异常实例（而非 Throwable）
	 */
	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}

}
