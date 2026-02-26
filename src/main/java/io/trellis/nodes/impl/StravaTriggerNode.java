package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Strava Trigger — starts workflow when Strava activity events occur.
 */
@Node(
		type = "stravaTrigger",
		displayName = "Strava Trigger",
		description = "Starts the workflow when a Strava event occurs",
		category = "Miscellaneous",
		icon = "strava",
		credentials = {"stravaOAuth2Api"},
		trigger = true
)
public class StravaTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.strava.com/api/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String event = context.getParameter("event", "activityCreate");

		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/push_subscriptions", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("subscriptions", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Strava Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("activityCreate")
						.options(List.of(
								ParameterOption.builder().name("Activity Created").value("activityCreate").build(),
								ParameterOption.builder().name("Activity Deleted").value("activityDelete").build(),
								ParameterOption.builder().name("Activity Updated").value("activityUpdate").build()
						)).build()
		);
	}
}
