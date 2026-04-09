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

package org.springframework.cloud.gateway.sample;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * 额外的路由配置导入选择器。
 * <p>
 * 本类实现了 Spring 的 {@link DeferredImportSelector} 接口，用于条件性地导入额外的路由配置类。
 * 延迟导入选择器允许在其他配置类处理完毕后再执行导入逻辑，确保依赖的 Bean 已经就绪。
 * <p>
 * 工作原理：
 * <ol>
 *   <li>在 Spring 容器启动时，检查类路径中是否存在 {@code AdditionalRoutes} 类</li>
 *   <li>如果存在，则将该类导入到 Spring 容器中</li>
 *   <li>如果不存在，则不导入任何类</li>
 * </ol>
 * <p>
 * 这种设计模式允许：
 * <ul>
 *   <li>可选地加载额外的路由配置</li>
 *   <li>避免类不存在时的 ClassNotFoundException</li>
 *   <li>支持模块化配置，根据环境动态加载</li>
 * </ul>
 * <p>
 * 使用场景：
 * 当需要在特定环境（如测试环境）中加载额外的路由配置，而在其他环境中不加载时，
 * 可以将 AdditionalRoutes 类放在特定环境的源代码目录中，本选择器会自动检测并导入。
 *
 * @see DeferredImportSelector
 * @see ClassUtils#isPresent(String, ClassLoader)
 */
class AdditionalRoutesImportSelector implements DeferredImportSelector {

	/**
	 * 选择需要导入的配置类。
	 * <p>
	 * 检查类路径中是否存在 {@code org.springframework.cloud.gateway.sample.AdditionalRoutes} 类。
	 * 使用 {@link ClassUtils#isPresent(String, ClassLoader)} 方法进行安全的类存在性检查，
	 * 避免在类不存在时抛出异常。
	 *
	 * @param importingClassMetadata 导入类的注解元数据，包含被注解类的信息
	 * @return 需要导入的类的全限定名数组。如果 AdditionalRoutes 类存在，返回包含该类名的数组；
	 * 否则返回空数组
	 */
	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		if (ClassUtils.isPresent("org.springframework.cloud.gateway.sample.AdditionalRoutes", null)) {
			return new String[] { "org.springframework.cloud.gateway.sample.AdditionalRoutes" };
		}
		return new String[0];
	}

}
