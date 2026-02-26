package io.trellis.nodes.impl;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
 * Kafka Node -- produces messages to Apache Kafka topics.
 * Uses reflection to load the Kafka client library if available on the classpath.
 */
@Slf4j
@Node(
	type = "kafka",
	displayName = "Kafka",
	description = "Produce messages to Apache Kafka topics.",
	category = "Message Queues / Streaming",
	icon = "kafka",
	credentials = {"kafkaApi"}
)
public class KafkaNode extends AbstractNode {

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

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("sendMessage")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Send Message").value("sendMessage").description("Produce a message to a Kafka topic").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("topic").displayName("Topic")
			.type(ParameterType.STRING).required(true)
			.description("The Kafka topic to produce the message to.")
			.placeHolder("my-topic")
			.build());

		params.add(NodeParameter.builder()
			.name("message").displayName("Message")
			.type(ParameterType.STRING).required(true)
			.description("The message value to send.")
			.typeOptions(Map.of("rows", 4))
			.build());

		params.add(NodeParameter.builder()
			.name("key").displayName("Message Key")
			.type(ParameterType.STRING).defaultValue("")
			.description("Optional message key for partitioning.")
			.build());

		params.add(NodeParameter.builder()
			.name("partition").displayName("Partition")
			.type(ParameterType.NUMBER).defaultValue(-1)
			.description("Specific partition to send to. Use -1 for automatic partitioning.")
			.build());

		params.add(NodeParameter.builder()
			.name("headers").displayName("Headers (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Message headers as a JSON object (e.g. {\"key1\": \"value1\"}).")
			.build());

		params.add(NodeParameter.builder()
			.name("compression").displayName("Compression")
			.type(ParameterType.OPTIONS).defaultValue("none")
			.options(List.of(
				ParameterOption.builder().name("None").value("none").description("No compression").build(),
				ParameterOption.builder().name("GZIP").value("gzip").description("GZIP compression").build(),
				ParameterOption.builder().name("Snappy").value("snappy").description("Snappy compression").build(),
				ParameterOption.builder().name("LZ4").value("lz4").description("LZ4 compression").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("acks").displayName("Acknowledgements")
			.type(ParameterType.OPTIONS).defaultValue("all")
			.options(List.of(
				ParameterOption.builder().name("All").value("all").description("Wait for all replicas to acknowledge").build(),
				ParameterOption.builder().name("Leader Only (1)").value("1").description("Wait for leader to acknowledge").build(),
				ParameterOption.builder().name("None (0)").value("0").description("No acknowledgement required").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("timeout").displayName("Send Timeout (ms)")
			.type(ParameterType.NUMBER).defaultValue(30000)
			.description("Maximum time to wait for the send to complete.")
			.build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String topic = context.getParameter("topic", "");
		String message = context.getParameter("message", "");
		String key = context.getParameter("key", "");
		int partition = toInt(context.getParameter("partition", -1), -1);
		String headersJson = context.getParameter("headers", "{}");
		String compression = context.getParameter("compression", "none");
		String acks = context.getParameter("acks", "all");
		int timeout = toInt(context.getParameter("timeout", 30000), 30000);
		Map<String, Object> credentials = context.getCredentials();

		if (topic.isBlank()) {
			return NodeExecutionResult.error("Topic is required.");
		}
		if (message.isBlank()) {
			return NodeExecutionResult.error("Message is required.");
		}

		String bootstrapServers = String.valueOf(credentials.getOrDefault("bootstrapServers",
			credentials.getOrDefault("brokers", "localhost:9092")));

		try {
			return sendViaReflection(bootstrapServers, topic, key, message, partition,
				headersJson, compression, acks, timeout, credentials);
		} catch (ClassNotFoundException e) {
			return NodeExecutionResult.error(
				"Kafka client library not found on classpath. " +
				"To use the Kafka node, add the Apache Kafka client dependency to your project: " +
				"org.apache.kafka:kafka-clients:3.x.x. " +
				"Message was not sent to topic '" + topic + "'."
			);
		} catch (Exception e) {
			return handleError(context, "Kafka error: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult sendViaReflection(String bootstrapServers, String topic, String key,
			String message, int partition, String headersJson, String compression,
			String acks, int timeout, Map<String, Object> credentials) throws Exception {

		// Build producer properties
		Properties props = new Properties();
		props.put("bootstrap.servers", bootstrapServers);
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("acks", acks);
		props.put("compression.type", compression);

		// Optional SSL/SASL configuration from credentials
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

		// Load KafkaProducer via reflection
		Class<?> producerClass = Class.forName("org.apache.kafka.clients.producer.KafkaProducer");
		Class<?> producerRecordClass = Class.forName("org.apache.kafka.clients.producer.ProducerRecord");
		Class<?> recordMetadataClass = Class.forName("org.apache.kafka.clients.producer.RecordMetadata");

		Object producer = producerClass.getDeclaredConstructor(Properties.class).newInstance(props);

		try {
			// Build ProducerRecord
			Object record;
			if (partition >= 0) {
				// ProducerRecord(String topic, Integer partition, String key, String value)
				record = producerRecordClass.getDeclaredConstructor(
					String.class, Integer.class, Object.class, Object.class
				).newInstance(topic, partition, key.isEmpty() ? null : key, message);
			} else if (!key.isEmpty()) {
				// ProducerRecord(String topic, String key, String value)
				record = producerRecordClass.getDeclaredConstructor(
					String.class, Object.class, Object.class
				).newInstance(topic, key, message);
			} else {
				// ProducerRecord(String topic, String value)
				record = producerRecordClass.getDeclaredConstructor(
					String.class, Object.class
				).newInstance(topic, message);
			}

			// Add headers if provided
			Map<String, Object> headerMap = parseHeadersJson(headersJson);
			if (!headerMap.isEmpty()) {
				Class<?> headersClass = Class.forName("org.apache.kafka.common.header.Headers");
				Object recordHeaders = producerRecordClass.getMethod("headers").invoke(record);
				Method addMethod = headersClass.getMethod("add", String.class, byte[].class);
				for (Map.Entry<String, Object> entry : headerMap.entrySet()) {
					addMethod.invoke(recordHeaders, entry.getKey(),
						String.valueOf(entry.getValue()).getBytes(StandardCharsets.UTF_8));
				}
			}

			// Send the record and get metadata
			Object future = producerClass.getMethod("send", producerRecordClass).invoke(producer, record);
			Object metadata = future.getClass().getMethod("get", long.class, java.util.concurrent.TimeUnit.class)
				.invoke(future, (long) timeout, java.util.concurrent.TimeUnit.MILLISECONDS);

			// Extract result metadata
			long offset = (long) recordMetadataClass.getMethod("offset").invoke(metadata);
			int resultPartition = (int) recordMetadataClass.getMethod("partition").invoke(metadata);
			long timestamp = (long) recordMetadataClass.getMethod("timestamp").invoke(metadata);

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("topic", topic);
			result.put("partition", resultPartition);
			result.put("offset", offset);
			result.put("timestamp", timestamp);
			result.put("key", key.isEmpty() ? null : key);
			result.put("messageSize", message.getBytes(StandardCharsets.UTF_8).length);
			result.put("compression", compression);

			return NodeExecutionResult.success(List.of(wrapInJson(result)));

		} finally {
			try {
				producerClass.getMethod("close").invoke(producer);
			} catch (Exception e) {
				log.debug("Error closing Kafka producer: {}", e.getMessage());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseHeadersJson(String json) {
		if (json == null || json.isBlank() || "{}".equals(json.trim())) {
			return Map.of();
		}
		try {
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
		} catch (Exception e) {
			log.warn("Could not parse headers JSON: {}", json);
			return Map.of();
		}
	}
}
