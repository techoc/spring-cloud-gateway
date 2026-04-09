/*
 * Copyright 2016-2019 the original author or authors.
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

package org.springframework.cloud.gateway.webflux;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.gateway.webflux.ReactiveTests.TestApplication;
import org.springframework.cloud.gateway.webflux.ReactiveTests.TestApplication.Bar;
import org.springframework.cloud.gateway.webflux.ReactiveTests.TestApplication.Foo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 响应式测试类。 用于测试 ProxyExchange 在响应式环境中的功能，包括 Flux 和 Mono 类型的处理。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TestApplication.class)
public class ReactiveTests {

	@Autowired
	private TestRestTemplate rest;

	@LocalServerPort
	private int port;

	/**
	 * 测试 POST 字节数组请求。
	 */
	@Test
	public void postBytes() throws Exception {
		ResponseEntity<List<Foo>> result = rest.exchange(RequestEntity
				.post(rest.getRestTemplate().getUriTemplateHandler().expand("/bytes")).body("hello foo".getBytes()),
				new ParameterizedTypeReference<List<Foo>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello foo");
	}

	/**
	 * 测试 POST 请求处理列表类型。
	 */
	@Test
	public void post() throws Exception {
		ResponseEntity<List<Bar>> result = rest.exchange(
				RequestEntity.post(rest.getRestTemplate().getUriTemplateHandler().expand("/bars"))
						.body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
				new ParameterizedTypeReference<List<Bar>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello foo");
	}

	/**
	 * 测试 POST 请求处理 Flux 类型。
	 */
	@Test
	public void postFlux() throws Exception {
		ResponseEntity<List<Bar>> result = rest.exchange(
				RequestEntity.post(rest.getRestTemplate().getUriTemplateHandler().expand("/flux/bars"))
						.body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
				new ParameterizedTypeReference<List<Bar>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello foo");
	}

	/**
	 * 测试 GET 请求处理 Flux 类型。
	 */
	@Test
	public void get() throws Exception {
		ResponseEntity<List<Foo>> result = rest.exchange(
				RequestEntity.get(rest.getRestTemplate().getUriTemplateHandler().expand("/foos")).build(),
				new ParameterizedTypeReference<List<Foo>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello");
	}

	/**
	 * 测试请求转发功能。
	 */
	@Test
	public void forward() throws Exception {
		ResponseEntity<List<Foo>> result = rest.exchange(
				RequestEntity.get(rest.getRestTemplate().getUriTemplateHandler().expand("/forward/foos")).build(),
				new ParameterizedTypeReference<List<Foo>>() {
				});
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("hello");
	}

	/**
	 * 测试应用程序配置类。
	 */
	@SpringBootApplication
	static class TestApplication {

		/**
		 * 测试控制器，提供响应式接口。
		 */
		@RestController
		static class TestController {

			@Autowired
			private DispatcherHandler handler;

			/**
			 * 处理 Bar 对象列表的 POST 请求。
			 * @param foos 请求体中的 Foo 对象列表
			 * @param headers 请求头
			 * @return Bar 对象列表
			 */
			@PostMapping("/bars")
			public List<Bar> bars(@RequestBody List<Foo> foos, @RequestHeader HttpHeaders headers) {
				String custom = "hello ";
				return foos.stream().map(foo -> new Bar(custom + foo.getName())).collect(Collectors.toList());
			}

			/**
			 * 处理 Flux 类型的 Bar 对象 POST 请求。
			 * @param foos 请求体中的 Flux Foo 对象流
			 * @param headers 请求头
			 * @return Flux Bar 对象流
			 */
			@PostMapping("/flux/bars")
			public Flux<Bar> fluxbars(@RequestBody Flux<Foo> foos, @RequestHeader HttpHeaders headers) {
				String custom = "hello ";
				return foos.map(foo -> new Bar(custom + foo.getName()));
			}

			/**
			 * 获取 Flux 类型的 Foo 对象流。
			 * @return Flux Foo 对象流
			 */
			@GetMapping("/foos")
			public Flux<Foo> foos() {
				return Flux.just(new Foo("hello"));
			}

			/**
			 * 转发 Foo 对象请求。
			 * @param exchange 服务器 Web 交换对象
			 * @return Mono 空值
			 */
			@GetMapping("/forward/foos")
			public Mono<Void> forwardFoos(ServerWebExchange exchange) {
				return handler.handle(exchange.mutate().request(request -> request.path("/foos").build()).build());
			}

			/**
			 * 处理字节数组的 POST 请求。
			 * @param body 请求体中的字节数组流
			 * @return Flux Foo 对象流
			 */
			@PostMapping("/bytes")
			public Flux<Foo> forwardBars(@RequestBody Flux<byte[]> body) {
				return Flux.from(body.reduce(this::concatenate).map(value -> new Foo(new String(value))));
			}

			/**
			 * 连接两个字节数组。
			 * @param array1 第一个字节数组
			 * @param array2 第二个字节数组
			 * @return 连接后的字节数组
			 */
			byte[] concatenate(@Nullable byte[] array1, @Nullable byte[] array2) {
				if (ObjectUtils.isEmpty(array1)) {
					return array2;
				}
				if (ObjectUtils.isEmpty(array2)) {
					return array1;
				}

				byte[] newArr = new byte[array1.length + array2.length];
				System.arraycopy(array1, 0, newArr, 0, array1.length);
				System.arraycopy(array2, 0, newArr, array1.length, array2.length);
				return newArr;
			}

		}

		/**
		 * Foo 数据模型类。
		 */
		@JsonIgnoreProperties(ignoreUnknown = true)
		static class Foo {

			private String name;

			Foo() {
			}

			Foo(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

		}

		/**
		 * Bar 数据模型类。
		 */
		@JsonIgnoreProperties(ignoreUnknown = true)
		static class Bar {

			private String name;

			Bar() {
			}

			Bar(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

		}

	}

}
