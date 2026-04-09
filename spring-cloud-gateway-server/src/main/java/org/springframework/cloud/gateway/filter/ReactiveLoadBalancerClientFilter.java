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

package org.springframework.cloud.gateway.filter;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycleValidator;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.ResponseData;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

/**
 * 响应式负载均衡客户端过滤器。
 * <p>
 * 该全局过滤器使用响应式 Spring Cloud LoadBalancer，对 {@code lb://} 协议的请求进行服务发现
 * 和负载均衡处理。它会从注册中心获取可用服务实例，并将原始请求 URL 中的服务名替换为实际 实例的 IP 和端口，实现客户端负载均衡。
 * <p>
 * 主要功能：
 * <ul>
 * <li>处理 {@code lb://serviceId/path} 格式的 URI，或前缀为 {@code lb:} 的 URI scheme；</li>
 * <li>触发 {@link LoadBalancerLifecycle} 回调（onStart、onStartRequest、onComplete）；</li>
 * <li>支持 hint 机制（基于服务 ID 的路由提示）；</li>
 * <li>处理服务实例未找到的情况，抛出 {@link NotFoundException}；</li>
 * <li>将选定的服务实例响应存入 exchange 属性，供后续过滤器使用。</li>
 * </ul>
 * <p>
 * 执行顺序为 {@link #LOAD_BALANCER_CLIENT_FILTER_ORDER}（值为 10150）。
 *
 * @author Spencer Gibb
 * @author Tim Ysewyn
 * @author Olga Maciaszek-Sharma
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ReactiveLoadBalancerClientFilter implements GlobalFilter, Ordered {

	private static final Log log = LogFactory.getLog(ReactiveLoadBalancerClientFilter.class);

	/**
	 * 负载均衡客户端过滤器的执行顺序，值为 10150。
	 */
	public static final int LOAD_BALANCER_CLIENT_FILTER_ORDER = 10150;

	/** 负载均衡客户端工厂，用于获取服务实例的负载均衡器和配置 */
	private final LoadBalancerClientFactory clientFactory;

	/** 网关负载均衡配置属性（如是否使用 404 替代 503） */
	private final GatewayLoadBalancerProperties properties;

	/**
	 * @deprecated 请使用
	 * {@link #ReactiveLoadBalancerClientFilter(LoadBalancerClientFactory, GatewayLoadBalancerProperties)}
	 * 代替。
	 * @param clientFactory 负载均衡客户端工厂
	 * @param properties 网关负载均衡配置属性
	 * @param loadBalancerProperties 负载均衡通用属性（已废弃，忽略）
	 */
	@Deprecated
	public ReactiveLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory,
			GatewayLoadBalancerProperties properties, LoadBalancerProperties loadBalancerProperties) {
		this.clientFactory = clientFactory;
		this.properties = properties;
	}

	/**
	 * 构造响应式负载均衡客户端过滤器。
	 * @param clientFactory 负载均衡客户端工厂
	 * @param properties 网关负载均衡配置属性
	 */
	public ReactiveLoadBalancerClientFilter(LoadBalancerClientFactory clientFactory,
			GatewayLoadBalancerProperties properties) {
		this.clientFactory = clientFactory;
		this.properties = properties;
	}

	/**
	 * 返回过滤器执行顺序。
	 * @return {@link #LOAD_BALANCER_CLIENT_FILTER_ORDER}，值为 10150
	 */
	@Override
	public int getOrder() {
		return LOAD_BALANCER_CLIENT_FILTER_ORDER;
	}

	/**
	 * 过滤请求，对 {@code lb://} 协议的请求进行负载均衡处理。
	 * <p>
	 * 处理流程：
	 * <ol>
	 * <li>检查请求 URL 协议，若不是 {@code lb} 则跳过；</li>
	 * <li>保存原始请求 URL；</li>
	 * <li>触发 LoadBalancerLifecycle.onStart 回调；</li>
	 * <li>调用负载均衡器选择服务实例；</li>
	 * <li>将目标实例 URI 重建后写入 exchange 属性；</li>
	 * <li>继续过滤器链，并在成功/失败时触发相应的 Lifecycle 回调。</li>
	 * </ol>
	 * @param exchange 当前服务器 Web 交换对象
	 * @param chain 过滤器链
	 * @return {@code Mono<Void>}，表示请求处理完成的信号
	 */
	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		URI url = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		String schemePrefix = exchange.getAttribute(GATEWAY_SCHEME_PREFIX_ATTR);
		if (url == null || (!"lb".equals(url.getScheme()) && !"lb".equals(schemePrefix))) {
			return chain.filter(exchange);
		}
		// 保存原始请求 URL 以供后续使用
		addOriginalRequestUrl(exchange, url);

		if (log.isTraceEnabled()) {
			log.trace(ReactiveLoadBalancerClientFilter.class.getSimpleName() + " url before: " + url);
		}

		URI requestUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
		String serviceId = requestUri.getHost();
		// 获取支持的生命周期处理器
		Set<LoadBalancerLifecycle> supportedLifecycleProcessors = LoadBalancerLifecycleValidator
				.getSupportedLifecycleProcessors(clientFactory.getInstances(serviceId, LoadBalancerLifecycle.class),
						RequestDataContext.class, ResponseData.class, ServiceInstance.class);
		DefaultRequest<RequestDataContext> lbRequest = new DefaultRequest<>(
				new RequestDataContext(new RequestData(exchange.getRequest()), getHint(serviceId)));
		LoadBalancerProperties loadBalancerProperties = clientFactory.getProperties(serviceId);
		return choose(lbRequest, serviceId, supportedLifecycleProcessors).doOnNext(response -> {

			if (!response.hasServer()) {
				// 未找到可用实例，触发 DISCARD 回调并抛出异常
				supportedLifecycleProcessors.forEach(lifecycle -> lifecycle
						.onComplete(new CompletionContext<>(CompletionContext.Status.DISCARD, lbRequest, response)));
				throw NotFoundException.create(properties.isUse404(), "Unable to find instance for " + url.getHost());
			}

			ServiceInstance retrievedInstance = response.getServer();

			URI uri = exchange.getRequest().getURI();

			// 若使用 lb:<scheme> 机制，则用 <scheme> 作为默认协议（若负载均衡器未提供）
			String overrideScheme = retrievedInstance.isSecure() ? "https" : "http";
			if (schemePrefix != null) {
				overrideScheme = url.getScheme();
			}

			DelegatingServiceInstance serviceInstance = new DelegatingServiceInstance(retrievedInstance,
					overrideScheme);

			URI requestUrl = reconstructURI(serviceInstance, uri);

			if (log.isTraceEnabled()) {
				log.trace("LoadBalancerClientFilter url chosen: " + requestUrl);
			}
			// 将重建的 URL 写入 exchange，供后续路由过滤器使用
			exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, requestUrl);
			exchange.getAttributes().put(GATEWAY_LOADBALANCER_RESPONSE_ATTR, response);
			supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onStartRequest(lbRequest, response));
		}).then(chain.filter(exchange))
				.doOnError(throwable -> supportedLifecycleProcessors.forEach(lifecycle -> lifecycle
						.onComplete(new CompletionContext<ResponseData, ServiceInstance, RequestDataContext>(
								CompletionContext.Status.FAILED, throwable, lbRequest,
								exchange.getAttribute(GATEWAY_LOADBALANCER_RESPONSE_ATTR)))))
				.doOnSuccess(aVoid -> supportedLifecycleProcessors.forEach(lifecycle -> lifecycle
						.onComplete(new CompletionContext<ResponseData, ServiceInstance, RequestDataContext>(
								CompletionContext.Status.SUCCESS, lbRequest,
								exchange.getAttribute(GATEWAY_LOADBALANCER_RESPONSE_ATTR), buildResponseData(exchange,
										loadBalancerProperties.isUseRawStatusCodeInResponseData())))));
	}

	/**
	 * 构建负载均衡响应数据。
	 * @param exchange 当前服务器 Web 交换对象
	 * @param useRawStatusCodes 是否使用原始状态码（而非 HttpStatus 枚举）
	 * @return 响应数据对象
	 */
	private ResponseData buildResponseData(ServerWebExchange exchange, boolean useRawStatusCodes) {
		if (useRawStatusCodes) {
			return new ResponseData(new RequestData(exchange.getRequest()), exchange.getResponse());
		}
		return new ResponseData(exchange.getResponse(), new RequestData(exchange.getRequest()));
	}

	/**
	 * 根据选定的服务实例重建请求 URI。
	 * @param serviceInstance 选定的服务实例（可能包含覆盖协议）
	 * @param original 原始请求 URI
	 * @return 重建后的目标 URI
	 */
	protected URI reconstructURI(ServiceInstance serviceInstance, URI original) {
		return LoadBalancerUriTools.reconstructURI(serviceInstance, original);
	}

	/**
	 * 使用负载均衡器选择服务实例。
	 * <p>
	 * 若未找到对应服务的负载均衡器，则抛出 {@link NotFoundException}。
	 * @param lbRequest 负载均衡请求对象
	 * @param serviceId 目标服务 ID
	 * @param supportedLifecycleProcessors 支持的生命周期处理器集合
	 * @return 包含所选服务实例的 {@code Mono<Response<ServiceInstance>>}
	 * @throws NotFoundException 若未找到对应服务的负载均衡器
	 */
	private Mono<Response<ServiceInstance>> choose(Request<RequestDataContext> lbRequest, String serviceId,
			Set<LoadBalancerLifecycle> supportedLifecycleProcessors) {
		ReactorLoadBalancer<ServiceInstance> loadBalancer = this.clientFactory.getInstance(serviceId,
				ReactorServiceInstanceLoadBalancer.class);
		if (loadBalancer == null) {
			throw new NotFoundException("No loadbalancer available for " + serviceId);
		}
		supportedLifecycleProcessors.forEach(lifecycle -> lifecycle.onStart(lbRequest));
		return loadBalancer.choose(lbRequest);
	}

	/**
	 * 获取路由提示（hint）值。
	 * <p>
	 * 从负载均衡属性的 hint 映射中获取服务特定的提示值，若未配置则使用 "default"。 hint 可用于实现金丝雀发布、蓝绿部署等高级路由策略。
	 * @param serviceId 目标服务 ID
	 * @return hint 值字符串
	 */
	private String getHint(String serviceId) {
		LoadBalancerProperties loadBalancerProperties = clientFactory.getProperties(serviceId);
		Map<String, String> hints = loadBalancerProperties.getHint();
		String defaultHint = hints.getOrDefault("default", "default");
		String hintPropertyValue = hints.get(serviceId);
		return hintPropertyValue != null ? hintPropertyValue : defaultHint;
	}

}
