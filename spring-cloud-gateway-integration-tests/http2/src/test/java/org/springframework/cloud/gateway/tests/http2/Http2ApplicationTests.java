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

package org.springframework.cloud.gateway.tests.http2;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * HTTP/2 协议集成测试类。
 * <p>
 * 验证 Spring Cloud Gateway 对 HTTP/2（h2）协议的完整支持：
 * <ul>
 * <li>客户端使用 HTTP/2 协议通过 TLS 连接到网关</li>
 * <li>网关通过路由将请求代理到后端服务</li>
 * <li>验证协商使用了 HTTP/2 协议（日志中包含 h2 协议协商标志）</li>
 * </ul>
 * </p>
 *
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class) // 启用标准输出/错误输出捕获
@SpringBootTest(classes = Http2Application.class, webEnvironment = WebEnvironment.RANDOM_PORT) // 随机端口启动应用
@DirtiesContext // 测试完成后重置 Spring 上下文
public class Http2ApplicationTests {

	/**
	 * Spring Boot 随机分配的本地服务器端口（HTTPS）。
	 */
	@LocalServerPort
	int port;

	/**
	 * 通用响应断言方法：向指定 URI 发送 HTTP GET 请求并验证响应内容。
	 * <p>
	 * 使用支持 HTTP/1.1 和 HTTP/2 双协议的 {@link HttpClient}， 通过 TLS ALPN 机制自动协商最优协议版本。
	 * </p>
	 * @param uri 请求目标 URI
	 * @param expected 期望的响应体字符串
	 */
	public static void assertResponse(String uri, String expected) {
		// 构建支持 HTTP/2 的 WebClient
		WebClient client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(getHttpClient())).build();
		// 发起 GET 请求，获取响应实体（包含状态码和响应体）
		Mono<ResponseEntity<String>> responseEntityMono = client.get().uri(uri).retrieve().toEntity(String.class);
		// 使用 StepVerifier 验证响应式流的结果
		StepVerifier.create(responseEntityMono).assertNext(entity -> {
			// 验证 HTTP 状态码为 200 OK
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
			// 验证响应体内容符合预期
			assertThat(entity.getBody()).isEqualTo(expected);
		}).expectComplete().verify();
	}

	/**
	 * 创建支持 HTTP/2 和 TLS 的 Reactor Netty {@link HttpClient}（测试环境专用）。
	 * <p>
	 * 配置说明：
	 * <ul>
	 * <li>连接池：最大连接数 100，无等待超时，无限制等待队列</li>
	 * <li>协议：同时支持 HTTP/1.1 和 HTTP/2（通过 ALPN 自动协商）</li>
	 * <li>TLS：使用 Netty 的 {@code InsecureTrustManagerFactory} 跳过证书验证</li>
	 * </ul>
	 * <strong>警告：</strong>不安全的 TrustManager 仅适用于测试，生产环境必须使用正规证书。
	 * </p>
	 * @return 配置完成的 Reactor Netty HttpClient
	 */
	static HttpClient getHttpClient() {
		return HttpClient
				// 自定义连接池：最大100连接，禁用等待超时，不限制等待队列大小
				.create(ConnectionProvider.builder("test").maxConnections(100)
						.pendingAcquireTimeout(Duration.ofMillis(0)).pendingAcquireMaxCount(-1).build())
				// 同时支持 HTTP/1.1 和 HTTP/2 协议（ALPN 协商）
				.protocol(HttpProtocol.HTTP11, HttpProtocol.H2)
				// 配置 TLS：使用不安全的信任管理器，跳过证书验证（仅测试用）
				.secure(sslContextSpec -> {
					Http2SslContextSpec clientSslCtxt = Http2SslContextSpec.forClient()
							.configure(builder -> builder.trustManager(InsecureTrustManagerFactory.INSTANCE));
					sslContextSpec.sslContext(clientSslCtxt);
				});
	}

	/**
	 * 测试：通过网关发送 HTTP/2 请求，验证协议协商成功且响应正确。
	 * <p>
	 * 验证日志输出中包含：
	 * <ul>
	 * <li>{@code "Negotiated application-level protocol [h2]"} - 表明 TLS ALPN 协商了
	 * HTTP/2</li>
	 * <li>{@code "PRI * HTTP/2.0"} - HTTP/2 连接前言，标志着 HTTP/2 连接建立</li>
	 * </ul>
	 * </p>
	 * @param output 捕获的标准输出内容
	 */
	@Test
	public void http2Works(CapturedOutput output) {
		// 开启 Reactor 操作符调试模式，帮助定位异步流中的问题
		Hooks.onOperatorDebug();
		String uri = "https://localhost:" + port + "/myprefix/hello";
		String expected = "Hello";
		// 发送请求并验证响应
		assertResponse(uri, expected);
		// 验证日志中出现了 HTTP/2 协议协商和连接标志
		Assertions.assertThat(output).contains("Negotiated application-level protocol [h2]", "PRI * HTTP/2.0");
	}

}
