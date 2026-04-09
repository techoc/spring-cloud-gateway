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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;

/**
 * 名称处理工具类，提供网关组件名称的规范化功能。
 * <p>
 * 该工具类主要功能是将 Java 类的简单名称转换为符合 Spring 规范命名的字符串， 包括去除工厂类后缀、将驼峰命名转换为短横线分隔命名（kebab-case）等。
 * 这些名称通常用于 YAML 配置中的路由谓词和过滤器引用。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 * <li>生成唯一的自动命名（如 "_genkey_0"）</li>
 * <li>移除路由谓词工厂类名后缀（如 "PathRoutePredicateFactory" -> "Path"）</li>
 * <li>移除过滤器工厂类名后缀（如 "AddRequestHeaderGatewayFilterFactory" -> "AddRequestHeader"）</li>
 * <li>移除全局过滤器类名后缀和 "Filter" 后缀（如 "HystrixGatewayFilterFactory" -> "Hystrix"）</li>
 * <li>转换为标准的属性配置格式（kebab-case）</li>
 * </ul>
 * </p>
 *
 * @author Spencer Gibb
 */
public final class NameUtils {

	/**
	 * 私有构造函数，防止实例化。 这是工具类，所有方法应为静态方法。
	 */
	private NameUtils() {
		throw new AssertionError("Must not instantiate utility class.");
	}

	/**
	 * 自动生成的名称前缀。
	 * <p>
	 * 用于系统自动生成的路由或过滤器名称，避免与用户定义的名称冲突。
	 */
	public static final String GENERATED_NAME_PREFIX = "_genkey_";

	/**
	 * 用于提取驼峰命名的正则表达式模式。
	 * <p>
	 * 匹配规则：首字母大写后跟小写字母或数字的组合。 例如："AddRequestHeader" -> ["Add", "Request", "Header"]
	 */
	private static final Pattern NAME_PATTERN = Pattern.compile("([A-Z][a-z0-9]+)");

	/**
	 * 生成唯一的自动名称。
	 * @param i 用于区分不同自动生成名称的序号
	 * @return 格式为 "_genkey_{i}" 的唯一名称
	 */
	public static String generateName(int i) {
		return GENERATED_NAME_PREFIX + i;
	}

	/**
	 * 规范化路由谓词工厂的类名为短名称。
	 * <p>
	 * 移除类名后的 "RoutePredicateFactory" 后缀。 例如：{@code PathRoutePredicateFactory} ->
	 * {@code "Path"}
	 * </p>
	 * @param clazz 路由谓词工厂类
	 * @return 规范化的短名称
	 */
	public static String normalizeRoutePredicateName(Class<? extends RoutePredicateFactory> clazz) {
		return removeGarbage(clazz.getSimpleName().replace(RoutePredicateFactory.class.getSimpleName(), ""));
	}

	/**
	 * 规范化路由谓词工厂名称为属性配置格式。
	 * <p>
	 * 在 {@link #normalizeRoutePredicateName(Class)} 基础上， 进一步转换为短横线分隔的小写格式。
	 * 例如：{@code Path} -> {@code "path"}
	 * </p>
	 * @param clazz 路由谓词工厂类
	 * @return 属性配置格式的名称
	 */
	public static String normalizeRoutePredicateNameAsProperty(Class<? extends RoutePredicateFactory> clazz) {
		return normalizeToCanonicalPropertyFormat(normalizeRoutePredicateName(clazz));
	}

	/**
	 * 规范化过滤器工厂的类名为短名称。
	 * <p>
	 * 移除类名后的 "GatewayFilterFactory" 后缀。 例如：{@code AddRequestHeaderGatewayFilterFactory}
	 * -> {@code "AddRequestHeader"}
	 * </p>
	 * @param clazz 过滤器工厂类
	 * @return 规范化的短名称
	 */
	public static String normalizeFilterFactoryName(Class<? extends GatewayFilterFactory> clazz) {
		return removeGarbage(clazz.getSimpleName().replace(GatewayFilterFactory.class.getSimpleName(), ""));
	}

	/**
	 * 规范化全局过滤器的类名为短名称。
	 * <p>
	 * 移除类名后的 "GatewayFilterFactory" 后缀和 "Filter" 后缀。
	 * 例如：{@code HystrixGatewayFilterFactory} -> {@code "Hystrix"}
	 * </p>
	 * @param clazz 全局过滤器类
	 * @return 规范化的短名称
	 */
	public static String normalizeGlobalFilterName(Class<? extends GlobalFilter> clazz) {
		return removeGarbage(clazz.getSimpleName().replace(GlobalFilter.class.getSimpleName(), "")).replace("Filter",
				"");
	}

	/**
	 * 规范化过滤器工厂名称为属性配置格式。
	 * @param clazz 过滤器工厂类
	 * @return 属性配置格式的名称
	 * @see #normalizeFilterFactoryName(Class)
	 */
	public static String normalizeFilterFactoryNameAsProperty(Class<? extends GatewayFilterFactory> clazz) {
		return normalizeToCanonicalPropertyFormat(normalizeFilterFactoryName(clazz));
	}

	/**
	 * 规范化全局过滤器名称为属性配置格式。
	 * @param filterClass 全局过滤器类
	 * @return 属性配置格式的名称
	 * @see #normalizeGlobalFilterName(Class)
	 */
	public static String normalizeGlobalFilterNameAsProperty(Class<? extends GlobalFilter> filterClass) {
		return normalizeToCanonicalPropertyFormat(normalizeGlobalFilterName(filterClass));
	}

	/**
	 * 将驼峰命名转换为标准的属性配置格式（kebab-case，小写）。
	 * <p>
	 * 通过正则表达式分割驼峰命名，并在非首段前插入短横线。 例如：{@code "AddRequestHeader"} ->
	 * {@code "add-request-header"}
	 * </p>
	 * @param name 原始的驼峰命名
	 * @return 转换后的属性配置格式名称（全小写，短横线分隔）
	 */
	public static String normalizeToCanonicalPropertyFormat(String name) {
		Matcher matcher = NAME_PATTERN.matcher(name);
		StringBuffer stringBuffer = new StringBuffer();
		while (matcher.find()) {
			if (stringBuffer.length() != 0) {
				// 非首段，在前面添加短横线
				matcher.appendReplacement(stringBuffer, "-" + matcher.group(1));
			}
			else {
				// 首段，直接使用
				matcher.appendReplacement(stringBuffer, matcher.group(1));
			}
		}
		return stringBuffer.toString().toLowerCase();
	}

	/**
	 * 移除类名中的 Mockito 测试相关的垃圾字符。
	 * <p>
	 * 在测试环境中，类名可能包含 "$Mockito" 等后缀， 此方法用于清理这些不影响业务的后缀。
	 * </p>
	 * @param s 原始字符串
	 * @return 清理后的字符串
	 */
	private static String removeGarbage(String s) {
		int garbageIdx = s.indexOf("$Mockito");
		if (garbageIdx > 0) {
			return s.substring(0, garbageIdx);
		}

		return s;
	}

}
