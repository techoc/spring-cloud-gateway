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

package org.springframework.cloud.gateway.filter.factory.rewrite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.FileCopyUtils;

/**
 * GZIP 压缩消息体解析器。
 *
 * <p>
 * 该类实现了 GZIP 格式的编码和解码功能， 用于在网关过滤器中处理 GZIP 压缩的请求/响应体。
 *
 * <p>
 * 实现接口：
 * <ul>
 * <li>{@link MessageBodyDecoder} - 解码（解压）GZIP 压缩的数据</li>
 * <li>{@link MessageBodyEncoder} - 编码（压缩）数据为 GZIP 格式</li>
 * </ul>
 *
 * <p>
 * 使用场景：
 * <ul>
 * <li>支持 GZIP 压缩的响应体解码</li>
 * <li>支持 GZIP 压缩的请求体编码</li>
 * <li>与 {@link ModifyRequestBodyGatewayFilterFactory} 和
 * {@link ModifyResponseBodyGatewayFilterFactory} 配合使用</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see MessageBodyDecoder
 * @see MessageBodyEncoder
 * @see ModifyRequestBodyGatewayFilterFactory
 * @see ModifyResponseBodyGatewayFilterFactory
 */
public class GzipMessageBodyResolver implements MessageBodyDecoder, MessageBodyEncoder {

	/**
	 * 返回编码类型标识符。
	 * @return 编码类型："gzip"
	 */
	@Override
	public String encodingType() {
		return "gzip";
	}

	/**
	 * 解码（解压）GZIP 压缩的数据。
	 *
	 * <p>
	 * 将 GZIP 格式的字节数组解压为原始字节数组。
	 * @param encoded GZIP 压缩的字节数组
	 * @return 解压后的原始字节数组
	 * @throws IllegalStateException 如果解压失败
	 */
	@Override
	public byte[] decode(byte[] encoded) {
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(encoded);
			GZIPInputStream gis = new GZIPInputStream(bis);
			return FileCopyUtils.copyToByteArray(gis);
		}
		catch (IOException e) {
			throw new IllegalStateException("couldn't decode body from gzip", e);
		}
	}

	/**
	 * 编码（压缩）数据为 GZIP 格式。
	 *
	 * <p>
	 * 将原始数据缓冲区压缩为 GZIP 格式的字节数组。
	 * @param original 原始数据缓冲区
	 * @return GZIP 压缩后的字节数组
	 * @throws IllegalStateException 如果压缩失败
	 */
	@Override
	public byte[] encode(DataBuffer original) {
		try {
			ByteArrayOutputStream bis = new ByteArrayOutputStream();
			GZIPOutputStream gos = new GZIPOutputStream(bis);
			FileCopyUtils.copy(original.asInputStream(), gos);
			return bis.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("couldn't encode body to gzip", e);
		}
	}

}
