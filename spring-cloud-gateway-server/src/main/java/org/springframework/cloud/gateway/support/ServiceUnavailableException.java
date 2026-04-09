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

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * 服务不可用异常，表示上游（后端）服务暂时无法处理的异常。
 * <p>
 * 当网关无法连接到后端服务、后端服务负载过高或后端服务明确返回 503 状态码时， 会抛出此异常。该异常自动关联 HTTP 503 (Service Unavailable)
 * 状态码。
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 * <li>负载均衡器找不到可用的服务实例</li>
 * <li>后端服务连接超时</li>
 * <li>后端服务主动返回不可用状态</li>
 * </ul>
 * </p>
 *
 * @see ResponseStatus
 * @see TimeoutException
 */
@ResponseStatus(value = SERVICE_UNAVAILABLE, reason = "Upstream service is temporarily unavailable")
public class ServiceUnavailableException extends Exception {

	/**
	 * 默认构造函数，创建一个无详细信息的异常。
	 */
	public ServiceUnavailableException() {
	}

	/**
	 * 带错误消息的构造函数。
	 * @param message 异常描述信息
	 */
	public ServiceUnavailableException(String message) {
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
