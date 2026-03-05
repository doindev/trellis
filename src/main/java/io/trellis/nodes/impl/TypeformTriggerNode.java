package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * Typeform Trigger — receive webhook events when Typeform responses are submitted.
 */
@Node(
		type = "typeformTrigger",
		displayName = "Typeform Trigger",
		description = "Triggers when a Typeform response is submitted",
		category = "Surveys & Forms",
		icon = "typeform",
		trigger = true,
		credentials = {"typeformApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class TypeformTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"formId", context.getParameter("formId", ""),
				"event", context.getParameter("event", "form_response"),
				"message", "Typeform trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("formId").displayName("Form ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Typeform form ID to watch.").build(),
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("form_response")
						.options(List.of(
								ParameterOption.builder().name("Form Response").value("form_response").build()
						)).build()
		);
	}
}
