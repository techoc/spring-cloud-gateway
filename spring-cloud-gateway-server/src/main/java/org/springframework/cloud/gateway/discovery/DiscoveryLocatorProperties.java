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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.core.style.ToStringCreator;

/**
 * 服务发现定位器配置属性类。
 * <p>
 * 该类用于配置Spring Cloud Gateway与服务发现组件（如Eureka、Consul等）集成时的相关属性。
 * 通过配置这些属性，可以控制网关如何从服务注册中心自动发现服务并生成对应的路由规则。
 * <p>
 * 配置前缀：spring.cloud.gateway.discovery.locator
 *
 * @author Spring Cloud Gateway Team
 * @see ConfigurationProperties
 */
@ConfigurationProperties("spring.cloud.gateway.discovery.locator")
public class DiscoveryLocatorProperties {

	/**
	 * 是否启用服务发现客户端网关集成。 默认为false，需要显式设置为true才能开启自动路由发现功能。
	 */
	private boolean enabled = false;

	/**
	 * 路由ID的前缀。 默认值为discoveryClient.getClass().getSimpleName() + "_"。
	 * 服务ID将被追加此前缀后作为完整的路由ID。
	 */
	private String routeIdPrefix;

	/**
	 * SpEL表达式，用于评估是否将某个服务包含在网关集成中。 默认值为"true"，表示包含所有服务。 可通过自定义表达式实现服务筛选，例如：serviceId
	 * matches 'user-.*'
	 */
	private String includeExpression = "true";

	/**
	 * SpEL表达式，用于为每个路由创建URI。 默认值为'lb://'+serviceId，表示使用负载均衡方式访问服务。 支持自定义表达式以修改服务访问地址格式。
	 */
	private String urlExpression = "'lb://'+serviceId";

	/**
	 * 是否将serviceId转换为小写形式。 默认值为false。 当与Eureka配合使用时特别有用，因为Eureka会自动将服务ID转为大写。
	 * 启用后，MYSERVICE将匹配/myservice/**
	 */
	private boolean lowerCaseServiceId = false;

	/**
	 * 路由断言定义列表。 用于定义匹配路由的条件，如路径匹配、Header匹配等。
	 */
	private List<PredicateDefinition> predicates = new ArrayList<>();

	/**
	 * 路由过滤器定义列表。 用于定义应用于路由的过滤器，如重写路径、添加Header等。
	 */
	private List<FilterDefinition> filters = new ArrayList<>();

	/**
	 * 获取是否启用服务发现定位器。
	 * @return true表示启用，false表示禁用
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * 设置是否启用服务发现定位器。
	 * @param enabled 是否启用
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * 获取路由ID前缀。
	 * @return 路由ID前缀，可能为null
	 */
	public String getRouteIdPrefix() {
		return routeIdPrefix;
	}

	/**
	 * 设置路由ID前缀。
	 * @param routeIdPrefix 路由ID前缀
	 */
	public void setRouteIdPrefix(String routeIdPrefix) {
		this.routeIdPrefix = routeIdPrefix;
	}

	/**
	 * 获取服务包含表达式。
	 * @return SpEL表达式字符串
	 */
	public String getIncludeExpression() {
		return includeExpression;
	}

	/**
	 * 设置服务包含表达式。
	 * @param includeExpression SpEL表达式字符串
	 */
	public void setIncludeExpression(String includeExpression) {
		this.includeExpression = includeExpression;
	}

	/**
	 * 获取URL生成表达式。
	 * @return SpEL表达式字符串
	 */
	public String getUrlExpression() {
		return urlExpression;
	}

	/**
	 * 设置URL生成表达式。
	 * @param urlExpression SpEL表达式字符串
	 */
	public void setUrlExpression(String urlExpression) {
		this.urlExpression = urlExpression;
	}

	/**
	 * 获取是否将服务ID转换为小写。
	 * @return true表示转换为小写，false表示保持原样
	 */
	public boolean isLowerCaseServiceId() {
		return lowerCaseServiceId;
	}

	/**
	 * 设置是否将服务ID转换为小写。
	 * @param lowerCaseServiceId 是否转换为小写
	 */
	public void setLowerCaseServiceId(boolean lowerCaseServiceId) {
		this.lowerCaseServiceId = lowerCaseServiceId;
	}

	/**
	 * 获取路由断言定义列表。
	 * @return 断言定义列表
	 */
	public List<PredicateDefinition> getPredicates() {
		return predicates;
	}

	/**
	 * 设置路由断言定义列表。
	 * @param predicates 断言定义列表
	 */
	public void setPredicates(List<PredicateDefinition> predicates) {
		this.predicates = predicates;
	}

	/**
	 * 获取路由过滤器定义列表。
	 * @return 过滤器定义列表
	 */
	public List<FilterDefinition> getFilters() {
		return filters;
	}

	/**
	 * 设置路由过滤器定义列表。
	 * @param filters 过滤器定义列表
	 */
	public void setFilters(List<FilterDefinition> filters) {
		this.filters = filters;
	}

	/**
	 * 返回该对象的字符串表示形式，包含所有属性值。
	 * @return 格式化的字符串表示
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("enabled", enabled).append("routeIdPrefix", routeIdPrefix)
				.append("includeExpression", includeExpression).append("urlExpression", urlExpression)
				.append("lowerCaseServiceId", lowerCaseServiceId).append("predicates", predicates)
				.append("filters", filters).toString();
	}

}
