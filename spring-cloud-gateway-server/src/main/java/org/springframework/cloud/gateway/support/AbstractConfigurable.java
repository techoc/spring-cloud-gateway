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

import org.springframework.beans.BeanUtils;
import org.springframework.core.style.ToStringCreator;

/**
 * 可配置对象的抽象基类，实现了 {@link Configurable} 接口的通用逻辑。
 * <p>
 * 该类使用泛型参数 {@code C} 表示配置对象的类型，提供了获取配置类以及通过反射实例化 新配置对象的默认实现。子类只需继承本类并指定配置类型即可使用。
 * </p>
 *
 * @param <C> 配置对象的类型
 */
public abstract class AbstractConfigurable<C> implements Configurable<C> {

	/** 配置对象的 Class 类型，用于反射实例化配置对象 */
	private Class<C> configClass;

	/**
	 * 构造函数，初始化配置类类型。
	 * @param configClass 配置对象的 Class 类型，不可为 null
	 */
	protected AbstractConfigurable(Class<C> configClass) {
		this.configClass = configClass;
	}

	/**
	 * 返回配置对象的 Class 类型。
	 * @return 配置对象的 Class 类型
	 */
	@Override
	public Class<C> getConfigClass() {
		return configClass;
	}

	/**
	 * 通过反射实例化并返回一个新的配置对象。
	 * <p>
	 * 使用 {@link BeanUtils#instantiateClass(Class)} 创建配置对象， 要求配置类必须有无参构造函数。
	 * </p>
	 * @return 新创建的配置对象实例
	 */
	@Override
	public C newConfig() {
		return BeanUtils.instantiateClass(this.configClass);
	}

	/**
	 * 返回当前对象的字符串表示，包含配置类信息。
	 * @return 对象的字符串描述
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("configClass", configClass).toString();
	}

}
