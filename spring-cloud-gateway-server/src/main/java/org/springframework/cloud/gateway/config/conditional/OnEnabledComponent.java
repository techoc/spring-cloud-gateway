/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.config.conditional;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import static org.springframework.boot.autoconfigure.condition.ConditionMessage.forCondition;

/**
 * Gateway 组件条件化注册的抽象基类。
 * <p>
 * 该类是 {@code @ConditionalOnEnabledFilter}、{@code @ConditionalOnEnabledGlobalFilter} 和
 * {@code @ConditionalOnEnabledPredicate} 注解的条件匹配逻辑实现。
 * <p>
 * 通过检查 Spring 配置属性 {@code spring.cloud.gateway.{type}.{name}.enabled} 来决定组件是否应该被注册。
 * 当该属性值为 {@code false} 时，组件不会被注册到 Spring 容器中。
 * <p>
 * 子类需要实现抽象方法，指定组件类型、注解类型和属性名称的规范化方式。
 *
 * @param <T> 组件类型参数，可以是 {@code GatewayFilterFactory}、{@code GlobalFilter} 或
 * {@code RoutePredicateFactory} 等
 * @see org.springframework.boot.autoconfigure.condition.SpringBootCondition
 * @see org.springframework.context.annotation.ConfigurationCondition
 */
public abstract class OnEnabledComponent<T> extends SpringBootCondition implements ConfigurationCondition {

	/** Spring Cloud Gateway 配置属性的前缀 */
	private static final String PREFIX = "spring.cloud.gateway.";

	/** Spring Cloud Gateway 配置属性的后缀 */
	private static final String SUFFIX = ".enabled";

	/**
	 * 返回配置条件的评估阶段。
	 * <p>
	 * 此条件在 Bean 注册阶段进行评估，确保条件不匹配的 Bean 不会被注册到容器中。
	 * @return 配置阶段，始终返回 {@link ConfigurationPhase#REGISTER_BEAN}
	 */
	@Override
	public ConfigurationPhase getConfigurationPhase() {
		return ConfigurationPhase.REGISTER_BEAN;
	}

	/**
	 * 评估条件是否匹配。
	 * <p>
	 * 获取标注元素指定的组件类型，然后根据配置属性判断是否应该注册该组件。
	 * @param context 条件上下文，包含环境信息和 Bean 工厂等
	 * @param metadata 注解元数据，包含被检查注解的属性信息
	 * @return 条件评估结果，指示组件是否应该被注册
	 */
	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Class<? extends T> candidate = getComponentType(annotationClass(), context, metadata);
		return determineOutcome(candidate, context.getEnvironment());
	}

	/**
	 * 从注解元数据中获取组件类型。
	 * <p>
	 * 如果注解的 {@code value} 属性已指定且不为默认值，则使用该值。 否则，从标注方法的返回类型中提取组件类型。
	 * @param annotationClass 条件注解的 Class 对象
	 * @param context 条件上下文
	 * @param metadata 注解元数据
	 * @return 要检查的组件类型
	 * @throws IllegalStateException 如果无法从方法返回类型提取组件类
	 */
	@SuppressWarnings("unchecked")
	protected Class<? extends T> getComponentType(Class<?> annotationClass, ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		Map<String, Object> attributes = metadata.getAnnotationAttributes(annotationClass.getName());
		if (attributes != null && attributes.containsKey("value")) {
			Class<?> target = (Class<?>) attributes.get("value");
			if (target != defaultValueClass()) {
				return (Class<? extends T>) target;
			}
		}
		Assert.state(metadata instanceof MethodMetadata && metadata.isAnnotated(Bean.class.getName()),
				getClass().getSimpleName() + " must be used on @Bean methods when the value is not specified");
		MethodMetadata methodMetadata = (MethodMetadata) metadata;
		try {
			return (Class<? extends T>) ClassUtils.forName(methodMetadata.getReturnTypeName(),
					context.getClassLoader());
		}
		catch (Throwable ex) {
			throw new IllegalStateException("Failed to extract component class for "
					+ methodMetadata.getDeclaringClassName() + "." + methodMetadata.getMethodName(), ex);
		}
	}

	/**
	 * 根据组件类和属性解析器确定条件匹配结果。
	 * <p>
	 * 构建配置属性键 {@code spring.cloud.gateway.{type}.{name}.enabled}， 检查该属性的值。如果属性值为
	 * {@code false}，则条件不匹配。
	 * @param componentClass 组件类
	 * @param resolver 属性解析器，用于读取配置属性
	 * @return 条件评估结果
	 */
	private ConditionOutcome determineOutcome(Class<? extends T> componentClass, PropertyResolver resolver) {
		String key = PREFIX + normalizeComponentName(componentClass) + SUFFIX;
		ConditionMessage.Builder messageBuilder = forCondition(annotationClass().getName(), componentClass.getName());
		if ("false".equalsIgnoreCase(resolver.getProperty(key))) {
			return ConditionOutcome.noMatch(messageBuilder.because("bean is not available"));
		}
		return ConditionOutcome.match();
	}

	/**
	 * 将组件类名规范化为配置属性中的名称格式。
	 * <p>
	 * 子类需要实现此方法，将组件的类名转换为配置属性中使用的名称。 例如，对于过滤器工厂，名称可能被转换为类似 "add-request-header" 的格式。
	 * @param componentClass 组件类
	 * @return 规范化的组件名称，用于构建配置属性键
	 */
	protected abstract String normalizeComponentName(Class<? extends T> componentClass);

	/**
	 * 返回此条件对应的注解类。
	 * <p>
	 * 子类需要实现此方法，返回用于触发条件检查的注解类型。
	 * @return 条件注解的 Class 对象
	 */
	protected abstract Class<?> annotationClass();

	/**
	 * 返回注解 {@code value} 属性的默认值类。
	 * <p>
	 * 当注解的 {@code value} 属性未指定时使用此默认值类， 该类用于标识属性未被显式指定的情况。
	 * @return 默认值类的类型
	 */
	protected abstract Class<? extends T> defaultValueClass();

}
