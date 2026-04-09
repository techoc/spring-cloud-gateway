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

package org.springframework.cloud.gateway.support;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Spring Boot 故障分析器，用于处理 {@link MvcFoundOnClasspathException} 异常。
 * <p>
 * 当应用启动过程中检测到 Spring MVC 与 Spring Cloud Gateway 共存时， 此分析器会生成友好的错误报告，帮助开发者快速定位并解决问题。
 * </p>
 * <p>
 * 分析结果包含以下信息：
 * <ul>
 * <li>问题描述：Spring MVC 发现于类路径中，与 Spring Cloud Gateway 不兼容</li>
 * <li>解决方案：设置 web-application-type 为 reactive 或移除 spring-boot-starter-web 依赖</li>
 * </ul>
 * </p>
 *
 * @see MvcFoundOnClasspathException
 */
public class MvcFoundOnClasspathFailureAnalyzer extends AbstractFailureAnalyzer<MvcFoundOnClasspathException> {

	/**
	 * 故障描述消息，说明问题的原因。
	 */
	public static final String MESSAGE = "Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway.";

	/**
	 * 建议的操作步骤，帮助用户解决问题。
	 */
	public static final String ACTION = "Please set spring.main.web-application-type=reactive or remove spring-boot-starter-web dependency.";

	/**
	 * 分析故障原因并生成故障报告。
	 * <p>
	 * 该方法将创建包含问题描述和解决方案的 {@link FailureAnalysis} 对象， 由 Spring Boot 的故障报告机制展示给用户。
	 * </p>
	 * @param rootFailure 根异常（当前为 MvcFoundOnClasspathException）
	 * @param cause 具体的异常原因
	 * @return 包含问题描述和解决方案的故障分析结果
	 */
	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, MvcFoundOnClasspathException cause) {
		return new FailureAnalysis(MESSAGE, ACTION, cause);
	}

}
