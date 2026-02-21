package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiMemoryNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.impl.ai.memory.ChatMessageSerialization;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Node(
		type = "memoryXata",
		displayName = "Xata",
		description = "Stores chat history using the Xata database API",
		category = "AI / Memory",
		icon = "database",
		credentials = "xataApi"
)
public class XataChatMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int windowSize = toInt(context.getParameters().get("contextWindowLength"), 10);
		String sessionId = context.getParameter("sessionId", "default");

		String apiKey = context.getCredentialString("apiKey");
		String databaseEndpoint = context.getCredentialString("databaseEndpoint");
		String branch = context.getCredentialString("branch", "main");

		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalArgumentException("Xata API key is required.");
		}
		if (databaseEndpoint == null || databaseEndpoint.isBlank()) {
			throw new IllegalArgumentException("Xata database endpoint is required.");
		}

		ChatMemoryStore store = new XataChatMemoryStore(apiKey, databaseEndpoint, branch);

		return MessageWindowChatMemory.builder()
				.id(sessionId)
				.maxMessages(windowSize)
				.chatMemoryStore(store)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("sessionId").displayName("Session ID")
						.type(ParameterType.STRING)
						.defaultValue("default")
						.description("A unique key to isolate this conversation")
						.build(),
				NodeParameter.builder()
						.name("contextWindowLength").displayName("Context Window Length")
						.type(ParameterType.NUMBER)
						.defaultValue(10)
						.description("Number of recent messages to keep in memory")
						.build()
		);
	}

	/**
	 * ChatMemoryStore backed by the Xata REST API.
	 * Uses the /data endpoint to store and retrieve session records.
	 * Records are keyed by session ID in a "chat_memories" table.
	 */
	@Slf4j
	static class XataChatMemoryStore implements ChatMemoryStore {
		private static final ObjectMapper MAPPER = new ObjectMapper();
		private static final HttpClient HTTP = HttpClient.newHttpClient();
		private static final Pattern DB_PATTERN = Pattern.compile("https://([^.]+)\\.([^.]+)\\.xata\\.sh/db/([^/:]+)");
		private static final String TABLE_NAME = "chat_memories";

		private final String apiKey;
		private final String baseUrl;

		XataChatMemoryStore(String apiKey, String databaseEndpoint, String branch) {
			this.apiKey = apiKey;

			// Parse database endpoint: https://{workspace}.{region}.xata.sh/db/{database}
			Matcher matcher = DB_PATTERN.matcher(databaseEndpoint);
			if (!matcher.find()) {
				throw new IllegalArgumentException(
						"Invalid Xata database endpoint. Expected format: https://<workspace>.<region>.xata.sh/db/<database>");
			}
			String workspace = matcher.group(1);
			String region = matcher.group(2);
			String database = matcher.group(3);

			// REST API base: https://{workspace}.{region}.xata.sh/db/{database}:{branch}
			this.baseUrl = String.format("https://%s.%s.xata.sh/db/%s:%s", workspace, region, database, branch);
		}

		@Override
		public List<ChatMessage> getMessages(Object memoryId) {
			try {
				// Query the table for this session ID
				String queryUrl = baseUrl + "/tables/" + TABLE_NAME + "/query";
				String body = MAPPER.writeValueAsString(Map.of(
						"filter", Map.of("session_id", memoryId.toString())
				));

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(queryUrl))
						.header("Authorization", "Bearer " + apiKey)
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(body))
						.build();

				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() == 200) {
					JsonNode root = MAPPER.readTree(response.body());
					JsonNode records = root.get("records");
					if (records != null && records.isArray() && !records.isEmpty()) {
						String messages = records.get(0).get("messages").asText();
						return ChatMessageSerialization.deserialize(messages);
					}
				}
			} catch (Exception e) {
				log.warn("Failed to read messages from Xata: {}", e.getMessage());
			}
			return List.of();
		}

		@Override
		public void updateMessages(Object memoryId, List<ChatMessage> messages) {
			String json = ChatMessageSerialization.serialize(messages);
			try {
				// Upsert: use the session ID as the record ID
				String recordUrl = baseUrl + "/tables/" + TABLE_NAME + "/data/" + memoryId.toString();
				String body = MAPPER.writeValueAsString(Map.of(
						"session_id", memoryId.toString(),
						"messages", json
				));

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(recordUrl))
						.header("Authorization", "Bearer " + apiKey)
						.header("Content-Type", "application/json")
						.PUT(HttpRequest.BodyPublishers.ofString(body))
						.build();

				HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (Exception e) {
				throw new RuntimeException("Failed to update messages in Xata", e);
			}
		}

		@Override
		public void deleteMessages(Object memoryId) {
			try {
				String recordUrl = baseUrl + "/tables/" + TABLE_NAME + "/data/" + memoryId.toString();

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(recordUrl))
						.header("Authorization", "Bearer " + apiKey)
						.DELETE()
						.build();

				HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (Exception e) {
				log.warn("Failed to delete messages from Xata: {}", e.getMessage());
			}
		}
	}
}
