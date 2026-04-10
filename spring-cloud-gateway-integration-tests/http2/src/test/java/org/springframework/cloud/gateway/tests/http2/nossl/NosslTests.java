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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.tests.http2.Http2Application;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.SocketUtils;
import reactor.core.publisher.Hooks;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import static org.springframework.cloud.gateway.tests.http2.Http2ApplicationTests.assertResponse;

/**
 * HTTP/2 TLS 终止（SSL Termination）集成测试类。
 * <p>
 * 验证 Spring Cloud Gateway 的 TLS 终止场景：
 * <ul>
 * <li>客户端使用 HTTPS（HTTP/2 over TLS）连接到网关</li>
 * <li>网关在接收到 HTTPS 请求后，以普通 HTTP（无 TLS）协议将请求转发到后端服务</li>
 * <li>后端服务（{@link NosslConfiguration}）以独立进程在随机端口启动</li>
 * </ul>
 * </p>
 * <p>
 * 同时验证在此场景下，整个链路中<strong>不出现</strong> HTTP/2 的连接前言 （{@code "PRI * HTTP/2.0"}），因为网关到后端使用的是
 * HTTP/1.1。
 * </p>
 *
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class) // 启用标准输出/错误输出捕获
@SpringBootTest(classes = Http2Application.class, webEnvironment = WebEnvironment.RANDOM_PORT) // 随机端口启动
// Gateway
// 应用
@DirtiesContext // 测试完成后重置 Spring 上下文
public class NosslTests {

	/**
	 * Spring Boot 随机分配的网关本地端口（HTTPS）。
	 */
	@LocalServerPort
	int port;

	/**
	 * 测试类初始化方法（所有测试方法执行前运行一次）。
	 * <p>
	 * 查找一个可用的 TCP 端口并通过系统属性 {@code nossl.port} 传递给
	 * {@link Http2Application.NosslServiceConf}，使其负载均衡指向正确端口。
	 * </p>
	 */
	@BeforeAll
	static void beforeAll() {
		// 查找一个空闲的 TCP 端口，分配给非 TLS 后端服务
		int noSslPort = SocketUtils.findAvailableTcpPort();
		// 通过系统属性传递端口，供 NosslServiceConf 读取
		System.setProperty("nossl.port", String.valueOf(noSslPort));
	}

	/**
	 * 测试类清理方法（所有测试方法执行后运行一次）。
	 * <p>
	 * 清除 {@code nossl.port} 系统属性，避免影响其他测试。
	 * </p>
	 */
	@AfterAll
	static void afterAll() {
		// 清除系统属性，避免测试间相互影响
		System.clearProperty("nossl.port");
	}

	/**
	 * 测试：验证网关的 HTTP/2 TLS 终止功能正常工作。
	 * <p>
	 * 测试步骤：
	 * <ol>
	 * <li>读取分配给后端服务的非 TLS 端口</li>
	 * <li>以该端口启动 {@link NosslConfiguration} 作为独立 HTTP 后端服务</li>
	 * <li>通过网关 HTTPS 访问 {@code /nossl} 路径</li>
	 * <li>验证响应体为 "nossl"，且日志中不含 HTTP/2 连接前言（后端链路为 HTTP/1.1）</li>
	 * </ol>
	 * </p>
	 * @param output 捕获的标准输出内容
	 */
	@Test
	public void http2TerminationWorks(CapturedOutput output) {
		// 从系统属性读取后端非 TLS 服务的端口
		int nosslPort = Integer.parseInt(System.getProperty("nossl.port"));
		System.err.println("nossl.port = " + nosslPort);

		// 开启 Reactor 操作符调试模式，便于定位异步问题
		Hooks.onOperatorDebug();

		// 在指定端口启动非 TLS 后端服务（使用 nossl profile），并在测试完成后自动关闭
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NosslConfiguration.class)
				.properties("server.port=" + nosslPort).profiles("nossl").run()) {
			// 构造访问网关 /nossl 路由的 HTTPS URL
			String uri = "https://localhost:" + port + "/nossl";
			String expected = "nossl";
			// 验证网关正确代理请求到非 TLS 后端，响应内容为 "nossl"
			assertResponse(uri, expected);
			// 验证日志中不含 HTTP/2 连接前言（即网关到后端未使用 HTTP/2）
			Assertions.assertThat(output).doesNotContain("PRI * HTTP/2.0");
		}
	}

}
