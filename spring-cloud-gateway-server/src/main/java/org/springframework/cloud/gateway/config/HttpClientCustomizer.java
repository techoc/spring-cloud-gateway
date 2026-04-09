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

import reactor.netty.http.client.HttpClient;

/**
 * HttpClient 自定义器接口，用于自定义和扩展 HttpClient 的配置。
 * <p>
 * 实现此接口可以对网关使用的 HTTP 客户端进行个性化配置，如添加拦截器、修改连接参数等。
 *
 * @author Spencer Gibb
 */
@FunctionalInterface
public interface HttpClientCustomizer {

	/**
	 * 自定义指定的 {@link HttpClient}。
	 * @param httpClient 需要自定义的 HTTP 客户端
	 * @return 自定义后的 HttpClient 实例
	 */
	HttpClient customize(HttpClient httpClient);

}
