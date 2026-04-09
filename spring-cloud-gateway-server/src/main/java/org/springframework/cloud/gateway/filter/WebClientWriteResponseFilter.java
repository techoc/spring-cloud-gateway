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
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;

/**
 * WebClient响应写入过滤器。
 *
 * <p>
 * 该过滤器负责将后端服务的响应写入到客户端响应中。
 *
 * <p>
 * 功能说明：
 * <ul>
 * <li>实现GlobalFilter接口，作为全局过滤器</li>
 * <li>将WebClientHttpRoutingFilter保存的响应写入实际响应流</li>
 * <li>处理响应体的流式传输</li>
 * <li>处理取消和错误情况下的资源清理</li>
 * </ul>
 *
 * <p>
 * 执行时机：
 * <ul>
 * <li>过滤器顺序为-1（WRITE_RESPONSE_FILTER_ORDER），高优先级</li>
 * <li>在所有路由过滤器执行完成后执行</li>
 * <li>必须在NettyWriteResponseFilter之前执行</li>
 * </ul>
 *
 * <p>
 * 工作流程：
 * <ol>
 * <li>等待过滤器链执行完成</li>
 * <li>从exchange获取后端响应（CLIENT_RESPONSE_ATTR）</li>
 * <li>将后端响应体写入客户端响应</li>
 * <li>处理取消信号，执行清理</li>
 * <li>处理错误情况，执行清理</li>
 * </ol>
 *
 * <p>
 * 与WebClientHttpRoutingFilter的协作：
 * <ul>
 * <li>WebClientHttpRoutingFilter：发起请求，保存响应到属性</li>
 * <li>WebClientWriteResponseFilter：从属性获取响应，写入客户端</li>
 * <li>这种设计延迟了响应写入，允许所有过滤器先执行</li>
 * </ul>
 *
 * <p>
 * 资源清理：
 * <ul>
 * <li>当请求被取消时，清理后端响应体</li>
 * <li>当发生错误时，清理后端响应体</li>
 * <li>清理确保后端连接被正确释放</li>
 * </ul>
 *
 * @author Spencer Gibb
 * @see GlobalFilter
 * @see Ordered
 * @see WebClientHttpRoutingFilter
 */
public class WebClientWriteResponseFilter implements GlobalFilter, Ordered {

	/**
	 * 写入响应过滤器的执行顺序。
	 * <p>
	 * 值为-1，确保在其他过滤器之后但在NettyWriteResponseFilter之前执行
	 * </p>
	 */
	public static final int WRITE_RESPONSE_FILTER_ORDER = -1;

	/**
	 * 日志记录器。
	 * <p>
	 * 用于记录过滤器的执行状态
	 * </p>
	 */
	private static final Log log = LogFactory.getLog(WebClientWriteResponseFilter.class);

	/**
	 * 获取过滤器执行顺序。
	 * @return 过滤器顺序值（-1）
	 */
	@Override
	public int getOrder() {
		return WRITE_RESPONSE_FILTER_ORDER;
	}

	/**
	 * 执行响应写入过滤。
	 *
	 * <p>
	 * 核心业务逻辑：
	 * <ol>
	 * <li>使用Mono.defer延迟执行，确保链式调用正确</li>
	 * <li>从exchange获取后端ClientResponse</li>
	 * <li>如果响应为空，直接返回空Mono</li>
	 * <li>将后端响应体写入客户端响应</li>
	 * <li>处理取消和错误情况下的清理</li>
	 * </ol>
	 *
	 * <p>
	 * 响应体传输：
	 * <ul>
	 * <li>使用BodyExtractors.toDataBuffers进行流式传输</li>
	 * <li>保持响应体的流式特性，避免一次性加载到内存</li>
	 * <li>支持大文件的高效传输</li>
	 * </ul>
	 *
	 * <p>
	 * 错误处理：
	 * <ul>
	 * <li>doOnError在错误时调用cleanup清理资源</li>
	 * <li>确保后端连接被正确关闭</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>pre阶段不执行任何操作，因为CLIENT_RESPONSE_ATTR尚未添加</li>
	 * <li>CLIENT_RESPONSE_ATTR由WebClientHttpRoutingFilter在WebHandler运行时添加</li>
	 * <li>使用defer确保正确订阅时机</li>
	 * </ul>
	 * @param exchange 服务器Web交换对象
	 * @param chain 过滤器链
	 * @return 完成信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// NOTICE: nothing in "pre" filter stage as CLIENT_RESPONSE_ATTR is not added
		// until the WebHandler is run
		return chain.filter(exchange).doOnError(throwable -> cleanup(exchange)).then(Mono.defer(() -> {
			ClientResponse clientResponse = exchange.getAttribute(CLIENT_RESPONSE_ATTR);
			if (clientResponse == null) {
				return Mono.empty();
			}
			log.trace("WebClientWriteResponseFilter start");
			ServerHttpResponse response = exchange.getResponse();

			return response.writeWith(clientResponse.body(BodyExtractors.toDataBuffers()))
					// .log("webClient response")
					.doOnCancel(() -> cleanup(exchange));
		}));
	}

	/**
	 * 清理后端响应资源。
	 *
	 * <p>
	 * 资源清理逻辑：
	 * <ul>
	 * <li>从exchange获取后端响应</li>
	 * <li>如果响应存在，订阅其响应体</li>
	 * <li>这确保后端连接被正确释放</li>
	 * </ul>
	 *
	 * <p>
	 * 调用场景：
	 * <ul>
	 * <li>请求被客户端取消时（doOnCancel）</li>
	 * <li>过滤器链执行出错时（doOnError）</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>订阅响应体会消费掉响应体数据</li>
	 * <li>这是必要的清理操作，防止连接泄漏</li>
	 * </ul>
	 * @param exchange 服务器Web交换对象
	 */
	private void cleanup(ServerWebExchange exchange) {
		ClientResponse clientResponse = exchange.getAttribute(CLIENT_RESPONSE_ATTR);
		if (clientResponse != null) {
			clientResponse.bodyToMono(Void.class).subscribe();
		}
	}

}
