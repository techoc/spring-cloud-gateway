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

package org.springframework.cloud.gateway.filter.factory;

import java.util.Arrays;
import java.util.List;

import javax.validation.constraints.NotEmpty;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

/**
 * 名称-值形式配置的网关过滤器工厂抽象基类。
 * <p>
 * 该类适用于只需要两个参数（名称和值）的过滤器，如添加请求头、添加响应头等。 它继承自 {@link AbstractGatewayFilterFactory}，配置类为
 * {@link NameValueConfig}。
 * <p>
 * 快捷配置示例： <pre>
 * - AddRequestHeader=X-Custom-Header, custom-value
 * </pre> 会被解析为 name="X-Custom-Header", value="custom-value"。
 *
 * @author Spencer Gibb
 */
public abstract class AbstractNameValueGatewayFilterFactory
		extends AbstractGatewayFilterFactory<AbstractNameValueGatewayFilterFactory.NameValueConfig> {

	/**
	 * 默认构造方法，使用 {@link NameValueConfig} 作为配置类。
	 */
	public AbstractNameValueGatewayFilterFactory() {
		super(NameValueConfig.class);
	}

	/**
	 * 返回快捷字段顺序，用于 YAML/Properties 配置解析。
	 * <p>
	 * 返回 {@link GatewayFilter#NAME_KEY} 和 {@link GatewayFilter#VALUE_KEY}， 使得配置可以简写为
	 * "Name=value" 格式。
	 * @return 快捷字段名称列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(GatewayFilter.NAME_KEY, GatewayFilter.VALUE_KEY);
	}

	/**
	 * 名称-值配置类。
	 * <p>
	 * 包含两个必需字段：name 和 value。用于配置过滤器需要操作的名称及其对应的值。
	 * <p>
	 * 使用示例：
	 * <ul>
	 * <li>AddRequestHeader: name=请求头名称, value=请求头值</li>
	 * <li>AddResponseHeader: name=响应头名称, value=响应头值</li>
	 * </ul>
	 */
	@Validated
	public static class NameValueConfig {

		/** 配置项名称（不能为空） */
		@NotEmpty
		protected String name;

		/** 配置项值（不能为空） */
		@NotEmpty
		protected String value;

		/**
		 * 获取名称。
		 * @return 名称字符串
		 */
		public String getName() {
			return name;
		}

		/**
		 * 设置名称。
		 * @param name 名称字符串
		 * @return 自身，用于链式调用
		 */
		public NameValueConfig setName(String name) {
			this.name = name;
			return this;
		}

		/**
		 * 获取值。
		 * @return 值字符串
		 */
		public String getValue() {
			return value;
		}

		/**
		 * 设置值。
		 * @param value 值字符串
		 * @return 自身，用于链式调用
		 */
		public NameValueConfig setValue(String value) {
			this.value = value;
			return this;
		}

		/**
		 * 返回配置对象的字符串表示。
		 * @return 格式为 "NameValueConfig{name=xxx, value=xxx}" 的字符串
		 */
		@Override
		public String toString() {
			return new ToStringCreator(this).append("name", name).append("value", value).toString();
		}

	}

}
