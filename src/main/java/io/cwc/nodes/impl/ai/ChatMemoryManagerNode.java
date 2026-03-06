package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeInput.InputType;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat Memory Manager — manages chat messages memory with operations
 * to get, insert, or delete messages from a connected memory node.
 */
@Node(
		type = "memoryManager",
		displayName = "Chat Memory Manager",
		description = "Manage chat messages memory and use it in the workflow",
		category = "AI / Memory",
		icon = "database"
)
public class ChatMemoryManagerNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatMemory memory = context.getAiInput("ai_memory", ChatMemory.class);
		if (memory == null) {
			return NodeExecutionResult.error("No memory input connected");
		}

		String mode = context.getParameter("mode", "load");

		try {
			return switch (mode) {
				case "load" -> handleLoad(context, memory);
				case "insert" -> handleInsert(context, memory);
				case "delete" -> handleDelete(context, memory);
				default -> NodeExecutionResult.error("Unknown mode: " + mode);
			};
		} catch (Exception e) {
			if (context.isContinueOnFail()) {
				return handleError(context, e.getMessage(), e);
			}
			throw new RuntimeException(e);
		}
	}

	private NodeExecutionResult handleLoad(NodeExecutionContext context, ChatMemory memory) {
		boolean simplify = toBoolean(context.getParameters().get("simplifyOutput"), true);
		List<ChatMessage> messages = memory.messages();
		List<Map<String, Object>> results = new ArrayList<>();

		for (ChatMessage msg : messages) {
			Map<String, Object> item = new LinkedHashMap<>();
			if (simplify) {
				item.put("type", msg.type().name().toLowerCase());
				item.put("text", getMessageText(msg));
			} else {
				item.put("type", msg.type().name().toLowerCase());
				item.put("text", getMessageText(msg));
				item.put("messageType", msg.type().name());
			}
			results.add(wrapInJson(item));
		}

		if (results.isEmpty()) {
			results.add(wrapInJson(Map.of("empty", true)));
		}

		return NodeExecutionResult.success(results);
	}

	private NodeExecutionResult handleInsert(NodeExecutionContext context, ChatMemory memory) {
		String insertMode = context.getParameter("insertMode", "insert");

		if ("override".equals(insertMode)) {
			memory.clear();
		}

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> messageDefs = (List<Map<String, Object>>)
				context.getParameters().get("messages");

		if (messageDefs != null) {
			for (Map<String, Object> def : messageDefs) {
				String type = toString(def.get("type"), "user");
				String text = toString(def.get("message"), "");
				if (text.isBlank()) continue;

				ChatMessage msg = switch (type.toLowerCase()) {
					case "ai" -> AiMessage.from(text);
					case "system" -> SystemMessage.from(text);
					default -> UserMessage.from(text);
				};
				memory.add(msg);
			}
		}

		return NodeExecutionResult.success(List.of(
				wrapInJson(Map.of("success", true))
		));
	}

	private NodeExecutionResult handleDelete(NodeExecutionContext context, ChatMemory memory) {
		String deleteMode = context.getParameter("deleteMode", "lastN");

		if ("all".equals(deleteMode)) {
			memory.clear();
		} else {
			int count = toInt(context.getParameters().get("messagesCount"), 2);
			List<ChatMessage> messages = new ArrayList<>(memory.messages());
			if (count >= messages.size()) {
				memory.clear();
			} else {
				List<ChatMessage> remaining = messages.subList(0, messages.size() - count);
				memory.clear();
				for (ChatMessage msg : remaining) {
					memory.add(msg);
				}
			}
		}

		return NodeExecutionResult.success(List.of(
				wrapInJson(Map.of("success", true))
		));
	}

	private String getMessageText(ChatMessage msg) {
		if (msg instanceof UserMessage um) return um.singleText();
		if (msg instanceof AiMessage am) return am.text() != null ? am.text() : "";
		if (msg instanceof SystemMessage sm) return sm.text();
		return msg.toString();
	}

	private String toString(Object value, String defaultValue) {
		if (value == null) return defaultValue;
		return value.toString();
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder()
						.name("main").displayName("Main")
						.type(InputType.MAIN).build(),
				NodeInput.builder()
						.name("ai_memory").displayName("Memory")
						.type(InputType.AI_MEMORY)
						.required(true).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("mode").displayName("Operation Mode")
						.type(ParameterType.OPTIONS)
						.defaultValue("load")
						.options(List.of(
								ParameterOption.builder().name("Get Many Messages").value("load").build(),
								ParameterOption.builder().name("Insert Messages").value("insert").build(),
								ParameterOption.builder().name("Delete Messages").value("delete").build()
						)).build(),
				NodeParameter.builder()
						.name("insertMode").displayName("Insert Mode")
						.type(ParameterType.OPTIONS)
						.defaultValue("insert")
						.options(List.of(
								ParameterOption.builder().name("Insert Messages").value("insert").build(),
								ParameterOption.builder().name("Override All Messages").value("override").build()
						))
						.description("Choose whether to append or replace all messages.").build(),
				NodeParameter.builder()
						.name("messages").displayName("Chat Messages")
						.type(ParameterType.JSON)
						.defaultValue("[]")
						.description("Array of messages to insert: [{\"type\": \"user|ai|system\", \"message\": \"...\"}]").build(),
				NodeParameter.builder()
						.name("deleteMode").displayName("Delete Mode")
						.type(ParameterType.OPTIONS)
						.defaultValue("lastN")
						.options(List.of(
								ParameterOption.builder().name("Last N").value("lastN").build(),
								ParameterOption.builder().name("All Messages").value("all").build()
						)).build(),
				NodeParameter.builder()
						.name("messagesCount").displayName("Messages Count")
						.type(ParameterType.NUMBER)
						.defaultValue(2)
						.description("Number of latest messages to delete.").build(),
				NodeParameter.builder()
						.name("simplifyOutput").displayName("Simplify Output")
						.type(ParameterType.BOOLEAN)
						.defaultValue(true)
						.description("Simplify output to show only sender type and text.").build()
		);
	}
}
