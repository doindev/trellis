package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manual Chat Trigger — starts a chat-based workflow execution.
 * Entry point for interactive chat workflows that connect to AI agents.
 */
@Slf4j
@Node(
		type = "manualChatTrigger",
		displayName = "Manual Chat Trigger",
		description = "Runs the flow on new manual chat message",
		category = "Core Triggers",
		icon = "comments",
		trigger = true,
		triggerFavorite = true
)
public class ManualChatTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("notice")
						.displayName("")
						.description("This node is where a manual chat workflow execution starts. " +
								"To make one, go back to the canvas and click 'Chat'.")
						.type(NodeParameter.ParameterType.NOTICE)
						.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		log.debug("Manual chat trigger fired for workflow: {}", context.getWorkflowId());

		// Get the chat message from the execution input if available
		String chatInput = context.getParameter("chatInput", "");
		String sessionId = context.getParameter("sessionId", "default");

		Map<String, Object> triggerData = Map.of(
				"chatInput", chatInput,
				"sessionId", sessionId,
				"executionMode", "manual_chat",
				"timestamp", Instant.now().toString()
		);

		Map<String, Object> triggerItem = createTriggerItem(triggerData);
		return NodeExecutionResult.success(List.of(triggerItem));
	}
}
