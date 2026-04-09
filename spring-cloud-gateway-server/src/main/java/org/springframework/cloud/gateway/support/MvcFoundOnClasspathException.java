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

/**
 * 异常类，当检测到 Spring MVC 出现在类路径中时抛出。
 * <p>
 * Spring Cloud Gateway 基于 Spring WebFlux（响应式编程模型），与基于 Servlet 的 Spring MVC
 * 存在冲突。如果在类路径中同时存在 spring-boot-starter-web 和
 * spring-cloud-starter-gateway，会导致应用无法正常启动，此时抛出此异常。
 * </p>
 * <p>
 * 解决方式：
 * <ul>
 * <li>设置 {@code spring.main.web-application-type=reactive}</li>
 * <li>移除 spring-boot-starter-web 依赖</li>
 * </ul>
 * </p>
 *
 * @see MvcFoundOnClasspathFailureAnalyzer
 */
public class MvcFoundOnClasspathException extends RuntimeException {

}
