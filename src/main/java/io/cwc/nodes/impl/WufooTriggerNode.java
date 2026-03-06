package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Wufoo Trigger — receive webhook events when Wufoo form submissions occur.
 */
@Node(
		type = "wufooTrigger",
		displayName = "Wufoo Trigger",
		description = "Triggers when a Wufoo form submission occurs",
		category = "Surveys & Forms",
		icon = "wufoo",
		trigger = true,
		credentials = {"wufooApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class WufooTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"formId", context.getParameter("formId", ""),
				"message", "Wufoo trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("formId").displayName("Form ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Wufoo form hash to watch.").build()
		);
	}
}
