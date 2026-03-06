package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Jotform Trigger — receive webhook events when Jotform submissions occur.
 */
@Node(
		type = "jotFormTrigger",
		displayName = "Jotform Trigger",
		description = "Triggers when a Jotform submission occurs",
		category = "Surveys & Forms",
		icon = "jotForm",
		trigger = true,
		credentials = {"jotFormApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class JotFormTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"formId", context.getParameter("formId", ""),
				"message", "Jotform trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("formId").displayName("Form ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Jotform form ID to watch.").build()
		);
	}
}
