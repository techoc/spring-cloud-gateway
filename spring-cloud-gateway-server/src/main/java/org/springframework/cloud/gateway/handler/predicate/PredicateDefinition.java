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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.validation.ValidationException;
import javax.validation.constraints.NotNull;

import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.validation.annotation.Validated;

import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * 路由断言定义类，表示一个路由断言的配置信息。
 *
 * <p>
 * PredicateDefinition 是路由断言的内部表示形式，用于存储和传递 断言的名称及其参数配置。它支持从字符串解析和序列化操作。
 * </p>
 *
 * <p>
 * <b>功能说明：</b>
 * </p>
 * <ul>
 * <li>存储断言的名称（类型）</li>
 * <li>存储断言的参数键值对</li>
 * <li>支持从字符串解析（格式：name=arg1,arg2）</li>
 * <li>支持与 RouteDefinition 配合使用</li>
 * </ul>
 *
 * <p>
 * <b>字符串格式：</b>
 * </p>
 * <pre>{@code
 * Path=/user/**
 * Host=**.example.com
 * Method=GET,POST
 * }</pre>
 *
 * @author Spencer Gibb
 */
@Validated
public class PredicateDefinition {

	/** 断言名称（如 Path、Host、Method 等） */
	@NotNull
	private String name;

	/** 断言参数字典，键为参数名，值为参数值 */
	private Map<String, String> args = new LinkedHashMap<>();

	/**
	 * 默认构造函数。
	 */
	public PredicateDefinition() {
	}

	/**
	 * 从字符串解析创建断言定义。
	 *
	 * <p>
	 * 字符串格式：name=value1,value2,... 参数值会按顺序自动命名为 arg_0, arg_1, ...
	 * </p>
	 * @param text 要解析的字符串，格式为 name=value
	 * @throws ValidationException 如果格式不正确
	 */
	public PredicateDefinition(String text) {
		int eqIdx = text.indexOf('=');
		if (eqIdx <= 0) {
			throw new ValidationException(
					"Unable to parse PredicateDefinition text '" + text + "'" + ", must be of the form name=value");
		}
		setName(text.substring(0, eqIdx));

		// 按逗号分割参数
		String[] args = tokenizeToStringArray(text.substring(eqIdx + 1), ",");

		// 按顺序添加参数，命名为 arg_0, arg_1, ...
		for (int i = 0; i < args.length; i++) {
			this.args.put(NameUtils.generateName(i), args[i]);
		}
	}

	/**
	 * 获取断言名称。
	 * @return 断言名称
	 */
	public String getName() {
		return name;
	}

	/**
	 * 设置断言名称。
	 * @param name 断言名称
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * 获取断言参数映射。
	 * @return 参数字典
	 */
	public Map<String, String> getArgs() {
		return args;
	}

	/**
	 * 设置断言参数映射。
	 * @param args 参数字典
	 */
	public void setArgs(Map<String, String> args) {
		this.args = args;
	}

	/**
	 * 添加断言参数。
	 * @param key 参数键
	 * @param value 参数值
	 */
	public void addArg(String key, String value) {
		this.args.put(key, value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PredicateDefinition that = (PredicateDefinition) o;
		return Objects.equals(name, that.name) && Objects.equals(args, that.args);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, args);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("PredicateDefinition{");
		sb.append("name='").append(name).append('\'');
		sb.append(", args=").append(args);
		sb.append('}');
		return sb.toString();
	}

}
