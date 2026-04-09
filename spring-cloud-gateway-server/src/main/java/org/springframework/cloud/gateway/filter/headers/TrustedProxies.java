/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 受信任代理接口，用于验证代理地址是否可信。
 *
 * <p>
 * 此接口用于在 ForwardedHeadersFilter 和 XForwardedHeadersFilter 中验证远程地址 和 X-Forwarded
 * 头信息，防止恶意客户端伪造代理链信息。
 * </p>
 *
 * <p>
 * 配置属性：{@code spring.cloud.gateway.trusted-proxies}
 * </p>
 *
 * <p>
 * 使用示例：
 * </p>
 * <pre>{@code
 * // 使用正则表达式匹配受信任代理
 * TrustedProxies trustedProxies = TrustedProxies.from("192\\.168\\..*");
 * boolean isTrusted = trustedProxies.isTrusted("192.168.1.1"); // true
 * }</pre>
 *
 * @author Spencer Gibb
 * @author Ryan Baxter
 */
@FunctionalInterface
public interface TrustedProxies {

	/**
	 * 配置属性名称。
	 */
	String PROPERTY = GatewayProperties.PREFIX + ".trusted-proxies";

	/**
	 * 检查主机是否受信任。
	 * @param host 主机地址（IP 或主机名）
	 * @return 如果受信任返回 true，否则返回 false
	 */
	boolean isTrusted(String host);

	/**
	 * 从正则表达式字符串创建 TrustedProxies 实例。
	 *
	 * <p>
	 * 使用正则表达式匹配主机地址，匹配成功则认为该主机受信任。
	 * </p>
	 * @param trustedProxies 匹配受信任代理的正则表达式
	 * @return TrustedProxies 实例
	 * @throws IllegalArgumentException 如果 trustedProxies 为空
	 */
	static TrustedProxies from(@NonNull String trustedProxies) {
		Assert.hasText(trustedProxies, "trustedProxies must not be empty");
		Pattern pattern = Pattern.compile(trustedProxies);
		return value -> pattern.matcher(value).matches();
	}

	/**
	 * Forwarded 头受信任代理条件类。
	 *
	 * <p>
	 * 用于条件化地创建 ForwardedHeadersFilter bean。 需要同时满足以下条件：
	 * </p>
	 * <ul>
	 * <li>spring.cloud.gateway.forwarded.enabled 为 true（或不存在，默认为 true）</li>
	 * <li>spring.cloud.gateway.trusted-proxies 属性已设置且不为空</li>
	 * </ul>
	 */
	class ForwardedTrustedProxiesCondition extends AllNestedConditions {

		/**
		 * 构造函数，指定配置阶段为 REGISTER_BEAN。
		 */
		public ForwardedTrustedProxiesCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		/**
		 * 检查 Forwarded 功能是否启用。
		 */
		@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".forwarded.enabled", matchIfMissing = true)
		static class OnPropertyEnabled {

		}

		/**
		 * 检查 trusted-proxies 属性是否存在。
		 */
		@ConditionalOnPropertyExists
		static class OnTrustedProxiesNotEmpty {

		}

	}

	/**
	 * X-Forwarded 头受信任代理条件类。
	 *
	 * <p>
	 * 用于条件化地创建 XForwardedHeadersFilter bean。 需要同时满足以下条件：
	 * </p>
	 * <ul>
	 * <li>spring.cloud.gateway.x-forwarded.enabled 为 true（或不存在，默认为 true）</li>
	 * <li>spring.cloud.gateway.trusted-proxies 属性已设置且不为空</li>
	 * </ul>
	 */
	class XForwardedTrustedProxiesCondition extends AllNestedConditions {

		/**
		 * 构造函数，指定配置阶段为 REGISTER_BEAN。
		 */
		public XForwardedTrustedProxiesCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		/**
		 * 检查 X-Forwarded 功能是否启用。
		 */
		@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".x-forwarded.enabled", matchIfMissing = true)
		static class OnPropertyEnabled {

		}

		/**
		 * 检查 trusted-proxies 属性是否存在。
		 */
		@ConditionalOnPropertyExists
		static class OnTrustedProxiesNotEmpty {

		}

	}

	/**
	 * 属性存在条件类。
	 *
	 * <p>
	 * 检查 {@link #PROPERTY} 配置属性是否存在且不为空。
	 * </p>
	 */
	class OnPropertyExistsCondition extends SpringBootCondition {

		/**
		 * 获取条件匹配结果。
		 * @param context 条件上下文
		 * @param metadata 注解类型元数据
		 * @return 条件结果
		 */
		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			try {
				String value = context.getEnvironment().getProperty(PROPERTY);
				if (!StringUtils.hasText(value)) {
					return ConditionOutcome.noMatch(PROPERTY + " property is not set or is empty.");
				}
				return ConditionOutcome.match(PROPERTY + " property is not empty.");
			}
			catch (NoSuchElementException e) {
				return ConditionOutcome.noMatch("Missing required property 'value' of @ConditionalOnPropertyExists");
			}
		}

	}

	/**
	 * 条件注解，用于检查 {@link #PROPERTY} 属性是否存在。
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Documented
	@Conditional(OnPropertyExistsCondition.class)
	@interface ConditionalOnPropertyExists {

	}

}
