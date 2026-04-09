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

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.support.NameUtils;

/**
 * {@link OnEnabledComponent} 的具体实现，用于控制 {@link GatewayFilterFactory} Bean 的条件注册。
 * <p>
 * 此类与 {@link ConditionalOnEnabledFilter} 注解配合使用， 通过检查配置属性
 * {@code spring.cloud.gateway.filter.<filter-name>.enabled} 来决定 过滤器工厂 Bean 是否应该被注册。
 * <p>
 * 特殊处理：当检测到是断路器过滤器工厂时，会使用规范化的断路器名称格式。
 *
 * @see ConditionalOnEnabledFilter
 * @see OnEnabledComponent
 */
public class OnEnabledFilter extends OnEnabledComponent<GatewayFilterFactory<?>> {

	/**
	 * 将过滤器工厂类名规范化为配置属性中的名称格式。
	 * <p>
	 * 生成的配置属性键格式为： {@code spring.cloud.gateway.filter.<normalized-name>.enabled}
	 * <p>
	 * 对于 {@link SpringCloudCircuitBreakerFilterFactory} 的子类， 使用其预定义的规范名称。
	 * @param filterClass 过滤器工厂类
	 * @return 规范化的属性名称前缀（不含前缀和后缀）
	 */
	@Override
	protected String normalizeComponentName(Class<? extends GatewayFilterFactory<?>> filterClass) {
		if (SpringCloudCircuitBreakerFilterFactory.class.isAssignableFrom(filterClass)) {
			return "filter."
					+ NameUtils.normalizeToCanonicalPropertyFormat(SpringCloudCircuitBreakerFilterFactory.NAME);
		}
		else {
			return "filter." + NameUtils.normalizeFilterFactoryNameAsProperty(filterClass);
		}
	}

	/**
	 * 返回此条件对应的注解类。
	 * @return {@link ConditionalOnEnabledFilter} 类
	 */
	@Override
	protected Class<?> annotationClass() {
		return ConditionalOnEnabledFilter.class;
	}

	/**
	 * 返回注解 value 属性的默认值类。
	 * @return {@link DefaultValue} 类
	 */
	@Override
	protected Class<? extends GatewayFilterFactory<?>> defaultValueClass() {
		return DefaultValue.class;
	}

	/**
	 * 默认值标记类，用于标识注解的 value 属性未被显式指定。
	 * <p>
	 * 当 {@link ConditionalOnEnabledFilter} 注解未指定 value 属性时使用此类作为默认值。
	 * 此类永远不会被实例化，其任何方法调用都会抛出 {@link UnsupportedOperationException}。
	 */
	static class DefaultValue implements GatewayFilterFactory<Object> {

		/**
		 * 不支持的操作。
		 * @param config 配置对象
		 * @return 不返回任何值
		 * @throws UnsupportedOperationException 始终抛出此异常
		 */
		@Override
		public GatewayFilter apply(Object config) {
			throw new UnsupportedOperationException("class DefaultValue is never meant to be intantiated");
		}

	}

}
