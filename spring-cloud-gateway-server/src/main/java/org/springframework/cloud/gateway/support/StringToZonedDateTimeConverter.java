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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.core.convert.converter.Converter;

/**
 * 字符串到 {@link ZonedDateTime} 的类型转换器。
 * <p>
 * 该转换器实现 Spring 的 {@link Converter} 接口，用于将字符串类型的日期时间 转换为带时区的 {@link ZonedDateTime}
 * 对象。支持两种输入格式：
 * </p>
 * <ul>
 * <li><b>时间戳格式</b>：长整型的毫秒数（如 "1614556800000"）， 将被解析为自
 * epoch（1970-01-01T00:00:00Z）以来的毫秒数</li>
 * <li><b>ISO 格式</b>：符合 ISO-8601 标准的日期时间字符串 （如 "2021-03-01T12:00:00+08:00"）</li>
 * </ul>
 * <p>
 * 转换优先级：首先尝试解析为长整型时间戳，若失败则按 ISO 格式解析。
 * </p>
 *
 * @see Converter
 * @see ZonedDateTime
 */
public class StringToZonedDateTimeConverter implements Converter<String, ZonedDateTime> {

	/**
	 * 将字符串转换为带时区的日期时间对象。
	 * <p>
	 * 转换逻辑：
	 * <ol>
	 * <li>首先尝试将输入解析为长整型毫秒时间戳</li>
	 * <li>若解析失败，则尝试按 ISO-8601 格式解析为 ZonedDateTime</li>
	 * <li>若两种方式均失败，将抛出异常</li>
	 * </ol>
	 * </p>
	 * <p>
	 * 时间戳模式下，时区偏移量固定为 UTC（ZoneOffset.ofTotalSeconds(0)）。
	 * </p>
	 * @param source 待转换的字符串，不能为 null
	 * @return 转换后的 ZonedDateTime 对象
	 * @throws NumberFormatException 如果既不是有效时间戳也不是有效 ISO 日期时间格式
	 */
	@Override
	public ZonedDateTime convert(String source) {
		ZonedDateTime dateTime;
		try {
			// 尝试作为时间戳解析（毫秒）
			long epoch = Long.parseLong(source);

			// 将毫秒时间戳转换为 ZonedDateTime，时区偏移量为 0
			dateTime = Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.ofTotalSeconds(0)).toZonedDateTime();
		}
		catch (NumberFormatException e) {
			// 时间戳解析失败，尝试 ISO 格式解析
			dateTime = ZonedDateTime.parse(source);
		}

		return dateTime;
	}

}
