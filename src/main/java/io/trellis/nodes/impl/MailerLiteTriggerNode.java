package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * MailerLite Trigger — starts workflow when MailerLite subscriber events occur.
 */
@Node(
		type = "mailerLiteTrigger",
		displayName = "MailerLite Trigger",
		description = "Starts the workflow when a MailerLite event occurs",
		category = "Marketing",
		icon = "mailerLite",
		credentials = {"mailerLiteApi"},
		trigger = true,
		searchOnly = true,
		other = true
)
public class MailerLiteTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://connect.mailerlite.com/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String event = context.getParameter("event", "subscriber.created");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/webhooks", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("webhooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "MailerLite Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("subscriber.created")
						.options(List.of(
								ParameterOption.builder().name("Campaign Sent").value("campaign.sent").build(),
								ParameterOption.builder().name("Subscriber Added to Group").value("subscriber.added_to_group").build(),
								ParameterOption.builder().name("Subscriber Automation Completed").value("subscriber.automation_completed").build(),
								ParameterOption.builder().name("Subscriber Automation Triggered").value("subscriber.automation_triggered").build(),
								ParameterOption.builder().name("Subscriber Bounced").value("subscriber.bounced").build(),
								ParameterOption.builder().name("Subscriber Created").value("subscriber.created").build(),
								ParameterOption.builder().name("Subscriber Complained").value("subscriber.complaint").build(),
								ParameterOption.builder().name("Subscriber Removed from Group").value("subscriber.removed_from_group").build(),
								ParameterOption.builder().name("Subscriber Unsubscribed").value("subscriber.unsubscribed").build(),
								ParameterOption.builder().name("Subscriber Updated").value("subscriber.updated").build()
						)).build()
		);
	}
}
