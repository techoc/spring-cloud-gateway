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

package org.springframework.cloud.gateway.filter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.validation.constraints.NotNull;

import org.springframework.cloud.gateway.support.NameUtils;
import org.springframework.validation.annotation.Validated;

import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * 过滤器定义类，用于描述一个网关过滤器的配置信息。
 * <p>
 * 该类是过滤器配置的数据模型，包含过滤器的名称和参数映射。通常用于 YAML/Properties 配置文件的解析，或通过 Actuator API 进行动态路由配置时使用。
 * <p>
 * 支持快捷方式字符串格式，例如 {@code "AddRequestHeader=X-Header,value"}， 会被解析为名称为
 * {@code AddRequestHeader}、参数为 {@code {_genkey_0=X-Header, _genkey_1=value}} 的定义。
 *
 * @author Spencer Gibb
 */
@Validated
public class FilterDefinition {

	/**
	 * 过滤器名称，不能为空，对应已注册的
	 * {@link org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory} 名称。
	 */
	@NotNull
	private String name;

	/**
	 * 过滤器参数映射，key 为参数名，value 为参数值。 使用 {@link LinkedHashMap} 保证参数顺序。
	 */
	private Map<String, String> args = new LinkedHashMap<>();

	/**
	 * 默认无参构造方法，用于框架反序列化。
	 */
	public FilterDefinition() {
	}

	/**
	 * 通过快捷方式文本字符串构造过滤器定义。
	 * <p>
	 * 字符串格式为 {@code "FilterName=arg1,arg2,..."}，等号左侧为过滤器名称，右侧为逗号分隔的参数列表。 参数键名自动生成（如
	 * {@code _genkey_0}、{@code _genkey_1}）。 若字符串中不含等号，则整个字符串作为过滤器名称，无参数。
	 * @param text 过滤器快捷方式文本，格式为 "FilterName=arg1,arg2,..."
	 */
	public FilterDefinition(String text) {
		int eqIdx = text.indexOf('=');
		if (eqIdx <= 0) {
			setName(text);
			return;
		}
		setName(text.substring(0, eqIdx));

		String[] args = tokenizeToStringArray(text.substring(eqIdx + 1), ",");

		for (int i = 0; i < args.length; i++) {
			this.args.put(NameUtils.generateName(i), args[i]);
		}
	}

	/**
	 * 获取过滤器名称。
	 * @return 过滤器名称
	 */
	public String getName() {
		return name;
	}

	/**
	 * 设置过滤器名称。
	 * @param name 过滤器名称，不能为空
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * 获取过滤器参数映射。
	 * @return 参数名到参数值的映射
	 */
	public Map<String, String> getArgs() {
		return args;
	}

	/**
	 * 设置过滤器参数映射。
	 * @param args 参数名到参数值的映射
	 */
	public void setArgs(Map<String, String> args) {
		this.args = args;
	}

	/**
	 * 添加单个过滤器参数。
	 * @param key 参数名
	 * @param value 参数值
	 */
	public void addArg(String key, String value) {
		this.args.put(key, value);
	}

	/**
	 * 判断两个过滤器定义是否相等（基于名称和参数）。
	 * @param o 待比较对象
	 * @return 若名称和参数均相等则返回 {@code true}，否则返回 {@code false}
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FilterDefinition that = (FilterDefinition) o;
		return Objects.equals(name, that.name) && Objects.equals(args, that.args);
	}

	/**
	 * 返回基于名称和参数的哈希值。
	 * @return 哈希值
	 */
	@Override
	public int hashCode() {
		return Objects.hash(name, args);
	}

	/**
	 * 返回过滤器定义的字符串表示形式。
	 * @return 格式为 "FilterDefinition{name='xxx', args={...}}" 的字符串
	 */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("FilterDefinition{");
		sb.append("name='").append(name).append('\'');
		sb.append(", args=").append(args);
		sb.append('}');
		return sb.toString();
	}

}
