package io.trellis.nodes.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * Kafka Trigger Node -- consumes messages from an Apache Kafka topic.
 * Uses reflection to load the Kafka consumer library if available on the classpath.
 * Stores consumer offset in staticData for continuation across polls.
 */
@Slf4j
@Node(
	type = "kafkaTrigger",
	displayName = "Kafka Trigger",
	description = "Consume messages from an Apache Kafka topic.",
	category = "Message Queues / Streaming",
	icon = "kafka",
	credentials = {"kafkaApi"},
	trigger = true,
	polling = true
)
public class KafkaTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("topic").displayName("Topic")
			.type(ParameterType.STRING).required(true)
			.description("The Kafka topic to consume messages from.")
			.placeHolder("my-topic")
			.build());

		params.add(NodeParameter.builder()
			.name("groupId").displayName("Group ID")
			.type(ParameterType.STRING).required(true).defaultValue("trellis-consumer-group")
			.description("The consumer group ID.")
			.build());

		params.add(NodeParameter.builder()
			.name("fromBeginning").displayName("Read from Beginning")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("If true, start reading from the beginning of the topic on first connect.")
			.build());

		params.add(NodeParameter.builder()
			.name("maxMessages").displayName("Max Messages")
			.type(ParameterType.NUMBER).defaultValue(10)
			.description("Maximum number of messages to consume per poll.")
			.build());

		params.add(NodeParameter.builder()
			.name("pollTimeout").displayName("Poll Timeout (seconds)")
			.type(ParameterType.NUMBER).defaultValue(5)
			.description("Maximum time to wait for messages during each poll.")
			.build());

		params.add(NodeParameter.builder()
			.name("sessionTimeout").displayName("Session Timeout (ms)")
			.type(ParameterType.NUMBER).defaultValue(30000)
			.description("Session timeout for the consumer group.")
			.build());

		params.add(NodeParameter.builder()
			.name("autoCommit").displayName("Auto Commit")
			.type(ParameterType.BOOLEAN).defaultValue(true)
			.description("Whether to automatically commit offsets.")
			.build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String topic = context.getParameter("topic", "");
		String groupId = context.getParameter("groupId", "trellis-consumer-group");
		boolean fromBeginning = toBoolean(context.getParameter("fromBeginning", false), false);
		int maxMessages = toInt(context.getParameter("maxMessages", 10), 10);
		int pollTimeout = toInt(context.getParameter("pollTimeout", 5), 5);
		int sessionTimeout = toInt(context.getParameter("sessionTimeout", 30000), 30000);
		boolean autoCommit = toBoolean(context.getParameter("autoCommit", true), true);
		Map<String, Object> credentials = context.getCredentials();

		if (topic.isBlank()) {
			return NodeExecutionResult.error("Topic is required.");
		}

		String bootstrapServers = String.valueOf(credentials.getOrDefault("bootstrapServers",
			credentials.getOrDefault("brokers", "localhost:9092")));

		try {
			return consumeViaReflection(bootstrapServers, topic, groupId, fromBeginning,
				maxMessages, pollTimeout, sessionTimeout, autoCommit, credentials, context);
		} catch (ClassNotFoundException e) {
			return NodeExecutionResult.error(
				"Kafka client library not found on classpath. " +
				"To use the Kafka Trigger node, add the Apache Kafka client dependency to your project: " +
				"org.apache.kafka:kafka-clients:3.x.x."
			);
		} catch (Exception e) {
			return handleError(context, "Kafka Trigger error: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult consumeViaReflection(String bootstrapServers, String topic, String groupId,
			boolean fromBeginning, int maxMessages, int pollTimeout, int sessionTimeout,
			boolean autoCommit, Map<String, Object> credentials, NodeExecutionContext context) throws Exception {

		// Build consumer properties
		Properties props = new Properties();
		props.put("bootstrap.servers", bootstrapServers);
		props.put("group.id", groupId);
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("enable.auto.commit", String.valueOf(autoCommit));
		props.put("session.timeout.ms", String.valueOf(sessionTimeout));
		props.put("auto.offset.reset", fromBeginning ? "earliest" : "latest");
		props.put("max.poll.records", String.valueOf(maxMessages));

		// Optional security configuration
		String securityProtocol = String.valueOf(credentials.getOrDefault("securityProtocol", ""));
		if (!securityProtocol.isEmpty()) {
			props.put("security.protocol", securityProtocol);
		}
		String saslMechanism = String.valueOf(credentials.getOrDefault("saslMechanism", ""));
		if (!saslMechanism.isEmpty()) {
			props.put("sasl.mechanism", saslMechanism);
		}
		String saslJaasConfig = String.valueOf(credentials.getOrDefault("saslJaasConfig", ""));
		if (!saslJaasConfig.isEmpty()) {
			props.put("sasl.jaas.config", saslJaasConfig);
		}

		// Load KafkaConsumer via reflection
		Class<?> consumerClass = Class.forName("org.apache.kafka.clients.consumer.KafkaConsumer");
		Class<?> consumerRecordsClass = Class.forName("org.apache.kafka.clients.consumer.ConsumerRecords");
		Class<?> consumerRecordClass = Class.forName("org.apache.kafka.clients.consumer.ConsumerRecord");

		Object consumer = consumerClass.getDeclaredConstructor(Properties.class).newInstance(props);

		List<Map<String, Object>> messages = new ArrayList<>();

		try {
			// Subscribe to topic
			consumerClass.getMethod("subscribe", Collection.class).invoke(consumer, List.of(topic));

			// Poll for messages
			Object durationObj = Duration.ofSeconds(pollTimeout);
			Object records = consumerClass.getMethod("poll", Duration.class).invoke(consumer, durationObj);

			// Iterate over records
			Iterable<?> iterable = (Iterable<?>) records;
			int count = 0;
			for (Object record : iterable) {
				if (count >= maxMessages) break;

				String recordKey = (String) consumerRecordClass.getMethod("key").invoke(record);
				String recordValue = (String) consumerRecordClass.getMethod("value").invoke(record);
				int recordPartition = (int) consumerRecordClass.getMethod("partition").invoke(record);
				long recordOffset = (long) consumerRecordClass.getMethod("offset").invoke(record);
				long recordTimestamp = (long) consumerRecordClass.getMethod("timestamp").invoke(record);
				String recordTopic = (String) consumerRecordClass.getMethod("topic").invoke(record);

				Map<String, Object> msgData = new LinkedHashMap<>();
				msgData.put("topic", recordTopic);
				msgData.put("partition", recordPartition);
				msgData.put("offset", recordOffset);
				msgData.put("key", recordKey);
				msgData.put("value", recordValue);
				msgData.put("timestamp", recordTimestamp);

				// Extract headers
				try {
					Object headers = consumerRecordClass.getMethod("headers").invoke(record);
					Iterable<?> headerIterable = (Iterable<?>) headers;
					Map<String, String> headerMap = new LinkedHashMap<>();
					for (Object header : headerIterable) {
						Class<?> headerClass = Class.forName("org.apache.kafka.common.header.Header");
						String hKey = (String) headerClass.getMethod("key").invoke(header);
						byte[] hValue = (byte[]) headerClass.getMethod("value").invoke(header);
						headerMap.put(hKey, hValue != null ? new String(hValue, StandardCharsets.UTF_8) : null);
					}
					if (!headerMap.isEmpty()) {
						msgData.put("headers", headerMap);
					}
				} catch (Exception e) {
					log.debug("Could not extract Kafka message headers: {}", e.getMessage());
				}

				messages.add(msgData);
				count++;
			}

			// Store last offset in static data for continuation
			if (!messages.isEmpty()) {
				Map<String, Object> staticData = context.getStaticData();
				if (staticData != null) {
					Map<String, Object> lastMsg = messages.get(messages.size() - 1);
					staticData.put("lastOffset", lastMsg.get("offset"));
					staticData.put("lastPartition", lastMsg.get("partition"));
					staticData.put("lastTimestamp", lastMsg.get("timestamp"));
				}
			}

		} finally {
			try {
				consumerClass.getMethod("close").invoke(consumer);
			} catch (Exception e) {
				log.debug("Error closing Kafka consumer: {}", e.getMessage());
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
