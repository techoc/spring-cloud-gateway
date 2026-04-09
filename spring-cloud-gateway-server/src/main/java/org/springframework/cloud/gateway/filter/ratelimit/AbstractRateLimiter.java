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

package org.springframework.cloud.gateway.filter.ratelimit;

import java.util.Map;

import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.support.AbstractStatefulConfigurable;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationListener;
import org.springframework.core.style.ToStringCreator;

/**
 * 限流器的抽象基类，提供限流器的通用配置管理和事件监听功能。
 *
 * <p>
 * 该类是所有限流器实现的基类，主要功能包括：
 * <ul>
 * <li>继承自{@link AbstractStatefulConfigurable}，提供配置状态管理能力</li>
 * <li>实现{@link RateLimiter}接口，定义限流器的核心行为</li>
 * <li>监听{@link FilterArgsEvent}事件，支持动态配置更新</li>
 * </ul>
 *
 * <p>
 * 使用场景：
 * <ul>
 * <li>作为RedisRateLimiter等具体限流器的父类</li>
 * <li>管理不同路由的限流配置</li>
 * <li>响应路由配置变更事件</li>
 * </ul>
 *
 * <p>
 * 配置管理机制：
 * <ul>
 * <li>通过ConfigurationService绑定配置属性</li>
 * <li>配置属性名由子类在构造时指定</li>
 * <li>支持按路由ID存储独立的配置</li>
 * </ul>
 *
 * @param <C> 配置类型，指定限流器使用的配置类
 * @author Spencer Gibb
 * @see AbstractStatefulConfigurable
 * @see RateLimiter
 * @see FilterArgsEvent
 */
public abstract class AbstractRateLimiter<C> extends AbstractStatefulConfigurable<C>
		implements RateLimiter<C>, ApplicationListener<FilterArgsEvent> {

	/**
	 * 配置属性名称前缀。
	 * <p>
	 * 用于标识限流器相关的配置属性，格式为 "配置属性名.路由ID.具体配置项"
	 * </p>
	 */
	private String configurationPropertyName;

	/**
	 * 配置服务，用于绑定和解析配置属性。
	 * <p>
	 * 支持SpEL表达式解析和配置绑定功能
	 * </p>
	 */
	private ConfigurationService configurationService;

	/**
	 * 构造函数，创建一个限流器实例。
	 *
	 * <p>
	 * 参数说明：
	 * <ul>
	 * <li>configClass - 限流器的配置类，用于实例化配置对象</li>
	 * <li>configurationPropertyName - 配置属性名称前缀，用于匹配配置文件中的属性</li>
	 * <li>configurationService - 配置服务，处理配置绑定和SpEL表达式解析</li>
	 * </ul>
	 * @param configClass 配置类的Class对象
	 * @param configurationPropertyName 配置属性名称前缀
	 * @param configurationService 配置服务实例
	 */
	protected AbstractRateLimiter(Class<C> configClass, String configurationPropertyName,
			ConfigurationService configurationService) {
		super(configClass);
		this.configurationPropertyName = configurationPropertyName;
		this.configurationService = configurationService;
	}

	/**
	 * 获取配置属性名称前缀。
	 *
	 * <p>
	 * 返回值说明：
	 * <ul>
	 * <li>返回配置属性名称前缀字符串</li>
	 * <li>格式例如："redis-rate-limiter"</li>
	 * </ul>
	 * @return 配置属性名称前缀
	 */
	protected String getConfigurationPropertyName() {
		return configurationPropertyName;
	}

	/**
	 * 设置配置服务。
	 *
	 * <p>
	 * 用于更新配置服务实例，支持动态更换配置服务实现。
	 * @param configurationService 新的配置服务实例
	 */
	protected void setConfigurationService(ConfigurationService configurationService) {
		this.configurationService = configurationService;
	}

	/**
	 * 处理过滤器参数事件，更新对应路由的限流配置。
	 *
	 * <p>
	 * 核心业务逻辑：
	 * <ul>
	 * <li>当FilterArgsEvent事件触发时，检查是否包含相关配置</li>
	 * <li>过滤出以configurationPropertyName开头的配置参数</li>
	 * <li>通过ConfigurationService将参数绑定到新的配置对象</li>
	 * <li>将配置按路由ID存储到config Map中</li>
	 * </ul>
	 *
	 * <p>
	 * 事件处理流程：
	 * <ol>
	 * <li>获取事件中的参数集合</li>
	 * <li>判断参数是否与当前限流器相关</li>
	 * <li>获取目标路由ID</li>
	 * <li>创建新的配置对象并绑定参数</li>
	 * <li>更新config Map中的配置</li>
	 * </ol>
	 * @param event 过滤器参数事件，包含路由ID和配置参数
	 * @see FilterArgsEvent
	 */
	@Override
	public void onApplicationEvent(FilterArgsEvent event) {
		Map<String, Object> args = event.getArgs();

		if (args.isEmpty() || !hasRelevantKey(args)) {
			return;
		}

		String routeId = event.getRouteId();

		C routeConfig = newConfig();
		if (this.configurationService != null) {
			this.configurationService.with(routeConfig).name(this.configurationPropertyName).normalizedProperties(args)
					.bind();
		}
		getConfig().put(routeId, routeConfig);
	}

	/**
	 * 检查参数集合中是否包含与当前限流器相关的配置。
	 *
	 * <p>
	 * 判断逻辑：
	 * <ul>
	 * <li>遍历参数的所有键</li>
	 * <li>检查是否有任何键以 "配置属性名." 开头</li>
	 * <li>用于过滤掉不相关的FilterArgsEvent</li>
	 * </ul>
	 *
	 * <p>
	 * 注意事项：
	 * <ul>
	 * <li>使用流式API进行键匹配</li>
	 * <li>前缀匹配确保只处理限流器相关配置</li>
	 * </ul>
	 * @param args 配置参数映射
	 * @return 如果包含相关配置返回true，否则返回false
	 */
	private boolean hasRelevantKey(Map<String, Object> args) {
		return args.keySet().stream().anyMatch(key -> key.startsWith(configurationPropertyName + "."));
	}

	/**
	 * 返回限流器的字符串表示。
	 *
	 * <p>
	 * 返回内容包含：
	 * <ul>
	 * <li>configurationPropertyName - 配置属性名称</li>
	 * <li>config - 当前所有路由的配置映射</li>
	 * <li>configClass - 配置类类型</li>
	 * </ul>
	 * @return 限流器的字符串描述
	 */
	@Override
	public String toString() {
		return new ToStringCreator(this).append("configurationPropertyName", configurationPropertyName)
				.append("config", getConfig()).append("configClass", getConfigClass()).toString();
	}

}
