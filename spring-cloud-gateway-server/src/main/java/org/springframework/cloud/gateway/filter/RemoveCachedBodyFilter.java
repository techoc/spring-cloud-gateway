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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR;

/**
 * 移除缓存请求体过滤器。
 * <p>
 * 该全局过滤器负责在请求处理完成后释放已缓存的请求体数据缓冲区。 出于性能考虑，请求体数据在缓存时通常使用池化的缓冲区（{@link PooledDataBuffer}）。
 * 若不及时释放，可能导致内存泄漏。
 * <p>
 * 执行顺序为 {@link Ordered#HIGHEST_PRECEDENCE}（最高优先级），确保在所有过滤器完成后 最后执行清理工作。
 * <p>
 * 注意：该过滤器仅负责清理工作，不处理实际的请求体数据。
 */
public class RemoveCachedBodyFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(RemoveCachedBodyFilter.class);

	/**
	 * 过滤请求，在请求处理完成后释放缓存的请求体缓冲区。
	 * <p>
	 * 使用 {@code doFinally} 回调确保无论请求处理成功还是失败，都会执行清理逻辑。 若缓存的请求体是池化缓冲区且已分配，则主动释放其内存。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return chain.filter(exchange).doFinally(s -> {
			Object attribute = exchange.getAttributes().remove(CACHED_REQUEST_BODY_ATTR);
			if (attribute != null && attribute instanceof PooledDataBuffer) {
				PooledDataBuffer dataBuffer = (PooledDataBuffer) attribute;
				if (dataBuffer.isAllocated()) {
					if (log.isTraceEnabled()) {
						log.trace("releasing cached body in exchange attribute");
					}
					dataBuffer.release();
				}
			}
		});
	}

	/**
	 * 返回过滤器执行顺序。
	 * <p>
	 * 返回 {@link Ordered#HIGHEST_PRECEDENCE}，确保在所有过滤器最后执行。
	 * @return 最高优先级
	 */
	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

}
