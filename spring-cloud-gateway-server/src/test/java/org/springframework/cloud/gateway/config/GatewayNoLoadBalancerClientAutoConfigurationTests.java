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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.test.PermitAllSecurityConfiguration;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.SocketUtils;

/**
 * GatewayNoLoadBalancerClientAutoConfigurationTests - 无负载均衡器客户端自动配置测试类
 *
 * 本测试类验证当classpath中不存在spring-cloud-loadbalancer时： - 使用lb://协议的路由会返回5xx服务器错误 -
 * 这是预期的行为，因为缺少负载均衡器支持
 *
 * @author test
 */
@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({ "spring-cloud-loadbalancer-*.jar" })
public class GatewayNoLoadBalancerClientAutoConfigurationTests {

	/** 服务器端口 */
	private static int port;

	/**
	 * init - 初始化测试环境
	 *
	 * 在测试类初始化时分配一个可用的TCP端口
	 */
	@BeforeClass
	public static void init() {
		port = SocketUtils.findAvailableTcpPort();
	}

	/**
	 * noLoadBalancerClientReportsError - 测试无负载均衡器时报告错误
	 *
	 * 验证当classpath中没有spring-cloud-loadbalancer时， 使用lb://协议前缀的路由请求会返回5xx服务器错误
	 * 这是因为ReactiveLoadBalancerClientFilter无法正常工作
	 */
	@Test
	public void noLoadBalancerClientReportsError() {
		try (ConfigurableApplicationContext context = new SpringApplication(Config.class).run("--server.port=" + port,
				"--spring.jmx.enabled=false")) {
			WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
			client.get().header(HttpHeaders.HOST, "www.lbfail.org").exchange().expectStatus().is5xxServerError();
		}
	}

	/**
	 * Config - 测试配置类
	 *
	 * 提供使用lb://协议的测试路由配置
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import(PermitAllSecurityConfiguration.class)
	public static class Config {

		/**
		 * routeLocator - 测试用路由定位器
		 *
		 * 配置一个使用lb://协议的目标为"fail"服务的路由
		 * @param builder 路由定位器构建器
		 * @return RouteLocator实例
		 */
		@Bean
		public RouteLocator routeLocator(RouteLocatorBuilder builder) {
			return builder.routes().route("lb_fail", r -> r.host("**.lbfail.org").uri("lb://fail")).build();
		}

	}

}
