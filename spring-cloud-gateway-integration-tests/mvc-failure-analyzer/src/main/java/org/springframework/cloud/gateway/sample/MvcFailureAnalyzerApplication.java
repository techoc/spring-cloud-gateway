/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MVC 失败分析器集成测试的主应用程序入口。
 * <p>
 * 该类用于模拟一个同时引入了 Spring MVC 和 Spring Cloud Gateway 的应用， 以触发
 * {@code MvcFoundOnClasspathFailureAnalyzer} 的检测逻辑， 验证当类路径中存在 MVC 时网关能否给出友好的错误提示。
 * </p>
 * <p>
 * 同时作为 Reactive Web 应用的路由配置示例：通过负载均衡路由将 {@code /myprefix/**} 请求转发到本地 "myservice" 服务。
 * </p>
 *
 * @author Spencer Gibb
 */
@SpringBootConfiguration // 标记为 Spring Boot 配置类（不扫描子包）
@EnableAutoConfiguration // 启用自动配置
@RestController // 同时作为 REST 控制器，提供测试接口
@LoadBalancerClient(name = "myservice", configuration = MyServiceConf.class) // 使用自定义配置注册负载均衡客户端
public class MvcFailureAnalyzerApplication {

	/**
	 * 提供一个简单的 HTTP GET 接口，返回 "Hello" 字符串。
	 * <p>
	 * 用于测试路由转发是否成功将请求代理到该接口。
	 * </p>
	 * @return 固定字符串 "Hello"
	 */
	@GetMapping("hello")
	public String hello() {
		return "Hello";
	}

	/**
	 * 仅在 Reactive Web 应用类型下注册路由定位器 Bean。
	 * <p>
	 * 将路径 {@code /myprefix/**} 的请求去掉前缀后， 通过负载均衡转发到名为 "myservice" 的服务。
	 * </p>
	 * @param builder 路由定位器构建器，由 Spring 自动注入
	 * @return 配置好的 {@link RouteLocator} 实例
	 */
	@Bean
	@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE) // 仅在
	// Reactive
	// 模式下生效
	public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
				// 匹配 /myprefix/** 路径，去掉第一段前缀，转发到负载均衡的 myservice
				.route(r -> r.path("/myprefix/**").filters(f -> f.stripPrefix(1)).uri("lb://myservice")).build();
	}

}

/**
 * "myservice" 负载均衡客户端的自定义配置类。
 * <p>
 * 提供一个静态的服务实例列表，将 "myservice" 指向本地随机端口， 用于测试环境中避免依赖真实的服务注册中心。
 * </p>
 */
class MyServiceConf {

	/**
	 * 注入本地服务器随机分配的端口号，用于构造服务实例地址。
	 */
	@LocalServerPort
	private int port = 0;

	/**
	 * 注册一个静态服务实例列表供 "myservice" 使用。
	 * <p>
	 * 将 "myservice" 映射到 localhost 上的本地端口，不启用 TLS（secure=false）。
	 * </p>
	 * @return 静态服务实例列表提供者
	 */
	@Bean
	public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
		return ServiceInstanceListSuppliers.from("myservice",
				new DefaultServiceInstance("myservice-1", "myservice", "localhost", port, false));
	}

}
