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

import io.micrometer.core.instrument.Tags;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.AbstractServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

/**
 * HTTP 指标标签提供者，为请求生成 HTTP 相关的指标标签。
 * <p>
 * 该提供者生成以下标签：
 * <ul>
 * <li><b>outcome</b> - HTTP 状态系列名称（如 SUCCESSFUL、CLIENT_ERROR、SERVER_ERROR）</li>
 * <li><b>status</b> - HTTP 状态码名称（如 OK、NOT_FOUND、INTERNAL_SERVER_ERROR）</li>
 * <li><b>httpStatusCode</b> - HTTP 状态码的整数值（如 200、404、500）</li>
 * <li><b>httpMethod</b> - HTTP 请求方法（如 GET、POST、PUT、DELETE）</li>
 * </ul>
 * </p>
 * <p>
 * 该提供者同时支持标准和非标准的 HTTP 状态码：
 * <ul>
 * <li>对于 {@link AbstractServerHttpResponse}（支持原始状态码），直接获取整数状态码</li>
 * <li>对于其他响应类型，使用标准的 {@link ServerWebExchange#getResponse()} 方法</li>
 * </ul>
 * </p>
 *
 * @author Ingyu Hwang
 * @see GatewayTagsProvider
 * @see Tags
 */
public class GatewayHttpTagsProvider implements GatewayTagsProvider {

	/**
	 * 生成 HTTP 相关的指标标签。
	 * <p>
	 * 标签包含：outcome（状态系列）、status（状态名称）、 httpStatusCode（状态码数值）、httpMethod（请求方法）。
	 * </p>
	 * @param exchange 当前请求交换对象
	 * @return 包含 HTTP 标签的 Tags 对象
	 */
	@Override
	public Tags apply(ServerWebExchange exchange) {
		String outcome = "CUSTOM";
		String status = "CUSTOM";
		String httpStatusCodeStr = "NA";

		String httpMethod = exchange.getRequest().getMethodValue();

		// 检查是否为非标准 HTTPS 状态
		// 需要首先检查，否则会使用未改变状态的委托响应
		if (exchange.getResponse() instanceof AbstractServerHttpResponse) {
			Integer statusInt = ((AbstractServerHttpResponse) exchange.getResponse()).getRawStatusCode();
			if (statusInt != null) {
				status = String.valueOf(statusInt);
				httpStatusCodeStr = status;
				HttpStatus resolved = HttpStatus.resolve(statusInt);
				if (resolved != null) {
					// 这不是自定义状态，使用状态系列名称
					outcome = resolved.series().name();
					status = resolved.name();
				}
			}
		}
		else {
			HttpStatus statusCode = exchange.getResponse().getStatusCode();
			if (statusCode != null) {
				httpStatusCodeStr = String.valueOf(statusCode.value());
				outcome = statusCode.series().name();
				status = statusCode.name();
			}
		}

		return Tags.of("outcome", outcome, "status", status, "httpStatusCode", httpStatusCodeStr, "httpMethod",
				httpMethod);
	}

}
