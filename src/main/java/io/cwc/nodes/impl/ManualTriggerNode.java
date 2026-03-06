package io.cwc.nodes.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manual Trigger Node - the start node for manually-executed workflows.
 * Produces a single trigger item with a timestamp when the workflow is manually triggered.
 */
@Slf4j
@Node(
	type = "manualTrigger",
	displayName = "Trigger Manually",
	description = "Starts the workflow on manual trigger. Use this as the entry point for workflows that are executed manually.",
	category = "Core Triggers",
	icon = "play",
	trigger = true
)
public class ManualTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("notice")
				.displayName("")
				.description("This node is the starting point for workflows that are triggered manually. It will execute once when you click 'Execute Workflow'.")
				.type(NodeParameter.ParameterType.NOTICE)
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		log.debug("Manual trigger fired for workflow: {}", context.getWorkflowId());

		Map<String, Object> triggerData = Map.of(
			"executionMode", "manual",
			"timestamp", Instant.now().toString()
		);

		Map<String, Object> triggerItem = createTriggerItem(triggerData);
		return NodeExecutionResult.success(List.of(triggerItem));
	}
}
