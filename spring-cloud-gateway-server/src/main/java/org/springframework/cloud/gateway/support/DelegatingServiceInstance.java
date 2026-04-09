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

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;

/**
 * 服务实例的委托实现类，使用委托模式包装底层的服务实例。
 * <p>
 * 该类实现 Spring Cloud 的 {@link ServiceInstance} 接口，将所有调用委托给被包装的 实际服务实例，同时支持覆盖特定的属性（如
 * scheme）。这在网关需要动态修改 服务实例的某些属性时非常有用，例如根据路由配置覆盖协议（http/https）。
 * </p>
 * <p>
 * 典型使用场景：
 * <ul>
 * <li>强制使用 HTTPS 协议访问后端服务</li>
 * <li>动态修改服务实例的端口或路径</li>
 * <li>包装服务发现返回的原始实例，添加额外逻辑</li>
 * </ul>
 * </p>
 *
 * @author Spencer Gibb
 * @see ServiceInstance
 */
public class DelegatingServiceInstance implements ServiceInstance {

	/** 被委托的实际服务实例，不可为 null */
	final ServiceInstance delegate;

	/**
	 * 覆盖的协议scheme，用于替换委托实例的协议。 支持的值包括 "http"、"https"、"ws"、"wss" 等。
	 */
	private String overrideScheme;

	/**
	 * 构造函数，创建包装了指定服务实例的委托实例。
	 * @param delegate 被委托的实际服务实例
	 * @param overrideScheme 要覆盖的协议scheme，为 null 则不覆盖
	 */
	public DelegatingServiceInstance(ServiceInstance delegate, String overrideScheme) {
		this.delegate = delegate;
		this.overrideScheme = overrideScheme;
	}

	/**
	 * 返回服务 ID，直接委托给被包装的实例。
	 */
	@Override
	public String getServiceId() {
		return delegate.getServiceId();
	}

	/**
	 * 返回服务主机名，直接委托给被包装的实例。
	 */
	@Override
	public String getHost() {
		return delegate.getHost();
	}

	/**
	 * 返回服务端口，直接委托给被包装的实例。
	 */
	@Override
	public int getPort() {
		return delegate.getPort();
	}

	/**
	 * 返回是否安全访问。
	 * <p>
	 * 优先级：若覆盖scheme为 "https" 或 "wss"，返回 true； 否则委托给被包装实例的 isSecure() 方法。
	 * </p>
	 * @return true 表示安全连接，false 表示非安全连接
	 */
	@Override
	public boolean isSecure() {
		// TODO: move to map
		if ("https".equals(this.overrideScheme) || "wss".equals(this.overrideScheme)) {
			return true;
		}
		return delegate.isSecure();
	}

	/**
	 * 返回服务的完整 URI，直接委托给被包装的实例。
	 */
	@Override
	public URI getUri() {
		return delegate.getUri();
	}

	/**
	 * 返回服务的元数据映射，直接委托给被包装的实例。
	 */
	@Override
	public Map<String, String> getMetadata() {
		return delegate.getMetadata();
	}

	/**
	 * 返回服务的协议scheme。
	 * <p>
	 * 优先返回被包装实例的scheme，若为 null 则返回覆盖的scheme。
	 * </p>
	 * @return 协议scheme，若均无则返回 null
	 */
	@Override
	public String getScheme() {
		String scheme = delegate.getScheme();
		if (scheme != null) {
			return scheme;
		}
		return this.overrideScheme;
	}

}
