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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.validation.constraints.Max;

import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.ResourceUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Netty {@link reactor.netty.http.client.HttpClient} 的配置属性类。
 * <p>
 * 提供对 Netty HTTP 客户端各种配置选项的绑定和验证功能，包括连接池、代理、SSL、 WebSocket、超时设置等配置。
 *
 * @author Spencer Gibb
 * @author BTC
 */
@ConfigurationProperties("spring.cloud.gateway.httpclient")
@Validated
public class HttpClientProperties {

	/** The connect timeout in millis, the default is 30s. */
	private Integer connectTimeout;

	/** The response timeout. */
	private Duration responseTimeout;

	/** The max response header size. */
	private DataSize maxHeaderSize;

	/**
	 * 最大初始行长度。
	 * <p>
	 * The max initial line length.
	 */
	private DataSize maxInitialLineLength;

	/** Pool configuration for Netty HttpClient. */
	private Pool pool = new Pool();

	/** Proxy configuration for Netty HttpClient. */
	private Proxy proxy = new Proxy();

	/** SSL configuration for Netty HttpClient. */
	private Ssl ssl = new Ssl();

	/** Websocket configuration for Netty HttpClient. */
	private Websocket websocket = new Websocket();

	/** Enables wiretap debugging for Netty HttpClient. */
	private boolean wiretap;

	/** Enables compression for Netty HttpClient. */
	private boolean compression;

	/**
	 * 获取连接超时时间。
	 * @return 连接超时时间（毫秒）
	 */
	public Integer getConnectTimeout() {
		return connectTimeout;
	}

	/**
	 * 设置连接超时时间。
	 * @param connectTimeout 连接超时时间（毫秒）
	 */
	public void setConnectTimeout(Integer connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	/**
	 * 获取响应超时时间。
	 * @return 响应超时时间
	 */
	public Duration getResponseTimeout() {
		return responseTimeout;
	}

	/**
	 * 设置响应超时时间。
	 * @param responseTimeout 响应超时时间
	 */
	public void setResponseTimeout(Duration responseTimeout) {
		this.responseTimeout = responseTimeout;
	}

	/**
	 * 获取最大响应头大小。
	 * @return 最大响应头大小
	 */
	@Max(Integer.MAX_VALUE)
	public DataSize getMaxHeaderSize() {
		return maxHeaderSize;
	}

	/**
	 * 设置最大响应头大小。
	 * @param maxHeaderSize 最大响应头大小
	 */
	public void setMaxHeaderSize(DataSize maxHeaderSize) {
		this.maxHeaderSize = maxHeaderSize;
	}

	/**
	 * 获取最大初始行长度。
	 * @return 最大初始行长度
	 */
	@Max(Integer.MAX_VALUE)
	public DataSize getMaxInitialLineLength() {
		return maxInitialLineLength;
	}

	/**
	 * 设置最大初始行长度。
	 * @param maxInitialLineLength 最大初始行长度
	 */
	public void setMaxInitialLineLength(DataSize maxInitialLineLength) {
		this.maxInitialLineLength = maxInitialLineLength;
	}

	/**
	 * 获取连接池配置。
	 * @return 连接池配置
	 */
	public Pool getPool() {
		return pool;
	}

	/**
	 * 设置连接池配置。
	 * @param pool 连接池配置
	 */
	public void setPool(Pool pool) {
		this.pool = pool;
	}

	/**
	 * 获取代理配置。
	 * @return 代理配置
	 */
	public Proxy getProxy() {
		return proxy;
	}

	/**
	 * 设置代理配置。
	 * @param proxy 代理配置
	 */
	public void setProxy(Proxy proxy) {
		this.proxy = proxy;
	}

	/**
	 * 获取 SSL/TLS 配置。
	 * @return SSL/TLS 配置
	 */
	public Ssl getSsl() {
		return ssl;
	}

	/**
	 * 设置 SSL/TLS 配置。
	 * @param ssl SSL/TLS 配置
	 */
	public void setSsl(Ssl ssl) {
		this.ssl = ssl;
	}

	/**
	 * 获取 WebSocket 配置。
	 * @return WebSocket 配置
	 */
	public Websocket getWebsocket() {
		return this.websocket;
	}

	/**
	 * 设置 WebSocket 配置。
	 * @param websocket WebSocket 配置
	 */
	public void setWebsocket(Websocket websocket) {
		this.websocket = websocket;
	}

	/**
	 * 检查是否启用 Wiretap 调试功能。
	 * @return 是否启用 Wiretap 调试
	 */
	public boolean isWiretap() {
		return this.wiretap;
	}

	/**
	 * 设置是否启用 Wiretap 调试功能。
	 * @param wiretap 是否启用 Wiretap 调试
	 */
	public void setWiretap(boolean wiretap) {
		this.wiretap = wiretap;
	}

	public boolean isCompression() {
		return compression;
	}

	/**
	 * 设置是否启用压缩功能。
	 * @param compression 是否启用压缩
	 */
	public void setCompression(boolean compression) {
		this.compression = compression;
	}

	@Override
	public String toString() {
		// @formatter:off
		return new ToStringCreator(this)
				.append("connectTimeout", connectTimeout)
				.append("responseTimeout", responseTimeout)
				.append("maxHeaderSize", maxHeaderSize)
				.append("maxInitialLineLength", maxInitialLineLength)
				.append("pool", pool)
				.append("proxy", proxy)
				.append("ssl", ssl)
				.append("websocket", websocket)
				.append("wiretap", wiretap)
				.append("compression", compression)
				.toString();
		// @formatter:on

	}

	/**
	 * Netty HttpClient 连接池配置类。
	 * <p>
	 * 提供对连接池类型、最大连接数、获取超时、空闲时间等配置的管理。
	 */
	public static class Pool {

		/**
		 * 连接池类型，默认为 ELASTIC。
		 * <p>
		 * Type of pool for HttpClient to use, defaults to ELASTIC.
		 */
		private PoolType type = PoolType.ELASTIC;

		/** The channel pool map name, defaults to proxy. */
		private String name = "proxy";

		/**
		 * Only for type FIXED, the maximum number of connections before starting pending
		 * acquisition on existing ones.
		 */
		private Integer maxConnections = ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS;

		/** Only for type FIXED, the maximum time in millis to wait for acquiring. */
		private Long acquireTimeout = ConnectionProvider.DEFAULT_POOL_ACQUIRE_TIMEOUT;

		/**
		 * Time in millis after which the channel will be closed. If NULL, there is no max
		 * idle time.
		 */
		private Duration maxIdleTime = null;

		/**
		 * Duration after which the channel will be closed. If NULL, there is no max life
		 * time.
		 */
		private Duration maxLifeTime = null;

		/**
		 * Perform regular eviction checks in the background at a specified interval.
		 * Disabled by default ({@link Duration#ZERO})
		 */
		private Duration evictionInterval = Duration.ZERO;

		/**
		 * 是否启用通道池指标收集并注册到 Micrometer。
		 * <p>
		 * 默认禁用。
		 * <p>
		 * Enables channel pools metrics to be collected and registered in Micrometer.
		 * Disabled by default.
		 */
		private boolean metrics = false;

		public PoolType getType() {
			return type;
		}

		public void setType(PoolType type) {
			this.type = type;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getMaxConnections() {
			return maxConnections;
		}

		public void setMaxConnections(Integer maxConnections) {
			this.maxConnections = maxConnections;
		}

		public Long getAcquireTimeout() {
			return acquireTimeout;
		}

		public void setAcquireTimeout(Long acquireTimeout) {
			this.acquireTimeout = acquireTimeout;
		}

		/**
		 * 获取通道最大空闲时间。
		 * @return 最大空闲时间
		 */
		public Duration getMaxIdleTime() {
			return maxIdleTime;
		}

		public void setMaxIdleTime(Duration maxIdleTime) {
			this.maxIdleTime = maxIdleTime;
		}

		/**
		 * 获取通道最大生命周期。
		 * @return 最大生命周期
		 */
		public Duration getMaxLifeTime() {
			return maxLifeTime;
		}

		/**
		 * 设置通道最大生命周期。
		 * @param maxLifeTime 最大生命周期
		 */
		public void setMaxLifeTime(Duration maxLifeTime) {
			this.maxLifeTime = maxLifeTime;
		}

		/**
		 * 获取驱逐检查的时间间隔。
		 * @return 驱逐检查间隔
		 */
		public Duration getEvictionInterval() {
			return evictionInterval;
		}

		/**
		 * 设置驱逐检查的时间间隔。
		 * @param evictionInterval 驱逐检查间隔
		 */
		public void setEvictionInterval(Duration evictionInterval) {
			this.evictionInterval = evictionInterval;
		}

		/**
		 * 检查是否启用指标收集。
		 * @return 是否启用指标收集
		 */
		public boolean isMetrics() {
			return metrics;
		}

		/**
		 * 设置是否启用指标收集。
		 * @param metrics 是否启用指标收集
		 */
		public void setMetrics(boolean metrics) {
			this.metrics = metrics;
		}

		/**
		 * 返回该连接池配置的字符串表示。
		 * @return 包含所有配置属性的字符串表示
		 */
		@Override
		public String toString() {
			return "Pool{" + "type=" + type + ", name='" + name + '\'' + ", maxConnections=" + maxConnections
					+ ", acquireTimeout=" + acquireTimeout + ", maxIdleTime=" + maxIdleTime + ", maxLifeTime="
					+ maxLifeTime + ", evictionInterval=" + evictionInterval + ", metrics=" + metrics + '}';
		}

		/**
		 * 连接池类型枚举。
		 * <p>
		 * 定义了 Netty HttpClient 支持的连接池类型。
		 */
		public enum PoolType {

			/**
			 * 弹性连接池类型。
			 * <p>
			 * Elastic pool type.
			 */
			ELASTIC,

			/**
			 * 固定大小连接池类型。
			 * <p>
			 * Fixed pool type.
			 */
			FIXED,

			/**
			 * 禁用连接池类型。
			 * <p>
			 * Disabled pool type.
			 */
			DISABLED

		}

	}

	/**
	 * Netty HttpClient 代理配置类。
	 * <p>
	 * 提供对 HTTP 代理、HTTPS 代理、SOCKS 代理的配置支持， 包括代理类型、主机、端口、认证信息和直连主机列表。
	 */
	public static class Proxy {

		/** 代理类型，默认为 HTTP */
		private ProxyProvider.Proxy type = ProxyProvider.Proxy.HTTP;

		/** 代理服务器主机名 */
		private String host;

		/** 代理服务器端口 */
		private Integer port;

		/** 代理认证用户名 */
		private String username;

		/** 代理认证密码 */
		private String password;

		/**
		 * 直连（非代理）主机的正则表达式模式。
		 * <p>
		 * 配置直接连接绕过代理的主机列表。
		 * <p>
		 * Regular expression (Java) for a configured list of hosts. that should be
		 * reached directly, bypassing the proxy
		 */
		private String nonProxyHostsPattern;

		public ProxyProvider.Proxy getType() {
			return type;
		}

		public void setType(ProxyProvider.Proxy type) {
			this.type = type;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public Integer getPort() {
			return port;
		}

		public void setPort(Integer port) {
			this.port = port;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		/**
		 * 设置代理认证密码。
		 * @param password 代理密码
		 */
		public void setPassword(String password) {
			this.password = password;
		}

		/**
		 * 获取直连（非代理）主机正则表达式模式。
		 * @return 直连主机正则表达式
		 */
		public String getNonProxyHostsPattern() {
			return nonProxyHostsPattern;
		}

		/**
		 * 设置直连（非代理）主机正则表达式模式。
		 * @param nonProxyHostsPattern 直连主机正则表达式
		 */
		public void setNonProxyHostsPattern(String nonProxyHostsPattern) {
			this.nonProxyHostsPattern = nonProxyHostsPattern;
		}

		/**
		 * 返回该代理配置的字符串表示。
		 * @return 包含所有配置属性的字符串表示
		 */
		@Override
		public String toString() {
			return "Proxy{" + "type='" + type + '\'' + "host='" + host + '\'' + ", port=" + port + ", username='"
					+ username + '\'' + ", password='" + password + '\'' + ", nonProxyHostsPattern='"
					+ nonProxyHostsPattern + '\'' + '}';
		}

	}

	public static class Ssl {

		/**
		 * Installs the netty InsecureTrustManagerFactory. This is insecure and not
		 * suitable for production.
		 */
		private boolean useInsecureTrustManager = false;

		/** Trusted certificates for verifying the remote endpoint's certificate. */
		private List<String> trustedX509Certificates = new ArrayList<>();

		// use netty default SSL timeouts
		/** SSL handshake timeout. Default to 10000 ms */
		private Duration handshakeTimeout = Duration.ofMillis(10000);

		/** SSL close_notify flush timeout. Default to 3000 ms. */
		private Duration closeNotifyFlushTimeout = Duration.ofMillis(3000);

		/** SSL close_notify read timeout. Default to 0 ms. */
		private Duration closeNotifyReadTimeout = Duration.ZERO;

		/** The default ssl configuration type. Defaults to TCP. */
		@Deprecated
		private SslProvider.DefaultConfigurationType defaultConfigurationType = SslProvider.DefaultConfigurationType.TCP;

		/** Keystore path for Netty HttpClient. */
		private String keyStore;

		/** Keystore type for Netty HttpClient, default is JKS. */
		private String keyStoreType = "JKS";

		/** Keystore provider for Netty HttpClient, optional field. */
		private String keyStoreProvider;

		/**
		 * 密钥库密码。
		 * <p>
		 * Keystore password.
		 */
		private String keyStorePassword;

		/**
		 * 密钥密码，默认为 keyStorePassword。
		 * <p>
		 * Key password, default is same as keyStorePassword.
		 */
		private String keyPassword;

		/**
		 * 获取密钥库密码。
		 * @return 密钥库密码
		 */
		public String getKeyStorePassword() {
			return keyStorePassword;
		}

		/**
		 * 设置密钥库密码。
		 * @param keyStorePassword 密钥库密码
		 */
		public void setKeyStorePassword(String keyStorePassword) {
			this.keyStorePassword = keyStorePassword;
		}

		/**
		 * 获取密钥库类型。
		 * @return 密钥库类型
		 */
		public String getKeyStoreType() {
			return keyStoreType;
		}

		/**
		 * 设置密钥库类型。
		 * @param keyStoreType 密钥库类型
		 */
		public void setKeyStoreType(String keyStoreType) {
			this.keyStoreType = keyStoreType;
		}

		/**
		 * 获取密钥库提供者。
		 * @return 密钥库提供者
		 */
		public String getKeyStoreProvider() {
			return keyStoreProvider;
		}

		/**
		 * 设置密钥库提供者。
		 * @param keyStoreProvider 密钥库提供者
		 */
		public void setKeyStoreProvider(String keyStoreProvider) {
			this.keyStoreProvider = keyStoreProvider;
		}

		/**
		 * 获取密钥库路径。
		 * @return 密钥库路径
		 */
		public String getKeyStore() {
			return keyStore;
		}

		/**
		 * 设置密钥库路径。
		 * @param keyStore 密钥库路径
		 */
		public void setKeyStore(String keyStore) {
			this.keyStore = keyStore;
		}

		/**
		 * 获取密钥密码。
		 * @return 密钥密码
		 */
		public String getKeyPassword() {
			return keyPassword;
		}

		public void setKeyPassword(String keyPassword) {
			this.keyPassword = keyPassword;
		}

		/**
		 * 获取受信任的 X.509 证书路径列表。
		 * @return 受信任证书路径列表
		 */
		public List<String> getTrustedX509Certificates() {
			return trustedX509Certificates;
		}

		/**
		 * 设置受信任的 X.509 证书路径列表。
		 * @param trustedX509 受信任证书路径列表
		 */
		public void setTrustedX509Certificates(List<String> trustedX509) {
			this.trustedX509Certificates = trustedX509;
		}

		/**
		 * 获取用于 TrustManager 的受信任 X.509 证书数组。
		 * @return 信任证书数组
		 * @deprecated 该方法将在后续版本中移除
		 */
		@Deprecated
		public X509Certificate[] getTrustedX509CertificatesForTrustManager() {
			try {
				CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
				ArrayList<Certificate> allCerts = new ArrayList<>();
				for (String trustedCert : getTrustedX509Certificates()) {
					try {
						URL url = ResourceUtils.getURL(trustedCert);
						Collection<? extends Certificate> certs = certificateFactory
								.generateCertificates(url.openStream());
						allCerts.addAll(certs);
					}
					catch (IOException e) {
						throw new WebServerException("Could not load certificate '" + trustedCert + "'", e);
					}
				}
				return allCerts.toArray(new X509Certificate[allCerts.size()]);
			}
			catch (CertificateException e1) {
				throw new WebServerException("Could not load CertificateFactory X.509", e1);
			}
		}

		/**
		 * 获取 KeyManagerFactory。
		 * @return KeyManagerFactory 实例，若未配置密钥库则返回 null
		 * @deprecated 该方法将在后续版本中移除
		 */
		@Deprecated
		public KeyManagerFactory getKeyManagerFactory() {
			try {
				if (getKeyStore() != null && getKeyStore().length() > 0) {
					KeyManagerFactory keyManagerFactory = KeyManagerFactory
							.getInstance(KeyManagerFactory.getDefaultAlgorithm());
					char[] keyPassword = getKeyPassword() != null ? getKeyPassword().toCharArray() : null;

					if (keyPassword == null && getKeyStorePassword() != null) {
						keyPassword = getKeyStorePassword().toCharArray();
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
		 * 创建 KeyStore 实例。
		 * @return 加载完成的 KeyStore
		 * @deprecated 该方法将在后续版本中移除
		 */
		@Deprecated
		public KeyStore createKeyStore() {
			try {
				KeyStore store = getKeyStoreProvider() != null
						? KeyStore.getInstance(getKeyStoreType(), getKeyStoreProvider())
						: KeyStore.getInstance(getKeyStoreType());
				try {
					URL url = ResourceUtils.getURL(getKeyStore());
					store.load(url.openStream(),
							getKeyStorePassword() != null ? getKeyStorePassword().toCharArray() : null);
				}
				catch (Exception e) {
					throw new WebServerException("Could not load key store ' " + getKeyStore() + "'", e);
				}

				return store;
			}
			catch (KeyStoreException | NoSuchProviderException e) {
				throw new WebServerException("Could not load KeyStore for given type and provider", e);
			}
		}

		// TODO: support configuration of other trust manager factories

		/**
		 * 检查是否使用不安全的信任管理器。
		 * @return 是否使用不安全的信任管理器
		 */
		public boolean isUseInsecureTrustManager() {
			return useInsecureTrustManager;
		}

		public void setUseInsecureTrustManager(boolean useInsecureTrustManager) {
			this.useInsecureTrustManager = useInsecureTrustManager;
		}

		/**
		 * 获取 SSL 握手超时时间。
		 * @return SSL 握手超时时间，默认 10000 毫秒
		 */
		public Duration getHandshakeTimeout() {
			return handshakeTimeout;
		}

		/**
		 * 设置 SSL 握手超时时间。
		 * @param handshakeTimeout 握手超时时间
		 */
		public void setHandshakeTimeout(Duration handshakeTimeout) {
			this.handshakeTimeout = handshakeTimeout;
		}

		/**
		 * 获取 SSL close_notify 刷新超时时间。
		 * @return close_notify 刷新超时时间，默认 3000 毫秒
		 */
		public Duration getCloseNotifyFlushTimeout() {
			return closeNotifyFlushTimeout;
		}

		public void setCloseNotifyFlushTimeout(Duration closeNotifyFlushTimeout) {
			this.closeNotifyFlushTimeout = closeNotifyFlushTimeout;
		}

		/**
		 * 获取 SSL close_notify 读取超时时间。
		 * @return close_notify 读取超时时间，默认 0 毫秒
		 */
		public Duration getCloseNotifyReadTimeout() {
			return closeNotifyReadTimeout;
		}

		public void setCloseNotifyReadTimeout(Duration closeNotifyReadTimeout) {
			this.closeNotifyReadTimeout = closeNotifyReadTimeout;
		}

		@Deprecated
		public SslProvider.DefaultConfigurationType getDefaultConfigurationType() {
			return defaultConfigurationType;
		}

		@Deprecated
		public void setDefaultConfigurationType(SslProvider.DefaultConfigurationType defaultConfigurationType) {
			this.defaultConfigurationType = defaultConfigurationType;
		}

		/**
		 * 返回该 SSL 配置的字符串表示。
		 * @return 包含所有配置属性的字符串表示
		 */
		@Override
		public String toString() {
			return new ToStringCreator(this).append("useInsecureTrustManager", useInsecureTrustManager)
					.append("trustedX509Certificates", trustedX509Certificates)
					.append("handshakeTimeout", handshakeTimeout)
					.append("closeNotifyFlushTimeout", closeNotifyFlushTimeout)
					.append("closeNotifyReadTimeout", closeNotifyReadTimeout)
					.append("defaultConfigurationType", defaultConfigurationType).toString();
		}

	}

	/**
	 * Netty HttpClient WebSocket 配置类。
	 * <p>
	 * 提供对 WebSocket 帧负载长度、代理 ping 等配置的管理。
	 */
	public static class Websocket {

		/**
		 * 最大帧负载长度。
		 * <p>
		 * Max frame payload length.
		 */
		private Integer maxFramePayloadLength;

		/** Proxy ping frames to downstream services, defaults to true. */
		private boolean proxyPing = true;

		public Integer getMaxFramePayloadLength() {
			return this.maxFramePayloadLength;
		}

		public void setMaxFramePayloadLength(Integer maxFramePayloadLength) {
			this.maxFramePayloadLength = maxFramePayloadLength;
		}

		public boolean isProxyPing() {
			return proxyPing;
		}

		public void setProxyPing(boolean proxyPing) {
			this.proxyPing = proxyPing;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("maxFramePayloadLength", maxFramePayloadLength)
					.append("proxyPing", proxyPing).toString();
		}

	}

}
