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

package org.springframework.cloud.gateway.config;

import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.style.ToStringCreator;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway 指标配置属性类。
 * <p>
 * 用于配置网关指标收集的相关参数，包括指标开关、指标前缀、自定义标签等。 配置前缀为 {@code spring.cloud.gateway.metrics}。
 *
 * @author Ingyu Hwang
 */
@ConfigurationProperties("spring.cloud.gateway.metrics")
@Validated
public class GatewayMetricsProperties {

	/**
	 * Default metrics prefix. 默认的指标前缀。
	 */
	public static final String DEFAULT_PREFIX = "spring.cloud.gateway";

	/**
	 * Enables the collection of metrics data. 是否启用指标数据收集功能。
	 */
	private boolean enabled;

	/**
	 * The prefix of all metrics emitted by gateway. 网关发出的所有指标的统一前缀。
	 */
	private String prefix = DEFAULT_PREFIX;

	/**
	 * Tags map that added to metrics. 添加到指标上的标签映射表。
	 */
	@NotNull
	private Map<String, String> tags = new HashMap<>();

	/**
	 * 获取指标收集是否启用的状态。
	 * @return 如果启用则返回 true，否则返回 false
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * 设置指标收集的启用状态。
	 * @param enabled 是否启用指标收集
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * 获取指标的前缀。
	 * @return 指标前缀
	 */
	public String getPrefix() {
		return prefix;
	}

	/**
	 * 设置指标的前缀。
	 * @param prefix 指标前缀
	 */
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * 获取自定义指标标签映射表。
	 * @return 标签映射表
	 */
	public Map<String, String> getTags() {
		return tags;
	}

	/**
	 * 设置自定义指标标签映射表。
	 * @param tags 标签映射表
	 */
	public void setTags(Map<String, String> tags) {
		this.tags = tags;
	}

	/**
	 * 返回该对象的字符串表示形式。
	 * <p>
	 * 包含 enabled、prefix 和 tags 三个属性的值。
	 * @return 对象的字符串表示
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("enabled", enabled).append("prefix", prefix).append("tags", tags)
				.toString();

	}

}
