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

import java.util.function.Function;

import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.core.style.DefaultToStringStyler;
import org.springframework.core.style.DefaultValueStyler;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.ClassUtils;

/**
 * 网关专用的 toString 样式器，继承自 Spring 的 {@link DefaultToStringStyler}。
 * <p>
 * 该类为网关组件（特别是过滤器工厂）提供更友好的字符串表示形式， 自动将 Java 类的全限定名转换为短名称，便于日志输出和调试。
 * </p>
 * <p>
 * 主要特性：
 * <ul>
 * <li>对于 {@link GatewayFilterFactory} 实例，使用规范化的短名称（如 "AddRequestHeader"）</li>
 * <li>对于其他对象，使用 Spring 的标准短名称格式</li>
 * <li>数组类型使用标准格式显示</li>
 * </ul>
 * </p>
 *
 * @author Spencer Gibb
 * @see DefaultToStringStyler
 * @see ToStringCreator
 * @see NameUtils
 */
public class GatewayToStringStyler extends DefaultToStringStyler {

	/**
	 * 过滤器专用的样式器单例。
	 * <p>
	 * 使用过滤器工厂类名规范化函数初始化， 确保所有过滤器都使用统一的命名格式。
	 */
	private static final GatewayToStringStyler FILTER_INSTANCE = new GatewayToStringStyler(GatewayFilterFactory.class,
			NameUtils::normalizeFilterFactoryName);

	/** 类名格式化函数，用于将类转换为友好的名称 */
	private final Function<Class, String> classNameFormatter;

	/** 实例类型，用于判断对象是否属于特定类型 */
	private final Class instanceClass;

	/**
	 * 为过滤器对象创建 ToStringCreator 的便捷方法。
	 * @param obj 要生成字符串表示的对象
	 * @return 配置了网关样式器的 ToStringCreator 实例
	 */
	public static ToStringCreator filterToStringCreator(Object obj) {
		return new ToStringCreator(obj, FILTER_INSTANCE);
	}

	/**
	 * 构造函数，创建自定义样式器。
	 * @param instanceClass 用于判断对象类型的基准类
	 * @param classNameFormatter 类名格式化函数
	 */
	public GatewayToStringStyler(Class instanceClass, Function<Class, String> classNameFormatter) {
		super(new DefaultValueStyler());
		this.classNameFormatter = classNameFormatter;
		this.instanceClass = instanceClass;
	}

	/**
	 * 开始样式化对象，生成对象字符串表示的前缀部分。
	 * <p>
	 * 对于过滤器工厂实例，使用规范化的短名称； 对于其他对象，使用标准的短类名。
	 * </p>
	 * @param buffer 用于追加结果的 StringBuilder
	 * @param obj 要样式化的对象
	 */
	@Override
	public void styleStart(StringBuilder buffer, Object obj) {
		if (!obj.getClass().isArray()) {
			String shortName;
			if (instanceClass.isInstance(obj)) {
				// 对象是过滤器工厂实例，使用规范化名称
				shortName = classNameFormatter.apply(obj.getClass());
			}
			else {
				// 其他对象使用标准短名称
				shortName = ClassUtils.getShortName(obj.getClass());
			}
			buffer.append('[').append(shortName);
		}
		else {
			// 数组类型使用标准格式
			buffer.append('[');
			styleValue(buffer, obj);
		}
	}

}
