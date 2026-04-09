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

package org.springframework.cloud.gateway.support.tagsprovider;

import java.util.Map;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import org.springframework.web.server.ServerWebExchange;

/**
 * 属性标签提供者，使用预定义的静态标签。
 * <p>
 * 该提供者允许通过配置方式定义一组固定的标签， 这些标签会被添加到所有请求的指标中。 与动态计算的标签不同，这些标签是静态的， 在构造时从配置中加载。
 * </p>
 * <p>
 * 配置示例（YAML）： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       metrics:
 *         tags:
 *           env: production
 *           region: us-east-1
 * </pre>
 * </p>
 * <p>
 * 使用场景：
 * <ul>
 * <li>添加环境标识（开发/测试/生产）</li>
 * <li>添加地域/区域标识</li>
 * <li>添加应用版本标签</li>
 * <li>任何需要在所有指标中出现的维度</li>
 * </ul>
 * </p>
 *
 * @author Ingyu Hwang
 * @see GatewayTagsProvider
 * @see Tags
 */
public class PropertiesTagsProvider implements GatewayTagsProvider {

	/** 预定义的静态标签 */
	private final Tags propertiesTags;

	/**
	 * 构造函数，从标签映射创建标签提供者。
	 * @param tagsMap 标签名称到标签值的映射
	 */
	public PropertiesTagsProvider(Map<String, String> tagsMap) {
		this.propertiesTags = Tags.of(tagsMap.entrySet().stream().map(entry -> Tag.of(entry.getKey(), entry.getValue()))
				.collect(Collectors.toList()));
	}

	/**
	 * 返回预定义的静态标签。
	 * <p>
	 * 由于标签是静态的，对于任何请求都返回相同的结果。
	 * </p>
	 * @param serverWebExchange 当前请求交换对象（未使用）
	 * @return 预定义的静态标签
	 */
	@Override
	public Tags apply(ServerWebExchange serverWebExchange) {
		return propertiesTags;
	}

}
