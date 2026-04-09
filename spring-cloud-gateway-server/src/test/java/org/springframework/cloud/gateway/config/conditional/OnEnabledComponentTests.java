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

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OnEnabledComponentTests - 组件启用条件判断测试类
 *
 * 本测试类验证OnEnabledComponent条件判断逻辑： - 默认情况下组件应该被匹配（启用） -
 * 当配置spring.cloud.gateway.{component-name}.enabled=false时，组件不应被匹配
 *
 * @author test
 */
class OnEnabledComponentTests {

	/** 待测试的OnEnabledComponent实例 */
	private OnEnabledComponent<Object> onEnabledComponent;

	/** 模拟环境对象 */
	private MockEnvironment environment;

	/** 条件上下文 */
	private ConditionContext conditionContext;

	/**
	 * setUp - 测试前准备
	 *
	 * 初始化测试所需的组件和环境对象
	 */
	@BeforeEach
	void setUp() {
		this.onEnabledComponent = createOnEnabledComponent("test-class");
		this.environment = new MockEnvironment();
		this.conditionContext = mock(ConditionContext.class);
	}

	/**
	 * shouldMatchComponent - 测试默认启用组件应该被匹配
	 * <p>
	 * 验证当没有禁用配置时，组件条件判断应该返回匹配
	 */
	@Test
	public void shouldMatchComponent() {
		when(conditionContext.getEnvironment()).thenReturn(environment);

		ConditionOutcome outcome = onEnabledComponent.getMatchOutcome(conditionContext,
				mockMetaData(EnabledComponent.class));

		assertThat(outcome.isMatch()).isTrue();
	}

	/**
	 * shouldNotMatchDisabledComponent - 测试禁用组件不应该被匹配
	 * <p>
	 * 验证当配置spring.cloud.gateway.{component-name}.enabled=false时 组件条件判断应该返回不匹配，并包含正确的错误消息
	 */
	@Test
	public void shouldNotMatchDisabledComponent() {
		String componentName = "disabled-component";
		this.onEnabledComponent = createOnEnabledComponent(componentName);
		when(conditionContext.getEnvironment()).thenReturn(environment);
		environment.setProperty("spring.cloud.gateway." + componentName + ".enabled", "false");

		ConditionOutcome outcome = onEnabledComponent.getMatchOutcome(conditionContext,
				mockMetaData(DisabledComponent.class));

		assertThat(outcome.isMatch()).isFalse();
		assertThat(outcome.getMessage()).contains("DisabledComponent").contains("bean is not available");
	}

	/**
	 * mockMetaData - 创建模拟的注解元数据
	 * @param value 注解属性值
	 * @return AnnotatedTypeMetadata模拟对象
	 */
	private AnnotatedTypeMetadata mockMetaData(Class<?> value) {
		AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
		when(metadata.getAnnotationAttributes(eq(ConditionalOnEnabledFilter.class.getName())))
				.thenReturn(Collections.singletonMap("value", value));
		return metadata;
	}

	/**
	 * createOnEnabledComponent - 创建测试用的OnEnabledComponent实例
	 * @param componentName 组件名称
	 * @return OnEnabledComponent实例
	 */
	private OnEnabledComponent<Object> createOnEnabledComponent(String componentName) {
		return new OnEnabledComponent<Object>() {
			@Override
			protected String normalizeComponentName(Class<?> componentClass) {
				return componentName;
			}

			@Override
			protected Class<?> annotationClass() {
				return ConditionalOnEnabledFilter.class;
			}

			@Override
			protected Class<?> defaultValueClass() {
				return Object.class;
			}
		};
	}

	/**
	 * EnabledComponent - 启用的测试组件
	 */
	protected static class EnabledComponent {

	}

	/**
	 * DisabledComponent - 禁用的测试组件
	 */
	protected static class DisabledComponent {

	}

}
