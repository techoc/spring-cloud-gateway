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

import java.nio.charset.Charset;
import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import org.junit.Test;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import static io.netty.buffer.PooledByteBufAllocator.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * NettyWriteResponseFilter 单元测试类
 * <p>
 * 本测试类用于验证 NettyWriteResponseFilter 的响应包装功能，包括： - 测试使用 NettyDataBufferFactory 包装 ByteBuf
 * - 测试使用默认 DataBufferFactory 包装 ByteBuf - 验证 ByteBuf 的引用计数在包装后正确释放
 * <p>
 * NettyWriteResponseFilter 负责将 Netty 的 ByteBuf 包装为 Spring 的 DataBuffer，
 * 确保响应数据能够被正确处理并在处理完成后释放内存。
 *
 * @author Violeta Georgieva
 * @author 译者：Spring Cloud Gateway 团队
 */
public class NettyWriteResponseFilterTests {

	/**
	 * 测试使用 NettyDataBufferFactory 包装 ByteBuf 验证：ByteBuf 应被正确包装为 DataBuffer，并在完成后释放引用
	 */
	@Test
	public void testWrap_NettyDataBufferFactory() {
		doTestWrap(new MockServerHttpResponse(new NettyDataBufferFactory(DEFAULT)));
	}

	/**
	 * 测试使用默认 DataBufferFactory 包装 ByteBuf 验证：ByteBuf 应被正确包装为 DataBuffer
	 */
	@Test
	public void testWrap_DefaultDataBufferFactory() {
		doTestWrap(new MockServerHttpResponse());
	}

	/**
	 * 执行 ByteBuf 包装测试的辅助方法
	 * @param response 模拟的 HTTP 响应对象
	 */
	private void doTestWrap(MockServerHttpResponse response) {
		NettyWriteResponseFilter filter = new NettyWriteResponseFilter(new ArrayList<>());

		ByteBuf buffer = DEFAULT.buffer();
		buffer.writeCharSequence("test", Charset.defaultCharset());

		DataBuffer result = filter.wrap(buffer, response);

		assertThat(result.toString(Charset.defaultCharset())).isEqualTo("test");

		if (result instanceof PooledDataBuffer) {
			((PooledDataBuffer) result).release();
		}

		assertThat(buffer.refCnt()).isEqualTo(0);
	}

}
