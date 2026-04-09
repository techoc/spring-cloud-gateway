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

import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

/**
 * HTTP 状态持有器，用于封装和解析 HTTP 状态码信息。
 * <p>
 * 该类可以同时持有两种形式的状态表示：
 * <ul>
 * <li>{@link HttpStatus} 枚举值（如 {@code OK}、{@code NOT_FOUND}）</li>
 * <li>整数状态码（如 200、404）</li>
 * </ul>
 * 这种设计允许灵活处理标准和非标准的 HTTP 状态码。
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 * <li>解析路由配置中的状态码定义</li>
 * <li>网关响应状态码的设置和判断</li>
 * <li>根据状态码判断请求结果类型（成功、客户端错误、服务端错误等）</li>
 * </ul>
 * </p>
 *
 * @see HttpStatus
 */
public class HttpStatusHolder {

	/** HTTP 状态枚举值，可能为 null */
	private final HttpStatus httpStatus;

	/** 整数形式的状态码，可能为 null */
	private final Integer status;

	/**
	 * 构造函数，创建包含 HTTP 状态信息的持有器。
	 * <p>
	 * 至少需要提供 {@code httpStatus} 和 {@code status} 中的一个。
	 * </p>
	 * @param httpStatus HTTP 状态枚举值，可以为 null
	 * @param status 整数形式的状态码，可以为 null
	 * @throws IllegalArgumentException 当两个参数均为 null 时抛出
	 */
	public HttpStatusHolder(HttpStatus httpStatus, Integer status) {
		Assert.isTrue(httpStatus != null || status != null, "httpStatus and status may not both be null");
		this.httpStatus = httpStatus;
		this.status = status;
	}

	/**
	 * 解析字符串为 HttpStatusHolder。
	 * <p>
	 * 支持两种格式：
	 * <ul>
	 * <li>整数状态码字符串（如 "200"、"503"）</li>
	 * <li>HTTP 状态枚举名称字符串（如 "OK"、"NOT_FOUND"）</li>
	 * </ul>
	 * </p>
	 * @param status 待解析的状态字符串，不能为 null
	 * @return 解析后的 HttpStatusHolder 实例
	 * @throws IllegalArgumentException 当无法解析为有效状态时抛出
	 */
	public static HttpStatusHolder parse(String status) {
		final HttpStatus httpStatus = ServerWebExchangeUtils.parse(status);
		final Integer intStatus;
		if (httpStatus == null) {
			// 不是枚举名称，尝试解析为整数
			intStatus = Integer.parseInt(status);
		}
		else {
			intStatus = null;
		}

		return new HttpStatusHolder(httpStatus, intStatus);
	}

	/**
	 * 获取 HTTP 状态枚举值。
	 * @return HTTP 状态枚举值，若不存在则返回 null
	 */
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	/**
	 * 获取整数形式的状态码。
	 * @return 整数状态码，若不存在则返回 null
	 */
	public Integer getStatus() {
		return status;
	}

	/**
	 * 判断是否为 1xx 信息性状态码（Informational）。
	 * @return true 表示 1xx 状态码
	 */
	public boolean is1xxInformational() {
		return HttpStatus.Series.INFORMATIONAL.equals(getSeries());
	}

	/**
	 * 判断是否为 2xx 成功状态码（Successful）。
	 * @return true 表示 2xx 状态码
	 */
	public boolean is2xxSuccessful() {
		return HttpStatus.Series.SUCCESSFUL.equals(getSeries());
	}

	/**
	 * 判断是否为 3xx 重定向状态码（Redirection）。
	 * @return true 表示 3xx 状态码
	 */
	public boolean is3xxRedirection() {
		return HttpStatus.Series.REDIRECTION.equals(getSeries());
	}

	/**
	 * 判断是否为 4xx 客户端错误状态码（Client Error）。
	 * @return true 表示 4xx 状态码
	 */
	public boolean is4xxClientError() {
		return HttpStatus.Series.CLIENT_ERROR.equals(getSeries());
	}

	/**
	 * 判断是否为 5xx 服务端错误状态码（Server Error）。
	 * @return true 表示 5xx 状态码
	 */
	public boolean is5xxServerError() {
		return HttpStatus.Series.SERVER_ERROR.equals(getSeries());
	}

	/**
	 * 获取状态码所属的 HTTP 系列。
	 * <p>
	 * 优先从 HttpStatus 枚举获取系列信息， 若无则根据整数状态码推断。
	 * </p>
	 * @return HTTP 状态系列枚举值
	 */
	public HttpStatus.Series getSeries() {
		if (httpStatus != null) {
			return httpStatus.series();
		}
		if (status != null) {
			return HttpStatus.Series.valueOf(status);
		}
		return null;
	}

	/**
	 * 判断是否为错误状态码（4xx 或 5xx）。
	 * @return true 表示客户端错误或服务端错误
	 */
	public boolean isError() {
		return is4xxClientError() || is5xxServerError();
	}

	/**
	 * 返回当前持有器的字符串表示。
	 * @return 包含 httpStatus 和 status 的字符串描述
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("httpStatus", httpStatus).append("status", status).toString();
	}

}
