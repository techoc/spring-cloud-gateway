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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.Before;
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
import org.springframework.cloud.gateway.webflux.ProductionConfigurationTests.TestApplication;
import org.springframework.cloud.gateway.webflux.ProductionConfigurationTests.TestApplication.Bar;
import org.springframework.cloud.gateway.webflux.ProductionConfigurationTests.TestApplication.Foo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产配置测试类。
 * 用于测试 ProxyExchange 在生产环境中的各种功能，包括请求转发、路径处理、请求头管理等。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "spring.cloud.gateway.proxy.auto-forward=baz" },
		webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TestApplication.class)
@DirtiesContext
public class ProductionConfigurationTests {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private TestApplication application;

	@LocalServerPort
	private int port;

	/**
	 * 初始化测试环境，设置应用的主机地址。
	 */
	@Before
	public void init() throws Exception {
		application.setHome(new URI("http://localhost:" + port));
	}

	/**
	 * 测试 GET 请求代理功能。
	 */
	@Test
	public void get() throws Exception {
		assertThat(rest.getForObject("/proxy/0", Foo.class).getName()).isEqualTo("bye");
	}

	/**
	 * 测试 GET 请求转发功能。
	 */
	@Test
	public void forwardGet() throws Exception {
		assertThat(rest.getForObject("/proxy/forward/0", Foo.class).getName()).isEqualTo("bye");
	}

	/**
	 * 测试路径提取功能。
	 */
	@Test
	public void path() throws Exception {
		assertThat(rest.getForObject("/proxy/path/1", Foo.class).getName()).isEqualTo("foo");
	}

	/**
	 * 测试静态资源代理功能。
	 */
	@Test
	public void resource() throws Exception {
		assertThat(rest.getForObject("/proxy/html/test.html", String.class)).contains("<body>Test");
	}

	/**
	 * 测试无类型资源的代理功能。
	 */
	@Test
	public void resourceWithNoType() throws Exception {
		assertThat(rest.getForObject("/proxy/typeless/test.html", String.class)).contains("<body>Test");
	}

	/**
	 * 测试资源不存在时的处理。
	 */
	@Test
	public void missing() throws Exception {
		assertThat(rest.getForEntity("/proxy/missing/0", Foo.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/**
	 * 测试 URI 设置功能。
	 */
	@Test
	public void uri() throws Exception {
		assertThat(rest.getForObject("/proxy/0", Foo.class).getName()).isEqualTo("bye");
	}

	/**
	 * 测试 POST 请求代理功能。
	 */
	@Test
	public void post() throws Exception {
		assertThat(rest.postForObject("/proxy/0", Collections.singletonMap("name", "foo"), Bar.class).getName())
				.isEqualTo("host=localhost:" + port + ";foo");
	}

	/**
	 * 测试 POST 请求转发功能。
	 */
	@Test
	public void forwardPost() throws Exception {
		assertThat(rest.postForObject("/proxy/forward/0", Collections.singletonMap("name", "foo"), Bar.class).getName())
				.isEqualTo("host=localhost:" + port + ";foo");
	}

	/**
	 * 测试列表类型的请求体处理。
	 */
	@Test
	public void list() throws Exception {
		ResponseEntity<List<Bar>> result = rest.exchange(
				RequestEntity.post(rest.getRestTemplate().getUriTemplateHandler().expand("/proxy"))
						.contentType(MediaType.APPLICATION_JSON)
						.body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
				new ParameterizedTypeReference<List<Bar>>() {
				});
		assertThat(result.getBody().iterator().next().getName()).isEqualTo("host=localhost:" + port + ";foo");
	}

	/**
	 * 测试无请求体的 POST 请求。
	 */
	@Test
	public void bodyless() throws Exception {
		assertThat(rest.postForObject("/proxy/0", Collections.singletonMap("name", "foo"), Bar.class).getName())
				.isEqualTo("host=localhost:" + port + ";foo");
	}

	/**
	 * 测试显式实体类型的请求处理。
	 */
	@Test
	public void entity() throws Exception {
		assertThat(
				rest.exchange(RequestEntity.post(rest.getRestTemplate().getUriTemplateHandler().expand("/proxy/entity"))
						.body(Collections.singletonMap("name", "foo")), new ParameterizedTypeReference<List<Bar>>() {
						}).getBody().iterator().next().getName()).isEqualTo("host=localhost:" + port + ";foo");
	}

	/**
	 * 测试带类型的实体请求处理。
	 */
	@Test
	public void entityWithType() throws Exception {
		assertThat(
				rest.exchange(RequestEntity.post(rest.getRestTemplate().getUriTemplateHandler().expand("/proxy/type"))
						.body(Collections.singletonMap("name", "foo")), new ParameterizedTypeReference<List<Bar>>() {
						}).getBody().iterator().next().getName()).isEqualTo("host=localhost:" + port + ";foo");
	}

	/**
	 * 测试单个对象的请求处理。
	 */
	@Test
	public void single() throws Exception {
		assertThat(rest.postForObject("/proxy/single", Collections.singletonMap("name", "foobar"), Bar.class).getName())
				.isEqualTo("host=localhost:" + port + ";foobar");
	}

	/**
	 * 测试响应转换器功能。
	 */
	@Test
	public void converter() throws Exception {
		assertThat(
				rest.postForObject("/proxy/converter", Collections.singletonMap("name", "foobar"), Bar.class).getName())
						.isEqualTo("host=localhost:" + port + ";foobar");
	}

	/**
	 * 测试敏感请求头的覆盖功能。
	 */
	@Test
	@SuppressWarnings({ "Duplicates", "unchecked" })
	public void testSensitiveHeadersOverride() throws Exception {
		Map<String, List<String>> headers = rest
				.exchange(
						RequestEntity.get(rest.getRestTemplate().getUriTemplateHandler().expand("/proxy/headers"))
								.header("foo", "bar").header("abc", "xyz").header("cookie", "monster").build(),
						Map.class)
				.getBody();
		assertThat(headers).doesNotContainKey("foo").doesNotContainKey("hello").containsKeys("bar", "abc");

		assertThat(headers.get("cookie")).containsOnly("monster");
	}

	/**
	 * 测试默认的敏感请求头处理。
	 */
	@Test
	public void testSensitiveHeadersDefault() throws Exception {
		Map<String, List<String>> headers = rest.exchange(RequestEntity
				.get(rest.getRestTemplate().getUriTemplateHandler().expand("/proxy/sensitive-headers-default"))
				.header("cookie", "monster").build(), Map.class).getBody();

		assertThat(headers).doesNotContainKey("cookie");
	}

	/**
	 * 测试请求头的转发和过滤功能。
	 */
	@Test
	@SuppressWarnings({ "Duplicates", "unchecked" })
	public void headers() throws Exception {
		Map<String, List<String>> headers = rest
				.exchange(RequestEntity.get(rest.getRestTemplate().getUriTemplateHandler().expand("/proxy/headers"))
						.header("foo", "bar").header("abc", "xyz").header("baz", "fob").build(), Map.class)
				.getBody();
		assertThat(headers).doesNotContainKey("foo").doesNotContainKey("hello").containsKeys("bar", "abc");

		assertThat(headers.get("bar")).containsOnly("hello");
		assertThat(headers.get("abc")).containsOnly("123");
		assertThat(headers.get("baz")).containsOnly("fob");
	}

	/**
	 * 测试 Forwarded 请求头使用主机名的情况。
	 */
	@Test
	public void forwardedHeaderUsesHost() throws Exception {
		Map<String, List<String>> headers = rest
				.exchange(RequestEntity.get(rest.getRestTemplate().getUriTemplateHandler().expand("/proxy/headers"))
						.header("host", "foo:1234").build(), Map.class)
				.getBody();

		assertThat(headers).containsKey("forwarded");
		assertThat(headers.get("forwarded").size()).isEqualTo(1);
		assertThat(headers.get("forwarded").get(0)).isEqualTo("host=localhost:" + port);
	}

	/**
	 * 测试不带请求体的 DELETE 请求。
	 */
	@Test
	public void deleteWithoutBody() throws Exception {
		ResponseEntity<Void> deleteResponse = rest.exchange("/proxy/{id}/no-body", HttpMethod.DELETE, null, Void.TYPE,
				Collections.singletonMap("id", "123"));
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	/**
	 * 测试带请求体的 DELETE 请求。
	 */
	@Test
	public void deleteWithBody() throws Exception {
		Foo foo = new Foo("to-be-deleted");
		ParameterizedTypeReference<Map<String, Foo>> returnType = new ParameterizedTypeReference<Map<String, Foo>>() {
		};
		ResponseEntity<Map<String, Foo>> deleteResponse = rest.exchange("/proxy/{id}", HttpMethod.DELETE,
				new HttpEntity<Foo>(foo), returnType, Collections.singletonMap("id", "123"));
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(deleteResponse.getBody().get("deleted")).isEqualToComparingFieldByField(foo);
	}

	/**
	 * 测试应用程序配置类。
	 */
	@SpringBootApplication
	static class TestApplication {

		@Autowired
		private ProxyController controller;

		/**
		 * 设置代理服务的主机地址。
		 * @param home 主机 URI
		 */
		public void setHome(URI home) {
			controller.setHome(home);
		}

		/**
		 * 代理控制器，处理各种代理请求。
		 */
		@RestController
		static class ProxyController {

			private URI home;

			/**
			 * 设置代理服务的主机地址。
			 * @param home 主机 URI
			 */
			public void setHome(URI home) {
				this.home = home;
			}

			/**
			 * 代理 GET 请求获取 Foo 对象。
			 * @param id 资源 ID
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/{id}")
			public Mono<ResponseEntity<Object>> proxyFoos(@PathVariable Integer id, ProxyExchange<Object> proxy)
					throws Exception {
				return proxy.uri(home.toString() + "/foos/" + id).get();
			}

			/**
			 * 代理 GET 请求并处理路径。
			 * @param proxy 代理交换器
			 * @param uri URI 构建器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/path/**")
			public Mono<ResponseEntity<Object>> proxyPath(ProxyExchange<Object> proxy, UriComponentsBuilder uri)
					throws Exception {
				String path = proxy.path("/proxy/path/");
				return proxy.uri(home.toString() + "/foos/" + path).get();
			}

			/**
			 * 代理 HTML 资源请求。
			 * @param proxy 代理交换器
			 * @param uri URI 构建器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/html/**")
			public Mono<ResponseEntity<String>> proxyHtml(ProxyExchange<String> proxy, UriComponentsBuilder uri)
					throws Exception {
				String path = proxy.path("/proxy/html");
				return proxy.uri(home.toString() + path).get();
			}

			/**
			 * 代理无类型资源请求。
			 * @param proxy 代理交换器
			 * @param uri URI 构建器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/typeless/**")
			public Mono<ResponseEntity<byte[]>> proxyTypeless(ProxyExchange<byte[]> proxy, UriComponentsBuilder uri)
					throws Exception {
				String path = proxy.path("/proxy/typeless");
				return proxy.uri(home.toString() + path).get();
			}

			/**
			 * 代理不存在的资源请求。
			 * @param id 资源 ID
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/missing/{id}")
			public Mono<ResponseEntity<Object>> proxyMissing(@PathVariable Integer id, ProxyExchange<Object> proxy)
					throws Exception {
				return proxy.uri(home.toString() + "/missing/" + id).get();
			}

			/**
			 * 代理 GET 请求获取资源列表。
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@GetMapping("/proxy")
			public Mono<ResponseEntity<Object>> proxyUri(ProxyExchange<Object> proxy) throws Exception {
				return proxy.uri(home.toString() + "/foos").get();
			}

			/**
			 * 代理 POST 请求创建 Bar 对象。
			 * @param id 资源 ID
			 * @param body 请求体
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@PostMapping("/proxy/{id}")
			public Mono<ResponseEntity<Object>> proxyBars(@PathVariable Integer id,
					@RequestBody Map<String, Object> body, ProxyExchange<List<Object>> proxy) throws Exception {
				body.put("id", id);
				return proxy.uri(home.toString() + "/bars").body(Arrays.asList(body)).post(this::first);
			}

			/**
			 * 代理无请求体的 POST 请求。
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@PostMapping("/proxy")
			public Mono<ResponseEntity<List<Object>>> barsWithNoBody(ProxyExchange<List<Object>> proxy)
					throws Exception {
				return proxy.uri(home.toString() + "/bars").post();
			}

			/**
			 * 代理显式实体的 POST 请求。
			 * @param foo 请求体中的 Foo 对象
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@PostMapping("/proxy/entity")
			public Mono<ResponseEntity<Object>> explicitEntity(@RequestBody Mono<Foo> foo, ProxyExchange<Object> proxy)
					throws Exception {
				return proxy.uri(home.toString() + "/bars").body(Flux.from(foo)).post();
			}

			/**
			 * 代理带类型的显式实体 POST 请求。
			 * @param foo 请求体中的 Foo 对象
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@PostMapping("/proxy/type")
			public Mono<ResponseEntity<List<Bar>>> explicitEntityWithType(@RequestBody Mono<Foo> foo,
					ProxyExchange<List<Bar>> proxy) throws Exception {
				return proxy.uri(home.toString() + "/bars").body(Flux.from(foo)).post();
			}

			/**
			 * 代理隐式实体的 POST 请求。
			 * @param foo 请求体中的 Foo 对象
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@PostMapping("/proxy/single")
			public Mono<ResponseEntity<Object>> implicitEntity(@RequestBody Mono<Foo> foo,
					ProxyExchange<List<Object>> proxy) throws Exception {
				return proxy.uri(home.toString() + "/bars").body(Flux.from(foo)).post(this::first);
			}

			/**
			 * 代理带转换器的隐式实体 POST 请求。
			 * @param foo 请求体中的 Foo 对象
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@PostMapping("/proxy/converter")
			public Mono<ResponseEntity<Bar>> implicitEntityWithConverter(@RequestBody Foo foo,
					ProxyExchange<List<Bar>> proxy) throws Exception {
				return proxy.uri(home.toString() + "/bars").body(Arrays.asList(foo))
						.post(response -> ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
								.body(response.getBody().iterator().next()));
			}

			/**
			 * 测试请求头代理功能。
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/headers")
			public Mono<ResponseEntity<Map<String, List<String>>>> headers(
					ProxyExchange<Map<String, List<String>>> proxy) {
				proxy.sensitive("foo", "hello");
				proxy.header("bar", "hello");
				proxy.header("abc", "123");
				proxy.header("hello", "world");
				return proxy.uri(home.toString() + "/headers").get();
			}

			/**
			 * 测试默认敏感请求头处理。
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/sensitive-headers-default")
			public Mono<ResponseEntity<Map<String, List<String>>>> defaultSensitiveHeaders(
					ProxyExchange<Map<String, List<String>>> proxy) {
				proxy.header("bar", "hello");
				proxy.header("abc", "123");
				proxy.header("hello", "world");
				return proxy.uri(home.toString() + "/headers").get();
			}

			/**
			 * 从列表响应中提取第一个元素。
			 * @param response 列表响应
			 * @return 单个元素的响应
			 */
			private <T> ResponseEntity<T> first(ResponseEntity<List<T>> response) {
				return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
						.body(response.getBody().iterator().next());
			}

			/**
			 * 代理转发的 GET 请求。
			 * @param id 资源 ID
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@GetMapping("/proxy/forward/{id}")
			public Mono<ResponseEntity<Object>> proxyForwardFoos(@PathVariable Integer id, ProxyExchange<Object> proxy)
					throws Exception {
				return proxy.uri(home.toString() + "/foos/" + id).forward();
			}

			/**
			 * 代理转发的 POST 请求。
			 * @param id 资源 ID
			 * @param body 请求体
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@PostMapping("/proxy/forward/{id}")
			public Mono<ResponseEntity<Object>> proxyForwardBars(@PathVariable Integer id,
					@RequestBody Map<String, Object> body, ProxyExchange<List<Object>> proxy) throws Exception {
				body.put("id", id);
				return proxy.uri(home.toString() + "/bars").body(Arrays.asList(body)).forward(this::first);
			}

			/**
			 * 代理不带请求体的 DELETE 请求。
			 * @param id 资源 ID
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@DeleteMapping("/proxy/{id}/no-body")
			public Mono<ResponseEntity<Object>> deleteWithoutBody(@PathVariable Integer id, ProxyExchange<Object> proxy)
					throws Exception {
				return proxy.uri(home.toString() + "/foos/" + id + "/no-body").delete();
			}

			/**
			 * 代理带请求体的 DELETE 请求。
			 * @param id 资源 ID
			 * @param foo 请求体中的 Foo 对象
			 * @param proxy 代理交换器
			 * @return 响应实体
			 */
			@DeleteMapping("/proxy/{id}")
			public Mono<ResponseEntity<Object>> deleteWithBody(@PathVariable Integer id, @RequestBody Foo foo,
					ProxyExchange<Object> proxy) throws Exception {
				return proxy.uri(home.toString() + "/foos/" + id).body(foo).delete(response -> ResponseEntity
						.status(response.getStatusCode()).headers(response.getHeaders()).body(response.getBody()));
			}

		}

		/**
		 * 测试控制器，提供后端服务接口。
		 */
		@RestController
		static class TestController {

			/**
			 * 获取 Foo 对象列表。
			 * @return Foo 对象列表
			 */
			@GetMapping("/foos")
			public List<Foo> foos() {
				return Arrays.asList(new Foo("hello"));
			}

			/**
			 * 根据 ID 获取单个 Foo 对象。
			 * @param id 资源 ID
			 * @param headers 请求头
			 * @return Foo 对象
			 */
			@GetMapping("/foos/{id}")
			public Foo foo(@PathVariable Integer id, @RequestHeader HttpHeaders headers) {
				String custom = headers.getFirst("X-Custom");
				return new Foo(id == 1 ? "foo" : custom != null ? custom : "bye");
			}

			/**
			 * 创建 Bar 对象列表。
			 * @param foos 请求体中的 Foo 对象列表
			 * @param headers 请求头
			 * @return Bar 对象列表
			 */
			@PostMapping("/bars")
			public List<Bar> bars(@RequestBody List<Foo> foos, @RequestHeader HttpHeaders headers) {
				String custom = headers.getFirst("X-Custom");
				custom = custom == null ? "" : custom;
				custom = headers.getFirst("forwarded") == null ? custom : headers.getFirst("forwarded") + ";" + custom;
				return Arrays.asList(new Bar(custom + foos.iterator().next().getName()));
			}

			/**
			 * 获取所有请求头。
			 * @param headers 请求头
			 * @return 请求头映射
			 */
			@GetMapping("/headers")
			public Map<String, List<String>> headers(@RequestHeader HttpHeaders headers) {
				return headers;
			}

			/**
			 * 删除 Foo 对象（不带请求体）。
			 * @param id 资源 ID
			 * @return 响应实体
			 */
			@DeleteMapping("/foos/{id}/no-body")
			public ResponseEntity<?> deleteFoo(@PathVariable Integer id) {
				return ResponseEntity.ok().build();
			}

			/**
			 * 删除 Foo 对象（带请求体）。
			 * @param id 资源 ID
			 * @param foo 请求体中的 Foo 对象
			 * @return 响应实体
			 */
			@DeleteMapping("/foos/{id}")
			public ResponseEntity<?> deleteFoo(@PathVariable Integer id, @RequestBody Foo foo) {
				return ResponseEntity.ok().body(Collections.singletonMap("deleted", foo));
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
