package io.cwc.nodes.impl;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * AMQP Trigger Node -- consumes messages from an AMQP 0-9-1 queue (such as RabbitMQ).
 * Uses reflection to load the RabbitMQ AMQP client library if available on the classpath.
 */
@Slf4j
@Node(
	type = "amqpTrigger",
	displayName = "AMQP Trigger",
	description = "Consume messages from an AMQP 0-9-1 queue (e.g., RabbitMQ).",
	category = "Message Queues / Streaming",
	icon = "amqp",
	credentials = {"amqpApi"},
	trigger = true,
	polling = true
)
public class AmqpTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("queue").displayName("Queue Name")
			.type(ParameterType.STRING).required(true)
			.description("The name of the queue to consume messages from.")
			.placeHolder("my-queue")
			.build());

		params.add(NodeParameter.builder()
			.name("prefetch").displayName("Prefetch Count")
			.type(ParameterType.NUMBER).defaultValue(10)
			.description("Maximum number of unacknowledged messages the server will deliver.")
			.build());

		params.add(NodeParameter.builder()
			.name("maxMessages").displayName("Max Messages")
			.type(ParameterType.NUMBER).defaultValue(10)
			.description("Maximum number of messages to consume per poll.")
			.build());

		params.add(NodeParameter.builder()
			.name("autoAck").displayName("Auto Acknowledge")
			.type(ParameterType.BOOLEAN).defaultValue(true)
			.description("Whether to automatically acknowledge messages upon receipt.")
			.build());

		params.add(NodeParameter.builder()
			.name("noLocal").displayName("No Local")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("If true, the server will not deliver messages published by this connection.")
			.build());

		params.add(NodeParameter.builder()
			.name("exclusive").displayName("Exclusive")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("If true, request exclusive consumer access to the queue.")
			.build());

		params.add(NodeParameter.builder()
			.name("timeout").displayName("Poll Timeout (seconds)")
			.type(ParameterType.NUMBER).defaultValue(5)
			.description("Maximum time to wait for messages during each poll.")
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String queue = context.getParameter("queue", "");
		int prefetch = toInt(context.getParameter("prefetch", 10), 10);
		int maxMessages = toInt(context.getParameter("maxMessages", 10), 10);
		boolean autoAck = toBoolean(context.getParameter("autoAck", true), true);
		boolean noLocal = toBoolean(context.getParameter("noLocal", false), false);
		boolean exclusive = toBoolean(context.getParameter("exclusive", false), false);
		int timeout = toInt(context.getParameter("timeout", 5), 5);
		Map<String, Object> credentials = context.getCredentials();

		if (queue.isBlank()) {
			return NodeExecutionResult.error("Queue name is required.");
		}

		String host = String.valueOf(credentials.getOrDefault("host", "localhost"));
		int port = toInt(credentials.getOrDefault("port", 5672), 5672);
		String username = String.valueOf(credentials.getOrDefault("username", "guest"));
		String password = String.valueOf(credentials.getOrDefault("password", "guest"));
		String vhost = String.valueOf(credentials.getOrDefault("vhost", "/"));

		try {
			return consumeViaReflection(host, port, username, password, vhost, queue,
				prefetch, maxMessages, autoAck, noLocal, exclusive, timeout);
		} catch (ClassNotFoundException e) {
			return NodeExecutionResult.error(
				"AMQP client library not found on classpath. " +
				"To use the AMQP Trigger node, add the RabbitMQ AMQP client dependency to your project: " +
				"com.rabbitmq:amqp-client:5.x.x."
			);
		} catch (Exception e) {
			return handleError(context, "AMQP Trigger error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult consumeViaReflection(String host, int port, String username, String password,
			String vhost, String queue, int prefetch, int maxMessages, boolean autoAck,
			boolean noLocal, boolean exclusive, int timeout) throws Exception {

		// Load AMQP client classes via reflection
		Class<?> connectionFactoryClass = Class.forName("com.rabbitmq.client.ConnectionFactory");
		Object factory = connectionFactoryClass.getDeclaredConstructor().newInstance();

		connectionFactoryClass.getMethod("setHost", String.class).invoke(factory, host);
		connectionFactoryClass.getMethod("setPort", int.class).invoke(factory, port);
		connectionFactoryClass.getMethod("setUsername", String.class).invoke(factory, username);
		connectionFactoryClass.getMethod("setPassword", String.class).invoke(factory, password);
		connectionFactoryClass.getMethod("setVirtualHost", String.class).invoke(factory, vhost);

		Object connection = connectionFactoryClass.getMethod("newConnection").invoke(factory);
		Class<?> connectionClass = Class.forName("com.rabbitmq.client.Connection");
		Object channel = connectionClass.getMethod("createChannel").invoke(connection);
		Class<?> channelClass = Class.forName("com.rabbitmq.client.Channel");

		List<Map<String, Object>> messages = Collections.synchronizedList(new ArrayList<>());

		try {
			// Set prefetch
			channelClass.getMethod("basicQos", int.class).invoke(channel, prefetch);

			// Use basicGet for polling approach (non-blocking)
			Class<?> getResponseClass = Class.forName("com.rabbitmq.client.GetResponse");
			Method basicGetMethod = channelClass.getMethod("basicGet", String.class, boolean.class);

			long deadline = System.currentTimeMillis() + (timeout * 1000L);
			int count = 0;

			while (count < maxMessages && System.currentTimeMillis() < deadline) {
				Object response = basicGetMethod.invoke(channel, queue, autoAck);

				if (response == null) {
					// No message available, wait briefly and retry
					Thread.sleep(100);
					continue;
				}

				// Extract message data
				byte[] bodyBytes = (byte[]) getResponseClass.getMethod("getBody").invoke(response);
				Object envelope = getResponseClass.getMethod("getEnvelope").invoke(response);
				Object props = getResponseClass.getMethod("getProps").invoke(response);

				Class<?> envelopeClass = Class.forName("com.rabbitmq.client.Envelope");
				long deliveryTag = (long) envelopeClass.getMethod("getDeliveryTag").invoke(envelope);
				String exchange = (String) envelopeClass.getMethod("getExchange").invoke(envelope);
				String routingKey = (String) envelopeClass.getMethod("getRoutingKey").invoke(envelope);
				boolean redelivered = (boolean) envelopeClass.getMethod("isRedeliver").invoke(envelope);

				Map<String, Object> msgData = new LinkedHashMap<>();
				msgData.put("payload", new String(bodyBytes, StandardCharsets.UTF_8));
				msgData.put("deliveryTag", deliveryTag);
				msgData.put("exchange", exchange);
				msgData.put("routingKey", routingKey);
				msgData.put("redelivered", redelivered);
				msgData.put("timestamp", System.currentTimeMillis());

				// Extract basic properties if available
				try {
					Class<?> basicPropsClass = Class.forName("com.rabbitmq.client.AMQP$BasicProperties");
					Object contentType = basicPropsClass.getMethod("getContentType").invoke(props);
					if (contentType != null) msgData.put("contentType", contentType);
					Object correlationId = basicPropsClass.getMethod("getCorrelationId").invoke(props);
					if (correlationId != null) msgData.put("correlationId", correlationId);
					Object messageId = basicPropsClass.getMethod("getMessageId").invoke(props);
					if (messageId != null) msgData.put("messageId", messageId);
				} catch (Exception e) {
					log.debug("Could not extract message properties: {}", e.getMessage());
				}

				messages.add(msgData);
				count++;
			}

		} finally {
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

		if (messages.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> msg : messages) {
			results.add(createTriggerItem(msg));
		}
		return NodeExecutionResult.success(results);
	}
}
