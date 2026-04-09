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

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.cloud.gateway.support.AbstractConfigurable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

/**
 * 网关过滤器工厂抽象基类。
 * <p>
 * 该类是所有具体过滤器工厂的父类，提供了配置管理、应用事件发布等基础功能。 子类通过实现 {@link GatewayFilterFactory#apply(Object)}
 * 方法来定义具体的过滤器逻辑。
 * <p>
 * 特性：
 * <ul>
 * <li>继承自 {@link AbstractConfigurable}，支持配置类的管理与快捷字段解析；</li>
 * <li>实现 {@link ApplicationEventPublisherAware}，可发布 Spring 事件；</li>
 * <li>支持通过构造函数指定配置类的类型。</li>
 * </ul>
 *
 * @param <C> 配置类类型，必须继承自 {@link AbstractConfigurable}
 * @author Spencer Gibb
 */
public abstract class AbstractGatewayFilterFactory<C> extends AbstractConfigurable<C>
		implements GatewayFilterFactory<C>, ApplicationEventPublisherAware {

	/** Spring 应用事件发布器，用于发布过滤器相关事件 */
	private ApplicationEventPublisher publisher;

	/**
	 * 默认构造方法，使用 {@code Object.class} 作为配置类型。 适用于不需要特定配置类的简单过滤器工厂。
	 */
	@SuppressWarnings("unchecked")
	public AbstractGatewayFilterFactory() {
		super((Class<C>) Object.class);
	}

	/**
	 * 构造方法，指定配置类的类型。
	 * @param configClass 配置类的 Class 类型
	 */
	public AbstractGatewayFilterFactory(Class<C> configClass) {
		super(configClass);
	}

	/**
	 * 获取应用事件发布器。
	 * @return ApplicationEventPublisher 实例
	 */
	protected ApplicationEventPublisher getPublisher() {
		return this.publisher;
	}

	/**
	 * 设置应用事件发布器。
	 * @param publisher Spring 应用事件发布器
	 */
	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/**
	 * 仅包含名称字段的配置类，用于只需要一个名称参数的过滤器。
	 * <p>
	 * 例如
	 * {@link org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory}
	 * 使用此类作为配置。
	 */
	public static class NameConfig {

		/** 名称字段值 */
		private String name;

		/**
		 * 获取名称。
		 * @return 名称字符串
		 */
		public String getName() {
			return name;
		}

		/**
		 * 设置名称。
		 * @param name 名称字符串
		 */
		public void setName(String name) {
			this.name = name;
		}

	}

}
