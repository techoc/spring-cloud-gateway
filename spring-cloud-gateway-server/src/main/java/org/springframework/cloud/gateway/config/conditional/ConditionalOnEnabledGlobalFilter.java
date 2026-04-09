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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Conditional;

/**
 * 条件化注解，用于根据配置属性控制全局过滤器 Bean 的注册。
 * <p>
 * 当指定全局过滤器对应的配置属性 {@code spring.cloud.gateway.global-filter.<filter-name>.enabled} 不为
 * {@code false} 时， 标注的 Bean 才会被注册到 Spring 容器中。
 * <p>
 * 该注解提供了一种机制，允许用户通过配置文件禁用不需要的全局过滤器， 从而在运行时控制哪些全局过滤器生效。
 *
 * @see OnEnabledGlobalFilter
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Documented
@Conditional(OnEnabledGlobalFilter.class)
public @interface ConditionalOnEnabledGlobalFilter {

	/**
	 * 要检查的全局过滤器类组件。
	 * <p>
	 * 指定需要检查启用状态的 {@link GlobalFilter} 实现类。 当未指定值时，将根据标注方法的返回类型自动推断。
	 * @return 必须启用才能注册 Bean 的全局过滤器类
	 */
	Class<? extends GlobalFilter> value() default OnEnabledGlobalFilter.DefaultValue.class;

}
