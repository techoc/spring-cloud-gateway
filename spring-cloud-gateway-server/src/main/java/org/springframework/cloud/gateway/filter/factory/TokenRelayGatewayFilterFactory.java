/*
 * Copyright 2002-2018 the original author or authors.
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

import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.server.ServerWebExchange;

/**
 * Token 中继过滤器工厂。
 * <p>
 * 该过滤器将当前认证用户的 OAuth2 访问令牌中继到下游服务。 当网关作为 OAuth2 客户端时，使用此过滤器可以将用户的认证信息传递给需要认证的后端服务。
 * <p>
 * 使用条件：
 * <ul>
 * <li>请求必须经过 OAuth2 认证（包含 {@link OAuth2AuthenticationToken}）</li>
 * <li>Spring Security OAuth2 Client 依赖已引入</li>
 * </ul>
 * <p>
 * 配置示例（YAML）： <pre>
 * filters:
 *   - TokenRelay
 * </pre>
 *
 * @author Joe Grandja
 */
public class TokenRelayGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

	/** Reactive OAuth2 授权客户端管理器 */
	private final ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManagerProvider;

	/**
	 * 构造方法。
	 * @param clientManagerProvider Reactive OAuth2 授权客户端管理器
	 */
	public TokenRelayGatewayFilterFactory(ObjectProvider<ReactiveOAuth2AuthorizedClientManager> clientManagerProvider) {
		super(Object.class);
		this.clientManagerProvider = clientManagerProvider;
	}

	/**
	 * 创建 Token 中继过滤器。
	 * @return 网关过滤器实例
	 */
	public GatewayFilter apply() {
		return apply((Object) null);
	}

	/**
	 * 创建 Token 中继过滤器。
	 * @param config 配置对象（此过滤器无需配置）
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Object config) {
		return (exchange, chain) -> exchange.getPrincipal()
				// .log("token-relay-filter")
				// 过滤出 OAuth2 认证
				.filter(principal -> principal instanceof OAuth2AuthenticationToken)
				.cast(OAuth2AuthenticationToken.class)
				.flatMap(authentication -> authorizedClient(exchange, authentication))
				.map(OAuth2AuthorizedClient::getAccessToken).map(token -> withBearerAuth(exchange, token))
				// TODO: 若无令牌时的行为可调整
				.defaultIfEmpty(exchange).flatMap(chain::filter);
	}

	/**
	 * 获取授权客户端。
	 * @param exchange 当前交换对象
	 * @param oauth2Authentication OAuth2 认证信息
	 * @return 已授权的客户端
	 */
	private Mono<OAuth2AuthorizedClient> authorizedClient(ServerWebExchange exchange,
			OAuth2AuthenticationToken oauth2Authentication) {
		String clientRegistrationId = oauth2Authentication.getAuthorizedClientRegistrationId();
		OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(clientRegistrationId)
				.principal(oauth2Authentication).build();
		ReactiveOAuth2AuthorizedClientManager clientManager = clientManagerProvider.getIfAvailable();
		if (clientManager == null) {
			return Mono.error(new IllegalStateException(
					"No ReactiveOAuth2AuthorizedClientManager bean was found. Did you include the "
							+ "org.springframework.boot:spring-boot-starter-oauth2-client dependency?"));
		}
		// TODO: 是否使用 Mono.defer() 包装上述请求？
		return clientManager.authorize(request);
	}

	/**
	 * 将 Bearer Token 添加到请求头。
	 * @param exchange 当前交换对象
	 * @param accessToken OAuth2 访问令牌
	 * @return 添加了 Bearer Token 的新交换对象
	 */
	private ServerWebExchange withBearerAuth(ServerWebExchange exchange, OAuth2AccessToken accessToken) {
		return exchange.mutate().request(r -> r.headers(headers -> headers.setBearerAuth(accessToken.getTokenValue())))
				.build();
	}

}
