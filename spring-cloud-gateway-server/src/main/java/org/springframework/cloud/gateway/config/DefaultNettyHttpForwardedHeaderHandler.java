/*
 * Copyright 2013-2024 the original author or authors.
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

/*
 * Copyright (c) 2020-2023 VMware, Inc. or its affiliates, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.gateway.config;

import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.handler.codec.http.HttpRequest;
import reactor.netty.http.server.ConnectionInfo;
import reactor.netty.transport.AddressUtils;

import static reactor.netty.http.server.ConnectionInfo.getDefaultHostPort;

/**
 * {@code X-Forwarded-*} / {@code Forwarded} 请求头的默认处理器实现。
 * <p>
 * 该类实现了 {@link BiFunction}，用于从代理转发的请求头中提取原始客户端信息， 包括协议（proto）、主机地址（host）和远程地址（for）。 支持标准
 * {@code Forwarded} 头和传统的 {@code X-Forwarded-*} 系列头。
 * </p>
 * <p>
 * 解析优先级：先检查标准 {@code Forwarded} 头，若不存在则回退到 {@code X-Forwarded-*} 头。 多值头（逗号分隔）只取第一个值。
 * </p>
 *
 * @author Andrey Shlykov
 * @since 0.9.12
 */
final class DefaultNettyHttpForwardedHeaderHandler implements BiFunction<ConnectionInfo, HttpRequest, ConnectionInfo> {

	/** 单例实例 */
	static final DefaultNettyHttpForwardedHeaderHandler INSTANCE = new DefaultNettyHttpForwardedHeaderHandler();

	/** 标准 Forwarded 请求头名称 */
	static final String FORWARDED_HEADER = "Forwarded";

	/** X-Forwarded-For 请求头名称，用于标识客户端原始 IP */
	static final String X_FORWARDED_IP_HEADER = "X-Forwarded-For";

	/** X-Forwarded-Host 请求头名称，用于标识客户端原始 Host */
	static final String X_FORWARDED_HOST_HEADER = "X-Forwarded-Host";

	/** X-Forwarded-Port 请求头名称，用于标识客户端原始端口 */
	static final String X_FORWARDED_PORT_HEADER = "X-Forwarded-Port";

	/** X-Forwarded-Proto 请求头名称，用于标识客户端原始协议（http/https） */
	static final String X_FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";

	/** Forwarded 头中 host 字段的正则匹配模式 */
	static final Pattern FORWARDED_HOST_PATTERN = Pattern.compile("host=\"?([^;,\"]+)\"?");

	/** Forwarded 头中 proto 字段的正则匹配模式 */
	static final Pattern FORWARDED_PROTO_PATTERN = Pattern.compile("proto=\"?([^;,\"]+)\"?");

	/** Forwarded 头中 for 字段的正则匹配模式 */
	static final Pattern FORWARDED_FOR_PATTERN = Pattern.compile("for=\"?([^;,\"]+)\"?");

	/**
	 * 指定 HTTP 服务器是否对 {@code Forwarded} 头进行严格验证。
	 * <p>
	 * 默认启用严格验证。该系统属性用于向后兼容，将在 1.2.0 版本中移除。
	 * </p>
	 *
	 * @since 1.0.8
	 * @deprecated 使用系统属性仅为向后兼容，将在 1.2.0 版本中移除
	 */
	@Deprecated
	static final String FORWARDED_HEADER_VALIDATION = "reactor.netty.http.server.forwarded.strictValidation";

	/** 转发头验证默认值，默认为 true（启用严格验证） */
	static final boolean DEFAULT_FORWARDED_HEADER_VALIDATION = Boolean
			.parseBoolean(System.getProperty(FORWARDED_HEADER_VALIDATION, "true"));

	/**
	 * 根据请求中的转发头解析并更新连接信息。
	 * <p>
	 * 优先解析标准 {@code Forwarded} 头，若不存在则解析 {@code X-Forwarded-*} 系列头。
	 * </p>
	 * @param connectionInfo 当前连接信息
	 * @param request HTTP 请求对象，用于读取转发头
	 * @return 解析转发头后的更新连接信息
	 */
	@Override
	public ConnectionInfo apply(ConnectionInfo connectionInfo, HttpRequest request) {
		String forwardedHeader = request.headers().get(FORWARDED_HEADER);
		if (forwardedHeader != null) {
			return parseForwardedInfo(connectionInfo, forwardedHeader);
		}
		return parseXForwardedInfo(connectionInfo, request);
	}

	/**
	 * 解析标准 {@code Forwarded} 请求头，提取协议、主机和远程地址信息。
	 * <p>
	 * 只解析逗号分隔的第一个值。按 proto → host → for 的顺序依次匹配提取。
	 * </p>
	 * @param connectionInfo 当前连接信息
	 * @param forwardedHeader Forwarded 请求头的原始值
	 * @return 更新后的连接信息
	 */
	private ConnectionInfo parseForwardedInfo(ConnectionInfo connectionInfo, String forwardedHeader) {
		String forwarded = forwardedHeader.split(",", 2)[0];
		Matcher protoMatcher = FORWARDED_PROTO_PATTERN.matcher(forwarded);
		if (protoMatcher.find()) {
			connectionInfo = connectionInfo.withScheme(protoMatcher.group(1).trim());
		}
		Matcher hostMatcher = FORWARDED_HOST_PATTERN.matcher(forwarded);
		if (hostMatcher.find()) {
			connectionInfo = connectionInfo.withHostAddress(AddressUtils.parseAddress(hostMatcher.group(1),
					getDefaultHostPort(connectionInfo.getScheme()), DEFAULT_FORWARDED_HEADER_VALIDATION));
		}
		Matcher forMatcher = FORWARDED_FOR_PATTERN.matcher(forwarded);
		if (forMatcher.find()) {
			connectionInfo = connectionInfo.withRemoteAddress(AddressUtils.parseAddress(forMatcher.group(1).trim(),
					connectionInfo.getRemoteAddress().getPort(), DEFAULT_FORWARDED_HEADER_VALIDATION));
		}
		return connectionInfo;
	}

	/**
	 * 解析传统的 {@code X-Forwarded-*} 系列请求头。
	 * <p>
	 * 依次处理 X-Forwarded-For（IP）、X-Forwarded-Proto（协议）、
	 * X-Forwarded-Host（主机）、X-Forwarded-Port（端口）。
	 * 多值头只取逗号分隔的第一个值。端口值必须是纯数字，否则在严格验证模式下会抛出异常。
	 * </p>
	 * @param connectionInfo 当前连接信息
	 * @param request HTTP 请求对象
	 * @return 更新后的连接信息
	 * @throws IllegalArgumentException 当端口值不是纯数字且启用严格验证时
	 */
	private ConnectionInfo parseXForwardedInfo(ConnectionInfo connectionInfo, HttpRequest request) {
		String ipHeader = request.headers().get(X_FORWARDED_IP_HEADER);
		if (ipHeader != null) {
			connectionInfo = connectionInfo.withRemoteAddress(
					AddressUtils.parseAddress(ipHeader.split(",", 2)[0], connectionInfo.getRemoteAddress().getPort()));
		}
		String protoHeader = request.headers().get(X_FORWARDED_PROTO_HEADER);
		if (protoHeader != null) {
			connectionInfo = connectionInfo.withScheme(protoHeader.split(",", 2)[0].trim());
		}
		String hostHeader = request.headers().get(X_FORWARDED_HOST_HEADER);
		if (hostHeader != null) {
			connectionInfo = connectionInfo
					.withHostAddress(AddressUtils.parseAddress(hostHeader.split(",", 2)[0].trim(),
							getDefaultHostPort(connectionInfo.getScheme()), DEFAULT_FORWARDED_HEADER_VALIDATION));
		}

		String portHeader = request.headers().get(X_FORWARDED_PORT_HEADER);
		if (portHeader != null && !portHeader.isEmpty()) {
			String portStr = portHeader.split(",", 2)[0].trim();
			if (portStr.chars().allMatch(Character::isDigit)) {
				int port = Integer.parseInt(portStr);
				connectionInfo = connectionInfo.withHostAddress(
						AddressUtils.createUnresolved(connectionInfo.getHostAddress().getHostString(), port),
						connectionInfo.getHostName(), port);
			}
			else if (DEFAULT_FORWARDED_HEADER_VALIDATION) {
				throw new IllegalArgumentException("Failed to parse a port from " + portHeader);
			}
		}
		return connectionInfo;
	}

}
