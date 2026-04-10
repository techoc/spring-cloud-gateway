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

package org.springframework.cloud.gateway.tests.http2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP/2 集成测试的主应用程序。
 * <p>
 * 该应用集成了 Spring Cloud Gateway，并注册了多条测试路由：
 * <ul>
 *   <li>{@code /myprefix/**} → 负载均衡到本地 "myservice"（HTTPS，与网关同端口）</li>
 *   <li>{@code /nossl/**}    → 负载均衡到本地 "nossl" 服务（HTTP，无 TLS）</li>
 *   <li>{@code /neverssl/**} → 代理到外部 HTTP 地址 neverssl.com</li>
 *   <li>{@code /httpbin/**}  → 代理到 nghttp2.org 的 HTTPS 服务</li>
 * </ul>
 * 同时内置了一个简单的 REST 接口 {@code GET /hello} 用于验证请求是否成功到达后端。
 * </p>
 * <p>
 * 使用命令行快速测试（需要忽略自签名证书）：
 * <pre>curl -i --insecure https://localhost:8443/hello</pre>
 * </p>
 *
 * @author Spencer Gibb
 */
// curl -i --insecure https://localhost:8443/hello
@SpringBootConfiguration    // 标记为 Spring Boot 配置类
@EnableAutoConfiguration    // 启用自动配置（包含 Gateway 自动配置）
@RestController             // 同时作为 REST 控制器
@LoadBalancerClients({
		@LoadBalancerClient(name = "myservice", configuration = Http2Application.MyServiceConf.class),  // 注册 myservice 负载均衡客户端
		@LoadBalancerClient(name = "nossl", configuration = Http2Application.NosslServiceConf.class)    // 注册 nossl 负载均衡客户端
})
public class Http2Application {

	/** 应用日志记录器 */
	private static Log log = LogFactory.getLog(Http2Application.class);

	/**
	 * 提供一个简单的 HTTP GET 接口，返回 "Hello" 字符串。
	 * <p>
	 * 用于验证经过网关路由转发后，请求能够正确到达后端并返回响应。
	 * </p>
	 *
	 * @return 固定字符串 "Hello"
	 */
	@GetMapping("hello")
	public String hello() {
		return "Hello";
	}

	/**
	 * 注册网关路由定位器，配置多条测试路由规则。
	 * <p>
	 * 路由规则说明：
	 * <ul>
	 *   <li>{@code /myprefix/**}：去掉前缀后通过负载均衡转发到 myservice（HTTPS）</li>
	 *   <li>{@code /nossl/**}：去掉前缀后通过负载均衡转发到 nossl 服务（HTTP，无 TLS）</li>
	 *   <li>{@code /neverssl/**}：去掉前缀后转发到 neverssl.com（外部 HTTP 服务）</li>
	 *   <li>{@code /httpbin/**}：直接转发到 nghttp2.org（外部 HTTPS 服务，用于测试 HTTP/2）</li>
	 * </ul>
	 * </p>
	 *
	 * @param builder 路由定位器构建器，由 Spring 自动注入
	 * @return 配置完成的 {@link RouteLocator} 实例
	 */
	@Bean
	public RouteLocator myRouteLocator(RouteLocatorBuilder builder) {
		return builder.routes()
				// 路由1：/myprefix/** → 负载均衡到本地 myservice（TLS）
				.route(r -> r.path("/myprefix/**").filters(f -> f.stripPrefix(1)).uri("lb://myservice"))
				// 路由2：/nossl/** → 负载均衡到本地 nossl 服务（非 TLS）
				.route(r -> r.path("/nossl/**").filters(f -> f.stripPrefix(1)).uri("lb://nossl"))
				// 路由3：/neverssl/** → 转发到外部 neverssl.com（始终为 HTTP）
				.route(r -> r.path("/neverssl/**").filters(f -> f.stripPrefix(1)).uri("http://neverssl.com"))
				// 路由4：/httpbin/** → 直接代理到 nghttp2.org（支持 HTTP/2）
				.route(r -> r.path("/httpbin/**").uri("https://nghttp2.org"))
				.build();
	}

	/**
	 * 应用程序入口方法，启动 HTTP/2 集成测试应用。
	 *
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(Http2Application.class, args);
	}

	/**
	 * "myservice" 负载均衡客户端的自定义配置（HTTPS 服务实例）。
	 * <p>
	 * 将 "myservice" 映射到本地随机分配的 HTTPS 端口（{@code local.server.port}），
	 * 即指向网关自身，实现环回测试。服务实例启用 TLS（{@code secure=true}）。
	 * </p>
	 */
	static class MyServiceConf {

		/**
		 * 注册静态的 myservice 服务实例列表。
		 * <p>
		 * 从 Spring 环境读取 {@code local.server.port}，默认值为 8443。
		 * </p>
		 *
		 * @param env Spring 环境对象，用于读取动态端口
		 * @return 指向本地 HTTPS 端口的静态服务实例列表
		 */
		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier(Environment env) {
			Integer port = env.getProperty("local.server.port", Integer.class, 8443);
			log.info("local.server.port = " + port);
			// secure=true 表示使用 HTTPS/TLS 连接
			return ServiceInstanceListSuppliers.from("myservice",
					new DefaultServiceInstance("myservice-1", "myservice", "localhost", port, true));
		}

	}

	/**
	 * "nossl" 负载均衡客户端的自定义配置（HTTP 服务实例，不启用 TLS）。
	 * <p>
	 * 从系统属性 {@code nossl.port} 读取端口，默认值为 8080，
	 * 用于测试网关将 HTTPS 请求代理到非 TLS 后端的场景（TLS 终止）。
	 * </p>
	 */
	static class NosslServiceConf {

		/**
		 * 注册静态的 nossl 服务实例列表。
		 * <p>
		 * 从系统属性 {@code nossl.port} 读取端口，默认为 8080。
		 * </p>
		 *
		 * @return 指向本地 HTTP 端口的静态服务实例列表（不启用 TLS）
		 */
		@Bean
		public ServiceInstanceListSupplier noSslStaticServiceInstanceListSupplier() {
			int port = Integer.parseInt(System.getProperty("nossl.port", "8080"));
			log.info("nossl.port = " + port);
			// secure=false 表示使用 HTTP 连接（无 TLS）
			return ServiceInstanceListSuppliers.from("nossl",
					new DefaultServiceInstance("nossl-1", "nossl", "localhost", port, false));
		}

	}

}
