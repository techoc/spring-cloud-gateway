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

package org.springframework.cloud.gateway.filter.factory;

import java.util.List;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * 安全响应头过滤器工厂，用于在 HTTP 响应中添加安全相关的响应头。
 *
 * <p>
 * 该过滤器添加的安全头参考了 OWASP 推荐的安全最佳实践，包括：
 * <ul>
 * <li>X-XSS-Protection - XSS 攻击防护</li>
 * <li>Strict-Transport-Security (HSTS) - 强制使用 HTTPS</li>
 * <li>X-Frame-Options - 防止点击劫持攻击</li>
 * <li>X-Content-Type-Options - 防止 MIME 类型嗅探</li>
 * <li>Referrer-Policy - 控制 Referer 头的发送策略</li>
 * <li>Content-Security-Policy (CSP) - 内容安全策略</li>
 * <li>X-Download-Options - 防止在浏览器中直接打开下载文件</li>
 * <li>X-Permitted-Cross-Domain-Policies - 控制 Flash 和 PDF 的跨域策略</li>
 * </ul>
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       default-filters:
 *         - SecureHeaders
 *       filter:
 *         secure-headers:
 *           disable: # 可以禁用特定的安全头
 *             - X-Xss-Protection
 * </pre>
 *
 * @author Spencer Gibb
 * @author Thirunavukkarasu Ravichandran
 * @see SecureHeadersProperties
 * @see <a href="https://blog.appcanary.com/2017/http-security-headers.html">HTTP Security
 * Headers</a>
 */
public class SecureHeadersGatewayFilterFactory
		extends AbstractGatewayFilterFactory<SecureHeadersGatewayFilterFactory.Config> {

	/**
	 * XSS 防护响应头名称，用于启用浏览器的 XSS 过滤器。 默认值: "1 ; mode=block"
	 */
	public static final String X_XSS_PROTECTION_HEADER = "X-Xss-Protection";

	/**
	 * HTTP 严格传输安全头名称，强制浏览器使用 HTTPS 连接。 默认值: "max-age=631138519"
	 */
	public static final String STRICT_TRANSPORT_SECURITY_HEADER = "Strict-Transport-Security";

	/**
	 * 帧选项头名称，防止页面被嵌入到 iframe 中（防止点击劫持）。 默认值: "DENY"
	 */
	public static final String X_FRAME_OPTIONS_HEADER = "X-Frame-Options";

	/**
	 * 内容类型选项头名称，防止浏览器进行 MIME 类型嗅探。 默认值: "nosniff"
	 */
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";

	/**
	 * Referrer 策略头名称，控制 Referer 头的发送方式。 默认值: "no-referrer"
	 */
	public static final String REFERRER_POLICY_HEADER = "Referrer-Policy";

	/**
	 * 内容安全策略头名称，限制页面可以加载的资源来源。 默认值: "default-src 'self' https:; font-src 'self' https:
	 * data:; ..."
	 */
	public static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

	/**
	 * 下载选项头名称，控制文件下载行为。 默认值: "noopen"
	 */
	public static final String X_DOWNLOAD_OPTIONS_HEADER = "X-Download-Options";

	/**
	 * 跨域策略头名称，控制 Adobe Flash 和 PDF 的跨域行为。 默认值: "none"
	 */
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER = "X-Permitted-Cross-Domain-Policies";

	/** 安全头属性配置对象 */
	private final SecureHeadersProperties properties;

	/**
	 * 构造函数，注入安全头属性配置。
	 * @param properties 安全头属性配置
	 */
	public SecureHeadersGatewayFilterFactory(SecureHeadersProperties properties) {
		super(Config.class);
		this.properties = properties;
	}

	/**
	 * 应用此过滤器，根据配置创建安全头过滤器。
	 *
	 * <p>
	 * 该过滤器在响应发送后添加安全头，确保所有安全头都被正确设置。
	 * @param originalConfig 用户提供的配置，可能包含自定义值
	 * @return 新的 GatewayFilter 实例
	 */
	@Override
	public GatewayFilter apply(Config originalConfig) {
		return new GatewayFilter() {
			/**
			 * 执行过滤逻辑，在响应发送后添加安全头。
			 * @param exchange 当前请求的 ServerWebExchange 对象
			 * @param chain 过滤器链
			 * @return 表示完成的可发布对象
			 */
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				HttpHeaders headers = exchange.getResponse().getHeaders();

				// 获取被禁用的安全头列表
				List<String> disabled = properties.getDisable();
				// 合并用户配置和默认配置
				Config config = originalConfig.withDefaults(properties);

				// 先执行过滤器链，然后在响应发送后添加安全头
				return chain.filter(exchange).then(Mono.fromRunnable(() -> {
					// XSS 防护头
					if (isEnabled(disabled, X_XSS_PROTECTION_HEADER)) {
						headers.addIfAbsent(X_XSS_PROTECTION_HEADER, config.getXssProtectionHeader());
					}

					// HSTS 头
					if (isEnabled(disabled, STRICT_TRANSPORT_SECURITY_HEADER)) {
						headers.addIfAbsent(STRICT_TRANSPORT_SECURITY_HEADER, config.getStrictTransportSecurity());
					}

					// 帧选项头
					if (isEnabled(disabled, X_FRAME_OPTIONS_HEADER)) {
						headers.addIfAbsent(X_FRAME_OPTIONS_HEADER, config.getFrameOptions());
					}

					// 内容类型选项头
					if (isEnabled(disabled, X_CONTENT_TYPE_OPTIONS_HEADER)) {
						headers.addIfAbsent(X_CONTENT_TYPE_OPTIONS_HEADER, config.getContentTypeOptions());
					}

					// Referrer 策略头
					if (isEnabled(disabled, REFERRER_POLICY_HEADER)) {
						headers.addIfAbsent(REFERRER_POLICY_HEADER, config.getReferrerPolicy());
					}

					// 内容安全策略头
					if (isEnabled(disabled, CONTENT_SECURITY_POLICY_HEADER)) {
						headers.addIfAbsent(CONTENT_SECURITY_POLICY_HEADER, config.getContentSecurityPolicy());
					}

					// 下载选项头
					if (isEnabled(disabled, X_DOWNLOAD_OPTIONS_HEADER)) {
						headers.addIfAbsent(X_DOWNLOAD_OPTIONS_HEADER, config.getDownloadOptions());
					}

					// 跨域策略头
					if (isEnabled(disabled, X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER)) {
						headers.addIfAbsent(X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER,
								config.getPermittedCrossDomainPolicies());
					}
				}));
			}

			/**
			 * 返回此过滤器的字符串表示形式。
			 * @return 过滤器的字符串描述
			 */
			@Override
			public String toString() {
				return filterToStringCreator(SecureHeadersGatewayFilterFactory.this).toString();
			}
		};
	}

	/**
	 * 检查指定的安全头是否启用。
	 * @param disabledHeaders 已禁用的安全头列表
	 * @param header 要检查的安全头名称
	 * @return 如果安全头已启用返回 true，否则返回 false
	 */
	private boolean isEnabled(List<String> disabledHeaders, String header) {
		return !disabledHeaders.contains(header.toLowerCase());
	}

	/**
	 * 过滤器配置类，用于存储用户配置的安全头值。
	 *
	 * <p>
	 * 配置参数：
	 * <ul>
	 * <li>xssProtectionHeader - XSS 防护头值</li>
	 * <li>strictTransportSecurity - HSTS 头值</li>
	 * <li>frameOptions - 帧选项头值</li>
	 * <li>contentTypeOptions - 内容类型选项头值</li>
	 * <li>referrerPolicy - Referrer 策略头值</li>
	 * <li>contentSecurityPolicy - 内容安全策略头值</li>
	 * <li>downloadOptions - 下载选项头值</li>
	 * <li>permittedCrossDomainPolicies - 跨域策略头值</li>
	 * </ul>
	 */
	public static class Config {

		/** XSS 防护头值 */
		private String xssProtectionHeader;

		/** HSTS 头值 */
		private String strictTransportSecurity;

		/** 帧选项头值 */
		private String frameOptions;

		/** 内容类型选项头值 */
		private String contentTypeOptions;

		/** Referrer 策略头值 */
		private String referrerPolicy;

		/** 内容安全策略头值 */
		private String contentSecurityPolicy;

		/** 下载选项头值 */
		private String downloadOptions;

		/** 跨域策略头值 */
		private String permittedCrossDomainPolicies;

		/**
		 * 使用全局属性值填充配置中的空字段。
		 * @param properties 全局属性配置
		 * @return 合并后的配置对象
		 */
		public Config withDefaults(SecureHeadersProperties properties) {
			Config config = new Config();
			config.setXssProtectionHeader(xssProtectionHeader);
			config.setStrictTransportSecurity(strictTransportSecurity);
			config.setFrameOptions(frameOptions);
			config.setContentTypeOptions(contentTypeOptions);
			config.setReferrerPolicy(referrerPolicy);
			config.setContentSecurityPolicy(contentSecurityPolicy);
			config.setDownloadOptions(downloadOptions);
			config.setPermittedCrossDomainPolicies(permittedCrossDomainPolicies);

			// 使用全局属性填充空字段
			if (config.xssProtectionHeader == null) {
				config.xssProtectionHeader = properties.getXssProtectionHeader();
			}

			if (config.strictTransportSecurity == null) {
				config.strictTransportSecurity = properties.getStrictTransportSecurity();
			}

			if (config.frameOptions == null) {
				config.frameOptions = properties.getFrameOptions();
			}

			if (config.contentTypeOptions == null) {
				config.contentTypeOptions = properties.getContentTypeOptions();
			}

			if (config.referrerPolicy == null) {
				config.referrerPolicy = properties.getReferrerPolicy();
			}

			if (config.contentSecurityPolicy == null) {
				config.contentSecurityPolicy = properties.getContentSecurityPolicy();
			}

			if (config.downloadOptions == null) {
				config.downloadOptions = properties.getDownloadOptions();
			}

			if (config.permittedCrossDomainPolicies == null) {
				config.permittedCrossDomainPolicies = properties.getPermittedCrossDomainPolicies();
			}
			return config;
		}

		// Getters and Setters
		public String getXssProtectionHeader() {
			return xssProtectionHeader;
		}

		public void setXssProtectionHeader(String xssProtectionHeader) {
			this.xssProtectionHeader = xssProtectionHeader;
		}

		public String getStrictTransportSecurity() {
			return strictTransportSecurity;
		}

		public void setStrictTransportSecurity(String strictTransportSecurity) {
			this.strictTransportSecurity = strictTransportSecurity;
		}

		public String getFrameOptions() {
			return frameOptions;
		}

		public void setFrameOptions(String frameOptions) {
			this.frameOptions = frameOptions;
		}

		public String getContentTypeOptions() {
			return contentTypeOptions;
		}

		public void setContentTypeOptions(String contentTypeOptions) {
			this.contentTypeOptions = contentTypeOptions;
		}

		public String getReferrerPolicy() {
			return referrerPolicy;
		}

		public void setReferrerPolicy(String referrerPolicy) {
			this.referrerPolicy = referrerPolicy;
		}

		public String getContentSecurityPolicy() {
			return contentSecurityPolicy;
		}

		public void setContentSecurityPolicy(String contentSecurityPolicy) {
			this.contentSecurityPolicy = contentSecurityPolicy;
		}

		public String getDownloadOptions() {
			return downloadOptions;
		}

		public void setDownloadOptions(String downloadOptions) {
			this.downloadOptions = downloadOptions;
		}

		public String getPermittedCrossDomainPolicies() {
			return permittedCrossDomainPolicies;
		}

		public void setPermittedCrossDomainPolicies(String permittedCrossDomainPolicies) {
			this.permittedCrossDomainPolicies = permittedCrossDomainPolicies;
		}

	}

}
