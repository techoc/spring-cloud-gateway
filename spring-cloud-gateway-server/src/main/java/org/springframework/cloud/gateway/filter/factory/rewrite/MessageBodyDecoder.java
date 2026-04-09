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

/**
 * 消息体解码器接口。
 *
 * <p>
 * 该接口用于定义消息体解码器，解码器根据 Content-Encoding 头中的编码类型 来确定是否需要解码以及如何解码消息体。
 *
 * <p>
 * 实现此接口的类需要：
 * <ul>
 * <li>提供编码类型标识符，用于匹配 Content-Encoding 头</li>
 * <li>实现解码逻辑，将编码后的字节数组转换回原始字节数组</li>
 * </ul>
 *
 * <p>
 * 使用示例： <pre>
 * public class GzipMessageBodyResolver implements MessageBodyDecoder {
 *     {@literal @}Override
 *     public String encodingType() {
 *         return "gzip";
 *     }
 *
 *     {@literal @}Override
 *     public byte[] decode(byte[] encoded) {
 *         // 解码逻辑
 *     }
 * }
 * </pre>
 *
 * @author Spencer Gibb
 * @see GzipMessageBodyResolver
 * @see ModifyRequestBodyGatewayFilterFactory
 * @see ModifyResponseBodyGatewayFilterFactory
 */
public interface MessageBodyDecoder {

	/**
	 * 解码编码后的消息体。
	 *
	 * <p>
	 * 将根据 Content-Encoding 头编码后的字节数组解码为原始字节数组。
	 * @param encoded 编码后的字节数组
	 * @return 解码后的原始字节数组
	 */
	byte[] decode(byte[] encoded);

	/**
	 * 返回编码类型标识符。
	 *
	 * <p>
	 * 该值用于与 HTTP 请求/响应的 Content-Encoding 头进行匹配。 当 Content-Encoding 头包含此编码类型时，解码器将被调用。
	 *
	 * <p>
	 * 常见的编码类型包括：
	 * <ul>
	 * <li>"gzip" - GZIP 压缩</li>
	 * <li>"deflate" - DEFLATE 压缩</li>
	 * <li>"br" - Brotli 压缩</li>
	 * </ul>
	 * @return 编码类型标识符
	 */
	String encodingType();

}
