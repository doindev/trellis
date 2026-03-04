package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

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
		other = true
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
