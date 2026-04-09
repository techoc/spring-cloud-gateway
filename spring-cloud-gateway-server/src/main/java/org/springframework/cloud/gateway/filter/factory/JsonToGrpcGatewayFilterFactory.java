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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import javax.net.ssl.SSLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufFactory;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.netty.buffer.PooledByteBufAllocator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.config.GrpcSslConfigurer;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * JSON 转 gRPC 过滤器工厂。
 * <p>
 * 该过滤器将客户端发送的 JSON 请求转换为 gRPC protobuf 格式， 转发给后端 gRPC 服务，并将 gRPC 响应转换回 JSON 格式返回给客户端。
 * <p>
 * 这使得 gRPC 服务对客户端透明，客户端无需了解 protobuf 协议即可调用 gRPC 服务。
 * <p>
 * 配置参数：
 * <ul>
 * <li>protoDescriptor：proto 描述文件路径（.desc）</li>
 * <li>protoFile：proto 定义文件路径（.proto）</li>
 * <li>service：gRPC 服务名称</li>
 * <li>method：gRPC 方法名称</li>
 * </ul>
 *
 * @author Alberto C. Ríos
 */
public class JsonToGrpcGatewayFilterFactory
		extends AbstractGatewayFilterFactory<JsonToGrpcGatewayFilterFactory.Config> {

	/** gRPC SSL 配置器 */
	private final GrpcSslConfigurer grpcSslConfigurer;

	/** 资源加载器 */
	private final ResourceLoader resourceLoader;

	/**
	 * 构造方法。
	 * @param grpcSslConfigurer gRPC SSL 配置器
	 * @param resourceLoader 资源加载器
	 */
	public JsonToGrpcGatewayFilterFactory(GrpcSslConfigurer grpcSslConfigurer, ResourceLoader resourceLoader) {
		super(Config.class);
		this.grpcSslConfigurer = grpcSslConfigurer;
		this.resourceLoader = resourceLoader;
	}

	/**
	 * 返回快捷字段顺序。
	 * @return 字段顺序列表
	 */
	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("protoDescriptor", "protoFile", "service", "method");
	}

	/**
	 * 创建 JSON 转 gRPC 过滤器。
	 * @param config 过滤器配置
	 * @return 网关过滤器实例
	 */
	@Override
	public GatewayFilter apply(Config config) {
		GatewayFilter filter = new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				GRPCResponseDecorator modifiedResponse = new GRPCResponseDecorator(exchange, config);

				ServerWebExchangeUtils.setAlreadyRouted(exchange);
				return modifiedResponse.writeWith(exchange.getRequest().getBody())
						.then(chain.filter(exchange.mutate().response(modifiedResponse).build()));
			}

			@Override
			public String toString() {
				return filterToStringCreator(JsonToGrpcGatewayFilterFactory.this).toString();
			}
		};

		// 在 NettyWriteResponseFilter 之前执行
		int order = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
		return new OrderedGatewayFilter(filter, order);
	}

	/**
	 * JSON 转 gRPC 过滤器配置类。
	 */
	public static class Config {

		/** proto 描述文件路径 */
		private String protoDescriptor;

		/** proto 定义文件路径 */
		private String protoFile;

		/** gRPC 服务名称 */
		private String service;

		/** gRPC 方法名称 */
		private String method;

		public String getProtoDescriptor() {
			return protoDescriptor;
		}

		public Config setProtoDescriptor(String protoDescriptor) {
			this.protoDescriptor = protoDescriptor;
			return this;
		}

		public String getProtoFile() {
			return protoFile;
		}

		public Config setProtoFile(String protoFile) {
			this.protoFile = protoFile;
			return this;
		}

		public String getService() {
			return service;
		}

		public Config setService(String service) {
			this.service = service;
			return this;
		}

		public String getMethod() {
			return method;
		}

		public Config setMethod(String method) {
			this.method = method;
			return this;
		}

	}

	/**
	 * gRPC 响应装饰器，负责 JSON 与 protobuf 之间的转换。
	 */
	class GRPCResponseDecorator extends ServerHttpResponseDecorator {

		/** 当前交换对象 */
		private final ServerWebExchange exchange;

		/** protobuf 消息描述符 */
		private final Descriptors.Descriptor descriptor;

		/** JSON 对象写入器 */
		private final ObjectWriter objectWriter;

		/** JSON 对象读取器 */
		private final ObjectReader objectReader;

		/** gRPC 客户端调用 */
		private final ClientCall<DynamicMessage, DynamicMessage> clientCall;

		/** JSON 对象节点 */
		private final ObjectNode objectNode;

		/**
		 * 构造方法，初始化 protobuf 相关的编解码器。
		 * @param exchange 当前交换对象
		 * @param config 过滤器配置
		 */
		GRPCResponseDecorator(ServerWebExchange exchange, Config config) {
			super(exchange.getResponse());
			this.exchange = exchange;
			try {
				Resource descriptorFile = resourceLoader.getResource(config.getProtoDescriptor());
				Resource protoFile = resourceLoader.getResource(config.getProtoFile());

				// 解析 proto 描述文件
				descriptor = DescriptorProtos.FileDescriptorProto.parseFrom(descriptorFile.getInputStream())
						.getDescriptorForType();

				Descriptors.MethodDescriptor methodDescriptor = getMethodDescriptor(config,
						descriptorFile.getInputStream());
				Descriptors.ServiceDescriptor serviceDescriptor = methodDescriptor.getService();
				Descriptors.Descriptor outputType = methodDescriptor.getOutputType();

				// 创建 gRPC 客户端调用
				clientCall = createClientCallForType(config, serviceDescriptor, outputType);

				// 初始化 JSON-Protobuf 转换器
				ProtobufSchema schema = ProtobufSchemaLoader.std.load(protoFile.getInputStream());
				ProtobufSchema responseType = schema.withRootType(outputType.getName());

				ObjectMapper objectMapper = new ObjectMapper(new ProtobufFactory());
				objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
				objectWriter = objectMapper.writer(schema);
				objectReader = objectMapper.readerFor(JsonNode.class).with(responseType);
				objectNode = objectMapper.createObjectNode();

			}
			catch (IOException | Descriptors.DescriptorValidationException e) {
				throw new RuntimeException(e);
			}
		}

		/**
		 * 重写 writeWith，添加 JSON 到 protobuf 的转换逻辑。
		 * @param body 响应体数据流
		 * @return 完成信号
		 */
		@Override
		public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
			exchange.getResponse().getHeaders().set("Content-Type", "application/json");

			return getDelegate().writeWith(deserializeJSONRequest().map(callGRPCServer()).map(serialiseGRPCResponse())
					.map(wrapGRPCResponse()).cast(DataBuffer.class).last());
		}

		/**
		 * 创建指定类型的 gRPC 客户端调用。
		 */
		private ClientCall<DynamicMessage, DynamicMessage> createClientCallForType(Config config,
				Descriptors.ServiceDescriptor serviceDescriptor, Descriptors.Descriptor outputType) {
			MethodDescriptor.Marshaller<DynamicMessage> marshaller = ProtoUtils
					.marshaller(DynamicMessage.newBuilder(outputType).build());
			MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = MethodDescriptor
					.<DynamicMessage, DynamicMessage>newBuilder().setType(MethodDescriptor.MethodType.UNKNOWN)
					.setFullMethodName(MethodDescriptor.generateFullMethodName(serviceDescriptor.getFullName(),
							config.getMethod()))
					.setRequestMarshaller(marshaller).setResponseMarshaller(marshaller).build();
			Channel channel = createChannel();
			return channel.newCall(methodDescriptor, CallOptions.DEFAULT);
		}

		/**
		 * 获取方法描述符。
		 */
		private Descriptors.MethodDescriptor getMethodDescriptor(Config config, InputStream descriptorFile)
				throws IOException, Descriptors.DescriptorValidationException {
			DescriptorProtos.FileDescriptorSet fileDescriptorSet = DescriptorProtos.FileDescriptorSet
					.parseFrom(descriptorFile);
			DescriptorProtos.FileDescriptorProto fileProto = fileDescriptorSet.getFile(0);
			Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileProto,
					new Descriptors.FileDescriptor[0]);

			Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(config.getService());
			if (serviceDescriptor == null) {
				throw new NoSuchElementException("No Service found");
			}

			List<Descriptors.MethodDescriptor> methods = serviceDescriptor.getMethods();

			return methods.stream().filter(method -> method.getName().equals(config.getMethod())).findFirst()
					.orElseThrow(() -> new NoSuchElementException("No Method found"));
		}

		/**
		 * 创建 gRPC 通道。
		 */
		private ManagedChannel createChannel() {
			URI requestURI = ((Route) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)).getUri();
			return createChannelChannel(requestURI.getHost(), requestURI.getPort());
		}

		/**
		 * 调用 gRPC 服务器的函数。
		 */
		private Function<JsonNode, DynamicMessage> callGRPCServer() {
			return jsonRequest -> {
				try {
					byte[] request = objectWriter.writeValueAsBytes(jsonRequest);
					return ClientCalls.blockingUnaryCall(clientCall, DynamicMessage.parseFrom(descriptor, request));
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			};
		}

		/**
		 * 序列化 gRPC 响应的函数。
		 */
		private Function<DynamicMessage, Object> serialiseGRPCResponse() {
			return gRPCResponse -> {
				try {
					return objectReader.readValue(gRPCResponse.toByteArray());
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			};
		}

		/**
		 * 反序列化 JSON 请求。
		 */
		private Flux<JsonNode> deserializeJSONRequest() {
			return exchange.getRequest().getBody().mapNotNull(dataBufferBody -> {
				if (dataBufferBody.capacity() == 0) {
					return objectNode;
				}
				ResolvableType targetType = ResolvableType.forType(JsonNode.class);
				return new Jackson2JsonDecoder().decode(dataBufferBody, targetType, null, null);
			}).cast(JsonNode.class);
		}

		/**
		 * 包装 gRPC 响应为 DataBuffer 的函数。
		 */
		private Function<Object, DataBuffer> wrapGRPCResponse() {
			return jsonResponse -> {
				try {
					return new NettyDataBufferFactory(new PooledByteBufAllocator())
							.wrap(Objects.requireNonNull(new ObjectMapper().writeValueAsBytes(jsonResponse)));
				}
				catch (JsonProcessingException e) {
					return new NettyDataBufferFactory(new PooledByteBufAllocator()).allocateBuffer();
				}
			};
		}

		/**
		 * 创建 gRPC 通道（每次调用都创建，是否需要优化？）。
		 */
		// 是否需要优化？每次调用都创建新通道
		private ManagedChannel createChannelChannel(String host, int port) {
			NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(host, port);
			try {
				return grpcSslConfigurer.configureSsl(nettyChannelBuilder);
			}
			catch (SSLException e) {
				throw new RuntimeException(e);
			}
		}

	}

}
