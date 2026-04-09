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

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Max;

import org.springframework.util.unit.DataSize;

// https://in.relation.to/2017/03/02/adding-custom-constraint-definitions-via-the-java-service-loader/
/**
 * {@link DataSize} 类型的最大值校验器。
 * <p>
 * 配合 {@link Max} 注解使用，验证 {@link DataSize} 值是否不超过指定的最大字节数。 {@code null} 值视为合法。
 * </p>
 */
public class MaxDataSizeValidator implements ConstraintValidator<Max, DataSize> {

	/** 从 @Max 注解中获取的最大值 */
	private long maxValue;

	/**
	 * 校验给定的 DataSize 值是否合法。
	 * @param value 待校验的 DataSize 值，{@code null} 视为合法
	 * @param context 约束校验上下文
	 * @return {@code true} 如果值为 {@code null} 或不超过最大值；否则返回 {@code false}
	 */
	@Override
	public boolean isValid(DataSize value, ConstraintValidatorContext context) {
		// null values are valid
		if (value == null) {
			return true;
		}
		return value.toBytes() <= maxValue;
	}

	/**
	 * 初始化校验器，从 {@link Max} 注解中提取最大值。
	 * @param maxValue @Max 注解实例
	 */
	@Override
	public void initialize(Max maxValue) {
		this.maxValue = maxValue.value();
	}

}
