package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * Taiga Trigger — receive webhook events from Taiga project management.
 */
@Node(
		type = "taigaTrigger",
		displayName = "Taiga Trigger",
		description = "Triggers when a Taiga event occurs",
		category = "Project Management",
		icon = "taiga",
		trigger = true,
		credentials = {"taigaApi"}
)
public class TaigaTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"event", context.getParameter("event", "all"),
				"message", "Taiga trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("all")
						.options(List.of(
								ParameterOption.builder().name("All").value("all").build(),
								ParameterOption.builder().name("Epic: Create").value("epic.create").build(),
								ParameterOption.builder().name("Epic: Change").value("epic.change").build(),
								ParameterOption.builder().name("Epic: Delete").value("epic.delete").build(),
								ParameterOption.builder().name("Issue: Create").value("issue.create").build(),
								ParameterOption.builder().name("Issue: Change").value("issue.change").build(),
								ParameterOption.builder().name("Issue: Delete").value("issue.delete").build(),
								ParameterOption.builder().name("Milestone: Create").value("milestone.create").build(),
								ParameterOption.builder().name("Milestone: Change").value("milestone.change").build(),
								ParameterOption.builder().name("Milestone: Delete").value("milestone.delete").build(),
								ParameterOption.builder().name("Task: Create").value("task.create").build(),
								ParameterOption.builder().name("Task: Change").value("task.change").build(),
								ParameterOption.builder().name("Task: Delete").value("task.delete").build(),
								ParameterOption.builder().name("User Story: Create").value("userstory.create").build(),
								ParameterOption.builder().name("User Story: Change").value("userstory.change").build(),
								ParameterOption.builder().name("User Story: Delete").value("userstory.delete").build()
						)).build()
		);
	}
}
