package io.trellis.nodes.impl;

import java.util.List;
import java.util.Map;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import lombok.extern.slf4j.Slf4j;

/**
 * Execute Workflow Trigger - entry point for sub-workflows.
 * When a parent workflow calls this workflow via the Execute Sub-Workflow node,
 * this trigger receives the input data and passes it through to downstream nodes.
 */
@Slf4j
@Node(
	type = "executeWorkflowTrigger",
	displayName = "Execute Workflow Trigger",
	description = "Receives data from a parent workflow. Use this as the starting trigger in workflows called by the Execute Sub-Workflow node.",
	category = "Core Triggers",
	icon = "play",
	trigger = true
)
public class ExecuteWorkflowTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of();
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			log.debug("Execute Workflow Trigger: received {} items from parent workflow", inputData.size());
			return NodeExecutionResult.success(inputData);
		}

		// No input from parent - produce an empty trigger item
		log.debug("Execute Workflow Trigger: no input data, producing empty trigger item");
		return NodeExecutionResult.success(List.of(createEmptyTriggerItem()));
	}
}
