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

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.style.ToStringCreator;

/**
 * 有状态可配置对象的抽象基类，继承自 {@link AbstractConfigurable} 并实现了 {@link StatefulConfigurable} 接口。
 * <p>
 * 该类在无状态配置基础上扩展了状态管理能力，内部维护一个以路由 ID 为 key、 配置对象为 value 的 Map，用于存储各个路由对应的配置实例。
 * 典型使用场景包括需要按路由单独维护配置的过滤器工厂。
 * </p>
 *
 * @param <C> 配置对象的类型
 */
public abstract class AbstractStatefulConfigurable<C> extends AbstractConfigurable<C>
		implements StatefulConfigurable<C> {

	/**
	 * 存储路由 ID 与配置对象的映射关系。 key 为路由 ID，value 为对应的配置对象实例。
	 */
	private Map<String, C> config = new HashMap<>();

	/**
	 * 构造函数，初始化配置类类型。
	 * @param configClass 配置对象的 Class 类型，不可为 null
	 */
	protected AbstractStatefulConfigurable(Class<C> configClass) {
		super(configClass);
	}

	/**
	 * 返回路由 ID 与配置对象的映射 Map。
	 * <p>
	 * 返回的 Map 以路由 ID 为键，对应的配置对象为值，调用方可通过路由 ID 查询或更新特定路由的配置。
	 * </p>
	 * @return 路由 ID 到配置对象的映射，不会为 null
	 */
	@Override
	public Map<String, C> getConfig() {
		return this.config;
	}

	/**
	 * 返回当前对象的字符串表示，包含 config 映射和配置类信息。
	 * @return 对象的字符串描述
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("config", config).append("configClass", getConfigClass()).toString();
	}

}
