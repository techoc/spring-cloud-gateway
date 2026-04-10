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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.support.MvcFoundOnClasspathException;
import org.springframework.cloud.gateway.support.MvcFoundOnClasspathFailureAnalyzer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MVC 失败分析器集成测试类。
 * <p>
 * 用于验证当 Spring MVC 出现在类路径上时，Spring Cloud Gateway 能够正确检测
 * 并通过 {@link MvcFoundOnClasspathFailureAnalyzer} 给出友好的失败提示信息。
 * </p>
 *
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class) // 启用标准输出/错误输出的捕获扩展
public class MvcFailureAnalyzerApplicationTests {

	/**
	 * 测试：当 MVC 存在于类路径时，启动应用程序应抛出异常。
	 * <p>
	 * 验证根异常类型为 {@link MvcFoundOnClasspathException}，
	 * 并且日志输出中包含失败分析器定义的提示消息和处理建议。
	 * </p>
	 *
	 * @param output 捕获的标准输出内容
	 */
	@Test
	public void exceptionThrown(CapturedOutput output) {
		// 断言启动时应抛出异常，且根因为 MvcFoundOnClasspathException
		assertThatThrownBy(() -> new SpringApplication(MvcFailureAnalyzerApplication.class).run("--server.port=0"))
				.hasRootCauseInstanceOf(MvcFoundOnClasspathException.class);
		// 断言输出日志包含失败分析器的消息提示和解决建议
		assertThat(output).contains(MvcFoundOnClasspathFailureAnalyzer.MESSAGE,
				MvcFoundOnClasspathFailureAnalyzer.ACTION);
	}

	/**
	 * 测试：当 Spring Cloud Gateway 被禁用时，即使 MVC 存在于类路径也不应抛出异常。
	 * <p>
	 * 通过设置 {@code spring.cloud.gateway.enabled=false} 来禁用网关，
	 * 此时启动应该正常完成，不触发 MVC 检测逻辑。
	 * </p>
	 *
	 * @param output 捕获的标准输出内容
	 */
	@Test
	public void exceptionNotThrownWhenDisabled(CapturedOutput output) {
		// 禁用 Gateway，断言启动不抛异常
		assertThatCode(() -> new SpringApplication(MvcFailureAnalyzerApplication.class)
				.run("--spring.cloud.gateway.enabled=false", "--server.port=0")).doesNotThrowAnyException();
		// 断言输出日志中不含失败分析器的消息提示
		assertThat(output).doesNotContain(MvcFoundOnClasspathFailureAnalyzer.MESSAGE,
				MvcFoundOnClasspathFailureAnalyzer.ACTION);
	}

	/**
	 * 测试：当显式指定 Web 应用类型为 Reactive 时，应用应正常启动并响应请求。
	 * <p>
	 * 通过设置 {@code spring.main.web-application-type=reactive} 强制使用响应式模式，
	 * 绕过 MVC 冲突检测，并验证路由 {@code /myprefix/hello} 正确返回 "Hello"。
	 * </p>
	 *
	 * @param output 捕获的标准输出内容
	 */
	@Test
	public void exceptionNotThrownWhenReactiveTypeSet(CapturedOutput output) {
		assertThatCode(() -> {
			// 以 Reactive 模式启动应用，开启 debug 日志
			ConfigurableApplicationContext context = new SpringApplication(MvcFailureAnalyzerApplication.class)
					.run("--spring.main.web-application-type=reactive", "--server.port=0", "--debug=true");
			// 获取随机分配的本地端口
			Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
			// 构建 WebTestClient 并访问网关路由
			WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
			// 验证 /myprefix/hello 路由返回状态 200 且响应体为 "Hello"
			client.get().uri("/myprefix/hello").exchange().expectStatus().isOk().expectBody(String.class)
					.isEqualTo("Hello");
			// 关闭应用上下文，释放资源
			context.close();
		}).doesNotThrowAnyException();
		// 确认没有触发 MVC 失败分析器的告警输出
		assertThat(output).doesNotContain(MvcFoundOnClasspathFailureAnalyzer.MESSAGE,
				MvcFoundOnClasspathFailureAnalyzer.ACTION);

	}

}
