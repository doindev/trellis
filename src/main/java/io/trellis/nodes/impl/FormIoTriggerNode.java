package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * Form.io Trigger — receive webhook events when Form.io submissions occur.
 */
@Node(
		type = "formIoTrigger",
		displayName = "Form.io Trigger",
		description = "Triggers when a Form.io submission event occurs",
		category = "Surveys & Forms",
		icon = "formIo",
		trigger = true,
		credentials = {"formIoApi"}
)
public class FormIoTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"event", context.getParameter("event", "submissionCreate"),
				"message", "Form.io trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("submissionCreate")
						.options(List.of(
								ParameterOption.builder().name("Submission Created").value("submissionCreate").build(),
								ParameterOption.builder().name("Submission Updated").value("submissionUpdate").build(),
								ParameterOption.builder().name("Submission Deleted").value("submissionDelete").build()
						)).build()
		);
	}
}
