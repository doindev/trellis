package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ Trigger Node -- consumes messages from a RabbitMQ queue via the
 * Management HTTP API. Uses POST /api/queues/{vhost}/{queue}/get to
 * consume messages.
 */
@Slf4j
@Node(
	type = "rabbitMqTrigger",
	displayName = "RabbitMQ Trigger",
	description = "Consume messages from a RabbitMQ queue using the Management HTTP API.",
	category = "Message Queues / Streaming",
	icon = "rabbitMq",
	credentials = {"rabbitMqApi"},
	trigger = true,
	polling = true
)
public class RabbitMqTriggerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		// Trigger nodes have no inputs
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

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
			.name("vhost").displayName("Virtual Host")
			.type(ParameterType.STRING).defaultValue("/")
			.description("The RabbitMQ virtual host.")
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
			.name("requeue").displayName("Requeue on Nack")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to requeue messages that are not acknowledged.")
			.build());

		params.add(NodeParameter.builder()
			.name("encoding").displayName("Payload Encoding")
			.type(ParameterType.OPTIONS).defaultValue("auto")
			.options(List.of(
				ParameterOption.builder().name("Auto").value("auto").description("Auto-detect encoding").build(),
				ParameterOption.builder().name("Base64").value("base64").description("Return payload as Base64").build()
			)).build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String queue = context.getParameter("queue", "");
		String vhost = context.getParameter("vhost", "/");
		int maxMessages = toInt(context.getParameter("maxMessages", 10), 10);
		boolean autoAck = toBoolean(context.getParameter("autoAck", true), true);
		boolean requeue = toBoolean(context.getParameter("requeue", false), false);
		String encoding = context.getParameter("encoding", "auto");
		Map<String, Object> credentials = context.getCredentials();

		if (queue.isBlank()) {
			return NodeExecutionResult.error("Queue name is required.");
		}

		String host = String.valueOf(credentials.getOrDefault("host", "localhost"));
		int port = toInt(credentials.getOrDefault("managementPort", credentials.getOrDefault("port", 15672)), 15672);
		String username = String.valueOf(credentials.getOrDefault("username", "guest"));
		String password = String.valueOf(credentials.getOrDefault("password", "guest"));
		boolean useSsl = toBoolean(credentials.getOrDefault("ssl", false), false);

		String protocol = useSsl ? "https" : "http";
		String baseUrl = protocol + "://" + host + ":" + port + "/api";

		Map<String, String> headers = getAuthHeaders(username, password);

		try {
			// Build the get-messages request body per RabbitMQ Management API
			String ackMode;
			if (autoAck) {
				ackMode = "ack_requeue_false";
			} else if (requeue) {
				ackMode = "ack_requeue_true";
			} else {
				ackMode = "reject_requeue_false";
			}

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("count", maxMessages);
			body.put("ackmode", ackMode);
			body.put("encoding", encoding);

			String url = baseUrl + "/queues/" + encode(vhost) + "/" + encode(queue) + "/get";
			HttpResponse<String> response = post(url, body, headers);

			if (response.statusCode() >= 400) {
				return apiError(response);
			}

			List<Map<String, Object>> messages = parseArrayResponse(response);

			if (messages.isEmpty()) {
				return NodeExecutionResult.empty();
			}

			List<Map<String, Object>> results = new ArrayList<>();
			for (Map<String, Object> msg : messages) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("payload", msg.get("payload"));
				item.put("payloadEncoding", msg.get("payload_encoding"));
				item.put("exchange", msg.get("exchange"));
				item.put("routingKey", msg.get("routing_key"));
				item.put("messageCount", msg.get("message_count"));
				item.put("redelivered", msg.get("redelivered"));
				item.put("properties", msg.get("properties"));
				item.put("_triggerTimestamp", System.currentTimeMillis());
				results.add(wrapInJson(item));
			}

			return NodeExecutionResult.success(results);

		} catch (Exception e) {
			return handleError(context, "RabbitMQ Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String username, String password) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		String auth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
		headers.put("Authorization", "Basic " + auth);
		return headers;
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("RabbitMQ API error (HTTP " + response.statusCode() + "): " + body);
	}
}
