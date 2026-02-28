package io.trellis.nodes.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Error Trigger Node - triggers a workflow when another workflow execution fails.
 * The engine invokes workflows containing this node with error details after
 * any execution finishes with ERROR status.
 */
@Slf4j
@Node(
	type = "errorTrigger",
	displayName = "On Error",
	description = "Triggers when a workflow execution fails. Receives error details from the failed execution.",
	category = "Core Triggers",
	icon = "alert-triangle",
	trigger = true
)
public class ErrorTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("targetWorkflowId")
				.displayName("Source Workflow ID")
				.description("Only trigger for errors from a specific workflow. Leave empty to trigger for all workflows.")
				.type(ParameterType.STRING)
				.defaultValue("")
				.placeHolder("(all workflows)")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		log.debug("Error trigger fired for execution: {}", context.getExecutionId());

		List<Map<String, Object>> inputData = context.getInputData();

		// When invoked by the engine, input data contains error details
		if (inputData != null && !inputData.isEmpty()) {
			Map<String, Object> firstItem = inputData.get(0);
			Map<String, Object> json = unwrapJson(firstItem);

			// Filter by target workflow if configured
			String targetWorkflowId = context.getParameter("targetWorkflowId", "");
			if (targetWorkflowId != null && !targetWorkflowId.isEmpty()) {
				String sourceWorkflowId = toString(json.get("workflowId"));
				if (!targetWorkflowId.equals(sourceWorkflowId)) {
					log.debug("Error trigger skipping: source workflow {} does not match target {}",
						sourceWorkflowId, targetWorkflowId);
					return NodeExecutionResult.empty();
				}
			}

			Map<String, Object> triggerItem = createTriggerItem(json);
			return NodeExecutionResult.success(List.of(triggerItem));
		}

		// Manual execution — return empty trigger item
		Map<String, Object> triggerData = new HashMap<>();
		triggerData.put("error", "Manual trigger — no error data available");
		triggerData.put("workflowId", context.getWorkflowId());
		triggerData.put("executionId", context.getExecutionId());

		return NodeExecutionResult.success(List.of(createTriggerItem(triggerData)));
	}
}
