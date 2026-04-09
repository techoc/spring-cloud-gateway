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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全响应头的全局配置属性类。
 *
 * <p>
 * 此类提供了所有安全响应头的默认值，可以通过 Spring Boot 配置属性进行覆盖。 配置前缀:
 * {@code spring.cloud.gateway.filter.secure-headers}
 *
 * <p>
 * 配置示例： <pre>
 * spring:
 *   cloud:
 *     gateway:
 *       filter:
 *         secure-headers:
 *           xss-protection-header: "1; mode=block"
 *           strict-transport-security: "max-age=31536000; includeSubDomains"
 *           frame-options: "SAMEORIGIN"
 *           disable:
 *             - X-Xss-Protection  # 禁用特定的安全头
 * </pre>
 *
 * @author Spencer Gibb
 * @author Thirunavukkarasu Ravichandran
 * @see SecureHeadersGatewayFilterFactory
 */
@ConfigurationProperties("spring.cloud.gateway.filter.secure-headers")
public class SecureHeadersProperties {

	/**
	 * XSS 防护头的默认值。 启用浏览器 XSS 过滤器并设置为阻止模式。 默认值: "1 ; mode=block"
	 */
	public static final String X_XSS_PROTECTION_HEADER_DEFAULT = "1 ; mode=block";

	/**
	 * HTTP 严格传输安全(HSTS)头的默认值。 强制浏览器使用 HTTPS 连接，有效期约 1 年。 默认值: "max-age=631138519" (约 1
	 * 年，单位：秒)
	 */
	public static final String STRICT_TRANSPORT_SECURITY_HEADER_DEFAULT = "max-age=631138519";

	/**
	 * 帧选项头的默认值。 防止页面被嵌入到 iframe 中。 默认值: "DENY" - 完全禁止被嵌入
	 */
	public static final String X_FRAME_OPTIONS_HEADER_DEFAULT = "DENY";

	/**
	 * 内容类型选项头的默认值。 防止浏览器进行 MIME 类型嗅探。 默认值: "nosniff"
	 */
	public static final String X_CONTENT_TYPE_OPTIONS_HEADER_DEFAULT = "nosniff";

	/**
	 * Referrer 策略头的默认值。 控制浏览器发送 Referer 头的策略。 默认值: "no-referrer" - 不发送 Referer 头
	 */
	public static final String REFERRER_POLICY_HEADER_DEFAULT = "no-referrer";

	/**
	 * 内容安全策略(CSP)头的默认值。 限制页面可以加载的资源来源，防止 XSS 攻击。 默认值限制资源只能从同源和 HTTPS 加载
	 */
	public static final String CONTENT_SECURITY_POLICY_HEADER_DEFAULT = "default-src 'self' https:; font-src 'self' https: data:; img-src 'self' https: data:; object-src 'none'; script-src https:; style-src 'self' https: 'unsafe-inline'";

	/**
	 * 下载选项头的默认值。 防止在浏览器中直接打开下载文件。 默认值: "noopen"
	 */
	public static final String X_DOWNLOAD_OPTIONS_HEADER_DEFAULT = "noopen";

	/**
	 * 跨域策略头的默认值。 控制 Flash 和 PDF 的跨域行为。 默认值: "none" - 完全禁止跨域
	 */
	public static final String X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER_DEFAULT = "none";

	/** XSS 防护头值，默认为 "1 ; mode=block" */
	private String xssProtectionHeader = X_XSS_PROTECTION_HEADER_DEFAULT;

	/** HSTS 头值，默认为 "max-age=631138519" */
	private String strictTransportSecurity = STRICT_TRANSPORT_SECURITY_HEADER_DEFAULT;

	/** 帧选项头值，默认为 "DENY" */
	private String frameOptions = X_FRAME_OPTIONS_HEADER_DEFAULT;

	/** 内容类型选项头值，默认为 "nosniff" */
	private String contentTypeOptions = X_CONTENT_TYPE_OPTIONS_HEADER_DEFAULT;

	/** Referrer 策略头值，默认为 "no-referrer" */
	private String referrerPolicy = REFERRER_POLICY_HEADER_DEFAULT;

	/** 内容安全策略头值 */
	private String contentSecurityPolicy = CONTENT_SECURITY_POLICY_HEADER_DEFAULT;

	/** 下载选项头值，默认为 "noopen" */
	private String downloadOptions = X_DOWNLOAD_OPTIONS_HEADER_DEFAULT;

	/** 跨域策略头值，默认为 "none" */
	private String permittedCrossDomainPolicies = X_PERMITTED_CROSS_DOMAIN_POLICIES_HEADER_DEFAULT;

	/** 禁用的安全头列表，用于动态禁用特定的安全头 */
	private List<String> disable = new ArrayList<>();

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

	/**
	 * 获取禁用的安全头列表。
	 * @return 禁用的安全头名称列表
	 */
	public List<String> getDisable() {
		return disable;
	}

	/**
	 * 设置禁用的安全头列表。
	 * @param disable 要禁用的安全头名称列表
	 */
	public void setDisable(List<String> disable) {
		this.disable = disable;
	}

	/**
	 * 返回此配置对象的字符串表示形式。
	 * @return 包含所有配置值的字符串
	 */
	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("SecureHeadersProperties{");
		sb.append("xssProtectionHeader='").append(xssProtectionHeader).append('\'');
		sb.append(", strictTransportSecurity='").append(strictTransportSecurity).append('\'');
		sb.append(", frameOptions='").append(frameOptions).append('\'');
		sb.append(", contentTypeOptions='").append(contentTypeOptions).append('\'');
		sb.append(", referrerPolicy='").append(referrerPolicy).append('\'');
		sb.append(", contentSecurityPolicy='").append(contentSecurityPolicy).append('\'');
		sb.append(", downloadOptions='").append(downloadOptions).append('\'');
		sb.append(", permittedCrossDomainPolicies='").append(permittedCrossDomainPolicies).append('\'');
		sb.append(", disabled='").append(disable).append('\'');
		sb.append('}');
		return sb.toString();
	}

}
