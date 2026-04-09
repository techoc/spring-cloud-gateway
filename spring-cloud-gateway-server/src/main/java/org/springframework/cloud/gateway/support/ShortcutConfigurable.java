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

package org.springframework.cloud.gateway.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.env.Environment;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ConstructorResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.OperatorOverloader;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypeLocator;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 快捷配置接口，定义配置属性绑定的快捷方式支持。
 * <p>
 * 该接口扩展了配置能力，支持 YAML 中的简短配置语法。 例如，原本需要： <pre>
 * filters:
 *   - AddRequestHeader=name=value
 * </pre> 使用快捷语法后可以简化为： <pre>
 * filters:
 *   - AddRequestHeader=value
 * </pre>
 * </p>
 * <p>
 * 接口提供了配置属性规范化的抽象方法，以及 SpEL 表达式解析的支持。
 * </p>
 *
 * @author Spencer Gibb
 * @see ConfigurationService
 */
public interface ShortcutConfigurable {

	/**
	 * 规范化键名，处理快捷配置中的自动生成键。
	 * <p>
	 * 在快捷配置中，如果用户未指定键名，系统会自动生成（如 "_genkey_0"）。 此方法将这些自动生成的键替换为实际的字段名。
	 * </p>
	 * @param key 原始键名
	 * @param entryIdx 键值对的索引位置
	 * @param argHints 包含字段顺序提示的可配置对象
	 * @param args 完整的参数字典
	 * @return 规范化后的键名
	 */
	static String normalizeKey(String key, int entryIdx, ShortcutConfigurable argHints, Map<String, String> args) {
		// 如果键以 "_genkey_" 开头，且有字段顺序提示，则替换为实际字段名
		if (key.startsWith(NameUtils.GENERATED_NAME_PREFIX) && !argHints.shortcutFieldOrder().isEmpty()
				&& entryIdx < args.size() && entryIdx < argHints.shortcutFieldOrder().size()) {
			key = argHints.shortcutFieldOrder().get(entryIdx);
		}
		return key;
	}

	/**
	 * 解析配置值，支持 SpEL 表达式。
	 * <p>
	 * 如果值以 "#{" 开头且以 "}" 结尾，则视为 SpEL 表达式进行解析； 否则直接返回原值。
	 * </p>
	 * @param parser SpEL 表达式解析器
	 * @param beanFactory Bean 工厂，用于 SpEL 上下文中解析 Bean 引用
	 * @param entryValue 原始配置值
	 * @return 解析后的值
	 */
	static Object getValue(SpelExpressionParser parser, BeanFactory beanFactory, String entryValue) {
		Object value;
		String rawValue = entryValue;
		if (rawValue != null) {
			rawValue = rawValue.trim();
		}
		if (rawValue != null && rawValue.startsWith("#{") && entryValue.endsWith("}")) {
			// 作为 SpEL 表达式解析
			GatewayEvaluationContext context = new GatewayEvaluationContext(beanFactory);
			Expression expression = parser.parseExpression(entryValue, new TemplateParserContext());
			value = expression.getValue(context);
		}
		else {
			value = entryValue;
		}
		return value;
	}

	/**
	 * 返回快捷配置的规范化类型。
	 * <p>
	 * 默认返回 DEFAULT 类型，子类可覆盖以支持不同的快捷配置格式。
	 * </p>
	 * @return 快捷配置类型
	 */
	default ShortcutType shortcutType() {
		return ShortcutType.DEFAULT;
	}

	/**
	 * 返回字段顺序提示列表。
	 * <p>
	 * 用于快捷配置中，将无键名的值按顺序映射到对应字段。 例如：{@code - AddRequestHeader=value} 等同于
	 * {@code - AddRequestHeader=name=value} 当 shortcutFieldOrder 返回 ["name"] 时。
	 * </p>
	 * @return 字段名列表
	 */
	default List<String> shortcutFieldOrder() {
		return Collections.emptyList();
	}

	/**
	 * 返回配置属性的前缀。
	 * <p>
	 * 用于配置绑定时确定属性路径的前缀部分。
	 * </p>
	 * @return 属性前缀，默认为空字符串
	 */
	default String shortcutFieldPrefix() {
		return "";
	}

	/**
	 * 快捷配置规范化类型枚举。
	 * <p>
	 * 定义了不同的快捷配置格式：
	 * <ul>
	 * <li>DEFAULT - 标准键值对格式</li>
	 * <li>GATHER_LIST - 收集所有值到列表</li>
	 * <li>GATHER_LIST_TAIL_FLAG - 收集值到列表，最后一个值作为布尔标志</li>
	 * </ul>
	 */
	enum ShortcutType {

		/**
		 * 默认格式：标准键值对映射。
		 * <p>
		 * 将配置属性直接映射到对象的字段。
		 * </p>
		 */
		DEFAULT {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
					SpelExpressionParser parser, BeanFactory beanFactory) {
				Map<String, Object> map = new HashMap<>();
				int entryIdx = 0;
				for (Map.Entry<String, String> entry : args.entrySet()) {
					String key = normalizeKey(entry.getKey(), entryIdx, shortcutConf, args);
					Object value = getValue(parser, beanFactory, entry.getValue());

					map.put(key, value);
					entryIdx++;
				}
				return map;
			}
		},

		/**
		 * 收集列表格式：将所有值收集到一个列表字段。
		 * <p>
		 * 适用于一个字段接收多个值的场景。
		 * </p>
		 */
		GATHER_LIST {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
					SpelExpressionParser parser, BeanFactory beanFactory) {
				Map<String, Object> map = new HashMap<>();
				// 字段顺序必须只有一个元素
				List<String> fieldOrder = shortcutConf.shortcutFieldOrder();
				Assert.isTrue(fieldOrder != null && fieldOrder.size() == 1,
						"Shortcut Configuration Type GATHER_LIST must have shortcutFieldOrder of size 1");
				String fieldName = fieldOrder.get(0);
				map.put(fieldName, args.values().stream().map(value -> getValue(parser, beanFactory, value))
						.collect(Collectors.toList()));
				return map;
			}
		},

		/**
		 * 收集列表+尾部标志格式：除最后一个值外的所有值收集到列表，最后一个值作为布尔标志。
		 * <p>
		 * 适用于需要一个布尔标志来控制行为的场景。
		 * </p>
		 */
		// list is all elements except last which is a boolean flag
		GATHER_LIST_TAIL_FLAG {
			@Override
			public Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
					SpelExpressionParser parser, BeanFactory beanFactory) {
				Map<String, Object> map = new HashMap<>();
				// 字段顺序必须有两个元素：列表字段名和标志字段名
				List<String> fieldOrder = shortcutConf.shortcutFieldOrder();
				Assert.isTrue(fieldOrder != null && fieldOrder.size() == 2,
						"Shortcut Configuration Type GATHER_LIST_HEAD must have shortcutFieldOrder of size 2");
				List<String> values = new ArrayList<>(args.values());
				if (!values.isEmpty()) {
					// 如果最后一个值是 true 或 false，则作为标志处理
					int lastIdx = values.size() - 1;
					String lastValue = values.get(lastIdx);
					if (lastValue.equalsIgnoreCase("true") || lastValue.equalsIgnoreCase("false")) {
						values = values.subList(0, lastIdx);
						map.put(fieldOrder.get(1), getValue(parser, beanFactory, lastValue));
					}
				}
				String fieldName = fieldOrder.get(0);
				map.put(fieldName, values.stream().map(value -> getValue(parser, beanFactory, value))
						.collect(Collectors.toList()));
				return map;
			}
		};

		/**
		 * 规范化配置属性。
		 * @param args 原始配置参数
		 * @param shortcutConf 快捷配置对象，提供字段顺序等信息
		 * @param parser SpEL 表达式解析器
		 * @param beanFactory Bean 工厂
		 * @return 规范化后的属性映射
		 */
		public abstract Map<String, Object> normalize(Map<String, String> args, ShortcutConfigurable shortcutConf,
				SpelExpressionParser parser, BeanFactory beanFactory);

	}

	/**
	 * 网关专用的 SpEL 评估上下文。
	 * <p>
	 * 该上下文支持在配置中引用 Bean（如 "#{@beanName}"）， 同时提供两种访问级别：
	 * <ul>
	 * <li>restrictive - 仅允许方法访问受限的属性访问器</li>
	 * <li>非 restrictive - 允许读写数据绑定</li>
	 * </ul>
	 */
	class GatewayEvaluationContext implements EvaluationContext {

		/** Bean 工厂解析器，用于解析 SpEL 中的 Bean 引用 */
		private final BeanFactoryResolver beanFactoryResolver;

		/** 委托的评估上下文 */
		private final SimpleEvaluationContext delegate;

		/**
		 * 构造函数。
		 * @param beanFactory Bean 工厂
		 */
		public GatewayEvaluationContext(BeanFactory beanFactory) {
			this.beanFactoryResolver = new BeanFactoryResolver(beanFactory);
			Environment env = beanFactory.getBean(Environment.class);
			// 检查是否启用限制性属性访问
			boolean restrictive = env.getProperty("spring.cloud.gateway.restrictive-property-accessor.enabled",
					Boolean.class, true);
			if (restrictive) {
				// 限制性模式：不允许属性读取，使用严格的属性访问器
				delegate = SimpleEvaluationContext.forPropertyAccessors(new RestrictivePropertyAccessor())
						.withMethodResolvers((context, targetObject, name, argumentTypes) -> null).build();
			}
			else {
				// 非限制性模式：允许读写数据绑定
				delegate = SimpleEvaluationContext.forReadOnlyDataBinding().build();
			}
		}

		@Override
		public TypedValue getRootObject() {
			return delegate.getRootObject();
		}

		@Override
		public List<PropertyAccessor> getPropertyAccessors() {
			return delegate.getPropertyAccessors();
		}

		@Override
		public List<ConstructorResolver> getConstructorResolvers() {
			return delegate.getConstructorResolvers();
		}

		@Override
		public List<MethodResolver> getMethodResolvers() {
			return delegate.getMethodResolvers();
		}

		@Override
		@Nullable
		public BeanResolver getBeanResolver() {
			return this.beanFactoryResolver;
		}

		@Override
		public TypeLocator getTypeLocator() {
			return delegate.getTypeLocator();
		}

		@Override
		public TypeConverter getTypeConverter() {
			return delegate.getTypeConverter();
		}

		@Override
		public TypeComparator getTypeComparator() {
			return delegate.getTypeComparator();
		}

		@Override
		public OperatorOverloader getOperatorOverloader() {
			return delegate.getOperatorOverloader();
		}

		@Override
		public void setVariable(String name, Object value) {
			delegate.setVariable(name, value);
		}

		@Override
		@Nullable
		public Object lookupVariable(String name) {
			return delegate.lookupVariable(name);
		}

	}

	/**
	 * 限制性属性访问器，禁止读取属性值。
	 * <p>
	 * 在安全敏感场景下使用，防止通过 SpEL 表达式读取任意 Bean 的属性。
	 */
	class RestrictivePropertyAccessor extends ReflectivePropertyAccessor {

		/**
		 * 始终返回 false，禁止属性读取。
		 */
		@Override
		public boolean canRead(EvaluationContext context, Object target, String name) {
			return false;
		}

	}

}
