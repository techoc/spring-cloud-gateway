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

import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import io.netty.handler.ssl.SslContextBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.ResourceUtils;

/**
 * SSL 配置的抽象基类。
 * <p>
 * 为组件类型 T 配置 SSL/TLS 安全上下文，返回配置完成后的实例 S（可以与 T 相同）。 子类需要实现 {@link #configureSsl(Object)}
 * 方法来定义具体的 SSL 配置逻辑。
 * </p>
 *
 * @param <T> 需要配置 SSL 的组件类型
 * @param <S> 配置完成后返回的实例类型
 * @author Abel Salgado Romero
 */
public abstract class AbstractSslConfigurer<T, S> {

	/** 日志记录器 */
	protected final Log logger = LogFactory.getLog(this.getClass());

	/** SSL 配置属性 */
	private final HttpClientProperties.Ssl ssl;

	/**
	 * 构造方法，初始化 SSL 配置属性。
	 * @param sslProperties SSL 配置属性对象，包含密钥库、信任证书等配置信息
	 */
	protected AbstractSslConfigurer(HttpClientProperties.Ssl sslProperties) {
		this.ssl = sslProperties;
	}

	/**
	 * 为指定的客户端组件配置 SSL。
	 * @param client 需要配置 SSL 的客户端实例
	 * @return 配置完成后的实例
	 * @throws SSLException 如果 SSL 配置过程中发生错误
	 */
	abstract public S configureSsl(T client) throws SSLException;

	/**
	 * 获取 SSL 配置属性。
	 * @return SSL 配置属性对象
	 */
	protected HttpClientProperties.Ssl getSslProperties() {
		return ssl;
	}

	/**
	 * 从配置的信任证书路径中加载 X.509 证书数组，用于初始化 TrustManager。
	 * <p>
	 * 遍历 {@link HttpClientProperties.Ssl#getTrustedX509Certificates()} 中配置的所有证书路径， 使用
	 * X.509 CertificateFactory 解析证书文件并返回数组。
	 * </p>
	 * @return 加载的 X.509 信任证书数组
	 * @throws RuntimeException 如果证书加载失败或 CertificateFactory 初始化失败
	 */
	protected X509Certificate[] getTrustedX509CertificatesForTrustManager() {

		try {
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			ArrayList<Certificate> allCerts = new ArrayList<>();
			for (String trustedCert : ssl.getTrustedX509Certificates()) {
				try {
					URL url = ResourceUtils.getURL(trustedCert);
					Collection<? extends Certificate> certs = certificateFactory.generateCertificates(url.openStream());
					allCerts.addAll(certs);
				}
				catch (IOException e) {
					throw new RuntimeException("Could not load certificate '" + trustedCert + "'", e);
				}
			}
			return allCerts.toArray(new X509Certificate[allCerts.size()]);
		}
		catch (CertificateException e1) {
			throw new RuntimeException("Could not load CertificateFactory X.509", e1);
		}
	}

	/**
	 * 创建并初始化 KeyManagerFactory。
	 * <p>
	 * 如果配置了密钥库（keyStore），则使用默认算法创建 KeyManagerFactory， 并用密钥库和密钥密码进行初始化。密钥密码优先使用
	 * {@code keyPassword}， 若未设置则回退使用 {@code keyStorePassword}。
	 * </p>
	 * @return 初始化完成的 KeyManagerFactory 实例；如果未配置密钥库则返回 {@code null}
	 * @throws IllegalStateException 如果 KeyManagerFactory 初始化过程中发生错误
	 */
	protected KeyManagerFactory getKeyManagerFactory() {

		try {
			if (ssl.getKeyStore() != null && ssl.getKeyStore().length() > 0) {
				KeyManagerFactory keyManagerFactory = KeyManagerFactory
						.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				char[] keyPassword = ssl.getKeyPassword() != null ? ssl.getKeyPassword().toCharArray() : null;

				if (keyPassword == null && ssl.getKeyStorePassword() != null) {
					keyPassword = ssl.getKeyStorePassword().toCharArray();
				}

				keyManagerFactory.init(this.createKeyStore(), keyPassword);

				return keyManagerFactory;
			}

			return null;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 根据配置属性创建并加载 KeyStore 实例。
	 * <p>
	 * 支持通过 {@code keyStoreProvider} 指定密钥库提供者，通过 {@code keyStoreType} 指定密钥库类型（如 PKCS12、JKS
	 * 等）。 使用 {@code keyStore} 路径加载密钥库文件，并使用 {@code keyStorePassword} 作为密码。
	 * </p>
	 * @return 加载完成的 KeyStore 实例
	 * @throws RuntimeException 如果密钥库加载失败或指定的类型/提供者不可用
	 */
	protected KeyStore createKeyStore() {

		try {
			KeyStore store = ssl.getKeyStoreProvider() != null
					? KeyStore.getInstance(ssl.getKeyStoreType(), ssl.getKeyStoreProvider())
					: KeyStore.getInstance(ssl.getKeyStoreType());
			try {
				URL url = ResourceUtils.getURL(ssl.getKeyStore());
				store.load(url.openStream(),
						ssl.getKeyStorePassword() != null ? ssl.getKeyStorePassword().toCharArray() : null);
			}
			catch (Exception e) {
				throw new RuntimeException("Could not load key store ' " + ssl.getKeyStore() + "'", e);
			}

			return store;
		}
		catch (KeyStoreException | NoSuchProviderException e) {
			throw new RuntimeException("Could not load KeyStore for given type and provider", e);
		}
	}

	/**
	 * 使用指定的 X.509 信任证书设置 SSL 上下文的信任管理器。
	 * @param sslContextBuilder SSL 上下文构建器
	 * @param trustedX509Certificates 信任的 X.509 证书数组
	 */
	protected void setTrustManager(SslContextBuilder sslContextBuilder, X509Certificate... trustedX509Certificates) {
		sslContextBuilder.trustManager(trustedX509Certificates);
	}

	/**
	 * 使用指定的 TrustManagerFactory 设置 SSL 上下文的信任管理器。
	 * @param sslContextBuilder SSL 上下文构建器
	 * @param factory 信任管理器工厂实例
	 */
	protected void setTrustManager(SslContextBuilder sslContextBuilder, TrustManagerFactory factory) {
		sslContextBuilder.trustManager(factory);
	}

}
