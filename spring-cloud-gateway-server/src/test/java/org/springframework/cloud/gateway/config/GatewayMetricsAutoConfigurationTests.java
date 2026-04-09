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

import java.util.List;

import io.micrometer.core.instrument.Tags;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.cloud.gateway.route.RouteDefinitionMetrics;
import org.springframework.cloud.gateway.support.tagsprovider.GatewayTagsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayMetricsAutoConfigurationTests - Gateway指标自动配置测试类
 *
 * 本测试类验证GatewayMetricsAutoConfiguration的指标相关配置功能： -
 * GatewayMetricsFilter和RouteDefinitionMetrics的自动配置 - 自定义TagsProvider的集成 - 指标前缀的自定义配置 -
 * 通过属性禁用指标功能
 *
 * @author Ingyu Hwang
 * @author test
 */
@RunWith(Enclosed.class)
public class GatewayMetricsAutoConfigurationTests {

	/**
	 * EnabledByDefault - 测试指标Bean默认启用
	 *
	 * 验证在默认情况下，Gateway指标相关的Bean会被正确创建
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class)
	public static class EnabledByDefault {

		/** Gateway指标过滤器 */
		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		/** 路由定义指标 */
		@Autowired(required = false)
		private RouteDefinitionMetrics routeDefinitionMetrics;

		/** Gateway标签提供者列表 */
		@Autowired(required = false)
		private List<GatewayTagsProvider> tagsProviders;

		/**
		 * gatewayMetricsBeansExists - 验证Gateway指标Bean存在
		 *
		 * 确认GatewayMetricsFilter存在且前缀为"spring.cloud.gateway"
		 * 确认至少有一个GatewayTagsProvider被注册
		 */
		@Test
		public void gatewayMetricsBeansExists() {
			assertThat(filter).isNotNull();
			assertThat(filter.getMetricsPrefix()).isEqualTo("spring.cloud.gateway");
			assertThat(tagsProviders).isNotEmpty();
		}

		/**
		 * routeDefinitionMetricsBeanExists - 验证路由定义指标Bean存在
		 *
		 * 确认RouteDefinitionMetrics存在且前缀为"spring.cloud.gateway"
		 */
		@Test
		public void routeDefinitionMetricsBeanExists() {
			assertThat(routeDefinitionMetrics).isNotNull();
			assertThat(routeDefinitionMetrics.getMetricsPrefix()).isEqualTo("spring.cloud.gateway");
		}

	}

	/**
	 * DisabledByProperty - 测试通过属性禁用指标
	 *
	 * 验证当设置spring.cloud.gateway.metrics.enabled=false时 指标相关的Bean不会被创建
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = Config.class, properties = "spring.cloud.gateway.metrics.enabled=false")
	public static class DisabledByProperty {

		/** Gateway指标过滤器 */
		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		/** 路由定义指标 */
		@Autowired(required = false)
		private RouteDefinitionMetrics routeDefinitionMetrics;

		/**
		 * gatewayMetricsBeanMissing - 验证Gateway指标Bean缺失
		 *
		 * 确认GatewayMetricsFilter为null
		 */
		@Test
		public void gatewayMetricsBeanMissing() {
			assertThat(filter).isNull();
		}

		/**
		 * routeDefinitionMetricsBeanMissing - 验证路由定义指标Bean缺失
		 *
		 * 确认RouteDefinitionMetrics为null
		 */
		@Test
		public void routeDefinitionMetricsBeanMissing() {
			assertThat(routeDefinitionMetrics).isNull();
		}

	}

	/**
	 * AddCustomTagsProvider - 测试添加自定义TagsProvider
	 *
	 * 验证可以注册自定义的GatewayTagsProvider 测试自定义指标前缀配置
	 */
	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = CustomTagsProviderConfig.class,
			properties = "spring.cloud.gateway.metrics.prefix=myprefix.")
	public static class AddCustomTagsProvider {

		/** Gateway指标过滤器 */
		@Autowired(required = false)
		private GatewayMetricsFilter filter;

		/** 路由定义指标 */
		@Autowired(required = false)
		private RouteDefinitionMetrics routeDefinitionMetrics;

		/** Gateway标签提供者列表 */
		@Autowired(required = false)
		private List<GatewayTagsProvider> tagsProviders;

		/**
		 * gatewayMetricsBeansExists - 验证Gateway指标Bean存在且使用自定义前缀
		 *
		 * 确认GatewayMetricsFilter存在且前缀为"myprefix" 确认自定义的EmptyTagsProvider被注册
		 */
		@Test
		public void gatewayMetricsBeansExists() {
			assertThat(filter).isNotNull();
			assertThat(filter.getMetricsPrefix()).isEqualTo("myprefix");
			assertThat(tagsProviders).extracting("class").contains(CustomTagsProviderConfig.EmptyTagsProvider.class);
		}

		/**
		 * routeDefinitionMetricsBeanExists - 验证路由定义指标Bean存在且使用自定义前缀
		 *
		 * 确认RouteDefinitionMetrics存在且前缀为"myprefix"
		 */
		@Test
		public void routeDefinitionMetricsBeanExists() {
			assertThat(routeDefinitionMetrics).isNotNull();
			assertThat(routeDefinitionMetrics.getMetricsPrefix()).isEqualTo("myprefix");
		}

	}

	/**
	 * Config - 基础测试配置类
	 *
	 * 提供最小化的Spring Boot配置用于测试
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class Config {

	}

	/**
	 * CustomTagsProviderConfig - 自定义TagsProvider配置类
	 *
	 * 提供自定义的GatewayTagsProvider Bean用于测试
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class CustomTagsProviderConfig {

		/**
		 * emptyTagsProvider - 自定义空标签提供者
		 * @return EmptyTagsProvider实例
		 */
		@Bean
		public GatewayTagsProvider emptyTagsProvider() {
			return new EmptyTagsProvider();
		}

		/**
		 * EmptyTagsProvider - 空标签提供者实现
		 *
		 * 实现GatewayTagsProvider接口，返回空的标签列表
		 */
		protected static class EmptyTagsProvider implements GatewayTagsProvider {

			/**
			 * apply - 应用标签提供者逻辑
			 * @param exchange ServerWebExchange实例
			 * @return 空的标签集合
			 */
			@Override
			public Tags apply(ServerWebExchange exchange) {
				return Tags.empty();
			}

		}

	}

}
