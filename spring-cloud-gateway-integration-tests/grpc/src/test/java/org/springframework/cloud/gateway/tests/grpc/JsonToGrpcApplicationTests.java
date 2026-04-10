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

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * JSON 转 gRPC 协议转换集成测试类。
 * <p>
 * 验证 Spring Cloud Gateway 的 {@code JsonToGrpc} 过滤器能够将客户端发送的 JSON HTTP 请求
 * 自动转换为 gRPC 协议请求，并将 gRPC 服务端返回的 Protobuf 响应转换为 JSON 格式返回给客户端。
 * </p>
 * <p>
 * 测试流程：
 * <ol>
 *   <li>通过 Actuator 动态注册带有 {@code JsonToGrpc} 过滤器的路由</li>
 *   <li>使用 RestTemplate 发送 JSON 格式的 HTTP POST 请求到网关</li>
 *   <li>验证网关返回的 JSON 响应是否包含正确的 gRPC 服务处理结果</li>
 * </ol>
 * </p>
 *
 * @author Alberto C. Ríos
 * @author Abel Salgado Romero
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT) // 以随机端口启动完整 Spring Boot 应用
public class JsonToGrpcApplicationTests {

	/**
	 * Spring Boot 随机分配的本地网关端口（HTTPS 端口）。
	 */
	@LocalServerPort
	private int gatewayPort;

	/**
	 * 跳过 SSL 证书验证的 HTTP 客户端（测试环境专用）。
	 */
	private RestTemplate restTemplate;

	/**
	 * 每个测试方法执行前的初始化操作：创建不校验证书的 RestTemplate。
	 */
	@BeforeEach
	void setUp() {
		restTemplate = createUnsecureClient();
	}

	/**
	 * 测试：通过网关将 JSON HTTP 请求转换并代理到 gRPC 服务，验证 JSON↔gRPC 协议转换正确性。
	 * <p>
	 * 由于 gRPC 服务器与网关运行在同一应用实例中，端口在测试启动时才能确定，
	 * 因此需要通过 Actuator 接口动态注册带有 {@code JsonToGrpc} 过滤器的路由。
	 * </p>
	 * <p>
	 * 路由配置说明：
	 * <ul>
	 *   <li>路径：{@code /json/hello}</li>
	 *   <li>过滤器：{@code JsonToGrpc=<pb文件>,<proto文件>,<服务名>,<方法名>}</li>
	 *   <li>目标：{@code https://localhost:<grpcServerPort>}</li>
	 * </ul>
	 * </p>
	 */
	@Test
	public void shouldConvertFromJSONToGRPC() {
		// 由于 gRPC 服务器与网关在同一实例中，端口在测试启动前未知，
		// 因此通过 Actuator 接口动态配置路由
		final RouteConfigurer configurer = new RouteConfigurer(gatewayPort);
		int grpcServerPort = gatewayPort + 1;
		// 注册路由：将 /json/hello 的请求通过 JsonToGrpc 过滤器转发到 gRPC 服务
		// 参数顺序：proto描述文件(.pb), proto定义文件(.proto), 服务名, 方法名
		configurer.addRoute(grpcServerPort, "/json/hello",
				"JsonToGrpc=file:src/main/proto/hello.pb,file:src/main/proto/hello.proto,HelloService,hello");

		// 发送 JSON 格式的 HTTP POST 请求到网关的 /json/hello 接口
		String response = restTemplate.postForEntity("https://localhost:" + this.gatewayPort + "/json/hello",
				"{\"firstName\":\"Duff\", \"lastName\":\"McKagan\"}", String.class).getBody();

		// 验证响应不为空
		Assertions.assertThat(response).isNotNull();
		// 验证 gRPC 服务返回的问候语经协议转换后出现在 JSON 响应中
		Assertions.assertThat(response).contains("{\"greeting\":\"Hello, Duff McKagan\"}");
	}

	/**
	 * 创建一个跳过 SSL 证书验证的 {@link RestTemplate}（测试环境专用）。
	 * <p>
	 * 使用 Apache HttpClient 构建底层连接，配置信任所有证书的策略和禁用主机名校验，
	 * 以支持访问使用自签名证书的 HTTPS 端点。
	 * </p>
	 * <p>
	 * <strong>警告：</strong>此方法仅适用于测试目的，严禁在生产环境使用。
	 * </p>
	 *
	 * @return 配置了不安全 SSL 的 RestTemplate 实例
	 */
	private RestTemplate createUnsecureClient() {
		// 定义信任策略：无条件信任所有证书
		TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
		SSLContext sslContext;
		try {
			// 使用自定义信任策略构建 SSL 上下文
			sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
		}
		catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
			throw new RuntimeException(e);
		}
		// 创建跳过主机名验证的 SSL Socket 工厂
		SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
				NoopHostnameVerifier.INSTANCE);

		// 注册 http 和 https 协议对应的 Socket 工厂
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
				.register("https", sslSocketFactory).register("http", new PlainConnectionSocketFactory()).build();

		// 使用自定义连接工厂注册表构建连接管理器
		BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(
				socketFactoryRegistry);
		// 构建 Apache HttpClient，应用 SSL 工厂和连接管理器
		CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslSocketFactory)
				.setConnectionManager(connectionManager).build();

		// 将 Apache HttpClient 包装为 Spring 的请求工厂
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

		return new RestTemplate(requestFactory);
	}

}
