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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.IgnoreTopLevelConverterNotFoundBindHandler;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.Assert;
import org.springframework.validation.Validator;

/**
 * 配置服务类，负责管理网关组件的配置绑定和处理。
 * <p>
 * 该类是 Spring Cloud Gateway 配置体系的核心组件，提供以下功能：
 * <ul>
 * <li>将 YAML/Properties 配置属性绑定到配置对象</li>
 * <li>支持 Spring EL (SpEL) 表达式解析</li>
 * <li>支持配置验证</li>
 * <li>支持配置变更事件发布</li>
 * <li>处理 AOP 代理对象的解包</li>
 * </ul>
 * </p>
 * <p>
 * 该服务通过内部构建器模式（{@link AbstractBuilder}、{@link ConfigurableBuilder}、
 * {@link InstanceBuilder}）提供灵活的配置绑定接口。
 * </p>
 *
 * @author Spencer Gibb
 * @see ConfigurableBuilder
 * @see InstanceBuilder
 * @see ShortcutConfigurable
 */
public class ConfigurationService implements ApplicationEventPublisherAware {

	/** 应用事件发布器，用于发布配置变更事件 */
	private ApplicationEventPublisher publisher;

	/** Spring Bean 工厂，用于获取 Bean 实例 */
	private BeanFactory beanFactory;

	/** 类型转换服务提供者，用于属性值转换 */
	private Supplier<ConversionService> conversionService;

	/** SpEL 表达式解析器，用于解析配置中的 SpEL 表达式 */
	private SpelExpressionParser parser = new SpelExpressionParser();

	/** 验证器提供者，用于配置验证 */
	private Supplier<Validator> validator;

	/**
	 * 构造函数，使用 ObjectProvider 构造。
	 * @param beanFactory Spring Bean 工厂
	 * @param conversionService 类型转换服务提供者
	 * @param validator 验证器提供者
	 */
	public ConfigurationService(BeanFactory beanFactory, ObjectProvider<ConversionService> conversionService,
			ObjectProvider<Validator> validator) {
		this.beanFactory = beanFactory;
		this.conversionService = conversionService::getIfAvailable;
		this.validator = validator::getIfAvailable;
	}

	/**
	 * 构造函数，使用直接 Supplier 构造。
	 * @param beanFactory Spring Bean 工厂
	 * @param conversionService 类型转换服务
	 * @param validator 验证器
	 */
	public ConfigurationService(BeanFactory beanFactory, Supplier<ConversionService> conversionService,
			Supplier<Validator> validator) {
		this.beanFactory = beanFactory;
		this.conversionService = conversionService;
		this.validator = validator;
	}

	/**
	 * 获取应用事件发布器。
	 * @return 事件发布器
	 */
	public ApplicationEventPublisher getPublisher() {
		return this.publisher;
	}

	/**
	 * 设置应用事件发布器。
	 * @param publisher 事件发布器
	 */
	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	/**
	 * 为可配置对象创建构建器。
	 * <p>
	 * 使用 fluent 风格创建配置构建器，用于绑定和验证配置。
	 * </p>
	 * @param configurable 实现 {@link ShortcutConfigurable} 的可配置对象
	 * @param <T> 配置对象的类型
	 * @param <C> 可配置对象的类型
	 * @return 配置构建器实例
	 */
	public <T, C extends Configurable<T> & ShortcutConfigurable> ConfigurableBuilder<T, C> with(C configurable) {
		return new ConfigurableBuilder<T, C>(this, configurable);
	}

	/**
	 * 为已有实例创建构建器。
	 * <p>
	 * 适用于将配置绑定到已存在的对象实例。
	 * </p>
	 * @param instance 要绑定配置的实例
	 * @param <T> 实例的类型
	 * @return 实例构建器
	 */
	public <T> InstanceBuilder<T> with(T instance) {
		return new InstanceBuilder<T>(this, instance);
	}

	/**
	 * 静态方法：绑定或创建配置对象。
	 * <p>
	 * 这是配置绑定的核心方法，使用 Spring Boot 的配置绑定机制。
	 * </p>
	 * @param bindable 可绑定的目标对象
	 * @param properties 属性映射
	 * @param configurationPropertyName 配置属性前缀名称
	 * @param validator 验证器
	 * @param conversionService 类型转换服务
	 * @param <T> 配置类型
	 * @return 绑定后的配置对象
	 */
	/* for testing */ static <T> T bindOrCreate(Bindable<T> bindable, Map<String, Object> properties,
			String configurationPropertyName, Validator validator, ConversionService conversionService) {
		// 参考 Spring Boot 的 ConfigurationPropertiesBinder 实现
		BindHandler handler = new IgnoreTopLevelConverterNotFoundBindHandler();

		if (validator != null) { // TODO: list of validators?
			handler = new ValidationBindHandler(handler, validator);
		}

		List<ConfigurationPropertySource> propertySources = Collections
				.singletonList(new MapConfigurationPropertySource(properties));

		return new Binder(propertySources, null, conversionService).bindOrCreate(configurationPropertyName, bindable,
				handler);
	}

	/**
	 * 静态方法：从 AOP 代理中获取原始目标对象。
	 * <p>
	 * 如果对象是 Spring AOP 代理，则解包并返回原始目标对象； 否则直接返回原对象。
	 * </p>
	 * @param candidate 可能是代理的对象
	 * @param <T> 目标类型
	 * @return 原始目标对象
	 * @throws IllegalStateException 解包失败时抛出
	 */
	@SuppressWarnings("unchecked")
	/* for testing */ static <T> T getTargetObject(Object candidate) {
		try {
			if (AopUtils.isAopProxy(candidate) && (candidate instanceof Advised)) {
				return (T) ((Advised) candidate).getTargetSource().getTarget();
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to unwrap proxied object", ex);
		}
		return (T) candidate;
	}

	/**
	 * 可配置对象构建器，用于构建实现 {@link ShortcutConfigurable} 接口的对象配置。
	 * <p>
	 * 支持配置属性的快捷绑定和规范化处理。
	 *
	 * @param <T> 配置对象类型
	 * @param <C> 可配置对象类型
	 */
	public static class ConfigurableBuilder<T, C extends Configurable<T> & ShortcutConfigurable>
			extends AbstractBuilder<T, ConfigurableBuilder<T, C>> {

		/** 要绑定的可配置对象 */
		private final C configurable;

		/**
		 * 构造函数。
		 * @param service 配置服务
		 * @param configurable 可配置对象
		 */
		public ConfigurableBuilder(ConfigurationService service, C configurable) {
			super(service);
			this.configurable = configurable;
		}

		@Override
		protected ConfigurableBuilder<T, C> getThis() {
			return this;
		}

		@Override
		protected void validate() {
			Assert.notNull(this.configurable, "configurable may not be null");
		}

		/**
		 * 规范化属性，处理快捷配置格式。
		 */
		@Override
		protected Map<String, Object> normalizeProperties() {
			if (this.service.beanFactory != null) {
				return this.configurable.shortcutType().normalize(this.properties, this.configurable,
						this.service.parser, this.service.beanFactory);
			}
			return super.normalizeProperties();
		}

		@Override
		protected T doBind() {
			Bindable<T> bindable = Bindable.of(this.configurable.getConfigClass());
			T bound = bindOrCreate(bindable, this.normalizedProperties, this.configurable.shortcutFieldPrefix(),
					/* this.name, */this.service.validator.get(), this.service.conversionService.get());

			return bound;
		}

	}

	/**
	 * 实例构建器，用于为已有实例绑定配置。
	 *
	 * @param <T> 实例类型
	 */
	public static class InstanceBuilder<T> extends AbstractBuilder<T, InstanceBuilder<T>> {

		/** 要绑定配置的实例 */
		private final T instance;

		/**
		 * 构造函数。
		 * @param service 配置服务
		 * @param instance 目标实例
		 */
		public InstanceBuilder(ConfigurationService service, T instance) {
			super(service);
			this.instance = instance;
		}

		@Override
		protected InstanceBuilder<T> getThis() {
			return this;
		}

		@Override
		protected void validate() {
			Assert.notNull(this.instance, "instance may not be null");
		}

		@Override
		protected T doBind() {
			T toBind = getTargetObject(this.instance);
			Bindable<T> bindable = Bindable.ofInstance(toBind);
			return bindOrCreate(bindable, this.normalizedProperties, this.name, this.service.validator.get(),
					this.service.conversionService.get());
		}

	}

	/**
	 * 配置构建器的抽象基类，提供通用的构建流程。
	 * <p>
	 * 构建流程：验证 -> 规范化属性 -> 绑定 -> 发布事件
	 *
	 * @param <T> 配置对象类型
	 * @param <B> 构建器类型（用于方法链）
	 */
	public static abstract class AbstractBuilder<T, B extends AbstractBuilder<T, B>> {

		/** 配置服务引用 */
		protected final ConfigurationService service;

		/** 事件函数，用于生成配置变更事件 */
		protected BiFunction<T, Map<String, Object>, ApplicationEvent> eventFunction;

		/** 配置名称 */
		protected String name;

		/** 规范化后的属性映射 */
		protected Map<String, Object> normalizedProperties;

		/** 原始属性映射 */
		protected Map<String, String> properties;

		/**
		 * 构造函数。
		 * @param service 配置服务
		 */
		public AbstractBuilder(ConfigurationService service) {
			this.service = service;
		}

		/** 返回 this，用于方法链 */
		protected abstract B getThis();

		/**
		 * 设置配置名称。
		 * @param name 配置名称
		 * @return this
		 */
		public B name(String name) {
			this.name = name;
			return getThis();
		}

		/**
		 * 设置事件函数。
		 * @param eventFunction 事件生成函数
		 * @return this
		 */
		public B eventFunction(BiFunction<T, Map<String, Object>, ApplicationEvent> eventFunction) {
			this.eventFunction = eventFunction;
			return getThis();
		}

		/**
		 * 设置规范化后的属性。
		 * @param normalizedProperties 规范化属性映射
		 * @return this
		 */
		public B normalizedProperties(Map<String, Object> normalizedProperties) {
			this.normalizedProperties = normalizedProperties;
			return getThis();
		}

		/**
		 * 设置原始属性。
		 * @param properties 原始属性映射
		 * @return this
		 */
		public B properties(Map<String, String> properties) {
			this.properties = properties;
			return getThis();
		}

		/** 验证输入 */
		protected abstract void validate();

		/**
		 * 规范化属性，默认为直接复制。
		 * @return 规范化后的属性映射
		 */
		protected Map<String, Object> normalizeProperties() {
			Map<String, Object> normalizedProperties = new HashMap<>();
			this.properties.forEach(normalizedProperties::put);
			return normalizedProperties;
		}

		/** 执行绑定 */
		protected abstract T doBind();

		/**
		 * 执行完整的绑定流程。
		 * <p>
		 * 流程：验证 -> 规范化属性 -> 绑定 -> 发布事件
		 * </p>
		 * @return 绑定后的配置对象
		 */
		public T bind() {
			validate();
			Assert.hasText(this.name, "name may not be empty");
			Assert.isTrue(this.properties != null || this.normalizedProperties != null,
					"properties and normalizedProperties both may not be null");

			if (this.normalizedProperties == null) {
				this.normalizedProperties = normalizeProperties();
			}

			T bound = doBind();

			if (this.eventFunction != null && this.service.publisher != null) {
				ApplicationEvent applicationEvent = this.eventFunction.apply(bound, this.normalizedProperties);
				this.service.publisher.publishEvent(applicationEvent);
			}

			return bound;
		}

	}

}
