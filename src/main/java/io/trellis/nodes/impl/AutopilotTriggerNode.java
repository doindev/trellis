package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Autopilot Trigger — starts workflow when Autopilot contact events occur.
 */
@Node(
		type = "autopilotTrigger",
		displayName = "Autopilot Trigger",
		description = "Starts the workflow when an Autopilot event occurs",
		category = "Marketing",
		icon = "autopilot",
		credentials = {"autopilotApi"},
		trigger = true,
		searchOnly = true,
		triggerCategory = "Other"
)
public class AutopilotTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api2.autopilothq.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String event = context.getParameter("event", "contactAdded");

		Map<String, String> headers = new HashMap<>();
		headers.put("autopilotapikey", apiKey);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/hooks", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("hooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Autopilot Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("contactAdded")
						.options(List.of(
								ParameterOption.builder().name("Contact Added").value("contactAdded").build(),
								ParameterOption.builder().name("Contact Added to List").value("contactAddedToList").build(),
								ParameterOption.builder().name("Contact Entered Segment").value("contactEnteredSegment").build(),
								ParameterOption.builder().name("Contact Left Segment").value("contactLeftSegment").build(),
								ParameterOption.builder().name("Contact Removed from List").value("contactRemovedFromList").build(),
								ParameterOption.builder().name("Contact Unsubscribed").value("contactUnsubscribed").build(),
								ParameterOption.builder().name("Contact Updated").value("contactUpdated").build()
						)).build()
		);
	}
}
