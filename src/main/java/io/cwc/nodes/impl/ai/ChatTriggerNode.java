package io.cwc.nodes.impl.ai;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Chat Trigger — trigger node for chat-based workflows.
 * Designed for webhook/API-based chat input, as opposed to ManualChatTriggerNode
 * which is for interactive manual usage. Accepts chat messages from external sources
 * and starts a workflow execution.
 */
@Slf4j
@Node(
		type = "chatTrigger",
		displayName = "Chat Trigger",
		description = "Triggers workflow execution from chat input via webhook or API",
		category = "AI",
		icon = "comments",
		trigger = true,
		triggerFavorite = true
)
public class ChatTriggerNode extends AbstractTriggerNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		log.debug("Chat trigger fired for workflow: {}", context.getWorkflowId());

		String chatInput = context.getParameter("chatInput", "");
		String sessionId = context.getParameter("sessionId", "default");
		String allowedOrigins = context.getParameter("allowedOrigins", "*");
		String initialMessages = context.getParameter("initialMessages", "");

		Map<String, Object> triggerData = Map.of(
				"chatInput", chatInput,
				"sessionId", sessionId,
				"allowedOrigins", allowedOrigins,
				"initialMessages", initialMessages,
				"executionMode", "chat_trigger",
				"timestamp", Instant.now().toString()
		);

		Map<String, Object> triggerItem = createTriggerItem(triggerData);
		return NodeExecutionResult.success(List.of(triggerItem));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("allowedOrigins").displayName("Allowed Origins")
						.type(ParameterType.STRING)
						.defaultValue("*")
						.description("Comma-separated list of allowed origins for CORS. "
								+ "Use * to allow all origins.").build(),
				NodeParameter.builder()
						.name("initialMessages").displayName("Initial Messages")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 3))
						.defaultValue("")
						.description("Optional initial messages to display when the chat starts. "
								+ "One message per line.").build()
		);
	}
}
