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

import java.security.cert.X509Certificate;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;

import org.springframework.boot.autoconfigure.web.ServerProperties;

/**
 * HTTP 客户端 SSL 配置器。
 * <p>
 * 继承自 {@link AbstractSslConfigurer}，专门用于配置 Netty HttpClient 的 SSL/TLS 上下文。 支持根据服务器 HTTP/2
 * 配置自动选择 HTTP/1.1 或 HTTP/2 的 SSL 上下文。
 * </p>
 *
 * @see AbstractSslConfigurer
 */
public class HttpClientSslConfigurer extends AbstractSslConfigurer<HttpClient, HttpClient> {

	/** Spring Boot 服务器配置属性，用于获取 HTTP/2 等配置 */
	private final ServerProperties serverProperties;

	/**
	 * 构造 HTTP 客户端 SSL 配置器。
	 * @param sslProperties SSL 配置属性
	 * @param serverProperties 服务器配置属性
	 */
	public HttpClientSslConfigurer(HttpClientProperties.Ssl sslProperties, ServerProperties serverProperties) {
		super(sslProperties);
		this.serverProperties = serverProperties;
	}

	/**
	 * 为 HTTP 客户端配置 SSL。
	 * <p>
	 * 如果配置了密钥库、信任证书或使用了不安全的信任管理器，则对客户端启用 SSL 安全连接。
	 * </p>
	 * @param client 需要配置 SSL 的 HTTP 客户端
	 * @return 配置 SSL 后的 HTTP 客户端
	 */
	public HttpClient configureSsl(HttpClient client) {
		final HttpClientProperties.Ssl ssl = getSslProperties();

		if ((ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0)
				|| getTrustedX509CertificatesForTrustManager().length > 0 || ssl.isUseInsecureTrustManager()) {
			client = client.secure(sslContextSpec -> {
				// configure ssl
				configureSslContext(ssl, sslContextSpec);
			});
		}
		return client;
	}

	/**
	 * 配置 SSL 上下文。
	 * <p>
	 * 根据服务器是否启用 HTTP/2，选择对应的 SSL 上下文规格（HTTP/2 或 HTTP/1.1），
	 * 并配置信任管理器和密钥管理器。同时设置握手超时、关闭通知超时等参数。
	 * </p>
	 * @param ssl SSL 配置属性
	 * @param sslContextSpec SSL 上下文规格
	 */
	protected void configureSslContext(HttpClientProperties.Ssl ssl, SslProvider.SslContextSpec sslContextSpec) {
		SslProvider.ProtocolSslContextSpec clientSslContext = (serverProperties.getHttp2().isEnabled())
				? Http2SslContextSpec.forClient() : Http11SslContextSpec.forClient();
		clientSslContext.configure(sslContextBuilder -> {
			X509Certificate[] trustedX509Certificates = getTrustedX509CertificatesForTrustManager();
			if (trustedX509Certificates.length > 0) {
				setTrustManager(sslContextBuilder, trustedX509Certificates);
			}
			else if (ssl.isUseInsecureTrustManager()) {
				setTrustManager(sslContextBuilder, InsecureTrustManagerFactory.INSTANCE);
			}

			try {
				sslContextBuilder.keyManager(getKeyManagerFactory());
			}
			catch (Exception e) {
				logger.error(e);
			}
		});

		sslContextSpec.sslContext(clientSslContext).handshakeTimeout(ssl.getHandshakeTimeout())
				.closeNotifyFlushTimeout(ssl.getCloseNotifyFlushTimeout())
				.closeNotifyReadTimeout(ssl.getCloseNotifyReadTimeout());
	}

}
