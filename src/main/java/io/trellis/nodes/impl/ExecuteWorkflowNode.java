package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import io.trellis.engine.WorkflowEngine;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Execute Sub-Workflow Node - calls another workflow by ID and returns its output.
 * The target workflow should start with an Execute Workflow Trigger node.
 * Supports two modes:
 * - Run Once With All Items: pass all input items in a single sub-workflow execution
 * - Run Once For Each Item: execute the sub-workflow separately for each input item
 */
@Slf4j
@Node(
	type = "executeWorkflow",
	displayName = "Execute Sub-Workflow",
	description = "Execute another workflow and use its output data. The target workflow should use an Execute Workflow Trigger as its starting node.",
	category = "Flow",
	icon = "route"
)
public class ExecuteWorkflowNode extends AbstractNode {

	@Lazy
	@Autowired
	private WorkflowEngine workflowEngine;

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("workflowId")
				.displayName("Workflow")
				.description("The ID of the workflow to execute. The target workflow should have an Execute Workflow Trigger as its starting node.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("Workflow ID")
				.build(),

			NodeParameter.builder()
				.name("mode")
				.displayName("Mode")
				.description("How to pass data to the sub-workflow.")
				.type(ParameterType.OPTIONS)
				.defaultValue("runOnceWithAllItems")
				.options(List.of(
					ParameterOption.builder()
						.name("Run Once With All Items")
						.value("runOnceWithAllItems")
						.description("Pass all input items to the sub-workflow in a single execution")
						.build(),
					ParameterOption.builder()
						.name("Run Once For Each Item")
						.value("runOnceForEachItem")
						.description("Execute the sub-workflow separately for each input item and merge results")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.description("Additional options.")
				.type(ParameterType.COLLECTION)
				.defaultValue(Map.of())
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("waitForCompletion")
						.displayName("Wait For Sub-Workflow Completion")
						.description("Wait for the sub-workflow to complete and return its output. When disabled, the node completes immediately without output.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(true)
						.build()
				))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String workflowId = context.getParameter("workflowId", "");
		String mode = context.getParameter("mode", "runOnceWithAllItems");
		Map<String, Object> options = context.getParameter("options", Map.of());
		boolean waitForCompletion = Boolean.TRUE.equals(options.getOrDefault("waitForCompletion", true));

		if (workflowId.isEmpty()) {
			return NodeExecutionResult.error("Workflow ID is required. Select or enter the ID of the workflow to execute.");
		}

		// Prevent calling own workflow to avoid simple recursion
		if (workflowId.equals(context.getWorkflowId())) {
			return NodeExecutionResult.error("A workflow cannot execute itself as a sub-workflow. This would cause infinite recursion.");
		}

		try {
			List<Map<String, Object>> inputData = context.getInputData();
			if (inputData == null) inputData = List.of();

			if (!waitForCompletion) {
				// Fire and forget - just trigger the sub-workflow without waiting
				log.info("Execute Sub-Workflow (fire-and-forget): triggering workflow {} with {} items",
					workflowId, inputData.size());
				workflowEngine.startExecution(workflowId, Map.of("_subWorkflowItems", (Object) inputData));
				return NodeExecutionResult.success(inputData);
			}

			List<Map<String, Object>> result;

			if ("runOnceForEachItem".equals(mode)) {
				log.debug("Execute Sub-Workflow: running workflow {} once for each of {} items",
					workflowId, inputData.size());
				result = new ArrayList<>();
				for (Map<String, Object> item : inputData) {
					List<Map<String, Object>> itemResult = workflowEngine.executeSubWorkflow(
						workflowId, List.of(item));
					result.addAll(itemResult);
				}
			} else {
				log.debug("Execute Sub-Workflow: running workflow {} once with all {} items",
					workflowId, inputData.size());
				result = workflowEngine.executeSubWorkflow(workflowId, inputData);
			}

			log.debug("Execute Sub-Workflow: workflow {} returned {} items", workflowId, result.size());
			return NodeExecutionResult.success(result);

		} catch (Exception e) {
			return handleError(context, "Execute Sub-Workflow error: " + e.getMessage(), e);
		}
	}
}
