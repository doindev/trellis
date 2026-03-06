package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Lemlist Trigger — receive webhook events from Lemlist email campaigns.
 */
@Node(
		type = "lemlistTrigger",
		displayName = "Lemlist Trigger",
		description = "Triggers when a Lemlist campaign event occurs",
		category = "Marketing",
		icon = "lemlist",
		trigger = true,
		credentials = {"lemlistApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class LemlistTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"event", context.getParameter("event", "emailsSent"),
				"message", "Lemlist trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("emailsSent")
						.options(List.of(
								ParameterOption.builder().name("Email Bounced").value("emailsBounced").build(),
								ParameterOption.builder().name("Email Clicked").value("emailsClicked").build(),
								ParameterOption.builder().name("Email Failed").value("emailsFailed").build(),
								ParameterOption.builder().name("Email Interested").value("emailsInterested").build(),
								ParameterOption.builder().name("Email Not Interested").value("emailsNotInterested").build(),
								ParameterOption.builder().name("Email Opened").value("emailsOpened").build(),
								ParameterOption.builder().name("Email Replied").value("emailsReplied").build(),
								ParameterOption.builder().name("Email Sent").value("emailsSent").build(),
								ParameterOption.builder().name("Email Unsubscribed").value("emailsUnsubscribed").build()
						)).build()
		);
	}
}
