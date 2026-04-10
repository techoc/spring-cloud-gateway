/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.webflux.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.webflux.ProxyExchange;
import org.springframework.http.HttpHeaders;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 代理交换器的配置属性类。
 * <p>
 * 用于在 <code>@RequestMapping</code> 方法中配置 {@link ProxyExchange} 参数处理器的行为。 通过
 * {@link ConfigurationProperties} 注解，这些属性可以从 application.yml 或 application.properties
 * 文件中读取，配置前缀为 <code>spring.cloud.gateway.proxy</code>。
 * <p>
 * 配置示例（application.yml）： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       proxy:
 *         headers:
 *           X-Custom-Header: custom-value
 *         auto-forward:
 *           - X-Request-ID
 *           - X-User-ID
 *         sensitive:
 *           - authorization
 *           - cookie
 *           - x-auth-token
 * </pre>
 * <p>
 * 可配置属性：
 * <ul>
 * <li><b>headers</b> - 固定请求头，将添加到所有下游请求中</li>
 * <li><b>auto-forward</b> - 需要自动转发的请求头名称集合</li>
 * <li><b>sensitive</b> - 敏感请求头名称集合，默认不会发送到下游服务</li>
 * </ul>
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 * @see ConfigurationProperties
 * @see ProxyExchange
 */
@ConfigurationProperties("spring.cloud.gateway.proxy")
public class ProxyProperties {

	/**
	 * 固定请求头值集合，将添加到所有下游请求中。
	 * <p>
	 * 这些请求头会在代理转发时自动包含到后端服务的请求中。 使用 {@link LinkedHashMap} 保持插入顺序。
	 */
	private Map<String, String> headers = new LinkedHashMap<>();

	/**
	 * 默认需要自动转发的请求头名称集合。
	 * <p>
	 * 当接收到客户端请求时，这些指定的请求头会自动从原始请求中提取并转发到下游服务。 使用 {@link HashSet} 提供快速的查找性能。
	 */
	private Set<String> autoForward = new HashSet<>();

	/**
	 * 敏感请求头名称集合，默认不会发送到下游服务。
	 * <p>
	 * 被标记为敏感的请求头（如 Authorization、Cookie 等）出于安全考虑不会被转发。 如果设置为 {@code null}，则使用
	 * {@link ProxyExchange#DEFAULT_SENSITIVE} 默认值。
	 */
	private Set<String> sensitive = null;

	/**
	 * 返回固定请求头的键值对映射。
	 * @return 固定请求头集合，键为请求头名称，值为请求头值
	 */
	public Map<String, String> getHeaders() {
		return headers;
	}

	/**
	 * 设置固定请求头的键值对映射。
	 * @param headers 固定请求头集合
	 */
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	/**
	 * 返回需要自动转发的请求头名称集合。
	 * @return 自动转发的请求头名称集合
	 */
	public Set<String> getAutoForward() {
		return autoForward;
	}

	/**
	 * 设置需要自动转发的请求头名称集合。
	 * @param autoForward 自动转发的请求头名称集合
	 */
	public void setAutoForward(Set<String> autoForward) {
		this.autoForward = autoForward;
	}

	/**
	 * 返回敏感请求头名称集合。
	 * <p>
	 * 这些请求头默认不会转发到下游服务。若返回 {@code null}， 则使用框架默认的敏感请求头列表（cookie、authorization）。
	 * @return 敏感请求头名称集合，可能为 {@code null}
	 * @see ProxyExchange#DEFAULT_SENSITIVE
	 */
	public Set<String> getSensitive() {
		return sensitive;
	}

	/**
	 * 设置敏感请求头名称集合。
	 * @param sensitive 敏感请求头名称集合
	 */
	public void setSensitive(Set<String> sensitive) {
		this.sensitive = sensitive;
	}

	/**
	 * 将配置的固定请求头映射转换为 {@link HttpHeaders} 对象。
	 * <p>
	 * 遍历 {@link #headers} 中的所有键值对并逐一写入，供代理请求时直接使用。
	 * @return 包含所有固定请求头的 {@link HttpHeaders} 实例
	 */
	public HttpHeaders convertHeaders() {
		HttpHeaders headers = new HttpHeaders();
		for (String key : this.headers.keySet()) {
			headers.set(key, this.headers.get(key));
		}
		return headers;
	}

}
