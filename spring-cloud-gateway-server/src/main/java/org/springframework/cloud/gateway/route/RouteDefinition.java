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

package org.springframework.cloud.gateway.route;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.validation.annotation.Validated;

import static org.springframework.util.StringUtils.tokenizeToStringArray;

/**
 * 路由定义类，用于描述一条路由规则的配置信息。
 * <p>
 * {@code RouteDefinition} 是路由的"配置模型"，对应配置文件或动态存储中的路由配置项。 与运行时的 {@link Route}
 * 不同，它以纯数据形式存在，包含：
 * <ul>
 * <li>路由 ID（id）—— 路由的唯一标识，可不填由系统自动生成</li>
 * <li>断言定义列表（predicates）—— 决定请求是否匹配该路由，不可为空</li>
 * <li>过滤器定义列表（filters）—— 对请求/响应进行处理的过滤器配置</li>
 * <li>目标 URI（uri）—— 匹配后的转发目标，不可为 null</li>
 * <li>元数据（metadata）—— 附加的扩展信息</li>
 * <li>执行顺序（order）—— 多路由匹配时按此值升序优先</li>
 * </ul>
 * <p>
 * 支持通过文本格式（{@code name=uri,predicate1,predicate2,...}）构造，便于配置文件解析。
 *
 * @author Spencer Gibb
 */
@Validated
public class RouteDefinition {

	/** 路由唯一标识，可为空（由 {@link CompositeRouteDefinitionLocator} 自动生成 UUID） */
	private String id;

	/**
	 * 断言定义列表，至少需要一条断言。 多个断言之间为逻辑与（AND）关系，全部满足才路由到目标。
	 */
	@NotEmpty
	@Valid
	private List<PredicateDefinition> predicates = new ArrayList<>();

	/** 过滤器定义列表，对匹配该路由的请求/响应进行处理 */
	@Valid
	private List<FilterDefinition> filters = new ArrayList<>();

	/**
	 * 路由转发的目标 URI，不可为 null。 支持普通 URI（如 {@code http://example.com}）和负载均衡 URI（如
	 * {@code lb://service-name}）。
	 */
	@NotNull
	private URI uri;

	/** 路由的扩展元数据，可存储自定义的键值对信息 */
	private Map<String, Object> metadata = new HashMap<>();

	/** 路由的执行顺序，值越小优先级越高，默认为 0 */
	private int order = 0;

	/**
	 * 默认无参构造方法。
	 */
	public RouteDefinition() {
	}

	/**
	 * 通过文本格式解析构造路由定义。
	 * <p>
	 * 文本格式：{@code id=uri,predicate1,predicate2,...} <br>
	 * 示例：{@code myRoute=http://example.com,Path=/api/**,Method=GET}
	 * <p>
	 * 注意：
	 * <ul>
	 * <li>文本必须包含 {@code =} 分隔符</li>
	 * <li>逗号分隔的第一个参数为目标 URI，其余为断言定义</li>
	 * </ul>
	 * @param text 路由定义文本
	 * @throws ValidationException 文本格式不符合规范时抛出
	 */
	public RouteDefinition(String text) {
		int eqIdx = text.indexOf('=');
		if (eqIdx <= 0) {
			throw new ValidationException(
					"Unable to parse RouteDefinition text '" + text + "'" + ", must be of the form name=value");
		}

		setId(text.substring(0, eqIdx));

		String[] args = tokenizeToStringArray(text.substring(eqIdx + 1), ",");

		setUri(URI.create(args[0]));

		for (int i = 1; i < args.length; i++) {
			this.predicates.add(new PredicateDefinition(args[i]));
		}
	}

	/**
	 * 获取路由唯一标识。
	 * @return 路由 ID，可能为 null（未设置时由系统生成）
	 */
	public String getId() {
		return id;
	}

	/**
	 * 设置路由唯一标识。
	 * @param id 路由 ID
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * 获取路由的断言定义列表。
	 * @return 断言定义列表，不可为空
	 */
	public List<PredicateDefinition> getPredicates() {
		return predicates;
	}

	/**
	 * 设置路由的断言定义列表。
	 * @param predicates 断言定义列表
	 */
	public void setPredicates(List<PredicateDefinition> predicates) {
		this.predicates = predicates;
	}

	/**
	 * 获取路由的过滤器定义列表。
	 * @return 过滤器定义列表
	 */
	public List<FilterDefinition> getFilters() {
		return filters;
	}

	/**
	 * 设置路由的过滤器定义列表。
	 * @param filters 过滤器定义列表
	 */
	public void setFilters(List<FilterDefinition> filters) {
		this.filters = filters;
	}

	/**
	 * 获取路由转发的目标 URI。
	 * @return 目标 URI，不可为 null
	 */
	public URI getUri() {
		return uri;
	}

	/**
	 * 设置路由转发的目标 URI。
	 * @param uri 目标 URI
	 */
	public void setUri(URI uri) {
		this.uri = uri;
	}

	/**
	 * 获取路由的执行顺序。
	 * @return 排序值，越小优先级越高
	 */
	public int getOrder() {
		return order;
	}

	/**
	 * 设置路由的执行顺序。
	 * @param order 排序值
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	/**
	 * 获取路由的扩展元数据。
	 * @return 元数据 Map
	 */
	public Map<String, Object> getMetadata() {
		return metadata;
	}

	/**
	 * 设置路由的扩展元数据。
	 * @param metadata 元数据 Map
	 */
	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RouteDefinition that = (RouteDefinition) o;
		return this.order == that.order && Objects.equals(this.id, that.id)
				&& Objects.equals(this.predicates, that.predicates) && Objects.equals(this.filters, that.filters)
				&& Objects.equals(this.uri, that.uri) && Objects.equals(this.metadata, that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.predicates, this.filters, this.uri, this.metadata, this.order);
	}

	@Override
	public String toString() {
		return "RouteDefinition{" + "id='" + id + '\'' + ", predicates=" + predicates + ", filters=" + filters
				+ ", uri=" + uri + ", order=" + order + ", metadata=" + metadata + '}';
	}

}
