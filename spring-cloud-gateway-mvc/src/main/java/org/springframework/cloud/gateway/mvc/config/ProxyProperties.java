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

package org.springframework.cloud.gateway.mvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.http.HttpHeaders;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 用于@RequestMapping方法中{@link ProxyExchange}参数处理器的配置属性。
 *
 * @author Dave Syer
 * @author Tim Ysewyn
 */
@ConfigurationProperties("spring.cloud.gateway.proxy")
public class ProxyProperties {

	/**
	 * 将添加到所有下游请求的固定头部值。
	 */
	private Map<String, String> headers = new LinkedHashMap<>();

	/**
	 * 默认情况下应发送到下游的头部名称集合。
	 */
	private Set<String> autoForward = new HashSet<>();

	/**
	 * 默认情况下不会发送到下游的敏感头部名称集合。
	 */
	private Set<String> sensitive = null;

	/**
	 * 获取固定头部值映射。
	 *
	 * @return 头部键值对映射
	 */
	public Map<String, String> getHeaders() {
		return headers;
	}

	/**
	 * 设置固定头部值映射。
	 * @param headers 头部键值对映射
	 */
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	/**
	 * 获取自动转发头部名称集合。
	 * @return 自动转发的头部名称集合
	 */
	public Set<String> getAutoForward() {
		return autoForward;
	}

	/**
	 * 设置自动转发头部名称集合。
	 * @param autoForward 自动转发的头部名称集合
	 */
	public void setAutoForward(Set<String> autoForward) {
		this.autoForward = autoForward;
	}

	/**
	 * 获取敏感头部名称集合。
	 * @return 敏感头部名称集合
	 */
	public Set<String> getSensitive() {
		return sensitive;
	}

	/**
	 * 设置敏感头部名称集合。
	 * @param sensitive 敏感头部名称集合
	 */
	public void setSensitive(Set<String> sensitive) {
		this.sensitive = sensitive;
	}

	/**
	 * 将配置的头部转换为HttpHeaders对象。
	 * @return HttpHeaders对象
	 */
	public HttpHeaders convertHeaders() {
		HttpHeaders headers = new HttpHeaders();
		for (String key : this.headers.keySet()) {
			headers.set(key, this.headers.get(key));
		}
		return headers;
	}

}
