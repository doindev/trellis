package io.trellis.nodes.impl;

import java.nio.charset.StandardCharsets;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * MQTT Trigger Node -- subscribes to MQTT topics and receives messages.
 * Uses reflection to load the Eclipse Paho MqttClient library if available
 * on the classpath.
 */
@Slf4j
@Node(
	type = "mqttTrigger",
	displayName = "MQTT Trigger",
	description = "Subscribe to MQTT topics and receive messages.",
	category = "Message Queues / Streaming",
	icon = "mqtt",
	credentials = {"mqttApi"},
	trigger = true,
	polling = true
)
public class MqttTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("topic").displayName("Topic")
			.type(ParameterType.STRING).required(true)
			.description("The MQTT topic to subscribe to. Wildcards (+, #) are supported.")
			.placeHolder("sensors/temperature")
			.build());

		params.add(NodeParameter.builder()
			.name("qos").displayName("QoS Level")
			.type(ParameterType.OPTIONS).defaultValue("1")
			.options(List.of(
				ParameterOption.builder().name("0 - At most once").value("0").description("Fire and forget").build(),
				ParameterOption.builder().name("1 - At least once").value("1").description("Guaranteed delivery, may duplicate").build(),
				ParameterOption.builder().name("2 - Exactly once").value("2").description("Guaranteed single delivery").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("maxMessages").displayName("Max Messages")
			.type(ParameterType.NUMBER).defaultValue(10)
			.description("Maximum number of messages to receive per poll.")
			.build());

		params.add(NodeParameter.builder()
			.name("timeout").displayName("Poll Timeout (seconds)")
			.type(ParameterType.NUMBER).defaultValue(10)
			.description("Maximum time to wait for messages during each poll.")
			.build());

		params.add(NodeParameter.builder()
			.name("options").displayName("Options")
			.type(ParameterType.COLLECTION)
			.nestedParameters(List.of(
				NodeParameter.builder().name("clientId").displayName("Client ID")
					.type(ParameterType.STRING).defaultValue("")
					.description("MQTT client identifier. If empty, a random ID will be generated.").build(),
				NodeParameter.builder().name("cleanSession").displayName("Clean Session")
					.type(ParameterType.BOOLEAN).defaultValue(true)
					.description("Whether to start a clean session.").build(),
				NodeParameter.builder().name("keepAlive").displayName("Keep Alive (seconds)")
					.type(ParameterType.NUMBER).defaultValue(60)
					.description("Keep alive interval in seconds.").build(),
				NodeParameter.builder().name("useSsl").displayName("Use SSL/TLS")
					.type(ParameterType.BOOLEAN).defaultValue(false)
					.description("Whether to use SSL/TLS for the connection.").build()
			)).build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String topic = context.getParameter("topic", "");
		int qos = toInt(context.getParameter("qos", "1"), 1);
		int maxMessages = toInt(context.getParameter("maxMessages", 10), 10);
		int timeout = toInt(context.getParameter("timeout", 10), 10);
		Map<String, Object> options = context.getParameter("options", Map.of());
		Map<String, Object> credentials = context.getCredentials();

		if (topic.isBlank()) {
			return NodeExecutionResult.error("MQTT topic is required.");
		}

		// Build broker URL
		String host = String.valueOf(credentials.getOrDefault("host", "localhost"));
		int port = toInt(credentials.getOrDefault("port", 1883), 1883);
		String username = String.valueOf(credentials.getOrDefault("username", ""));
		String password = String.valueOf(credentials.getOrDefault("password", ""));
		boolean useSsl = toBoolean(options.getOrDefault("useSsl", false), false);

		String protocol = useSsl ? "ssl" : "tcp";
		String brokerUrl = protocol + "://" + host + ":" + port;

		String clientId = String.valueOf(options.getOrDefault("clientId", ""));
		if (clientId.isEmpty()) {
			clientId = "trellis-mqtt-trigger-" + UUID.randomUUID().toString().substring(0, 8);
		}

		try {
			return subscribeViaReflection(brokerUrl, clientId, username, password, topic,
				qos, maxMessages, timeout, options);
		} catch (ClassNotFoundException e) {
			return NodeExecutionResult.error(
				"MQTT client library not found on classpath. " +
				"To use the MQTT Trigger node, add the Eclipse Paho MQTT client dependency to your project: " +
				"org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5 (or later)."
			);
		} catch (Exception e) {
			return handleError(context, "MQTT Trigger error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult subscribeViaReflection(String brokerUrl, String clientId,
			String username, String password, String topic, int qos,
			int maxMessages, int timeout, Map<String, Object> options) throws Exception {

		// Load MQTT client via reflection
		Class<?> mqttClientClass = Class.forName("org.eclipse.paho.client.mqttv3.MqttClient");
		Class<?> mqttConnectOptionsClass = Class.forName("org.eclipse.paho.client.mqttv3.MqttConnectOptions");
		Class<?> mqttCallbackClass = Class.forName("org.eclipse.paho.client.mqttv3.MqttCallback");
		Class<?> memoryPersistenceClass = Class.forName("org.eclipse.paho.client.mqttv3.persist.MemoryPersistence");

		Object persistence = memoryPersistenceClass.getDeclaredConstructor().newInstance();
		Object client = mqttClientClass.getDeclaredConstructor(
			String.class, String.class, Class.forName("org.eclipse.paho.client.mqttv3.MqttClientPersistence")
		).newInstance(brokerUrl, clientId, persistence);

		// Connection options
		Object connOpts = mqttConnectOptionsClass.getDeclaredConstructor().newInstance();
		boolean cleanSession = toBoolean(options.getOrDefault("cleanSession", true), true);
		int keepAlive = toInt(options.getOrDefault("keepAlive", 60), 60);

		mqttConnectOptionsClass.getMethod("setCleanSession", boolean.class).invoke(connOpts, cleanSession);
		mqttConnectOptionsClass.getMethod("setKeepAliveInterval", int.class).invoke(connOpts, keepAlive);

		if (!username.isEmpty()) {
			mqttConnectOptionsClass.getMethod("setUserName", String.class).invoke(connOpts, username);
			mqttConnectOptionsClass.getMethod("setPassword", char[].class).invoke(connOpts, password.toCharArray());
		}

		// Collect messages
		List<Map<String, Object>> messages = Collections.synchronizedList(new ArrayList<>());

		try {
			// Connect
			mqttClientClass.getMethod("connect", mqttConnectOptionsClass).invoke(client, connOpts);

			// Set callback using proxy
			Object callback = java.lang.reflect.Proxy.newProxyInstance(
				mqttCallbackClass.getClassLoader(),
				new Class<?>[]{mqttCallbackClass},
				(proxy, method, args) -> {
					if ("messageArrived".equals(method.getName()) && args.length == 2) {
						String msgTopic = (String) args[0];
						Object mqttMessage = args[1];
						Class<?> mqttMessageClass = Class.forName("org.eclipse.paho.client.mqttv3.MqttMessage");
						byte[] payload = (byte[]) mqttMessageClass.getMethod("getPayload").invoke(mqttMessage);
						int msgQos = (int) mqttMessageClass.getMethod("getQos").invoke(mqttMessage);
						boolean isRetained = (boolean) mqttMessageClass.getMethod("isRetained").invoke(mqttMessage);

						Map<String, Object> msgData = new LinkedHashMap<>();
						msgData.put("topic", msgTopic);
						msgData.put("payload", new String(payload, StandardCharsets.UTF_8));
						msgData.put("qos", msgQos);
						msgData.put("retained", isRetained);
						msgData.put("timestamp", System.currentTimeMillis());
						messages.add(msgData);
					}
					return null;
				}
			);

			mqttClientClass.getMethod("setCallback", mqttCallbackClass).invoke(client, callback);

			// Subscribe
			mqttClientClass.getMethod("subscribe", String.class, int.class).invoke(client, topic, qos);

			// Wait for messages
			long deadline = System.currentTimeMillis() + (timeout * 1000L);
			while (System.currentTimeMillis() < deadline && messages.size() < maxMessages) {
				Thread.sleep(100);
			}

			// Unsubscribe
			mqttClientClass.getMethod("unsubscribe", String.class).invoke(client, topic);

		} finally {
			try {
				mqttClientClass.getMethod("disconnect").invoke(client);
			} catch (Exception e) {
				log.debug("Error disconnecting MQTT client: {}", e.getMessage());
			}
			try {
				mqttClientClass.getMethod("close").invoke(client);
			} catch (Exception e) {
				log.debug("Error closing MQTT client: {}", e.getMessage());
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
