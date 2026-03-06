package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * LoneScale Trigger — receive webhook events from LoneScale.
 */
@Node(
		type = "loneScaleTrigger",
		displayName = "LoneScale Trigger",
		description = "Triggers when a LoneScale event occurs",
		category = "Miscellaneous",
		icon = "loneScale",
		trigger = true,
		credentials = {"loneScaleApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class LoneScaleTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"event", context.getParameter("event", "listItemAdded"),
				"message", "LoneScale trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("listItemAdded")
						.options(List.of(
								ParameterOption.builder().name("List Item Added").value("listItemAdded").build(),
								ParameterOption.builder().name("List Created").value("listCreated").build()
						)).build()
		);
	}
}
