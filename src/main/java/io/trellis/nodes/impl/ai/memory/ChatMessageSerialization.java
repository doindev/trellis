package io.trellis.nodes.impl.ai.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for serializing/deserializing LangChain4j ChatMessage objects to/from JSON.
 * Used by all persistent chat memory store implementations.
 */
public final class ChatMessageSerialization {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private ChatMessageSerialization() {}

	public static String serialize(List<ChatMessage> messages) {
		try {
			ArrayNode array = MAPPER.createArrayNode();
			for (ChatMessage msg : messages) {
				ObjectNode node = MAPPER.createObjectNode();
				if (msg instanceof SystemMessage sm) {
					node.put("type", "SYSTEM");
					node.put("text", sm.text());
				} else if (msg instanceof UserMessage um) {
					node.put("type", "USER");
					node.put("text", um.singleText());
				} else if (msg instanceof AiMessage am) {
					node.put("type", "AI");
					node.put("text", am.text() != null ? am.text() : "");
				} else if (msg instanceof ToolExecutionResultMessage tm) {
					node.put("type", "TOOL_RESULT");
					node.put("text", tm.text());
					node.put("toolName", tm.toolName());
					node.put("id", tm.id());
				}
				if (node.has("type")) {
					array.add(node);
				}
			}
			return MAPPER.writeValueAsString(array);
		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize chat messages", e);
		}
	}

	public static List<ChatMessage> deserialize(String json) {
		if (json == null || json.isBlank()) return new ArrayList<>();
		try {
			JsonNode array = MAPPER.readTree(json);
			List<ChatMessage> messages = new ArrayList<>();
			for (JsonNode node : array) {
				String type = node.get("type").asText();
				String text = node.has("text") ? node.get("text").asText() : "";
				switch (type) {
					case "SYSTEM" -> messages.add(SystemMessage.from(text));
					case "USER" -> messages.add(UserMessage.from(text));
					case "AI" -> messages.add(AiMessage.from(text));
					case "TOOL_RESULT" -> messages.add(ToolExecutionResultMessage.from(
							node.has("id") ? node.get("id").asText() : null,
							node.has("toolName") ? node.get("toolName").asText() : "",
							text
					));
				}
			}
			return messages;
		} catch (Exception e) {
			throw new RuntimeException("Failed to deserialize chat messages", e);
		}
	}
}
