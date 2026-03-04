package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * Formstack Trigger — receive webhook events when Formstack form submissions occur.
 */
@Node(
		type = "formstackTrigger",
		displayName = "Formstack Trigger",
		description = "Triggers when a Formstack form submission occurs",
		category = "Surveys & Forms",
		icon = "formstack",
		trigger = true,
		credentials = {"formstackApi"}
)
public class FormstackTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"formId", context.getParameter("formId", ""),
				"message", "Formstack trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("formId").displayName("Form ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Formstack form ID to watch.").build()
		);
	}
}
