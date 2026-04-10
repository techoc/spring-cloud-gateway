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

package org.springframework.cloud.gateway.tests.grpc;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import static io.grpc.Status.FAILED_PRECONDITION;
import static io.grpc.netty.NegotiationType.TLS;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * gRPC 应用集成测试类。
 * <p>
 * 验证 Spring Cloud Gateway 能够正确代理 gRPC 一元调用（Unary Call）：
 * <ul>
 *   <li>正常请求：通过网关转发并正确返回问候响应</li>
 *   <li>异常请求：通过网关转发并正确传递服务端抛出的 {@link StatusRuntimeException}</li>
 * </ul>
 * 测试使用 TLS 加密通道，客户端配置为信任所有证书（测试环境专用）。
 * </p>
 *
 * @author Alberto C. Ríos
 */
@SpringBootTest(classes = GRPCApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT) // 以随机端口启动完整应用
@DirtiesContext // 每次测试后重置应用上下文，防止端口冲突
public class GRPCApplicationTests {

	/**
	 * Spring Boot 随机分配的本地网关端口（HTTP/HTTPS 端口）。
	 */
	@LocalServerPort
	private int gatewayPort;

	/**
	 * 每个测试方法执行前的初始化操作。
	 * <p>
	 * 计算 gRPC 服务器端口（网关端口 + 1），并通过 Actuator 动态注册路由，
	 * 将 {@code /**} 的请求转发到 gRPC 服务器。
	 * </p>
	 */
	@BeforeEach
	void setUp() {
		// gRPC 服务器端口 = Spring 网关端口 + 1
		int grpcServerPort = gatewayPort + 1;
		// 使用 Actuator 接口动态注册路由（不带 filter，直接透传）
		final RouteConfigurer configurer = new RouteConfigurer(gatewayPort);
		configurer.addRoute(grpcServerPort, "/**", null);
	}

	/**
	 * 测试：通过网关发送正常 gRPC 一元调用，应成功收到问候响应。
	 * <p>
	 * 客户端发送包含 firstName="Sir"、lastName="FromClient" 的请求，
	 * 期望响应的 greeting 字段为 "Hello, Sir FromClient"。
	 * </p>
	 *
	 * @throws SSLException 创建 SSL 通道失败时抛出
	 */
	@Test
	public void gRPCUnaryCallShouldReturnResponse() throws SSLException {
		// 创建经由网关端口的 TLS 安全通道
		ManagedChannel channel = createSecuredChannel(gatewayPort);

		// 发起阻塞式 Hello RPC 调用
		final HelloResponse response = HelloServiceGrpc.newBlockingStub(channel)
				.hello(HelloRequest.newBuilder().setFirstName("Sir").setLastName("FromClient").build());

		// 验证响应中的问候语是否符合预期
		Assertions.assertThat(response.getGreeting()).isEqualTo("Hello, Sir FromClient");
	}

	/**
	 * 创建经由指定端口的 TLS gRPC 通道。
	 * <p>
	 * 使用"信任所有证书"的 {@link TrustManager} 绕过证书校验，
	 * 仅适用于测试场景，生产环境请勿使用此方式。
	 * </p>
	 *
	 * @param port 连接的目标端口
	 * @return 配置好 TLS 的 gRPC 托管通道
	 * @throws SSLException SSL 上下文构建失败时抛出
	 */
	private ManagedChannel createSecuredChannel(int port) throws SSLException {
		// 创建信任所有证书的 TrustManager 数组
		TrustManager[] trustAllCerts = createTrustAllTrustManager();

		return NettyChannelBuilder.forAddress("localhost", port).useTransportSecurity()
				// 配置 SSL 上下文：使用自定义 TrustManager 跳过证书验证
				.sslContext(GrpcSslContexts.forClient().trustManager(trustAllCerts[0]).build())
				.negotiationType(TLS)
				.build();
	}

	/**
	 * 测试：通过网关发送会触发服务端抛异常的 gRPC 一元调用，应正确接收到错误状态。
	 * <p>
	 * 当 firstName 为 "failWithRuntimeException!" 时，服务端会抛出
	 * {@code FAILED_PRECONDITION} 状态的 {@link StatusRuntimeException}，
	 * 网关应将该错误原样传递给客户端。
	 * </p>
	 *
	 * @throws SSLException 创建 SSL 通道失败时抛出
	 */
	@Test
	public void gRPCUnaryCallShouldHandleRuntimeException() throws SSLException {
		// 创建经由网关端口的 TLS 安全通道
		ManagedChannel channel = createSecuredChannel(gatewayPort);

		try {
			// 使用特殊 firstName 触发服务端抛出 RuntimeException
			HelloServiceGrpc.newBlockingStub(channel)
					.hello(HelloRequest.newBuilder().setFirstName("failWithRuntimeException!").build());
		}
		catch (StatusRuntimeException e) {
			// 验证接收到的 gRPC 错误状态码是否为 FAILED_PRECONDITION
			Assertions.assertThat(FAILED_PRECONDITION.getCode()).isEqualTo(e.getStatus().getCode());
			// 验证错误描述信息是否正确传递
			Assertions.assertThat("Invalid firstName").isEqualTo(e.getStatus().getDescription());
		}
	}

	/**
	 * 创建一个信任所有 X.509 证书的 {@link TrustManager} 数组。
	 * <p>
	 * <strong>警告：</strong>此方法仅用于测试目的，会完全跳过 SSL 证书验证，
	 * 存在中间人攻击风险，严禁在生产环境使用。
	 * </p>
	 *
	 * @return 包含一个"信任所有"TrustManager 的数组
	 */
	private TrustManager[] createTrustAllTrustManager() {
		return new TrustManager[] { new X509TrustManager() {
			/** 返回空数组，表示不设置受信任的 CA 列表（信任所有颁发者）*/
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			/** 跳过客户端证书验证 */
			public void checkClientTrusted(X509Certificate[] certs, String authType) {
			}

			/** 跳过服务端证书验证 */
			public void checkServerTrusted(X509Certificate[] certs, String authType) {
			}
		} };
	}

}
