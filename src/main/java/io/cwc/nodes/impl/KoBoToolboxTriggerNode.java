package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * KoBoToolbox Trigger — receive webhook events from KoBoToolbox form submissions.
 */
@Node(
		type = "koBoToolboxTrigger",
		displayName = "KoBoToolbox Trigger",
		description = "Triggers when a KoBoToolbox submission occurs",
		category = "Surveys & Forms",
		icon = "koBoToolbox",
		trigger = true,
		credentials = {"koBoToolboxApi"},
		triggerCategory = "Other"
)
public class KoBoToolboxTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"formId", context.getParameter("formId", ""),
				"message", "KoBoToolbox trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("formId").displayName("Form ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The KoBoToolbox form/asset ID to watch.").build()
		);
	}
}
