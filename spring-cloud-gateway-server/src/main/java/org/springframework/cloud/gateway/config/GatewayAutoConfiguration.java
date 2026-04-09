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

import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.netty.http.server.WebsocketServerSpec;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.embedded.NettyWebServerFactoryCustomizer;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
import org.springframework.cloud.gateway.actuate.GatewayControllerEndpoint;
import org.springframework.cloud.gateway.actuate.GatewayLegacyControllerEndpoint;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledFilter;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledGlobalFilter;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledPredicate;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.ForwardPathFilter;
import org.springframework.cloud.gateway.filter.ForwardRoutingFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.RemoveCachedBodyFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.CacheRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.DedupeResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.JsonToGrpcGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.MapRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PrefixPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestParameterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestHeaderToRequestUriGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteLocationResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewriteResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SaveSessionGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersProperties;
import org.springframework.cloud.gateway.filter.factory.SetPathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetRequestHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetStatusGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.StripPrefixGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.GzipMessageBodyResolver;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyDecoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.MessageBodyEncoder;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.ForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.GRPCRequestHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.GRPCResponseHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.RemoveHopByHopHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.TransferEncodingNormalizationHeadersFilter;
import org.springframework.cloud.gateway.filter.headers.TrustedProxies;
import org.springframework.cloud.gateway.filter.headers.XForwardedHeadersFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.PrincipalNameKeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CloudFoundryRouteServiceRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.ReadBodyRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.XForwardedRemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.route.CachingRouteLocator;
import org.springframework.cloud.gateway.route.CompositeRouteDefinitionLocator;
import org.springframework.cloud.gateway.route.CompositeRouteLocator;
import org.springframework.cloud.gateway.route.InMemoryRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.RouteRefreshListener;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.StringToZonedDateTimeConverter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.RequestUpgradeStrategy;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;

/**
 * Spring Cloud Gateway 的核心自动配置类。
 * <p>
 * 负责注册 Gateway 运行所需的所有核心 Bean，包括：
 * <ul>
 * <li>路由定位器和路由定义定位器</li>
 * <li>全局过滤器（Global Filter）</li>
 * <li>网关过滤器工厂（Gateway Filter Factory）</li>
 * <li>路由谓词工厂（Route Predicate Factory）</li>
 * <li>HTTP 头部过滤器</li>
 * <li>Netty 相关配置（HTTP 客户端、WebSocket 等）</li>
 * <li>Actuator 端点配置</li>
 * <li>OAuth2 Token Relay 配置</li>
 * </ul>
 * </p>
 * <p>
 * 该配置在 {@link DispatcherHandler} 类存在时激活，默认启用（可通过
 * {@code spring.cloud.gateway.enabled=false} 关闭）。 自动配置顺序：在
 * {@link HttpHandlerAutoConfiguration} 和 {@link WebFluxAutoConfiguration} 之前， 在
 * {@link GatewayReactiveLoadBalancerClientAutoConfiguration} 之后。
 * </p>
 *
 * @author Spencer Gibb
 * @author Ziemowit Stolarczyk
 * @author Mete Alpaslan Katırcıoğlu
 * @author Alberto C. Ríos
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@EnableConfigurationProperties
@AutoConfigureBefore({ HttpHandlerAutoConfiguration.class, WebFluxAutoConfiguration.class })
@AutoConfigureAfter({ GatewayReactiveLoadBalancerClientAutoConfiguration.class,
		GatewayClassPathWarningAutoConfiguration.class })
@ConditionalOnClass(DispatcherHandler.class)
public class GatewayAutoConfiguration {

	/**
	 * 创建字符串到 ZonedDateTime 的转换器 Bean，用于日期时间谓词的参数转换。
	 * @return 字符串到 ZonedDateTime 的转换器实例
	 */
	@Bean
	public StringToZonedDateTimeConverter stringToZonedDateTimeConverter() {
		return new StringToZonedDateTimeConverter();
	}

	/**
	 * 创建路由定位器构建器，支持通过 Java DSL 方式定义路由。
	 * @param context Spring 应用上下文
	 * @return 路由定位器构建器实例
	 */
	@Bean
	public RouteLocatorBuilder routeLocatorBuilder(ConfigurableApplicationContext context) {
		return new RouteLocatorBuilder(context);
	}

	/**
	 * 基于配置属性的路由定义定位器，从 application.yml 中读取路由定义。
	 * @param properties 网关配置属性
	 * @return 配置属性路由定义定位器实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public PropertiesRouteDefinitionLocator propertiesRouteDefinitionLocator(GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	/**
	 * 基于内存的路由定义仓库，用于动态路由的增删改查。
	 * @return 内存路由定义仓库实例
	 */
	@Bean
	@ConditionalOnMissingBean(RouteDefinitionRepository.class)
	public InMemoryRouteDefinitionRepository inMemoryRouteDefinitionRepository() {
		return new InMemoryRouteDefinitionRepository();
	}

	/**
	 * 组合路由定义定位器，聚合所有 RouteDefinitionLocator 实例作为主要定位器。
	 * @param routeDefinitionLocators 路由定义定位器列表
	 * @return 组合路由定义定位器实例
	 */
	@Bean
	@Primary
	public RouteDefinitionLocator routeDefinitionLocator(List<RouteDefinitionLocator> routeDefinitionLocators) {
		return new CompositeRouteDefinitionLocator(Flux.fromIterable(routeDefinitionLocators));
	}

	/**
	 * 网关配置服务，提供类型转换和参数验证功能。
	 * @param beanFactory Spring Bean 工厂
	 * @param conversionService 类型转换服务
	 * @param validator 参数验证器
	 * @return 网关配置服务实例
	 */
	@Bean
	public ConfigurationService gatewayConfigurationService(BeanFactory beanFactory,
			@Qualifier("webFluxConversionService") ObjectProvider<ConversionService> conversionService,
			ObjectProvider<Validator> validator) {
		return new ConfigurationService(beanFactory, conversionService, validator);
	}

	/**
	 * 基于路由定义的路由定位器，将 RouteDefinition 转换为 Route。
	 * @param properties 网关配置属性
	 * @param gatewayFilters 网关过滤器工厂列表
	 * @param predicates 路由谓词工厂列表
	 * @param routeDefinitionLocator 路由定义定位器
	 * @param configurationService 配置服务
	 * @return 路由定义路由定位器实例
	 */
	@Bean
	public RouteLocator routeDefinitionRouteLocator(GatewayProperties properties,
			List<GatewayFilterFactory> gatewayFilters, List<RoutePredicateFactory> predicates,
			RouteDefinitionLocator routeDefinitionLocator, ConfigurationService configurationService) {
		return new RouteDefinitionRouteLocator(routeDefinitionLocator, predicates, gatewayFilters, properties,
				configurationService);
	}

	/**
	 * 缓存路由定位器，包装组合路由定位器以提升路由查找性能。
	 * @param routeLocators 路由定位器列表
	 * @return 缓存路由定位器实例
	 */
	@Bean
	@Primary
	@ConditionalOnMissingBean(name = "cachedCompositeRouteLocator")
	// TODO: property to disable composite?
	public RouteLocator cachedCompositeRouteLocator(List<RouteLocator> routeLocators) {
		return new CachingRouteLocator(new CompositeRouteLocator(Flux.fromIterable(routeLocators)));
	}

	/**
	 * 路由刷新监听器，监听服务发现心跳事件并触发路由刷新。
	 * @param publisher 应用事件发布器
	 * @return 路由刷新监听器实例
	 */
	@Bean
	@ConditionalOnClass(name = "org.springframework.cloud.client.discovery.event.HeartbeatMonitor")
	public RouteRefreshListener routeRefreshListener(ApplicationEventPublisher publisher) {
		return new RouteRefreshListener(publisher);
	}

	/**
	 * 过滤 Web 处理器，执行全局过滤器和路由过滤器的链式调用。
	 * @param globalFilters 全局过滤器列表
	 * @return 过滤 Web 处理器实例
	 */
	@Bean
	public FilteringWebHandler filteringWebHandler(List<GlobalFilter> globalFilters) {
		return new FilteringWebHandler(globalFilters);
	}

	/**
	 * 全局 CORS（跨域资源共享）配置属性。
	 * @return 全局 CORS 配置属性实例
	 */
	@Bean
	public GlobalCorsProperties globalCorsProperties() {
		return new GlobalCorsProperties();
	}

	/**
	 * 路由谓词处理器映射，匹配请求到对应的路由。
	 * @param webHandler 过滤 Web 处理器
	 * @param routeLocator 路由定位器
	 * @param globalCorsProperties 全局 CORS 配置属性
	 * @param environment Spring 环境
	 * @return 路由谓词处理器映射实例
	 */
	@Bean
	@ConditionalOnMissingBean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(FilteringWebHandler webHandler,
			RouteLocator routeLocator, GlobalCorsProperties globalCorsProperties, Environment environment) {
		return new RoutePredicateHandlerMapping(webHandler, routeLocator, globalCorsProperties, environment);
	}

	/**
	 * 网关核心配置属性（路由定义、默认过滤器等）。
	 * @return 网关配置属性实例
	 */
	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	// ConfigurationProperty beans

	/**
	 * 安全响应头配置属性。
	 * @return 安全响应头配置属性实例
	 */
	@Bean
	public SecureHeadersProperties secureHeadersProperties() {
		return new SecureHeadersProperties();
	}

	/**
	 * Forwarded 头部过滤器，处理代理转发的标准 Forwarded 头。
	 * @param properties 网关配置属性
	 * @return Forwarded 头部过滤器实例
	 */
	@Bean
	@Conditional(TrustedProxies.ForwardedTrustedProxiesCondition.class)
	public ForwardedHeadersFilter forwardedHeadersFilter(GatewayProperties properties) {
		return new ForwardedHeadersFilter(properties.getTrustedProxies());
	}

	// HttpHeaderFilter beans

	/**
	 * 移除逐跳头部过滤器，删除 HTTP 逐跳（Hop-by-Hop）头。
	 * @return 移除逐跳头部过滤器实例
	 */
	@Bean
	public RemoveHopByHopHeadersFilter removeHopByHopHeadersFilter() {
		return new RemoveHopByHopHeadersFilter();
	}

	/**
	 * X-Forwarded 头部过滤器，处理代理转发的 X-Forwarded-* 系列头。
	 * @param properties 网关配置属性
	 * @return X-Forwarded 头部过滤器实例
	 */
	@Bean
	@Conditional(TrustedProxies.XForwardedTrustedProxiesCondition.class)
	public XForwardedHeadersFilter xForwardedHeadersFilter(GatewayProperties properties) {
		return new XForwardedHeadersFilter(properties.getTrustedProxies());
	}

	/**
	 * gRPC 请求头部过滤器，处理 gRPC 请求的特殊头部。
	 * @return gRPC 请求头部过滤器实例
	 */
	@Bean
	@ConditionalOnProperty(name = "server.http2.enabled", matchIfMissing = true)
	public GRPCRequestHeadersFilter gRPCRequestHeadersFilter() {
		return new GRPCRequestHeadersFilter();
	}

	/**
	 * gRPC 响应头部过滤器，处理 gRPC 响应的特殊头部。
	 * @return gRPC 响应头部过滤器实例
	 */
	@Bean
	@ConditionalOnProperty(name = "server.http2.enabled", matchIfMissing = true)
	public GRPCResponseHeadersFilter gRPCResponseHeadersFilter() {
		return new GRPCResponseHeadersFilter();
	}

	/**
	 * JSON 转 gRPC 过滤器工厂，将 JSON 请求体转换为 gRPC 消息。
	 * @param gRPCSSLContext gRPC SSL 配置器
	 * @param resourceLoader 资源加载器
	 * @return JSON 转 gRPC 过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	@ConditionalOnProperty(name = "server.http2.enabled", matchIfMissing = true)
	@ConditionalOnClass(name = "io.grpc.Channel")
	public JsonToGrpcGatewayFilterFactory jsonToGRPCFilterFactory(GrpcSslConfigurer gRPCSSLContext,
			ResourceLoader resourceLoader) {
		return new JsonToGrpcGatewayFilterFactory(gRPCSSLContext, resourceLoader);
	}

	/**
	 * gRPC SSL 配置器，配置 gRPC 客户端的 SSL/TLS 上下文。
	 * @param properties HTTP 客户端配置属性
	 * @return gRPC SSL 配置器实例
	 * @throws KeyStoreException 密钥库异常
	 * @throws NoSuchAlgorithmException 无此算法异常
	 */
	@Bean
	@ConditionalOnEnabledFilter(JsonToGrpcGatewayFilterFactory.class)
	@ConditionalOnMissingBean(GrpcSslConfigurer.class)
	@ConditionalOnClass(name = "io.grpc.Channel")
	public GrpcSslConfigurer grpcSslConfigurer(HttpClientProperties properties)
			throws KeyStoreException, NoSuchAlgorithmException {
		TrustManagerFactory trustManagerFactory = TrustManagerFactory
				.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init(KeyStore.getInstance(KeyStore.getDefaultType()));

		return new GrpcSslConfigurer(properties.getSsl());
	}

	/**
	 * 传输编码规范化过滤器，统一传输编码头格式。
	 * @return 传输编码规范化过滤器实例
	 */
	@Bean
	public TransferEncodingNormalizationHeadersFilter transferEncodingNormalizationHeadersFilter() {
		return new TransferEncodingNormalizationHeadersFilter();
	}

	// GlobalFilter beans

	/**
	 * 适配缓存请求体全局过滤器，将请求体包装为可重复读取的形式。
	 * @return 适配缓存请求体全局过滤器实例
	 */
	@Bean
	@ConditionalOnEnabledGlobalFilter
	public AdaptCachedBodyGlobalFilter adaptCachedBodyGlobalFilter() {
		return new AdaptCachedBodyGlobalFilter();
	}

	/**
	 * 移除缓存请求体过滤器，在过滤器链结束后清理缓存的请求体。
	 * @return 移除缓存请求体过滤器实例
	 */
	@Bean
	@ConditionalOnEnabledGlobalFilter
	public RemoveCachedBodyFilter removeCachedBodyFilter() {
		return new RemoveCachedBodyFilter();
	}

	/**
	 * 路由到请求URL过滤器，将路由的 URI 转换为实际的请求 URL。
	 * @return 路由到请求URL过滤器实例
	 */
	@Bean
	@ConditionalOnEnabledGlobalFilter
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	/**
	 * 转发路由过滤器，使用 DispatcherHandler 将请求转发到本地处理器。
	 * @param dispatcherHandler 分发处理器
	 * @return 转发路由过滤器实例
	 */
	@Bean
	@ConditionalOnEnabledGlobalFilter
	public ForwardRoutingFilter forwardRoutingFilter(ObjectProvider<DispatcherHandler> dispatcherHandler) {
		return new ForwardRoutingFilter(dispatcherHandler);
	}

	/**
	 * 转发路径过滤器，调整转发请求的路径。
	 * @return 转发路径过滤器实例
	 */
	@Bean
	@ConditionalOnEnabledGlobalFilter
	public ForwardPathFilter forwardPathFilter() {
		return new ForwardPathFilter();
	}

	/**
	 * WebSocket 服务，处理 WebSocket 握手升级。
	 * @param requestUpgradeStrategy 请求升级策略
	 * @return WebSocket 服务实例
	 */
	@Bean
	@ConditionalOnEnabledGlobalFilter(WebsocketRoutingFilter.class)
	public WebSocketService webSocketService(RequestUpgradeStrategy requestUpgradeStrategy) {
		return new HandshakeWebSocketService(requestUpgradeStrategy);
	}

	/**
	 * WebSocket 路由过滤器，将 WebSocket 请求路由到后端服务。
	 * @param webSocketClient WebSocket 客户端
	 * @param webSocketService WebSocket 服务
	 * @param headersFilters HTTP 头部过滤器列表
	 * @return WebSocket 路由过滤器实例
	 */
	@Bean
	@ConditionalOnEnabledGlobalFilter
	public WebsocketRoutingFilter websocketRoutingFilter(WebSocketClient webSocketClient,
			WebSocketService webSocketService, ObjectProvider<List<HttpHeadersFilter>> headersFilters) {
		return new WebsocketRoutingFilter(webSocketClient, webSocketService, headersFilters);
	}

	/**
	 * 权重计算 Web 过滤器，根据路由权重计算各路由的分发比例。
	 * @param configurationService 配置服务
	 * @param routeLocator 路由定位器
	 * @return 权重计算 Web 过滤器实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate(WeightRoutePredicateFactory.class)
	public WeightCalculatorWebFilter weightCalculatorWebFilter(ConfigurationService configurationService,
			ObjectProvider<RouteLocator> routeLocator) {
		return new WeightCalculatorWebFilter(routeLocator, configurationService);
	}

	// Predicate Factory beans

	/**
	 * After 时间路由谓词工厂，匹配在指定日期时间之后的请求。
	 * @return After 时间路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public AfterRoutePredicateFactory afterRoutePredicateFactory() {
		return new AfterRoutePredicateFactory();
	}

	/**
	 * Before 时间路由谓词工厂，匹配在指定日期时间之前的请求。
	 * @return Before 时间路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public BeforeRoutePredicateFactory beforeRoutePredicateFactory() {
		return new BeforeRoutePredicateFactory();
	}

	/**
	 * Between 时间范围路由谓词工厂，匹配在指定时间范围内的请求。
	 * @return Between 时间范围路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public BetweenRoutePredicateFactory betweenRoutePredicateFactory() {
		return new BetweenRoutePredicateFactory();
	}

	/**
	 * Cookie 路由谓词工厂，根据请求中的 Cookie 匹配路由。
	 * @return Cookie 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public CookieRoutePredicateFactory cookieRoutePredicateFactory() {
		return new CookieRoutePredicateFactory();
	}

	/**
	 * Header 路由谓词工厂，根据请求头匹配路由。
	 * @return Header 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public HeaderRoutePredicateFactory headerRoutePredicateFactory() {
		return new HeaderRoutePredicateFactory();
	}

	/**
	 * Host 路由谓词工厂，根据请求的 Host 头匹配路由。
	 * @return Host 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public HostRoutePredicateFactory hostRoutePredicateFactory() {
		return new HostRoutePredicateFactory();
	}

	/**
	 * Method 路由谓词工厂，根据 HTTP 请求方法匹配路由。
	 * @return Method 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public MethodRoutePredicateFactory methodRoutePredicateFactory() {
		return new MethodRoutePredicateFactory();
	}

	/**
	 * Path 路由谓词工厂，根据请求路径模式匹配路由。
	 * @return Path 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public PathRoutePredicateFactory pathRoutePredicateFactory() {
		return new PathRoutePredicateFactory();
	}

	/**
	 * Query 路由谓词工厂，根据请求查询参数匹配路由。
	 * @return Query 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public QueryRoutePredicateFactory queryRoutePredicateFactory() {
		return new QueryRoutePredicateFactory();
	}

	/**
	 * ReadBody 路由谓词工厂，根据请求体内容匹配路由。
	 * @param codecConfigurer 服务器编解码配置器
	 * @return ReadBody 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public ReadBodyRoutePredicateFactory readBodyPredicateFactory(ServerCodecConfigurer codecConfigurer) {
		return new ReadBodyRoutePredicateFactory(codecConfigurer.getReaders());
	}

	/**
	 * RemoteAddr 路由谓词工厂，根据客户端远程 IP 地址匹配路由。
	 * @return RemoteAddr 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory() {
		return new RemoteAddrRoutePredicateFactory();
	}

	/**
	 * XForwardedRemoteAddr 路由谓词工厂，根据 X-Forwarded-For 头中的 IP 匹配路由。
	 * @return XForwardedRemoteAddr 路由谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public XForwardedRemoteAddrRoutePredicateFactory xForwardedRemoteAddrRoutePredicateFactory() {
		return new XForwardedRemoteAddrRoutePredicateFactory();
	}

	/**
	 * Weight 路由谓词工厂，根据权重值分配请求到不同路由（依赖权重计算过滤器）。
	 * @return Weight 路由谓词工厂实例
	 */
	@Bean
	@DependsOn("weightCalculatorWebFilter")
	@ConditionalOnEnabledPredicate
	public WeightRoutePredicateFactory weightRoutePredicateFactory() {
		return new WeightRoutePredicateFactory();
	}

	/**
	 * CloudFoundry 路由服务谓词工厂，用于 Cloud Foundry 路由服务集成。
	 * @return CloudFoundry 路由服务谓词工厂实例
	 */
	@Bean
	@ConditionalOnEnabledPredicate
	public CloudFoundryRouteServiceRoutePredicateFactory cloudFoundryRouteServiceRoutePredicateFactory() {
		return new CloudFoundryRouteServiceRoutePredicateFactory();
	}

	// GatewayFilter Factory beans

	/**
	 * 添加请求头过滤器工厂，为下游请求添加指定头部。
	 * @return 添加请求头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public AddRequestHeaderGatewayFilterFactory addRequestHeaderGatewayFilterFactory() {
		return new AddRequestHeaderGatewayFilterFactory();
	}

	/**
	 * 映射请求头过滤器工厂，将一个请求头的值映射到另一个请求头。
	 * @return 映射请求头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public MapRequestHeaderGatewayFilterFactory mapRequestHeaderGatewayFilterFactory() {
		return new MapRequestHeaderGatewayFilterFactory();
	}

	/**
	 * 添加请求参数过滤器工厂，为下游请求添加查询参数。
	 * @return 添加请求参数过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public AddRequestParameterGatewayFilterFactory addRequestParameterGatewayFilterFactory() {
		return new AddRequestParameterGatewayFilterFactory();
	}

	/**
	 * 添加响应头过滤器工厂，为网关响应添加指定头部。
	 * @return 添加响应头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public AddResponseHeaderGatewayFilterFactory addResponseHeaderGatewayFilterFactory() {
		return new AddResponseHeaderGatewayFilterFactory();
	}

	/**
	 * 修改请求体过滤器工厂，在请求转发前修改请求体内容。
	 * @param codecConfigurer 服务器编解码配置器
	 * @return 修改请求体过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public ModifyRequestBodyGatewayFilterFactory modifyRequestBodyGatewayFilterFactory(
			ServerCodecConfigurer codecConfigurer) {
		return new ModifyRequestBodyGatewayFilterFactory(codecConfigurer.getReaders());
	}

	/**
	 * 响应头去重过滤器工厂，移除响应中重复的头部。
	 * @return 响应头去重过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public DedupeResponseHeaderGatewayFilterFactory dedupeResponseHeaderGatewayFilterFactory() {
		return new DedupeResponseHeaderGatewayFilterFactory();
	}

	/**
	 * 修改响应体过滤器工厂，在响应返回前修改响应体内容。
	 * @param codecConfigurer 服务器编解码配置器
	 * @param bodyDecoders 消息体解码器集合
	 * @param bodyEncoders 消息体编码器集合
	 * @return 修改响应体过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public ModifyResponseBodyGatewayFilterFactory modifyResponseBodyGatewayFilterFactory(
			ServerCodecConfigurer codecConfigurer, Set<MessageBodyDecoder> bodyDecoders,
			Set<MessageBodyEncoder> bodyEncoders) {
		return new ModifyResponseBodyGatewayFilterFactory(codecConfigurer.getReaders(), bodyDecoders, bodyEncoders);
	}

	/**
	 * 缓存请求体过滤器工厂，将请求体缓存以支持多次读取。
	 * @param codecConfigurer 服务器编解码配置器
	 * @return 缓存请求体过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public CacheRequestBodyGatewayFilterFactory cacheRequestBodyGatewayFilterFactory(
			ServerCodecConfigurer codecConfigurer) {
		return new CacheRequestBodyGatewayFilterFactory(codecConfigurer.getReaders());
	}

	/**
	 * 前缀路径过滤器工厂，为请求路径添加前缀。
	 * @return 前缀路径过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public PrefixPathGatewayFilterFactory prefixPathGatewayFilterFactory() {
		return new PrefixPathGatewayFilterFactory();
	}

	/**
	 * 保留原始 Host 头过滤器工厂，将原始 Host 头传递给下游。
	 * @return 保留原始 Host 头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public PreserveHostHeaderGatewayFilterFactory preserveHostHeaderGatewayFilterFactory() {
		return new PreserveHostHeaderGatewayFilterFactory();
	}

	/**
	 * 重定向过滤器工厂，返回 HTTP 重定向响应。
	 * @return 重定向过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RedirectToGatewayFilterFactory redirectToGatewayFilterFactory() {
		return new RedirectToGatewayFilterFactory();
	}

	/**
	 * 移除请求头过滤器工厂，删除转发请求中的指定头部。
	 * @return 移除请求头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RemoveRequestHeaderGatewayFilterFactory removeRequestHeaderGatewayFilterFactory() {
		return new RemoveRequestHeaderGatewayFilterFactory();
	}

	/**
	 * 移除请求参数过滤器工厂，删除转发请求中的指定查询参数。
	 * @return 移除请求参数过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RemoveRequestParameterGatewayFilterFactory removeRequestParameterGatewayFilterFactory() {
		return new RemoveRequestParameterGatewayFilterFactory();
	}

	/**
	 * 移除响应头过滤器工厂，删除网关响应中的指定头部。
	 * @return 移除响应头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RemoveResponseHeaderGatewayFilterFactory removeResponseHeaderGatewayFilterFactory() {
		return new RemoveResponseHeaderGatewayFilterFactory();
	}

	/**
	 * 主体名称密钥解析器，使用认证用户的主体名称作为限流键。
	 * @return 主体名称密钥解析器实例
	 */
	@Bean(name = PrincipalNameKeyResolver.BEAN_NAME)
	@ConditionalOnBean(RateLimiter.class)
	@ConditionalOnMissingBean(KeyResolver.class)
	@ConditionalOnEnabledFilter(RequestRateLimiterGatewayFilterFactory.class)
	public PrincipalNameKeyResolver principalNameKeyResolver() {
		return new PrincipalNameKeyResolver();
	}

	/**
	 * 请求限流过滤器工厂，对请求进行速率限制。
	 * @param rateLimiter 限流器
	 * @param resolver 密钥解析器
	 * @return 请求限流过滤器工厂实例
	 */
	@Bean
	@ConditionalOnBean({ RateLimiter.class, KeyResolver.class })
	@ConditionalOnEnabledFilter
	public RequestRateLimiterGatewayFilterFactory requestRateLimiterGatewayFilterFactory(RateLimiter rateLimiter,
			KeyResolver resolver) {
		return new RequestRateLimiterGatewayFilterFactory(rateLimiter, resolver);
	}

	/**
	 * 重写路径过滤器工厂，使用正则表达式重写请求路径。
	 * @return 重写路径过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RewritePathGatewayFilterFactory rewritePathGatewayFilterFactory() {
		return new RewritePathGatewayFilterFactory();
	}

	/**
	 * 重试过滤器工厂，对失败的请求进行重试。
	 * @return 重试过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RetryGatewayFilterFactory retryGatewayFilterFactory() {
		return new RetryGatewayFilterFactory();
	}

	/**
	 * 设置路径过滤器工厂，替换请求的原始路径。
	 * @return 设置路径过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public SetPathGatewayFilterFactory setPathGatewayFilterFactory() {
		return new SetPathGatewayFilterFactory();
	}

	/**
	 * 安全响应头过滤器工厂，为响应添加一系列安全相关的头部。
	 * @param properties 安全响应头配置属性
	 * @return 安全响应头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public SecureHeadersGatewayFilterFactory secureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		return new SecureHeadersGatewayFilterFactory(properties);
	}

	/**
	 * 设置请求头过滤器工厂，替换转发请求中的指定头部。
	 * @return 设置请求头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public SetRequestHeaderGatewayFilterFactory setRequestHeaderGatewayFilterFactory() {
		return new SetRequestHeaderGatewayFilterFactory();
	}

	/**
	 * 设置请求 Host 头过滤器工厂，替换请求中的 Host 头。
	 * @return 设置请求 Host 头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public SetRequestHostHeaderGatewayFilterFactory setRequestHostHeaderGatewayFilterFactory() {
		return new SetRequestHostHeaderGatewayFilterFactory();
	}

	/**
	 * 设置响应头过滤器工厂，替换响应中的指定头部。
	 * @return 设置响应头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public SetResponseHeaderGatewayFilterFactory setResponseHeaderGatewayFilterFactory() {
		return new SetResponseHeaderGatewayFilterFactory();
	}

	/**
	 * 重写响应头过滤器工厂，使用正则表达式修改响应头。
	 * @return 重写响应头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RewriteResponseHeaderGatewayFilterFactory rewriteResponseHeaderGatewayFilterFactory() {
		return new RewriteResponseHeaderGatewayFilterFactory();
	}

	/**
	 * 重写 Location 响应头过滤器工厂，重写重定向中的 Location 头。
	 * @return 重写 Location 响应头过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RewriteLocationResponseHeaderGatewayFilterFactory rewriteLocationResponseHeaderGatewayFilterFactory() {
		return new RewriteLocationResponseHeaderGatewayFilterFactory();
	}

	/**
	 * 设置状态码过滤器工厂，设置网关响应的 HTTP 状态码。
	 * @return 设置状态码过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public SetStatusGatewayFilterFactory setStatusGatewayFilterFactory() {
		return new SetStatusGatewayFilterFactory();
	}

	/**
	 * 保存会话过滤器工厂，在转发前保存会话变更。
	 * @return 保存会话过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public SaveSessionGatewayFilterFactory saveSessionGatewayFilterFactory() {
		return new SaveSessionGatewayFilterFactory();
	}

	/**
	 * 剥离路径前缀过滤器工厂，移除请求路径的指定前缀段数。
	 * @return 剥离路径前缀过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public StripPrefixGatewayFilterFactory stripPrefixGatewayFilterFactory() {
		return new StripPrefixGatewayFilterFactory();
	}

	/**
	 * 请求头转 URI 过滤器工厂，使用请求头值作为请求 URI。
	 * @return 请求头转 URI 过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RequestHeaderToRequestUriGatewayFilterFactory requestHeaderToRequestUriGatewayFilterFactory() {
		return new RequestHeaderToRequestUriGatewayFilterFactory();
	}

	/**
	 * 请求大小过滤器工厂，限制请求体的最大允许大小。
	 * @return 请求大小过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RequestSizeGatewayFilterFactory requestSizeGatewayFilterFactory() {
		return new RequestSizeGatewayFilterFactory();
	}

	/**
	 * 请求头大小过滤器工厂，限制请求头的最大允许大小。
	 * @return 请求头大小过滤器工厂实例
	 */
	@Bean
	@ConditionalOnEnabledFilter
	public RequestHeaderSizeGatewayFilterFactory requestHeaderSizeGatewayFilterFactory() {
		return new RequestHeaderSizeGatewayFilterFactory();
	}

	/**
	 * Gzip 消息体解析器，处理 Gzip 压缩的请求/响应体。
	 * @return Gzip 消息体解析器实例
	 */
	@Bean
	public GzipMessageBodyResolver gzipMessageBodyResolver() {
		return new GzipMessageBodyResolver();
	}

	/**
	 * Netty 相关配置内部类。
	 * <p>
	 * 当类路径中存在 {@link HttpClient} 时激活，负责配置 Netty HTTP 服务器、 HTTP 客户端、WebSocket 支持以及路由转发过滤器。
	 * </p>
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpClient.class)
	protected static class NettyConfiguration {

		/** 日志记录器 */
		protected final Log logger = LogFactory.getLog(getClass());

		/**
		 * Netty 服务器 Wiretap 自定义器，启用网络流量调试日志。
		 * @param environment 环境配置
		 * @param serverProperties 服务器配置属性
		 * @return Netty 服务器工厂自定义器
		 */
		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.httpserver.wiretap")
		public NettyWebServerFactoryCustomizer nettyServerWiretapCustomizer(Environment environment,
				ServerProperties serverProperties) {
			return new NettyWebServerFactoryCustomizer(environment, serverProperties) {
				@Override
				public void customize(NettyReactiveWebServerFactory factory) {
					factory.addServerCustomizers(httpServer -> httpServer.wiretap(true));
					super.customize(factory);
				}
			};
		}

		/**
		 * 网关 Netty 服务器自定义器，配置受信任代理的转发头处理。
		 * @param gatewayProperties 网关配置属性
		 * @return Netty 服务器自定义器实例
		 */
		@Bean
		@TrustedProxies.ConditionalOnPropertyExists
		public NettyServerCustomizer gatewayNettyServerCustomizer(GatewayProperties gatewayProperties) {
			TrustedProxies trustedProxies = TrustedProxies.from(gatewayProperties.getTrustedProxies());

			return httpServer -> httpServer.forwarded((connectionInfo, httpRequest) -> {
				InetSocketAddress remoteAddress = connectionInfo.getRemoteAddress();
				if (remoteAddress != null && trustedProxies.isTrusted(remoteAddress.getHostString())) {
					// update remote address
					return DefaultNettyHttpForwardedHeaderHandler.INSTANCE.apply(connectionInfo, httpRequest);
				}
				return connectionInfo;
			});
		}

		/**
		 * HTTP 客户端 SSL 配置器。
		 * @param serverProperties 服务器配置属性
		 * @param httpClientProperties HTTP 客户端配置属性
		 * @return HTTP 客户端 SSL 配置器实例
		 */
		@Bean
		public HttpClientSslConfigurer httpClientSslConfigurer(ServerProperties serverProperties,
				HttpClientProperties httpClientProperties) {
			return new HttpClientSslConfigurer(httpClientProperties.getSsl(), serverProperties) {
			};
		}

		/**
		 * 网关 HTTP 客户端工厂，创建和配置 Netty HttpClient。
		 * @param properties HTTP 客户端配置属性
		 * @param serverProperties 服务器配置属性
		 * @param customizers HTTP 客户端自定义器列表
		 * @param sslConfigurer SSL 配置器
		 * @return HTTP 客户端工厂实例
		 */
		@Bean
		@ConditionalOnMissingBean({ HttpClient.class, HttpClientFactory.class })
		public HttpClientFactory gatewayHttpClientFactory(HttpClientProperties properties,
				ServerProperties serverProperties, List<HttpClientCustomizer> customizers,
				HttpClientSslConfigurer sslConfigurer) {
			return new HttpClientFactory(properties, serverProperties, sslConfigurer, customizers);
		}

		/**
		 * HTTP 客户端配置属性。
		 * @return HTTP 客户端配置属性实例
		 */
		@Bean
		public HttpClientProperties httpClientProperties() {
			return new HttpClientProperties();
		}

		/**
		 * Netty 路由过滤器，使用 HttpClient 将请求转发到后端服务。
		 * @param httpClient Netty HTTP 客户端
		 * @param headersFilters HTTP 头部过滤器列表
		 * @param properties HTTP 客户端配置属性
		 * @return Netty 路由过滤器实例
		 */
		@Bean
		@ConditionalOnEnabledGlobalFilter
		public NettyRoutingFilter routingFilter(HttpClient httpClient,
				ObjectProvider<List<HttpHeadersFilter>> headersFilters, HttpClientProperties properties) {
			return new NettyRoutingFilter(httpClient, headersFilters, properties);
		}

		/**
		 * Netty 写响应过滤器，将后端响应写回客户端。
		 * @param properties 网关配置属性
		 * @return Netty 写响应过滤器
		 */
		/**
		 * Netty 写响应过滤器，将后端响应写回客户端。
		 * @param properties 网关配置属性
		 * @return Netty 写响应过滤器实例
		 */
		@Bean
		@ConditionalOnEnabledGlobalFilter(NettyRoutingFilter.class)
		public NettyWriteResponseFilter nettyWriteResponseFilter(GatewayProperties properties) {
			return new NettyWriteResponseFilter(properties.getStreamingMediaTypes());
		}

		/**
		 * Reactor Netty WebSocket 客户端，用于 WebSocket 请求转发。
		 * @param properties HTTP 客户端配置属性
		 * @param httpClient HTTP 客户端
		 * @return Reactor Netty WebSocket 客户端
		 */
		/**
		 * Reactor Netty WebSocket 客户端，用于 WebSocket 请求转发。
		 * @param properties HTTP 客户端配置属性
		 * @param httpClient Netty HTTP 客户端
		 * @return Reactor Netty WebSocket 客户端实例
		 */
		@Bean
		@ConditionalOnEnabledGlobalFilter(WebsocketRoutingFilter.class)
		public ReactorNettyWebSocketClient reactorNettyWebSocketClient(HttpClientProperties properties,
				HttpClient httpClient) {
			Supplier<WebsocketClientSpec.Builder> builderSupplier = () -> {
				WebsocketClientSpec.Builder builder = WebsocketClientSpec.builder()
						.handlePing(properties.getWebsocket().isProxyPing());
				if (properties.getWebsocket().getMaxFramePayloadLength() != null) {
					builder.maxFramePayloadLength(properties.getWebsocket().getMaxFramePayloadLength());
				}
				return builder;
			};
			return new ReactorNettyWebSocketClient(httpClient, builderSupplier);
		}

		/**
		 * Reactor Netty WebSocket 升级策略，处理 WebSocket 协议升级请求。
		 * @param httpClientProperties HTTP 客户端配置属性
		 * @return Reactor Netty WebSocket 升级策略
		 */
		/**
		 * Reactor Netty WebSocket 升级策略。
		 * @param httpClientProperties HTTP 客户端配置属性
		 * @return Reactor Netty 请求升级策略实例
		 */
		@Bean
		@ConditionalOnEnabledGlobalFilter(WebsocketRoutingFilter.class)
		public ReactorNettyRequestUpgradeStrategy reactorNettyRequestUpgradeStrategy(
				HttpClientProperties httpClientProperties) {

			Supplier<WebsocketServerSpec.Builder> builderSupplier = () -> {
				WebsocketServerSpec.Builder builder = WebsocketServerSpec.builder();
				HttpClientProperties.Websocket websocket = httpClientProperties.getWebsocket();
				PropertyMapper map = PropertyMapper.get();
				map.from(websocket::getMaxFramePayloadLength).whenNonNull().to(builder::maxFramePayloadLength);
				map.from(websocket::isProxyPing).to(builder::handlePing);
				return builder;
			};

			return new ReactorNettyRequestUpgradeStrategy(builderSupplier);
		}

	}

	/**
	 * Gateway Actuator 端点配置内部类。
	 * <p>
	 * 当类路径中存在 {@link Health} 类时激活，注册网关管理相关的 Actuator 端点， 提供路由查看、刷新、创建和删除等管理功能。
	 * </p>
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(Health.class)
	protected static class GatewayActuatorConfiguration {

		/**
		 * 网关控制器端点，提供路由查看、刷新、创建/删除等管理 API。
		 * <p>
		 * 当 actuator verbose 功能启用时生效。
		 * </p>
		 * @param globalFilters 全局过滤器列表
		 * @param gatewayFilters 网关过滤器工厂列表
		 * @param routePredicates 路由谓词工厂列表
		 * @param routeDefinitionWriter 路由定义写入器
		 * @param routeLocator 路由定位器
		 * @param routeDefinitionLocator 路由定义定位器
		 * @return 网关控制器端点
		 */
		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.actuator.verbose.enabled", matchIfMissing = true)
		@ConditionalOnAvailableEndpoint
		public GatewayControllerEndpoint gatewayControllerEndpoint(List<GlobalFilter> globalFilters,
				List<GatewayFilterFactory> gatewayFilters, List<RoutePredicateFactory> routePredicates,
				RouteDefinitionWriter routeDefinitionWriter, RouteLocator routeLocator,
				RouteDefinitionLocator routeDefinitionLocator) {
			return new GatewayControllerEndpoint(globalFilters, gatewayFilters, routePredicates, routeDefinitionWriter,
					routeLocator, routeDefinitionLocator);
		}

		/**
		 * 网关旧版控制器端点，兼容旧版 API。
		 * <p>
		 * 当 actuator verbose 功能关闭时生效。
		 * </p>
		 * @param routeDefinitionLocator 路由定义定位器
		 * @param globalFilters 全局过滤器列表
		 * @param gatewayFilters 网关过滤器工厂列表
		 * @param routePredicates 路由谓词工厂列表
		 * @param routeDefinitionWriter 路由定义写入器
		 * @param routeLocator 路由定位器
		 * @return 网关旧版控制器端点
		 */
		/**
		 * 网关旧版控制器端点，兼容旧版 API。
		 * @param routeDefinitionLocator 路由定义定位器
		 * @param globalFilters 全局过滤器列表
		 * @param gatewayFilters 网关过滤器工厂列表
		 * @param routePredicates 路由谓词工厂列表
		 * @param routeDefinitionWriter 路由定义写入器
		 * @param routeLocator 路由定位器
		 * @return 网关旧版控制器端点实例
		 */
		@Bean
		@Conditional(OnVerboseDisabledCondition.class)
		@ConditionalOnAvailableEndpoint
		public GatewayLegacyControllerEndpoint gatewayLegacyControllerEndpoint(
				RouteDefinitionLocator routeDefinitionLocator, List<GlobalFilter> globalFilters,
				List<GatewayFilterFactory> gatewayFilters, List<RoutePredicateFactory> routePredicates,
				RouteDefinitionWriter routeDefinitionWriter, RouteLocator routeLocator) {
			return new GatewayLegacyControllerEndpoint(routeDefinitionLocator, globalFilters, gatewayFilters,
					routePredicates, routeDefinitionWriter, routeLocator);
		}

	}

	/**
	 * 详细模式禁用条件。
	 * <p>
	 * 当 {@code spring.cloud.gateway.actuator.verbose.enabled} 为 {@code true}（默认值）时，
	 * 所有嵌套条件都不匹配，从而使整个条件不满足。
	 * </p>
	 */
	private static class OnVerboseDisabledCondition extends NoneNestedConditions {

		OnVerboseDisabledCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@ConditionalOnProperty(name = "spring.cloud.gateway.actuator.verbose.enabled", matchIfMissing = true)
		/** Verbose 模式禁用的嵌套条件 */
		static class VerboseDisabled {

		}

	}

	/**
	 * OAuth2 Token Relay 配置内部类。
	 * <p>
	 * 当存在 OAuth2 相关类且 Token Relay 过滤器启用时， 注册 Token Relay 过滤器工厂，支持 OAuth2 令牌的自动转发。
	 * </p>
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
	@ConditionalOnClass({ OAuth2AuthorizedClient.class, SecurityWebFilterChain.class, SecurityProperties.class })
	@ConditionalOnEnabledFilter(TokenRelayGatewayFilterFactory.class)
	protected static class TokenRelayConfiguration {

		/**
		 * 创建 Token Relay 过滤器工厂，用于将 OAuth2 令牌转发到下游服务。
		 * @param clientManager OAuth2 授权客户端管理器
		 * @return Token Relay 过滤器工厂
		 */
		@Bean
		public TokenRelayGatewayFilterFactory tokenRelayGatewayFilterFactory(
				ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManager) {
			return new TokenRelayGatewayFilterFactory(clientManager);
		}

	}

}
