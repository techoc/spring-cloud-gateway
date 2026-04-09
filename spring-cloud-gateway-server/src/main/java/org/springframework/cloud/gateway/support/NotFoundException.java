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

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 资源未找到异常，用于表示请求的资源在网关或后端服务中不存在。
 * <p>
 * 该异常继承自 {@link ResponseStatusException}，自动关联 HTTP 状态码。 根据构造方式不同，可以返回 404 (NOT_FOUND) 或
 * 503 (SERVICE_UNAVAILABLE) 状态。
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 * <li>网关路由配置错误，请求到达不存在的服务</li>
 * <li>后端服务返回 404，网关需要向上游传递此状态</li>
 * <li>服务发现中找不到目标服务实例</li>
 * </ul>
 * </p>
 *
 * @author Spencer Gibb
 * @see ResponseStatusException
 * @see ServiceUnavailableException
 */
public class NotFoundException extends ResponseStatusException {

	/**
	 * 创建包含错误消息的异常，HTTP 状态默认为 503 (SERVICE_UNAVAILABLE)。
	 * @param message 错误描述信息
	 */
	public NotFoundException(String message) {
		this(HttpStatus.SERVICE_UNAVAILABLE, message);
	}

	/**
	 * 创建包含错误消息和根因异常的异常，HTTP 状态默认为 503 (SERVICE_UNAVAILABLE)。
	 * @param message 错误描述信息
	 * @param cause 根因异常
	 */
	public NotFoundException(String message, Throwable cause) {
		this(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
	}

	/**
	 * 私有构造函数，指定 HTTP 状态码和错误消息。
	 * @param httpStatus HTTP 状态码
	 * @param message 错误描述信息
	 */
	private NotFoundException(HttpStatus httpStatus, String message) {
		super(httpStatus, message);
	}

	/**
	 * 私有构造函数，指定 HTTP 状态码、错误消息和根因异常。
	 * @param httpStatus HTTP 状态码
	 * @param message 错误描述信息
	 * @param cause 根因异常
	 */
	private NotFoundException(HttpStatus httpStatus, String message, Throwable cause) {
		super(httpStatus, message, cause);
	}

	/**
	 * 工厂方法，创建带有明确状态码的未找到异常。
	 * @param with404 若为 true，返回 404 状态；否则返回 503 状态
	 * @param message 错误描述信息
	 * @return NotFoundException 实例
	 */
	public static NotFoundException create(boolean with404, String message) {
		HttpStatus httpStatus = with404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
		return new NotFoundException(httpStatus, message);
	}

	/**
	 * 工厂方法，创建带有明确状态码和根因异常的未找到异常。
	 * @param with404 若为 true，返回 404 状态；否则返回 503 状态
	 * @param message 错误描述信息
	 * @param cause 根因异常
	 * @return NotFoundException 实例
	 */
	public static NotFoundException create(boolean with404, String message, Throwable cause) {
		HttpStatus httpStatus = with404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE;
		return new NotFoundException(httpStatus, message, cause);
	}

}
