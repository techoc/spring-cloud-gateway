/*
 * Copyright 2013-2023 the original author or authors.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.SSLContext;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 通过 Spring Cloud Gateway Actuator 接口动态管理路由的工具类。
 * <p>
 * 在集成测试中，由于网关和后端服务端口是随机分配的，无法在配置文件中预先配置路由。
 * 本类通过调用 {@code /actuator/gateway/routes/{id}} 接口动态添加路由，
 * 并通过 {@code /actuator/gateway/refresh} 接口使新路由立即生效。
 * </p>
 * <p>
 * 内部使用跳过 SSL 证书验证的 Apache HttpClient，以支持 HTTPS Actuator 端点访问。
 * </p>
 */
public class RouteConfigurer {

	/**
	 * 网关/Actuator 所在服务器的端口号。
	 */
	private final int actuatorPort;

	/**
	 * 用于调用 Actuator 接口的 HTTP 客户端（跳过 SSL 验证）。
	 */
	private final RestTemplate restTemplate;

	/**
	 * 构造函数，初始化 Actuator 端口并创建不校验证书的 RestTemplate。
	 *
	 * @param actuatorPort 运行网关的服务器端口
	 */
	RouteConfigurer(int actuatorPort) {
		this.actuatorPort = actuatorPort;
		this.restTemplate = createUnsecureClient();
	}

	/**
	 * 通过 Actuator 接口动态添加一条网关路由，并立即刷新使其生效。
	 * <p>
	 * 路由将所有匹配 {@code path} 的请求转发到 {@code https://localhost:<grpcServerPort>}。
	 * 若 {@code filter} 不为空，则同时配置该路由过滤器。
	 * </p>
	 *
	 * @param grpcServerPort 后端 gRPC 服务器的端口号
	 * @param path           路由匹配的请求路径（如 {@code /**} 或 {@code /json/hello}）
	 * @param filter         路由过滤器配置字符串（如 {@code JsonToGrpc=...}），为 {@code null} 时不配置过滤器
	 */
	public void addRoute(int grpcServerPort, String path, String filter) {
		// 使用 UUID 生成唯一的路由 ID，避免测试间路由冲突
		final String routeId = "test-route-" + UUID.randomUUID();

		// 构建路由配置 Map，对应 Gateway Actuator 接口的请求体格式
		Map<String, Object> route = new HashMap<>();
		route.put("id", routeId);
		// 设置路由目标 URI（指向本地 gRPC 服务器）
		route.put("uri", "https://localhost:" + grpcServerPort);
		// 设置路由断言：匹配指定路径
		route.put("predicates", Collections.singletonList("Path=" + path));
		// 仅在 filter 非空时设置路由过滤器
		if (filter != null) {
			route.put("filters", Arrays.asList(filter));
		}

		// 通过 POST 请求调用 Actuator 接口创建路由
		ResponseEntity<String> exchange = restTemplate.exchange(url("/actuator/gateway/routes/" + routeId),
				HttpMethod.POST, new HttpEntity<>(route), String.class);

		// 断言路由创建成功（期望返回 201 Created）
		assert exchange.getStatusCode() == HttpStatus.CREATED;

		// 刷新路由使新配置立即生效
		refreshRoutes();
	}

	/**
	 * 调用 Actuator 的路由刷新接口，使最新的路由配置立即生效。
	 * <p>
	 * 对应接口：{@code POST /actuator/gateway/refresh}
	 * </p>
	 */
	private void refreshRoutes() {
		ResponseEntity<String> exchange = restTemplate.exchange(url("/actuator/gateway/refresh"), HttpMethod.POST,
				new HttpEntity<>(""), String.class);

		// 断言刷新成功（期望返回 200 OK）
		assert exchange.getStatusCode() == HttpStatus.OK;
	}

	/**
	 * 拼接 Actuator 接口的完整 URL。
	 *
	 * @param context 接口路径（如 {@code /actuator/gateway/refresh}）
	 * @return 完整的 HTTPS URL 字符串
	 */
	private String url(String context) {
		return String.format("https://localhost:%s%s", this.actuatorPort, context);
	}

	/**
	 * 创建一个跳过 SSL 证书验证的 {@link RestTemplate}（测试环境专用）。
	 * <p>
	 * 使用 Apache HttpClient 构建底层连接，禁用证书链校验和主机名校验，
	 * 以支持访问使用自签名证书的 HTTPS Actuator 端点。
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
		HttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);
		// 构建 Apache HttpClient，指定连接管理器
		CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();

		// 将 Apache HttpClient 包装为 Spring 的请求工厂
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

		return new RestTemplate(requestFactory);
	}

}
