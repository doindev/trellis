package io.trellis.nodes.impl;

import java.lang.reflect.Method;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * AMQP Sender Node -- sends messages to an AMQP 0-9-1 broker (such as RabbitMQ).
 * Attempts to use an AMQP client library (com.rabbitmq:amqp-client) via reflection
 * if available on the classpath. Otherwise, returns a descriptive error explaining
 * that the AMQP client library is required.
 */
@Slf4j
@Node(
	type = "amqp",
	displayName = "AMQP Sender",
	description = "Send messages to an AMQP 0-9-1 broker (e.g., RabbitMQ).",
	category = "Message Queues / Streaming",
	icon = "amqp",
	credentials = {"amqpApi"}
)
public class AmqpNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Exchange
		params.add(NodeParameter.builder()
			.name("exchange").displayName("Exchange")
			.type(ParameterType.STRING).defaultValue("")
			.description("The exchange to publish the message to. Leave empty for the default exchange.")
			.placeHolder("my-exchange")
			.build());

		// Routing Key
		params.add(NodeParameter.builder()
			.name("routingKey").displayName("Routing Key")
			.type(ParameterType.STRING).required(true)
			.description("The routing key for the message.")
			.placeHolder("my-routing-key")
			.build());

		// Message
		params.add(NodeParameter.builder()
			.name("message").displayName("Message")
			.type(ParameterType.STRING).required(true)
			.description("The message body to send. Can be plain text or JSON.")
			.typeOptions(Map.of("rows", 4))
			.build());

		// Options
		params.add(NodeParameter.builder()
			.name("options").displayName("Options")
			.type(ParameterType.COLLECTION)
			.nestedParameters(List.of(
				NodeParameter.builder().name("contentType").displayName("Content Type")
					.type(ParameterType.STRING).defaultValue("application/json")
					.description("MIME type of the message body.").build(),
				NodeParameter.builder().name("contentEncoding").displayName("Content Encoding")
					.type(ParameterType.STRING).defaultValue("utf-8")
					.description("Encoding of the message body.").build(),
				NodeParameter.builder().name("deliveryMode").displayName("Delivery Mode")
					.type(ParameterType.OPTIONS).defaultValue("2")
					.options(List.of(
						ParameterOption.builder().name("Non-persistent (1)").value("1").description("Message may be lost on broker restart").build(),
						ParameterOption.builder().name("Persistent (2)").value("2").description("Message survives broker restart").build()
					)).build(),
				NodeParameter.builder().name("priority").displayName("Priority")
					.type(ParameterType.NUMBER).defaultValue(0)
					.description("Message priority (0-9).").build(),
				NodeParameter.builder().name("correlationId").displayName("Correlation ID")
					.type(ParameterType.STRING).defaultValue("")
					.description("Correlation ID for RPC patterns.").build(),
				NodeParameter.builder().name("replyTo").displayName("Reply To")
					.type(ParameterType.STRING).defaultValue("")
					.description("Queue name for replies.").build(),
				NodeParameter.builder().name("expiration").displayName("Expiration (ms)")
					.type(ParameterType.STRING).defaultValue("")
					.description("Message TTL in milliseconds.").build(),
				NodeParameter.builder().name("messageId").displayName("Message ID")
					.type(ParameterType.STRING).defaultValue("")
					.description("Application-provided message ID.").build(),
				NodeParameter.builder().name("headers").displayName("Custom Headers (JSON)")
					.type(ParameterType.STRING).defaultValue("")
					.description("Custom AMQP headers as a JSON object.")
					.typeOptions(Map.of("rows", 3)).build()
			)).build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String exchange = context.getParameter("exchange", "");
		String routingKey = context.getParameter("routingKey", "");
		String message = context.getParameter("message", "");
		Map<String, Object> options = context.getParameter("options", Map.of());
		Map<String, Object> credentials = context.getCredentials();

		if (routingKey.isBlank()) {
			return NodeExecutionResult.error("Routing key is required.");
		}

		String host = String.valueOf(credentials.getOrDefault("host", "localhost"));
		int port = toInt(credentials.getOrDefault("port", 5672), 5672);
		String username = String.valueOf(credentials.getOrDefault("username", "guest"));
		String password = String.valueOf(credentials.getOrDefault("password", "guest"));
		String vhost = String.valueOf(credentials.getOrDefault("vhost", "/"));

		try {
			return sendViaReflection(host, port, username, password, vhost, exchange, routingKey, message, options);
		} catch (ClassNotFoundException e) {
			return NodeExecutionResult.error(
				"AMQP client library not found on classpath. " +
				"To use the AMQP Sender node, add the RabbitMQ AMQP client dependency to your project: " +
				"com.rabbitmq:amqp-client:5.x.x. " +
				"Message was not sent to exchange='" + exchange + "', routingKey='" + routingKey + "'."
			);
		} catch (Exception e) {
			return handleError(context, "AMQP error: " + e.getMessage(), e);
		}
	}

	// ========================= Reflection-based AMQP Send =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult sendViaReflection(String host, int port, String username, String password,
			String vhost, String exchange, String routingKey, String message, Map<String, Object> options) throws Exception {

		// Load AMQP client classes via reflection
		Class<?> connectionFactoryClass = Class.forName("com.rabbitmq.client.ConnectionFactory");
		Object factory = connectionFactoryClass.getDeclaredConstructor().newInstance();

		// Configure factory
		connectionFactoryClass.getMethod("setHost", String.class).invoke(factory, host);
		connectionFactoryClass.getMethod("setPort", int.class).invoke(factory, port);
		connectionFactoryClass.getMethod("setUsername", String.class).invoke(factory, username);
		connectionFactoryClass.getMethod("setPassword", String.class).invoke(factory, password);
		connectionFactoryClass.getMethod("setVirtualHost", String.class).invoke(factory, vhost);

		// Create connection and channel
		Object connection = connectionFactoryClass.getMethod("newConnection").invoke(factory);
		Class<?> connectionClass = Class.forName("com.rabbitmq.client.Connection");
		Object channel = connectionClass.getMethod("createChannel").invoke(connection);
		Class<?> channelClass = Class.forName("com.rabbitmq.client.Channel");

		try {
			// Build message properties
			Class<?> amqpBasicPropertiesClass = Class.forName("com.rabbitmq.client.AMQP$BasicProperties");
			Class<?> builderClass = Class.forName("com.rabbitmq.client.AMQP$BasicProperties$Builder");
			Object propsBuilder = builderClass.getDeclaredConstructor().newInstance();

			String contentType = String.valueOf(options.getOrDefault("contentType", "application/json"));
			String contentEncoding = String.valueOf(options.getOrDefault("contentEncoding", "utf-8"));
			int deliveryMode = toInt(options.getOrDefault("deliveryMode", 2), 2);

			builderClass.getMethod("contentType", String.class).invoke(propsBuilder, contentType);
			builderClass.getMethod("contentEncoding", String.class).invoke(propsBuilder, contentEncoding);
			builderClass.getMethod("deliveryMode", Integer.class).invoke(propsBuilder, deliveryMode);

			String correlationId = String.valueOf(options.getOrDefault("correlationId", ""));
			if (!correlationId.isEmpty()) {
				builderClass.getMethod("correlationId", String.class).invoke(propsBuilder, correlationId);
			}

			String replyTo = String.valueOf(options.getOrDefault("replyTo", ""));
			if (!replyTo.isEmpty()) {
				builderClass.getMethod("replyTo", String.class).invoke(propsBuilder, replyTo);
			}

			String expiration = String.valueOf(options.getOrDefault("expiration", ""));
			if (!expiration.isEmpty()) {
				builderClass.getMethod("expiration", String.class).invoke(propsBuilder, expiration);
			}

			String messageId = String.valueOf(options.getOrDefault("messageId", ""));
			if (!messageId.isEmpty()) {
				builderClass.getMethod("messageId", String.class).invoke(propsBuilder, messageId);
			}

			Object props = builderClass.getMethod("build").invoke(propsBuilder);

			// Publish message
			byte[] messageBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			Method publishMethod = channelClass.getMethod("basicPublish", String.class, String.class, amqpBasicPropertiesClass.getSuperclass(), byte[].class);
			publishMethod.invoke(channel, exchange, routingKey, props, messageBytes);

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("exchange", exchange);
			result.put("routingKey", routingKey);
			result.put("messageSize", messageBytes.length);
			result.put("contentType", contentType);
			result.put("deliveryMode", deliveryMode);
			result.put("timestamp", System.currentTimeMillis());

			return NodeExecutionResult.success(List.of(wrapInJson(result)));

		} finally {
			// Close channel and connection
			try {
				channelClass.getMethod("close").invoke(channel);
			} catch (Exception e) {
				log.debug("Error closing AMQP channel: {}", e.getMessage());
			}
			try {
				connectionClass.getMethod("close").invoke(connection);
			} catch (Exception e) {
				log.debug("Error closing AMQP connection: {}", e.getMessage());
			}
		}
	}
}
