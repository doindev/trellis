package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiMemoryNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Node(
		type = "memoryMotorhead",
		displayName = "Motorhead",
		description = "Stores chat history using a Motorhead memory server",
		category = "AI / Memory",
		icon = "database",
		credentials = "motorheadApi"
)
public class MotorheadMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String sessionId = context.getParameter("sessionId", "default");

		String host = context.getCredentialString("host", "http://localhost:8080");
		String clientId = context.getCredentialString("clientId");
		String apiKey = context.getCredentialString("apiKey");

		// Remove trailing slash
		if (host.endsWith("/")) {
			host = host.substring(0, host.length() - 1);
		}

		return new MotorheadChatMemory(sessionId, host, clientId, apiKey);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("sessionId").displayName("Session ID")
						.type(ParameterType.STRING)
						.defaultValue("default")
						.description("A unique key to isolate this conversation")
						.build()
		);
	}

	/**
	 * ChatMemory implementation backed by a Motorhead server.
	 * Motorhead manages its own context window server-side, so no window size parameter.
	 *
	 * API:
	 * - GET  /motorhead/sessions/{id}/memory → { messages: [...], context: "..." }
	 * - POST /motorhead/sessions/{id}/memory → { messages: [{ role, content }] }
	 * - DELETE /motorhead/sessions/{id}/memory
	 */
	@Slf4j
	static class MotorheadChatMemory implements ChatMemory {
		private static final ObjectMapper MAPPER = new ObjectMapper();
		private static final HttpClient HTTP = HttpClient.newHttpClient();

		private final String sessionId;
		private final String baseUrl;
		private final String clientId;
		private final String apiKey;
		private final List<ChatMessage> messages = new ArrayList<>();
		private boolean initialized = false;

		MotorheadChatMemory(String sessionId, String host, String clientId, String apiKey) {
			this.sessionId = sessionId;
			this.baseUrl = host + "/motorhead/sessions/" + sessionId + "/memory";
			this.clientId = clientId;
			this.apiKey = apiKey;
		}

		private void init() {
			if (initialized) return;
			initialized = true;
			try {
				HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
						.uri(URI.create(baseUrl))
						.header("Content-Type", "application/json")
						.GET();
				addAuthHeaders(reqBuilder);

				HttpResponse<String> response = HTTP.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() == 200) {
					JsonNode root = MAPPER.readTree(response.body());
					JsonNode messagesNode = root.get("messages");
					if (messagesNode != null && messagesNode.isArray()) {
						// Motorhead returns messages in reverse chronological order
						List<ChatMessage> loaded = new ArrayList<>();
						for (JsonNode msgNode : messagesNode) {
							String role = msgNode.get("role").asText();
							String content = msgNode.get("content").asText();
							if ("Human".equals(role)) {
								loaded.add(UserMessage.from(content));
							} else if ("AI".equals(role)) {
								loaded.add(AiMessage.from(content));
							}
						}
						// Reverse to chronological order
						for (int i = loaded.size() - 1; i >= 0; i--) {
							messages.add(loaded.get(i));
						}
					}
				}
			} catch (Exception e) {
				log.warn("Failed to initialize Motorhead memory: {}", e.getMessage());
			}
		}

		@Override
		public Object id() {
			return sessionId;
		}

		@Override
		public void add(ChatMessage message) {
			init();
			messages.add(message);

			// Send the new message to Motorhead
			try {
				String role;
				String content;
				if (message instanceof UserMessage um) {
					role = "Human";
					content = um.singleText();
				} else if (message instanceof AiMessage am) {
					role = "AI";
					content = am.text() != null ? am.text() : "";
				} else {
					return; // Motorhead only supports Human and AI roles
				}

				String body = MAPPER.writeValueAsString(Map.of(
						"messages", List.of(Map.of("role", role, "content", content))
				));

				HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
						.uri(URI.create(baseUrl))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(body));
				addAuthHeaders(reqBuilder);

				HTTP.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
			} catch (Exception e) {
				log.warn("Failed to save message to Motorhead: {}", e.getMessage());
			}
		}

		@Override
		public List<ChatMessage> messages() {
			init();
			return List.copyOf(messages);
		}

		@Override
		public void clear() {
			messages.clear();
			try {
				HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
						.uri(URI.create(baseUrl))
						.header("Content-Type", "application/json")
						.DELETE();
				addAuthHeaders(reqBuilder);

				HTTP.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
			} catch (Exception e) {
				log.warn("Failed to clear Motorhead memory: {}", e.getMessage());
			}
		}

		private void addAuthHeaders(HttpRequest.Builder builder) {
			if (clientId != null && !clientId.isBlank()) {
				builder.header("x-metal-client-id", clientId);
			}
			if (apiKey != null && !apiKey.isBlank()) {
				builder.header("x-metal-api-key", apiKey);
			}
		}
	}
}
