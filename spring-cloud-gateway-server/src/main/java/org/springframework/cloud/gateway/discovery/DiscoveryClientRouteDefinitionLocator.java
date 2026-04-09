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

package org.springframework.cloud.gateway.discovery;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.style.ToStringCreator;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * 基于服务发现客户端的路由定义定位器。
 * <p>
 * 该类实现了{@link RouteDefinitionLocator}接口，负责从服务发现组件（如Eureka、Consul、Nacos等）
 * 自动获取服务实例列表，并将其转换为网关路由定义。这是Spring Cloud Gateway实现动态路由的核心组件之一。
 * <p>
 * 工作流程：
 * <ol>
 * <li>从ReactiveDiscoveryClient获取所有服务列表</li>
 * <li>获取每个服务的实例列表</li>
 * <li>根据配置的includeExpression过滤服务</li>
 * <li>使用urlExpression生成目标URI</li>
 * <li>应用配置的断言和过滤器</li>
 * <li>生成最终的路由定义</li>
 * </ol>
 *
 * @author Spencer Gibb
 * @see RouteDefinitionLocator
 * @see ReactiveDiscoveryClient
 */
public class DiscoveryClientRouteDefinitionLocator implements RouteDefinitionLocator {

	/**
	 * 日志记录器，用于记录路由发现过程中的调试和错误信息。
	 */
	private static final Log log = LogFactory.getLog(DiscoveryClientRouteDefinitionLocator.class);

	/**
	 * 服务发现定位器配置属性，包含路由生成规则、过滤条件等配置。
	 */
	private final DiscoveryLocatorProperties properties;

	/**
	 * 路由ID前缀，用于生成唯一的路由标识符。 格式为：前缀 + 服务ID
	 */
	private final String routeIdPrefix;

	/**
	 * SpEL表达式求值上下文，用于解析和计算配置中的表达式。
	 */
	private final SimpleEvaluationContext evalCtxt;

	/**
	 * 服务实例流，包含从服务发现客户端获取的所有服务实例列表。
	 */
	private Flux<List<ServiceInstance>> serviceInstances;

	/**
	 * 构造方法，使用ReactiveDiscoveryClient初始化。
	 * <p>
	 * 该构造方法会：
	 * <ol>
	 * <li>设置路由ID前缀（默认使用发现客户端类名）</li>
	 * <li>初始化SpEL求值上下文</li>
	 * <li>订阅服务实例流，获取所有服务的实例列表</li>
	 * </ol>
	 * @param discoveryClient 响应式服务发现客户端，用于获取服务注册信息
	 * @param properties 服务发现定位器配置属性
	 */
	public DiscoveryClientRouteDefinitionLocator(ReactiveDiscoveryClient discoveryClient,
			DiscoveryLocatorProperties properties) {
		this(discoveryClient.getClass().getSimpleName(), properties);
		serviceInstances = discoveryClient.getServices()
				.flatMap(service -> discoveryClient.getInstances(service).collectList());
	}

	/**
	 * 私有构造方法，用于初始化基本属性。
	 * @param discoveryClientName 发现客户端名称，用于生成默认路由ID前缀
	 * @param properties 服务发现定位器配置属性
	 */
	private DiscoveryClientRouteDefinitionLocator(String discoveryClientName, DiscoveryLocatorProperties properties) {
		this.properties = properties;
		if (StringUtils.hasText(properties.getRouteIdPrefix())) {
			routeIdPrefix = properties.getRouteIdPrefix();
		}
		else {
			routeIdPrefix = discoveryClientName + "_";
		}
		evalCtxt = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
	}

	/**
	 * 获取路由定义流。
	 * <p>
	 * 这是核心方法，负责将服务发现客户端获取的服务实例转换为网关路由定义。 转换过程包括：
	 * <ol>
	 * <li>解析includeExpression和urlExpression表达式</li>
	 * <li>过滤符合条件的服务实例</li>
	 * <li>去重（同一服务的多个实例只保留一个用于生成路由）</li>
	 * <li>构建基础路由定义</li>
	 * <li>应用配置的断言（Predicates）</li>
	 * <li>应用配置的过滤器（Filters）</li>
	 * </ol>
	 * @return 路由定义响应式流，每个服务对应一个路由定义
	 */
	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {

		SpelExpressionParser parser = new SpelExpressionParser();
		Expression includeExpr = parser.parseExpression(properties.getIncludeExpression());
		Expression urlExpr = parser.parseExpression(properties.getUrlExpression());

		Predicate<ServiceInstance> includePredicate;
		if (properties.getIncludeExpression() == null || "true".equalsIgnoreCase(properties.getIncludeExpression())) {
			includePredicate = instance -> true;
		}
		else {
			includePredicate = instance -> {
				Boolean include = includeExpr.getValue(evalCtxt, instance, Boolean.class);
				if (include == null) {
					return false;
				}
				return include;
			};
		}

		return serviceInstances.filter(instances -> !instances.isEmpty()).flatMap(Flux::fromIterable)
				.filter(includePredicate).collectMap(ServiceInstance::getServiceId)
				// 去除重复项，同一服务只保留一个实例用于生成路由
				.flatMapMany(map -> Flux.fromIterable(map.values())).map(instance -> {
					RouteDefinition routeDefinition = buildRouteDefinition(urlExpr, instance);

					final ServiceInstance instanceForEval = new DelegatingServiceInstance(instance, properties);

					// 应用配置的断言定义
					for (PredicateDefinition original : this.properties.getPredicates()) {
						PredicateDefinition predicate = new PredicateDefinition();
						predicate.setName(original.getName());
						for (Map.Entry<String, String> entry : original.getArgs().entrySet()) {
							String value = getValueFromExpr(evalCtxt, parser, instanceForEval, entry);
							predicate.addArg(entry.getKey(), value);
						}
						routeDefinition.getPredicates().add(predicate);
					}

					// 应用配置的过滤器定义
					for (FilterDefinition original : this.properties.getFilters()) {
						FilterDefinition filter = new FilterDefinition();
						filter.setName(original.getName());
						for (Map.Entry<String, String> entry : original.getArgs().entrySet()) {
							String value = getValueFromExpr(evalCtxt, parser, instanceForEval, entry);
							filter.addArg(entry.getKey(), value);
						}
						routeDefinition.getFilters().add(filter);
					}

					return routeDefinition;
				});
	}

	/**
	 * 构建路由定义对象。
	 * <p>
	 * 根据服务实例和URL表达式生成基础路由定义，包含：
	 * <ul>
	 * <li>路由ID：由前缀和服务ID组成</li>
	 * <li>目标URI：通过urlExpression计算得出</li>
	 * <li>元数据：服务实例的元数据信息</li>
	 * </ul>
	 * @param urlExpr URL表达式，用于计算目标服务地址
	 * @param serviceInstance 服务实例信息
	 * @return 构建好的路由定义对象
	 */
	protected RouteDefinition buildRouteDefinition(Expression urlExpr, ServiceInstance serviceInstance) {
		String serviceId = serviceInstance.getServiceId();
		RouteDefinition routeDefinition = new RouteDefinition();
		routeDefinition.setId(this.routeIdPrefix + serviceId);
		String uri = urlExpr.getValue(this.evalCtxt, serviceInstance, String.class);
		routeDefinition.setUri(URI.create(uri));
		// 添加实例元数据到路由定义中
		routeDefinition.setMetadata(new LinkedHashMap<>(serviceInstance.getMetadata()));
		return routeDefinition;
	}

	/**
	 * 从SpEL表达式中获取值。
	 * <p>
	 * 解析并计算配置中的SpEL表达式，将结果转换为字符串。 如果表达式解析或求值失败，将抛出异常。
	 * @param evalCtxt SpEL求值上下文
	 * @param parser SpEL表达式解析器
	 * @param instance 服务实例，作为表达式求值的上下文对象
	 * @param entry 参数键值对，值部分为SpEL表达式
	 * @return 表达式计算后的字符串值
	 * @throws ParseException 当表达式解析失败时抛出
	 * @throws EvaluationException 当表达式求值失败时抛出
	 */
	String getValueFromExpr(SimpleEvaluationContext evalCtxt, SpelExpressionParser parser, ServiceInstance instance,
			Map.Entry<String, String> entry) {
		try {
			Expression valueExpr = parser.parseExpression(entry.getValue());
			return valueExpr.getValue(evalCtxt, instance, String.class);
		}
		catch (ParseException | EvaluationException e) {
			if (log.isDebugEnabled()) {
				log.debug("Unable to parse " + entry.getValue(), e);
			}
			throw e;
		}
	}

	/**
	 * 委托服务实例包装类。
	 * <p>
	 * 该类是ServiceInstance的装饰器，主要用于：
	 * <ul>
	 * <li>根据配置决定是否将服务ID转换为小写</li>
	 * <li>保持原始服务实例的其他属性不变</li>
	 * </ul>
	 * 这在处理Eureka等服务注册中心时特别有用，因为Eureka默认将服务名转为大写。
	 */
	private static class DelegatingServiceInstance implements ServiceInstance {

		/**
		 * 被委托的原始服务实例。
		 */
		final ServiceInstance delegate;

		/**
		 * 服务发现定位器配置属性。
		 */
		private final DiscoveryLocatorProperties properties;

		/**
		 * 构造方法。
		 * @param delegate 原始服务实例
		 * @param properties 配置属性
		 */
		private DelegatingServiceInstance(ServiceInstance delegate, DiscoveryLocatorProperties properties) {
			this.delegate = delegate;
			this.properties = properties;
		}

		/**
		 * 获取服务ID。
		 * <p>
		 * 如果配置了lowerCaseServiceId为true，则返回小写形式的服务ID。
		 * @return 服务ID（可能为小写形式）
		 */
		@Override
		public String getServiceId() {
			if (properties.isLowerCaseServiceId()) {
				return delegate.getServiceId().toLowerCase();
			}
			return delegate.getServiceId();
		}

		/**
		 * 获取服务实例的主机地址。
		 * @return 主机名或IP地址
		 */
		@Override
		public String getHost() {
			return delegate.getHost();
		}

		/**
		 * 获取服务实例的端口号。
		 * @return 端口号
		 */
		@Override
		public int getPort() {
			return delegate.getPort();
		}

		/**
		 * 判断服务实例是否使用安全连接（HTTPS）。
		 * @return true表示使用HTTPS，false表示使用HTTP
		 */
		@Override
		public boolean isSecure() {
			return delegate.isSecure();
		}

		/**
		 * 获取服务实例的完整URI。
		 * @return 服务实例的URI
		 */
		@Override
		public URI getUri() {
			return delegate.getUri();
		}

		/**
		 * 获取服务实例的元数据。
		 * @return 元数据键值对
		 */
		@Override
		public Map<String, String> getMetadata() {
			return delegate.getMetadata();
		}

		/**
		 * 获取服务实例的协议方案。
		 * @return 协议方案（如http、https）
		 */
		@Override
		public String getScheme() {
			return delegate.getScheme();
		}

		/**
		 * 返回该对象的字符串表示形式。
		 * @return 格式化的字符串表示
		 */
		@Override
		public String toString() {
			return new ToStringCreator(this).append("delegate", delegate).append("properties", properties).toString();
		}

	}

}
