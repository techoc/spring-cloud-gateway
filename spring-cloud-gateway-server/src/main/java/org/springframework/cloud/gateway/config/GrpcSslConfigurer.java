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

import javax.net.ssl.SSLException;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * gRPC SSL 配置器，用于配置 gRPC 通道的 SSL/TLS 安全连接。
 *
 * @author Alberto C. Ríos
 */
public class GrpcSslConfigurer extends AbstractSslConfigurer<NettyChannelBuilder, ManagedChannel> {

	/**
	 * 使用指定的 SSL 属性创建 GrpcSslConfigurer 实例。
	 * @param sslProperties HTTP 客户端 SSL 配置属性
	 */
	public GrpcSslConfigurer(HttpClientProperties.Ssl sslProperties) {
		super(sslProperties);
	}

	/**
	 * 配置并构建 SSL 安全的 gRPC 通道。
	 * @param NettyChannelBuilder Netty 通道构建器
	 * @return 配置完成的 ManagedChannel 实例
	 * @throws SSLException 当 SSL 配置失败时抛出
	 */
	@Override
	public ManagedChannel configureSsl(NettyChannelBuilder NettyChannelBuilder) throws SSLException {
		return NettyChannelBuilder.useTransportSecurity().sslContext(getSslContext()).build();
	}

	/**
	 * 获取 SSL 上下文，配置信任管理器和密钥管理器。
	 * @return 配置完成的 SslContext 实例
	 * @throws SSLException 当 SSL 上下文构建失败时抛出
	 */
	private SslContext getSslContext() throws SSLException {

		final SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();

		final HttpClientProperties.Ssl ssl = getSslProperties();
		boolean useInsecureTrustManager = ssl.isUseInsecureTrustManager();
		if (useInsecureTrustManager) {
			sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers()[0]);
		}

		if (!useInsecureTrustManager && ssl.getTrustedX509Certificates().size() > 0) {
			sslContextBuilder.trustManager(getTrustedX509CertificatesForTrustManager());
		}

		return sslContextBuilder.keyManager(getKeyManagerFactory()).build();
	}

}
