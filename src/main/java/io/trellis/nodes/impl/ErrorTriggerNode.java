package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * Error Trigger — starts a workflow when another workflow produces an error.
 * This trigger node provides the error data from the failed workflow execution.
 */
@Node(
		type = "errorTrigger",
		displayName = "Error Trigger",
		description = "Starts the workflow when another workflow has an error",
		category = "Core / Triggers",
		icon = "errorTrigger",
		trigger = true
)
public class ErrorTriggerNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		// The error trigger receives error data from the execution engine
		// when a workflow configured with an error workflow encounters an error.
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null || inputData.isEmpty()) {
			// Return placeholder data when no error data is available (manual test)
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("execution", Map.of(
					"id", "unknown",
					"mode", "error",
					"error", Map.of("message", "No error data available")
			));
			result.put("workflow", Map.of(
					"id", "unknown",
					"name", "unknown"
			));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		}

		return NodeExecutionResult.success(inputData);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of();
	}
}
