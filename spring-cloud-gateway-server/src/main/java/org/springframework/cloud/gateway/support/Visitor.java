/*
 * Copyright 2013-2021 the original author or authors.
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
 * 简单的访问者接口，用于遍历和检查对象图中的组件。
 * <p>
 * 该接口是访问者模式（Visitor Pattern）的简化实现，允许外部代码 以统一的方式遍历网关内部的各类可配置组件（如过滤器、谓词等）， 而无需了解组件的具体类型细节。
 * </p>
 * <p>
 * 使用场景：
 * <ul>
 * <li>网关配置的校验和审计</li>
 * <li>动态修改网关组件的行为</li>
 * <li>收集和统计网关组件信息</li>
 * </ul>
 * </p>
 *
 * @author Spencer Gibb
 * @since 3.1.0
 * @see HasConfig
 */
@FunctionalInterface
public interface Visitor {

	/**
	 * 访问指定的配置持有者对象。
	 * <p>
	 * 实现此方法以定义对各个组件的访问逻辑。 通过访问者模式，可以在不修改原有组件类的情况下， 为组件添加新的操作或行为。
	 * </p>
	 * @param hasConfig 被访问的配置持有者对象，不能为 null
	 */
	void visit(HasConfig hasConfig);

}
