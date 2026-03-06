package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Emelia Trigger — receive webhook events from Emelia email campaigns.
 */
@Node(
		type = "emeliaTrigger",
		displayName = "Emelia Trigger",
		description = "Triggers when an Emelia campaign event occurs",
		category = "Marketing",
		icon = "emelia",
		trigger = true,
		credentials = {"emeliaApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class EmeliaTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"event", context.getParameter("event", "sent"),
				"message", "Emelia trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("sent")
						.options(List.of(
								ParameterOption.builder().name("Email Bounced").value("bounced").build(),
								ParameterOption.builder().name("Email Clicked").value("clicked").build(),
								ParameterOption.builder().name("Email Opened").value("opened").build(),
								ParameterOption.builder().name("Email Replied").value("replied").build(),
								ParameterOption.builder().name("Email Sent").value("sent").build(),
								ParameterOption.builder().name("Unsubscribed").value("unsubscribed").build()
						)).build()
		);
	}
}
