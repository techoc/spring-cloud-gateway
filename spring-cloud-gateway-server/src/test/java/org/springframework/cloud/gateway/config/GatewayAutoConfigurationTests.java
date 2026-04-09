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

package org.springframework.cloud.gateway.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Test;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.netty.http.server.WebsocketServerSpec;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.actuate.GatewayControllerEndpoint;
import org.springframework.cloud.gateway.actuate.GatewayLegacyControllerEndpoint;
import org.springframework.cloud.gateway.config.GatewayAutoConfigurationTests.CustomHttpClientFactory.CustomSslConfigurer;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.GRPCRequestHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.GRPCResponseHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.web.filter.reactive.HiddenHttpMethodFilter;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GatewayAutoConfigurationTests - Gateway自动配置测试类
 *
 * 本测试类验证GatewayAutoConfiguration的各类配置功能，包括： - Netty HTTP客户端的默认配置和自定义配置 -
 * SSL/TLS配置（包括不安全信任管理器的设置） - Actuator端点的详细/传统模式切换 - OAuth2 Token Relay功能的Bean创建 -
 * gRPC过滤器的HTTP/2启用/禁用配置 - WebSocket协议支持配置 - 转发请求头过滤器的配置
 *
 * @author test
 */
public class GatewayAutoConfigurationTests {

	/**
	 * noHiddenHttpMethodFilter - 测试HiddenHttpMethodFilter被禁用
	 *
	 * 验证GatewayAutoConfiguration正确禁用了HiddenHttpMethodFilter
	 * 该过滤器在Gateway中不需要，因为它使用自定义的路由机制
	 */
	@Test
	public void noHiddenHttpMethodFilter() {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(Config.class, "--spring.jmx.enabled=false",
				"--server.port=0")) {
			assertThat(ctx.getEnvironment().getProperty("spring.webflux.hiddenmethod.filter.enabled"))
					.isEqualTo("false");
			assertThat(ctx.getBeanNamesForType(HiddenHttpMethodFilter.class)).isEmpty();
		}
	}

	/**
	 * nettyHttpClientDefaults - 测试Netty HTTP客户端的默认配置
	 *
	 * 验证Gateway使用默认配置创建Netty HttpClient： - 存在HttpClient Bean -
	 * 连接池为弹性池（maxConnections=Integer.MAX_VALUE） - 未配置代理 - 未配置SSL - 未启用gzip压缩 - 未配置日志处理器 -
	 * 未设置连接超时
	 */
	@Test
	public void nettyHttpClientDefaults() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						ServerPropertiesConfig.class))
				.withPropertyValues("debug=true").run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					CustomHttpClientFactory factory = context.getBean(CustomHttpClientFactory.class);

					assertThat(factory.connectionProvider).isNotNull();
					assertThat(factory.connectionProvider.maxConnections()).isEqualTo(Integer.MAX_VALUE); // elastic

					assertThat(factory.proxyProvider).isNull();
					assertThat(factory.isSslConfigured()).isFalse();

					assertThat(httpClient.configuration().isAcceptGzip()).isFalse();
					assertThat(httpClient.configuration().loggingHandler()).isNull();
					assertThat(httpClient.configuration().options())
							.doesNotContainKey(ChannelOption.CONNECT_TIMEOUT_MILLIS);
				});
	}

	/**
	 * nettyHttpClientConfigured - 测试Netty HTTP客户端的自定义配置
	 *
	 * 验证Gateway能够正确应用各种自定义配置： - SSL配置：使用不安全的信任管理器 - 连接超时：10毫秒 - 响应超时：10秒 - 连接池驱逐间隔：10秒 -
	 * 连接池类型：固定 - 连接池指标：启用 - 压缩：启用 - 调试模式（wiretap）：启用 - 最大初始行长度：Integer.MAX_VALUE -
	 * 代理主机：myhost - WebSocket最大帧负载：1024字节
	 */
	@Test
	public void nettyHttpClientConfigured() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("spring.cloud.gateway.httpclient.ssl.use-insecure-trust-manager=true",
						"spring.cloud.gateway.httpclient.connect-timeout=10",
						"spring.cloud.gateway.httpclient.response-timeout=10s",
						"spring.cloud.gateway.httpclient.pool.eviction-interval=10s",
						"spring.cloud.gateway.httpclient.pool.type=fixed",
						"spring.cloud.gateway.httpclient.pool.metrics=true",
						"spring.cloud.gateway.httpclient.compression=true",
						"spring.cloud.gateway.httpclient.wiretap=true",
						// greater than integer max value
						"spring.cloud.gateway.httpclient.max-initial-line-length=2147483647",
						"spring.cloud.gateway.httpclient.proxy.host=myhost",
						"spring.cloud.gateway.httpclient.websocket.max-frame-payload-length=1024")
				.run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					CustomHttpClientFactory factory = context.getBean(CustomHttpClientFactory.class);
					HttpClientProperties properties = context.getBean(HttpClientProperties.class);
					assertThat(properties.getMaxInitialLineLength().toBytes()).isLessThanOrEqualTo(Integer.MAX_VALUE);
					assertThat(properties.isCompression()).isEqualTo(true);
					assertThat(properties.getPool().getEvictionInterval()).hasSeconds(10);
					assertThat(properties.getPool().isMetrics()).isEqualTo(true);

					assertThat(httpClient.configuration().isAcceptGzip()).isTrue();
					assertThat(httpClient.configuration().loggingHandler()).isNotNull();
					assertThat(httpClient.configuration().options()).containsKey(ChannelOption.CONNECT_TIMEOUT_MILLIS);
					assertThat(httpClient.configuration().options().get(ChannelOption.CONNECT_TIMEOUT_MILLIS))
							.isEqualTo(10);

					assertThat(factory.connectionProvider).isNotNull();
					// fixed pool
					assertThat(factory.connectionProvider.maxConnections())
							.isEqualTo(ConnectionProvider.DEFAULT_POOL_MAX_CONNECTIONS);

					assertThat(factory.proxyProvider).isNotNull();
					assertThat(factory.proxyProvider.build().getAddress().get().getHostName()).isEqualTo("myhost");

					assertThat(factory.isSslConfigured()).isTrue();
					assertThat(factory.isInsecureTrustManagerSet()).isTrue();

					assertThat(context).hasSingleBean(ReactorNettyRequestUpgradeStrategy.class);
					ReactorNettyRequestUpgradeStrategy upgradeStrategy = context
							.getBean(ReactorNettyRequestUpgradeStrategy.class);
					assertThat(upgradeStrategy.getWebsocketServerSpec().maxFramePayloadLength()).isEqualTo(1024);
					assertThat(upgradeStrategy.getWebsocketServerSpec().handlePing()).isTrue();
					assertThat(context).hasSingleBean(ReactorNettyWebSocketClient.class);
					ReactorNettyWebSocketClient webSocketClient = context.getBean(ReactorNettyWebSocketClient.class);
					assertThat(webSocketClient.getWebsocketClientSpec().maxFramePayloadLength()).isEqualTo(1024);
					HttpClientCustomizedConfig config = context.getBean(HttpClientCustomizedConfig.class);
					assertThat(config.called.get()).isTrue();
				});
	}

	/**
	 * nettyHttpClientNoSslConfigurerIsBackwardsCompatible - 测试SSL配置器的向后兼容性
	 * @deprecated 测试旧版本不带SslConfigurer的HttpClientFactory的兼容性
	 *
	 * 验证不使用自定义SslConfigurer时，SSL配置仍然能够正确工作 确认以下方法被调用： - configureSsl -
	 * configureSslContext - getTrustedX509CertificatesForTrustManager -
	 * getKeyManagerFactory - setTrustManager(TrustManagerFactory)
	 */
	@Test
	@Deprecated
	public void nettyHttpClientNoSslConfigurerIsBackwardsCompatible() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						NoSslConfigurerCustomHttpClientFactoryConfig.class))
				.withPropertyValues("spring.cloud.gateway.httpclient.ssl.use-insecure-trust-manager=true")
				.run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					NoSslConfigurerHttpClientFactory factory = context.getBean(NoSslConfigurerHttpClientFactory.class);

					assertThat(factory.configureSslCalled).isTrue();
					assertThat(factory.configureSslContextCalled).isTrue();
					assertThat(factory.getTrustedX509CertificatesForTrustManagerCalled).isTrue();
					assertThat(factory.getKeyManagerFactoryCalled).isTrue();
					assertThat(factory.createKeyStoreCalled).isFalse();
					assertThat(factory.setTrustManagerCertCalled).isFalse();
					assertThat(factory.setTrustManagerFactoryCalled).isTrue();
				});
	}

	/**
	 * verboseActuatorEnabledByDefault - 测试详细Actuator端点默认启用
	 *
	 * 验证默认情况下GatewayControllerEndpoint（详细模式）被注册
	 * 而GatewayLegacyControllerEndpoint（传统模式）不会被注册
	 */
	@Test
	public void verboseActuatorEnabledByDefault() {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(Config.class, "--spring.jmx.enabled=false",
				"--server.port=0")) {
			assertThat(ctx.getBeanNamesForType(GatewayControllerEndpoint.class)).hasSize(1);
			assertThat(ctx.getBeanNamesForType(GatewayLegacyControllerEndpoint.class)).isEmpty();
		}
	}

	/**
	 * verboseActuatorDisabled - 测试详细Actuator端点被禁用
	 *
	 * 验证当配置spring.cloud.gateway.actuator.verbose.enabled=false时
	 * GatewayLegacyControllerEndpoint（传统模式）会被注册
	 */
	@Test
	public void verboseActuatorDisabled() {
		try (ConfigurableApplicationContext ctx = SpringApplication.run(Config.class, "--spring.jmx.enabled=false",
				"--server.port=0", "--spring.cloud.gateway.actuator.verbose.enabled=false")) {
			assertThat(ctx.getBeanNamesForType(GatewayLegacyControllerEndpoint.class)).hasSize(1);
		}
	}

	/**
	 * tokenRelayBeansAreCreated - 测试OAuth2 Token Relay相关Bean的创建
	 *
	 * 验证当配置OAuth2客户端后，TokenRelayGatewayFilterFactory和
	 * ReactiveOAuth2AuthorizedClientManager Bean能够正确创建 这些Bean用于在代理请求时传递OAuth2令牌
	 */
	@Test
	public void tokenRelayBeansAreCreated() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ReactiveSecurityAutoConfiguration.class,
						ReactiveOAuth2ClientAutoConfiguration.class, GatewayReactiveOAuth2AutoConfiguration.class,
						GatewayAutoConfiguration.TokenRelayConfiguration.class))
				.withPropertyValues(
						"spring.security.oauth2.client.provider[testprovider].authorization-uri=http://localhost",
						"spring.security.oauth2.client.provider[testprovider].token-uri=http://localhost/token",
						"spring.security.oauth2.client.registration[test].provider=testprovider",
						"spring.security.oauth2.client.registration[test].authorization-grant-type=authorization_code",
						"spring.security.oauth2.client.registration[test].redirect-uri=http://localhost/redirect",
						"spring.security.oauth2.client.registration[test].client-id=login-client")
				.run(context -> {
					assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
					assertThat(context).hasSingleBean(TokenRelayGatewayFilterFactory.class);
				});
	}

	/**
	 * gatewayReactiveOAuth2AuthorizedClientManagerBacksOffForCustomBean -
	 * 测试自定义OAuth2客户端管理器的优先级
	 *
	 * 验证当用户自定义了ReactiveOAuth2AuthorizedClientManager Bean时 Gateway不会覆盖用户的配置
	 */
	@Test
	public void gatewayReactiveOAuth2AuthorizedClientManagerBacksOffForCustomBean() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(ReactiveSecurityAutoConfiguration.class,
						ReactiveOAuth2ClientAutoConfiguration.class, GatewayReactiveOAuth2AutoConfiguration.class))
				.withUserConfiguration(TestReactiveOAuth2AuthorizedClientManagerConfig.class)
				.withPropertyValues(
						"spring.security.oauth2.client.provider[testprovider].authorization-uri=http://localhost",
						"spring.security.oauth2.client.provider[testprovider].token-uri=http://localhost/token",
						"spring.security.oauth2.client.registration[test].provider=testprovider",
						"spring.security.oauth2.client.registration[test].authorization-grant-type=authorization_code",
						"spring.security.oauth2.client.registration[test].redirect-uri=http://localhost/redirect",
						"spring.security.oauth2.client.registration[test].client-id=login-client")
				.run(context -> {
					assertThat(context).hasSingleBean(ReactiveOAuth2AuthorizedClientManager.class);
					assertThat(context).hasBean("myReactiveOAuth2AuthorizedClientManager");
				});
	}

	/**
	 * noTokenRelayFilter - 测试Token Relay过滤器禁用时的错误处理
	 *
	 * 验证当spring.cloud.gateway.filter.token-relay.enabled=false但路由使用了tokenRelay过滤器时
	 * 会抛出IllegalStateException异常，提示未找到TokenRelayGatewayFilterFactory Bean
	 */
	@Test
	public void noTokenRelayFilter() {
		assertThatThrownBy(() -> {
			try (ConfigurableApplicationContext ctx = SpringApplication.run(RouteLocatorBuilderConfig.class,
					"--spring.jmx.enabled=false", "--spring.cloud.gateway.filter.token-relay.enabled=false",
					"--spring.security.oauth2.client.provider[testprovider].authorization-uri=http://localhost",
					"--spring.security.oauth2.client.provider[testprovider].token-uri=http://localhost/token",
					"--spring.security.oauth2.client.registration[test].provider=testprovider",
					"--spring.security.oauth2.client.registration[test].authorization-grant-type=authorization_code",
					"--spring.security.oauth2.client.registration[test].redirect-uri=http://localhost/redirect",
					"--spring.security.oauth2.client.registration[test].client-id=login-client", "--server.port=0",
					"--spring.cloud.gateway.actuator.verbose.enabled=false")) {
				assertThat(ctx.getBeanNamesForType(GatewayLegacyControllerEndpoint.class)).hasSize(1);
			}
		}).hasRootCauseInstanceOf(IllegalStateException.class)
				.hasMessageContaining("No TokenRelayGatewayFilterFactory bean was found. Did you include");
	}

	/**
	 * reactorNettyRequestUpgradeStrategyWebSocketSpecBuilderIsUniquePerRequest -
	 * 测试WebSocket服务端点配置的独立性
	 *
	 * Issue: gh-2159
	 *
	 * 验证ReactorNettyRequestUpgradeStrategy为每个请求创建独立的WebsocketServerSpec 确保不同请求可以使用不同的协议配置
	 */
	@Test // gh-2159
	public void reactorNettyRequestUpgradeStrategyWebSocketSpecBuilderIsUniquePerRequest()
			throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ReactorNettyRequestUpgradeStrategy strategy = new GatewayAutoConfiguration.NettyConfiguration()
				.reactorNettyRequestUpgradeStrategy(new HttpClientProperties());

		// Method "buildSpec" was introduced for Tests, but has only default visiblity
		Method buildSpec = ReactorNettyRequestUpgradeStrategy.class.getDeclaredMethod("buildSpec", String.class);
		buildSpec.setAccessible(true);
		WebsocketServerSpec spec1 = (WebsocketServerSpec) buildSpec.invoke(strategy, "p1");
		WebsocketServerSpec spec2 = strategy.getWebsocketServerSpec();

		assertThat(spec1.protocols()).isEqualTo("p1");
		assertThat(spec2.protocols()).isNull();
	}

	/**
	 * webSocketClientSpecBuilderIsUniquePerReactorNettyWebSocketClient -
	 * 测试WebSocket客户端配置的独立性
	 *
	 * Issue: gh-2215
	 *
	 * 验证ReactorNettyWebSocketClient为每个请求创建独立的WebsocketClientSpec 确保协议配置不会在请求之间缓存
	 */
	@Test // gh-2215
	public void webSocketClientSpecBuilderIsUniquePerReactorNettyWebSocketClient()
			throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ReactorNettyWebSocketClient websocketClient = new GatewayAutoConfiguration.NettyConfiguration()
				.reactorNettyWebSocketClient(new HttpClientProperties(), HttpClient.create());

		// Method "buildSpec" has only private visibility
		Method buildSpec = ReactorNettyWebSocketClient.class.getDeclaredMethod("buildSpec", String.class);
		buildSpec.setAccessible(true);
		WebsocketClientSpec spec1 = (WebsocketClientSpec) buildSpec.invoke(websocketClient, "p1");
		WebsocketClientSpec spec2 = websocketClient.getWebsocketClientSpec();

		assertThat(spec1.protocols()).isEqualTo("p1");
		// Protocols should not be cached between requests:
		assertThat(spec2.protocols()).isNull();
	}

	/**
	 * gRPCFiltersConfiguredWhenHTTP2Enabled - 测试HTTP/2启用时gRPC过滤器配置
	 *
	 * 验证当server.http2.enabled=true时： - GRPCRequestHeadersFilter被注册 -
	 * GRPCResponseHeadersFilter被注册 - HttpClient支持HTTP/1.1和HTTP/2协议
	 */
	@Test
	public void gRPCFiltersConfiguredWhenHTTP2Enabled() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("server.http2.enabled=true").run(context -> {
					assertThat(context).hasSingleBean(GRPCRequestHeadersFilter.class);
					assertThat(context).hasSingleBean(GRPCResponseHeadersFilter.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					assertThat(httpClient.configuration().protocols()).contains(HttpProtocol.HTTP11, HttpProtocol.H2);
				});
	}

	/**
	 * gRPCFiltersNotConfiguredWhenHTTP2Disabled - 测试HTTP/2禁用时gRPC过滤器不配置
	 *
	 * 验证当server.http2.enabled=false时： - GRPCRequestHeadersFilter不会被注册 -
	 * GRPCResponseHeadersFilter不会被注册
	 */
	@Test
	public void gRPCFiltersNotConfiguredWhenHTTP2Disabled() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("server.http2.enabled=false").run(context -> {
					assertThat(context).doesNotHaveBean(GRPCRequestHeadersFilter.class);
					assertThat(context).doesNotHaveBean(GRPCResponseHeadersFilter.class);
				});
	}

	@Test
	public void insecureTrustManagerNotEnabledByDefaultWhenHTTP2Enabled() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, ServerPropertiesConfig.class))
				.withPropertyValues("server.http2.enabled=true").run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					CustomHttpClientFactory factory = context.getBean(CustomHttpClientFactory.class);
					assertThat(factory.isInsecureTrustManagerSet()).isFalse();
				});
	}

	@Test
	public void customHttpClientWorks() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						HttpClientCustomizedConfig.class, CustomHttpClientConfig.class))
				.run(context -> {
					assertThat(context).hasSingleBean(HttpClient.class);
					HttpClient httpClient = context.getBean(HttpClient.class);
					assertThat(httpClient).isInstanceOf(CustomHttpClient.class);
				});
	}

	@Test
	public void forwardedHeaderFiltersNotEnabledByDefault() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						ServerPropertiesConfig.class))
				.run(context -> {
					assertThat(context).doesNotHaveBean(XForwardedHeadersFilter.class)
							.doesNotHaveBean(ForwardedHeadersFilter.class);
				});
	}

	@Test
	public void forwardedHeaderFiltersEnabledWithProperties() {
		new ReactiveWebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(WebFluxAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, GatewayAutoConfiguration.class,
						ServerPropertiesConfig.class))
				.withPropertyValues("spring.cloud.gateway.forwarded.enabled=true",
						"spring.cloud.gateway.x-forwarded.enabled=true", "spring.cloud.gateway.trusted-proxies=.*")
				.run(context -> {
					assertThat(context).hasSingleBean(XForwardedHeadersFilter.class)
							.hasSingleBean(ForwardedHeadersFilter.class);
				});
	}

	/**
	 * ServerPropertiesConfig - 服务器配置类
	 *
	 * 在GatewayAutoConfiguration之前加载，提供自定义的HttpClientFactory和SslConfigurer
	 */
	@Configuration
	@EnableConfigurationProperties(ServerProperties.class)
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	protected static class ServerPropertiesConfig {

		/**
		 * customHttpClientFactory - 自定义HttpClient工厂
		 * @param properties HTTP客户端属性配置
		 * @param serverProperties 服务器属性配置
		 * @param customizers HttpClient自定义器列表
		 * @param sslConfigurer SSL配置器
		 * @return CustomHttpClientFactory实例
		 */
		@Bean
		@Primary
		CustomHttpClientFactory customHttpClientFactory(HttpClientProperties properties,
				ServerProperties serverProperties, List<HttpClientCustomizer> customizers,
				HttpClientSslConfigurer sslConfigurer) {
			return new CustomHttpClientFactory(properties, serverProperties, sslConfigurer, customizers);
		}

		/**
		 * customSslContextFactory - 自定义SSL上下文工厂
		 * @param serverProperties 服务器属性配置
		 * @param httpClientProperties HTTP客户端属性配置
		 * @return CustomSslConfigurer实例
		 */
		@Bean
		@Primary
		CustomSslConfigurer customSslContextFactory(ServerProperties serverProperties,
				HttpClientProperties httpClientProperties) {
			return new CustomSslConfigurer(httpClientProperties.getSsl(), serverProperties);
		}

	}

	/**
	 * NoSslConfigurerCustomHttpClientFactoryConfig - 旧版HttpClient工厂配置（向后兼容）
	 *
	 * @deprecated 用于测试不使用SslConfigurer的旧版HttpClientFactory的兼容性
	 */
	@Configuration
	@EnableConfigurationProperties(ServerProperties.class)
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	@Deprecated
	protected static class NoSslConfigurerCustomHttpClientFactoryConfig {

		/**
		 * noSslConfigurerHttpClientFactory - 旧版HttpClient工厂
		 * @param properties HTTP客户端属性配置
		 * @param serverProperties 服务器属性配置
		 * @param customizers HttpClient自定义器列表
		 * @return NoSslConfigurerHttpClientFactory实例
		 */
		@Bean
		@Primary
		NoSslConfigurerHttpClientFactory noSslConfigurerHttpClientFactory(HttpClientProperties properties,
				ServerProperties serverProperties, List<HttpClientCustomizer> customizers) {
			return new NoSslConfigurerHttpClientFactory(properties, serverProperties, customizers);
		}

	}

	/**
	 * CustomHttpClientFactory - 自定义HttpClient工厂
	 *
	 * 扩展HttpClientFactory，用于测试目的，能够追踪连接池、代理和SSL配置状态
	 */
	protected static class CustomHttpClientFactory extends HttpClientFactory {

		/** 连接提供器 - 用于追踪连接池配置 */
		private ConnectionProvider connectionProvider;

		/** 代理提供器构建器 - 用于追踪代理配置 */
		private ProxyProvider.Builder proxyProvider;

		/** 自定义SSL配置器引用 */
		private CustomSslConfigurer customSslContextFactory;

		/**
		 * 构造函数
		 * @param properties HTTP客户端属性配置
		 * @param serverProperties 服务器属性配置
		 * @param sslConfigurer SSL配置器
		 * @param customizers HttpClient自定义器列表
		 */
		public CustomHttpClientFactory(HttpClientProperties properties, ServerProperties serverProperties,
				HttpClientSslConfigurer sslConfigurer, List<HttpClientCustomizer> customizers) {
			super(properties, serverProperties, sslConfigurer, customizers);
			this.customSslContextFactory = (CustomSslConfigurer) sslConfigurer;
		}

		/**
		 * buildConnectionProvider - 构建连接提供器
		 *
		 * 重写以追踪连接池配置状态
		 * @param properties HTTP客户端属性配置
		 * @return ConnectionProvider实例
		 */
		@Override
		protected ConnectionProvider buildConnectionProvider(HttpClientProperties properties) {
			connectionProvider = super.buildConnectionProvider(properties);
			return connectionProvider;
		}

		/**
		 * configureProxyProvider - 配置代理提供器
		 *
		 * 重写以追踪代理配置状态
		 * @param proxy 代理配置
		 * @param proxySpec 代理提供器类型规格
		 * @return ProxyProvider.Builder
		 */
		@Override
		protected ProxyProvider.Builder configureProxyProvider(HttpClientProperties.Proxy proxy,
				ProxyProvider.TypeSpec proxySpec) {
			proxyProvider = super.configureProxyProvider(proxy, proxySpec);
			return proxyProvider;
		}

		/**
		 * isSslConfigured - 检查SSL是否已配置
		 * @return true如果SSL已配置
		 */
		public boolean isSslConfigured() {
			return customSslContextFactory.sslConfigured;
		}

		/**
		 * isInsecureTrustManagerSet - 检查是否设置了不安全的信任管理器
		 * @return true如果使用了InsecureTrustManagerFactory
		 */
		public boolean isInsecureTrustManagerSet() {
			return customSslContextFactory.insecureTrustManagerSet;
		}

		/**
		 * CustomSslConfigurer - 自定义SSL配置器
		 *
		 * 扩展HttpClientSslConfigurer，用于追踪SSL配置状态
		 */
		protected static class CustomSslConfigurer extends HttpClientSslConfigurer {

			/** 标记SSL是否已配置 */
			boolean sslConfigured;

			/** 标记是否使用了不安全的信任管理器 */
			boolean insecureTrustManagerSet;

			/**
			 * 构造函数
			 * @param sslProperties SSL属性配置
			 * @param serverProperties 服务器属性配置
			 */
			protected CustomSslConfigurer(HttpClientProperties.Ssl sslProperties, ServerProperties serverProperties) {
				super(sslProperties, serverProperties);
			}

			/**
			 * configureSslContext - 配置SSL上下文
			 *
			 * 重写以追踪configureSslContext方法是否被调用
			 * @param ssl SSL属性配置
			 * @param sslContextSpec SSL上下文规格
			 */
			@Override
			protected void configureSslContext(HttpClientProperties.Ssl ssl,
					SslProvider.SslContextSpec sslContextSpec) {
				sslConfigured = true;
				super.configureSslContext(getSslProperties(), sslContextSpec);
			}

			/**
			 * setTrustManager - 设置信任管理器
			 *
			 * 重写以追踪是否使用了InsecureTrustManagerFactory
			 * @param sslContextBuilder SSL上下文构建器
			 * @param factory 信任管理器工厂
			 */
			@Override
			protected void setTrustManager(SslContextBuilder sslContextBuilder, TrustManagerFactory factory) {
				insecureTrustManagerSet = factory == InsecureTrustManagerFactory.INSTANCE;
				super.setTrustManager(sslContextBuilder, factory);
			}

		}

	}

	/**
	 * NoSslConfigurerHttpClientFactory - 不使用SslConfigurer的旧版HttpClient工厂
	 *
	 * @deprecated 用于测试向后兼容性，不使用新引入的SslConfigurer机制
	 *
	 * 此类追踪SSL配置相关的所有方法调用，用于验证向后兼容性
	 */
	@Deprecated
	protected static class NoSslConfigurerHttpClientFactory extends HttpClientFactory {

		/** 标记configureSsl方法是否被调用 */
		boolean configureSslCalled;

		/** 标记configureSslContext方法是否被调用 */
		boolean configureSslContextCalled;

		/** 标记getTrustedX509CertificatesForTrustManager方法是否被调用 */
		boolean getTrustedX509CertificatesForTrustManagerCalled;

		/** 标记getKeyManagerFactory方法是否被调用 */
		boolean getKeyManagerFactoryCalled;

		/** 标记createKeyStore方法是否被调用 */
		boolean createKeyStoreCalled;

		/** 标记setTrustManager(X509Certificate...)方法是否被调用 */
		boolean setTrustManagerCertCalled;

		/** 标记setTrustManager(TrustManagerFactory)方法是否被调用 */
		boolean setTrustManagerFactoryCalled;

		/**
		 * 构造函数
		 * @param properties HTTP客户端属性配置
		 * @param serverProperties 服务器属性配置
		 * @param customizers HttpClient自定义器列表
		 */
		public NoSslConfigurerHttpClientFactory(HttpClientProperties properties, ServerProperties serverProperties,
				List<HttpClientCustomizer> customizers) {
			super(properties, serverProperties, customizers);
		}

		@Override
		protected HttpClient configureSsl(HttpClient httpClient) {
			configureSslCalled = true;
			return super.configureSsl(httpClient);
		}

		@Override
		protected void configureSslContext(HttpClientProperties.Ssl ssl, SslProvider.SslContextSpec sslContextSpec) {
			configureSslContextCalled = true;
			super.configureSslContext(ssl, sslContextSpec);
		}

		@Override
		protected X509Certificate[] getTrustedX509CertificatesForTrustManager() {
			getTrustedX509CertificatesForTrustManagerCalled = true;
			return super.getTrustedX509CertificatesForTrustManager();
		}

		@Override
		protected KeyManagerFactory getKeyManagerFactory() {
			getKeyManagerFactoryCalled = true;
			return super.getKeyManagerFactory();
		}

		@Override
		protected KeyStore createKeyStore() {
			createKeyStoreCalled = true;
			return super.createKeyStore();
		}

		@Override
		protected void setTrustManager(SslContextBuilder sslContextBuilder,
				X509Certificate... trustedX509Certificates) {
			setTrustManagerCertCalled = true;
			super.setTrustManager(sslContextBuilder, trustedX509Certificates);
		}

		@Override
		protected void setTrustManager(SslContextBuilder sslContextBuilder, TrustManagerFactory factory) {
			setTrustManagerFactoryCalled = true;
			super.setTrustManager(sslContextBuilder, factory);
		}

	}

	/**
	 * Config - 基础测试配置类
	 *
	 * 提供最小化的Spring Boot配置用于测试
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class Config {

	}

	/**
	 * CustomHttpClientConfig - 自定义HttpClient配置类
	 *
	 * 提供自定义的HttpClient Bean用于测试Bean覆盖功能
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	@EnableConfigurationProperties(ServerProperties.class)
	@AutoConfigureBefore(GatewayAutoConfiguration.class)
	protected static class CustomHttpClientConfig {

		/**
		 * customHttpClient - 自定义HttpClient Bean
		 * @return CustomHttpClient实例
		 */
		@Bean
		public HttpClient customHttpClient() {
			return new CustomHttpClient();
		}

	}

	/**
	 * CustomHttpClient - 自定义HttpClient实现
	 *
	 * 用于测试用户可以通过Bean定义覆盖默认的HttpClient
	 */
	protected static class CustomHttpClient extends HttpClient {

		@Override
		public HttpClientConfig configuration() {
			return null;
		}

		@Override
		protected HttpClient duplicate() {
			return this;
		}

	}

	/**
	 * RouteLocatorBuilderConfig - 路由定位器构建器配置类
	 *
	 * 提供测试用的路由定位器Bean，包含使用tokenRelay过滤器的路由
	 */
	@EnableAutoConfiguration
	@SpringBootConfiguration
	protected static class RouteLocatorBuilderConfig {

		/**
		 * myRouteLocator - 测试用路由定位器
		 * @param builder 路由定位器构建器
		 * @return 包含测试路由的RouteLocator
		 */
		@Bean
		public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
			return builder.routes()
					.route("test", r -> r.alwaysTrue().filters(GatewayFilterSpec::tokenRelay).uri("http://localhost"))
					.build();
		}

	}

	/**
	 * HttpClientCustomizedConfig - HttpClient自定义器配置类
	 *
	 * 提供测试用的HttpClientCustomizer Bean
	 */
	@Configuration
	protected static class HttpClientCustomizedConfig {

		/** 标记自定义器是否被调用 */
		private final AtomicBoolean called = new AtomicBoolean();

		/**
		 * myCustomCustomizer - 自定义HttpClient自定义器
		 * @return HttpClientCustomizer实例
		 */
		@Bean
		HttpClientCustomizer myCustomCustomizer() {
			return httpClient -> {
				called.compareAndSet(false, true);
				return httpClient;
			};
		}

	}

	/**
	 * TestReactiveOAuth2AuthorizedClientManagerConfig - 测试用OAuth2客户端管理器配置
	 *
	 * 提供自定义的ReactiveOAuth2AuthorizedClientManager Bean用于测试优先级
	 */
	@Configuration
	protected static class TestReactiveOAuth2AuthorizedClientManagerConfig {

		/**
		 * myReactiveOAuth2AuthorizedClientManager - 自定义OAuth2客户端管理器
		 * @return 自定义的ReactiveOAuth2AuthorizedClientManager实例
		 */
		@Bean
		ReactiveOAuth2AuthorizedClientManager myReactiveOAuth2AuthorizedClientManager() {
			return authorizeRequest -> null;
		}

	}

}
