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

/**
 * 标记接口，表示实现类持有配置对象。
 * <p>
 * 该接口用于标识那些包含配置信息的组件，如过滤器工厂（GatewayFilterFactory）
 * 和路由谓词工厂（RoutePredicateFactory）等。通过实现此接口， 外部可以统一获取这些组件持有的配置对象。
 * </p>
 * <p>
 * 默认实现返回 null，子类可覆盖 {@link #getConfig()} 方法提供实际的配置对象。
 * </p>
 */
public interface HasConfig {

	/**
	 * 返回当前组件持有的配置对象。
	 * <p>
	 * 默认实现返回 null，子类应覆盖此方法返回实际的配置对象。 返回值的具体类型由子类决定，调用方通常需要根据具体类型进行转换。
	 * </p>
	 * @return 配置对象，若无配置则返回 null
	 */
	default Object getConfig() {
		return null;
	}

}
