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
 * RabbitMQ Node -- sends messages to RabbitMQ queues and exchanges using the
 * RabbitMQ Management HTTP API (port 15672). Also supports queue/exchange
 * management operations via the same API.
 */
@Slf4j
@Node(
	type = "rabbitMq",
	displayName = "RabbitMQ",
	description = "Send messages and manage queues using the RabbitMQ Management HTTP API.",
	category = "Message Queues / Streaming",
	icon = "rabbitMq",
	credentials = {"rabbitMqApi"}
)
public class RabbitMqNode extends AbstractApiNode {

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

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("sendMessage")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Send Message").value("sendMessage").description("Publish a message to an exchange").build(),
				ParameterOption.builder().name("Get Messages").value("getMessages").description("Get messages from a queue").build(),
				ParameterOption.builder().name("List Queues").value("listQueues").description("List all queues").build(),
				ParameterOption.builder().name("List Exchanges").value("listExchanges").description("List all exchanges").build(),
				ParameterOption.builder().name("Create Queue").value("createQueue").description("Declare a new queue").build(),
				ParameterOption.builder().name("Delete Queue").value("deleteQueue").description("Delete a queue").build(),
				ParameterOption.builder().name("Purge Queue").value("purgeQueue").description("Purge all messages from a queue").build(),
				ParameterOption.builder().name("Get Queue Info").value("getQueueInfo").description("Get details about a queue").build()
			)).build());

		// Virtual host
		params.add(NodeParameter.builder()
			.name("vhost").displayName("Virtual Host")
			.type(ParameterType.STRING).defaultValue("/")
			.description("The RabbitMQ virtual host.")
			.build());

		// ========================= Send Message Parameters =========================

		params.add(NodeParameter.builder()
			.name("exchange").displayName("Exchange")
			.type(ParameterType.STRING).defaultValue("amq.default")
			.description("The exchange to publish the message to. Use 'amq.default' for the default exchange (direct to queue).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("sendMessage"))))
			.build());

		params.add(NodeParameter.builder()
			.name("routingKey").displayName("Routing Key")
			.type(ParameterType.STRING).required(true)
			.description("The routing key for the message. For the default exchange, use the queue name.")
			.placeHolder("my-queue")
			.displayOptions(Map.of("show", Map.of("operation", List.of("sendMessage"))))
			.build());

		params.add(NodeParameter.builder()
			.name("message").displayName("Message")
			.type(ParameterType.STRING).required(true)
			.description("The message body to send.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("operation", List.of("sendMessage"))))
			.build());

		params.add(NodeParameter.builder()
			.name("messageOptions").displayName("Message Options")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("operation", List.of("sendMessage"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("contentType").displayName("Content Type")
					.type(ParameterType.STRING).defaultValue("application/json")
					.description("MIME type of the message body.").build(),
				NodeParameter.builder().name("contentEncoding").displayName("Content Encoding")
					.type(ParameterType.STRING).defaultValue("utf-8").build(),
				NodeParameter.builder().name("persistent").displayName("Persistent")
					.type(ParameterType.BOOLEAN).defaultValue(true)
					.description("Whether the message should survive a broker restart.").build(),
				NodeParameter.builder().name("priority").displayName("Priority")
					.type(ParameterType.NUMBER).defaultValue(0)
					.description("Message priority (0-9).").build(),
				NodeParameter.builder().name("correlationId").displayName("Correlation ID")
					.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder().name("replyTo").displayName("Reply To")
					.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder().name("expiration").displayName("Expiration (ms)")
					.type(ParameterType.STRING).defaultValue("")
					.description("Per-message TTL in milliseconds.").build(),
				NodeParameter.builder().name("headers").displayName("Custom Headers (JSON)")
					.type(ParameterType.STRING).defaultValue("")
					.description("Custom AMQP headers as a JSON object.")
					.typeOptions(Map.of("rows", 3)).build()
			)).build());

		// ========================= Get Messages Parameters =========================

		params.add(NodeParameter.builder()
			.name("queue").displayName("Queue Name")
			.type(ParameterType.STRING).required(true)
			.placeHolder("my-queue")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getMessages", "createQueue", "deleteQueue", "purgeQueue", "getQueueInfo"))))
			.build());

		params.add(NodeParameter.builder()
			.name("count").displayName("Message Count")
			.type(ParameterType.NUMBER).defaultValue(1)
			.description("Number of messages to get from the queue.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getMessages"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ackMode").displayName("Acknowledge Mode")
			.type(ParameterType.OPTIONS).defaultValue("ack_requeue_true")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getMessages"))))
			.options(List.of(
				ParameterOption.builder().name("Nack / Requeue").value("ack_requeue_true").description("Leave message in queue").build(),
				ParameterOption.builder().name("Ack").value("ack_requeue_false").description("Remove message from queue").build(),
				ParameterOption.builder().name("Reject / Requeue").value("reject_requeue_true").description("Reject but requeue").build(),
				ParameterOption.builder().name("Reject").value("reject_requeue_false").description("Reject and discard").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("encoding").displayName("Encoding")
			.type(ParameterType.OPTIONS).defaultValue("auto")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getMessages"))))
			.options(List.of(
				ParameterOption.builder().name("Auto").value("auto").build(),
				ParameterOption.builder().name("Base64").value("base64").build()
			)).build());

		// ========================= Create Queue Parameters =========================

		params.add(NodeParameter.builder()
			.name("queueOptions").displayName("Queue Options")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("operation", List.of("createQueue"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("durable").displayName("Durable")
					.type(ParameterType.BOOLEAN).defaultValue(true)
					.description("Survive broker restart.").build(),
				NodeParameter.builder().name("autoDelete").displayName("Auto Delete")
					.type(ParameterType.BOOLEAN).defaultValue(false)
					.description("Delete when no consumers.").build(),
				NodeParameter.builder().name("exclusive").displayName("Exclusive")
					.type(ParameterType.BOOLEAN).defaultValue(false)
					.description("Restrict to this connection.").build(),
				NodeParameter.builder().name("messageTtl").displayName("Message TTL (ms)")
					.type(ParameterType.NUMBER).defaultValue(0)
					.description("Default message time-to-live (0 = no TTL).").build(),
				NodeParameter.builder().name("maxLength").displayName("Max Queue Length")
					.type(ParameterType.NUMBER).defaultValue(0)
					.description("Maximum number of messages (0 = unlimited).").build(),
				NodeParameter.builder().name("deadLetterExchange").displayName("Dead Letter Exchange")
					.type(ParameterType.STRING).defaultValue("")
					.description("Exchange for dead-lettered messages.").build()
			)).build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "sendMessage");
		Map<String, Object> credentials = context.getCredentials();

		String host = String.valueOf(credentials.getOrDefault("host", "localhost"));
		int port = toInt(credentials.getOrDefault("managementPort", credentials.getOrDefault("port", 15672)), 15672);
		String username = String.valueOf(credentials.getOrDefault("username", "guest"));
		String password = String.valueOf(credentials.getOrDefault("password", "guest"));
		boolean useSsl = toBoolean(credentials.getOrDefault("ssl", false), false);

		String protocol = useSsl ? "https" : "http";
		String baseUrl = protocol + "://" + host + ":" + port + "/api";

		Map<String, String> headers = getAuthHeaders(username, password);

		try {
			return switch (operation) {
				case "sendMessage" -> executeSendMessage(context, baseUrl, headers);
				case "getMessages" -> executeGetMessages(context, baseUrl, headers);
				case "listQueues" -> executeListQueues(context, baseUrl, headers);
				case "listExchanges" -> executeListExchanges(context, baseUrl, headers);
				case "createQueue" -> executeCreateQueue(context, baseUrl, headers);
				case "deleteQueue" -> executeDeleteQueue(context, baseUrl, headers);
				case "purgeQueue" -> executePurgeQueue(context, baseUrl, headers);
				case "getQueueInfo" -> executeGetQueueInfo(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "RabbitMQ error: " + e.getMessage(), e);
		}
	}

	// ========================= Send Message =========================

	private NodeExecutionResult executeSendMessage(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String exchange = context.getParameter("exchange", "amq.default");
		String routingKey = context.getParameter("routingKey", "");
		String message = context.getParameter("message", "");
		Map<String, Object> msgOptions = context.getParameter("messageOptions", Map.of());

		if (routingKey.isBlank()) {
			return NodeExecutionResult.error("Routing key is required for sending messages.");
		}

		// Build the publish request body per RabbitMQ Management API
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("routing_key", routingKey);
		body.put("payload", message);
		body.put("payload_encoding", "string");

		// Build properties
		Map<String, Object> properties = new LinkedHashMap<>();
		String contentType = String.valueOf(msgOptions.getOrDefault("contentType", "application/json"));
		properties.put("content_type", contentType);

		String contentEncoding = String.valueOf(msgOptions.getOrDefault("contentEncoding", "utf-8"));
		if (!contentEncoding.isEmpty()) {
			properties.put("content_encoding", contentEncoding);
		}

		boolean persistent = toBoolean(msgOptions.getOrDefault("persistent", true), true);
		properties.put("delivery_mode", persistent ? 2 : 1);

		int priority = toInt(msgOptions.getOrDefault("priority", 0), 0);
		if (priority > 0) {
			properties.put("priority", priority);
		}

		String correlationId = String.valueOf(msgOptions.getOrDefault("correlationId", ""));
		if (!correlationId.isEmpty()) {
			properties.put("correlation_id", correlationId);
		}

		String replyTo = String.valueOf(msgOptions.getOrDefault("replyTo", ""));
		if (!replyTo.isEmpty()) {
			properties.put("reply_to", replyTo);
		}

		String expiration = String.valueOf(msgOptions.getOrDefault("expiration", ""));
		if (!expiration.isEmpty()) {
			properties.put("expiration", expiration);
		}

		String customHeaders = String.valueOf(msgOptions.getOrDefault("headers", ""));
		if (!customHeaders.isEmpty()) {
			try {
				Map<String, Object> parsedHeaders = parseJson(customHeaders);
				properties.put("headers", parsedHeaders);
			} catch (Exception e) {
				log.warn("Could not parse custom headers as JSON: {}", customHeaders);
			}
		}

		body.put("properties", properties);

		String url = baseUrl + "/exchanges/" + encode(vhost) + "/" + encode(exchange) + "/publish";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		boolean routed = toBoolean(parsed.getOrDefault("routed", false), false);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("routed", routed);
		result.put("exchange", exchange);
		result.put("routingKey", routingKey);
		result.put("messageSize", message.length());
		result.put("persistent", persistent);
		result.put("timestamp", System.currentTimeMillis());

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Get Messages =========================

	private NodeExecutionResult executeGetMessages(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String queue = context.getParameter("queue", "");
		int count = toInt(context.getParameter("count", 1), 1);
		String ackMode = context.getParameter("ackMode", "ack_requeue_true");
		String encoding = context.getParameter("encoding", "auto");

		if (queue.isBlank()) {
			return NodeExecutionResult.error("Queue name is required.");
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("count", count);
		body.put("ackmode", ackMode);
		body.put("encoding", encoding);

		String url = baseUrl + "/queues/" + encode(vhost) + "/" + encode(queue) + "/get";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
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
			results.add(wrapInJson(item));
		}

		return NodeExecutionResult.success(results);
	}

	// ========================= List Queues =========================

	private NodeExecutionResult executeListQueues(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String url = baseUrl + "/queues/" + encode(vhost);
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
		}

		List<Map<String, Object>> queues = parseArrayResponse(response);
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> queue : queues) {
			results.add(wrapInJson(queue));
		}

		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	// ========================= List Exchanges =========================

	private NodeExecutionResult executeListExchanges(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String url = baseUrl + "/exchanges/" + encode(vhost);
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
		}

		List<Map<String, Object>> exchanges = parseArrayResponse(response);
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> exchange : exchanges) {
			results.add(wrapInJson(exchange));
		}

		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	// ========================= Create Queue =========================

	private NodeExecutionResult executeCreateQueue(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String queue = context.getParameter("queue", "");
		Map<String, Object> queueOpts = context.getParameter("queueOptions", Map.of());

		if (queue.isBlank()) {
			return NodeExecutionResult.error("Queue name is required.");
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("durable", toBoolean(queueOpts.getOrDefault("durable", true), true));
		body.put("auto_delete", toBoolean(queueOpts.getOrDefault("autoDelete", false), false));
		body.put("exclusive", toBoolean(queueOpts.getOrDefault("exclusive", false), false));

		// Queue arguments
		Map<String, Object> arguments = new LinkedHashMap<>();
		int messageTtl = toInt(queueOpts.getOrDefault("messageTtl", 0), 0);
		if (messageTtl > 0) {
			arguments.put("x-message-ttl", messageTtl);
		}
		int maxLength = toInt(queueOpts.getOrDefault("maxLength", 0), 0);
		if (maxLength > 0) {
			arguments.put("x-max-length", maxLength);
		}
		String dlx = String.valueOf(queueOpts.getOrDefault("deadLetterExchange", ""));
		if (!dlx.isEmpty()) {
			arguments.put("x-dead-letter-exchange", dlx);
		}
		if (!arguments.isEmpty()) {
			body.put("arguments", arguments);
		}

		String url = baseUrl + "/queues/" + encode(vhost) + "/" + encode(queue);
		HttpResponse<String> response = put(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("queue", queue);
		result.put("vhost", vhost);
		result.put("durable", body.get("durable"));
		result.put("autoDelete", body.get("auto_delete"));
		result.put("statusCode", response.statusCode());

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Delete Queue =========================

	private NodeExecutionResult executeDeleteQueue(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String queue = context.getParameter("queue", "");

		if (queue.isBlank()) {
			return NodeExecutionResult.error("Queue name is required.");
		}

		String url = baseUrl + "/queues/" + encode(vhost) + "/" + encode(queue);
		HttpResponse<String> response = delete(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("queue", queue);
		result.put("vhost", vhost);
		result.put("action", "deleted");
		result.put("statusCode", response.statusCode());

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Purge Queue =========================

	private NodeExecutionResult executePurgeQueue(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String queue = context.getParameter("queue", "");

		if (queue.isBlank()) {
			return NodeExecutionResult.error("Queue name is required.");
		}

		String url = baseUrl + "/queues/" + encode(vhost) + "/" + encode(queue) + "/contents";
		HttpResponse<String> response = delete(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("queue", queue);
		result.put("vhost", vhost);
		result.put("action", "purged");
		result.put("statusCode", response.statusCode());

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	// ========================= Get Queue Info =========================

	private NodeExecutionResult executeGetQueueInfo(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String vhost = context.getParameter("vhost", "/");
		String queue = context.getParameter("queue", "");

		if (queue.isBlank()) {
			return NodeExecutionResult.error("Queue name is required.");
		}

		String url = baseUrl + "/queues/" + encode(vhost) + "/" + encode(queue);
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("RabbitMQ", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String username, String password) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		String auth = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
		headers.put("Authorization", "Basic " + auth);
		return headers;
	}

	private NodeExecutionResult apiError(String service, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error(service + " API error (HTTP " + response.statusCode() + "): " + body);
	}
}
