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

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.web.server.ServerWebExchange;

/**
 * {@link OnEnabledComponent} 的具体实现，用于控制 {@link GlobalFilter} Bean 的条件注册。
 * <p>
 * 此类与 {@link ConditionalOnEnabledGlobalFilter} 注解配合使用， 通过检查配置属性
 * {@code spring.cloud.gateway.global-filter.<filter-name>.enabled} 来决定 全局过滤器 Bean
 * 是否应该被注册。
 *
 * @see ConditionalOnEnabledGlobalFilter
 * @see OnEnabledComponent
 */
public class OnEnabledGlobalFilter extends OnEnabledComponent<GlobalFilter> {

	/**
	 * 将全局过滤器类名规范化为配置属性中的名称格式。
	 * <p>
	 * 生成的配置属性键格式为： {@code spring.cloud.gateway.global-filter.<normalized-name>.enabled}
	 * @param filterClass 全局过滤器类
	 * @return 规范化的属性名称前缀（不含前缀和后缀）
	 */
	@Override
	protected String normalizeComponentName(Class<? extends GlobalFilter> filterClass) {
		return "global-filter." + NameUtils.normalizeGlobalFilterNameAsProperty(filterClass);
	}

	/**
	 * 返回此条件对应的注解类。
	 * @return {@link ConditionalOnEnabledGlobalFilter} 类
	 */
	@Override
	protected Class<?> annotationClass() {
		return ConditionalOnEnabledGlobalFilter.class;
	}

	/**
	 * 返回注解 value 属性的默认值类。
	 * @return {@link DefaultValue} 类
	 */
	@Override
	protected Class<? extends GlobalFilter> defaultValueClass() {
		return DefaultValue.class;
	}

	/**
	 * 默认值标记类，用于标识注解的 value 属性未被显式指定。
	 * <p>
	 * 当 {@link ConditionalOnEnabledGlobalFilter} 注解未指定 value 属性时使用此类作为默认值。
	 * 此类永远不会被实例化，其任何方法调用都会抛出 {@link UnsupportedOperationException}。
	 */
	static class DefaultValue implements GlobalFilter {

		/**
		 * 不支持的操作。
		 * @param exchange 服务器 Web 交换对象
		 * @param chain 过滤器链
		 * @return 不返回任何值
		 * @throws UnsupportedOperationException 始终抛出此异常
		 */
		@Override
		public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
			throw new UnsupportedOperationException("class DefaultValue is never meant to be intantiated");
		}

	}

}
