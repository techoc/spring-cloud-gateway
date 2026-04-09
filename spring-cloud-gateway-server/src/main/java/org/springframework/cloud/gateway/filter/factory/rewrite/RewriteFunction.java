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

import java.util.function.BiFunction;

import org.reactivestreams.Publisher;

import org.springframework.web.server.ServerWebExchange;

/**
 * 请求/响应体重写函数接口。
 *
 * <p>
 * 该接口用于定义请求体或响应体的转换逻辑， 可以在过滤器中将被转换的对象从一种类型转换为另一种类型。
 *
 * <p>
 * 主要用途：
 * <ul>
 * <li>{@link ModifyRequestBodyGatewayFilterFactory} - 修改请求体</li>
 * <li>{@link ModifyResponseBodyGatewayFilterFactory} - 修改响应体</li>
 * </ul>
 *
 * <p>
 * 使用示例： <pre>
 * {@code
 * &#64;Bean
 * public RewriteFunction<MyRequest, ModifiedRequest> myRewriteFunction() {
 *     return (exchange, original) -> {
 *         ModifiedRequest modified = new ModifiedRequest();
 *         modified.setId(original.getId());
 *         modified.setData(transform(original.getData()));
 *         return Mono.just(modified);
 *     };
 * }
 * }
 * </pre>
 *
 * <p>
 * 使用 Lambda 表达式： <pre>
 * {@code
 * f.modifyResponseBody(String.class, String.class,
 *     (exchange, json) -> {
 *         JsonNode node = objectMapper.readTree(json);
 *         // 处理 JSON 数据
 *         return Mono.just(objectMapper.writeValueAsString(node));
 *     })
 * }
 * </pre>
 *
 * @param <T> 输入对象类型（第一个参数）
 * @param <R> 输出对象类型（Publisher 中元素的类型）
 * @author Spencer Gibb
 * @see ModifyRequestBodyGatewayFilterFactory
 * @see ModifyResponseBodyGatewayFilterFactory
 */
public interface RewriteFunction<T, R> extends BiFunction<ServerWebExchange, T, Publisher<R>> {

}
