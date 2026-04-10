/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.gateway.tests.http2.nossl;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 无 SSL（非 TLS）后端服务配置类，用于 HTTP/2 TLS 终止测试。
 * <p>
 * 该类作为一个轻量级的 HTTP（非 HTTPS）后端服务，配合 {@link NosslTests} 使用，
 * 用于验证 Spring Cloud Gateway 能够接受客户端的 HTTPS 请求（TLS 终止），
 * 然后将其以普通 HTTP 协议转发给不支持 TLS 的后端服务。
 * </p>
 * <p>
 * 该类在测试中以独立的 Spring Boot 应用启动，监听通过系统属性
 * {@code nossl.port} 指定的端口。
 * </p>
 */
@RestController      // 标记为 REST 控制器，处理 HTTP 请求
@Configuration       // 标记为 Spring 配置类
@EnableAutoConfiguration // 启用自动配置（用于独立启动为 Spring Boot 应用）
public class NosslConfiguration {

	/**
	 * 提供根路径的 HTTP GET 接口，返回 "nossl" 字符串。
	 * <p>
	 * 用于验证网关将 HTTPS 请求正确转发到该非 TLS 后端后，
	 * 响应能够成功返回到客户端。
	 * </p>
	 *
	 * @return 固定字符串 "nossl"，用于标识该请求来自非 SSL 后端
	 */
	@GetMapping
	public String home() {
		return "nossl";
	}

}
