package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

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
