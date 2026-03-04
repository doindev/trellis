package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Toggl Trigger — starts workflow on new Toggl time entries (polling).
 */
@Node(
		type = "togglTrigger",
		displayName = "Toggl Trigger",
		description = "Starts the workflow when a new Toggl time entry is created",
		category = "Miscellaneous",
		icon = "toggl",
		credentials = {"togglApi"},
		trigger = true,
		polling = true,
		searchOnly = true,
		other = true
)
public class TogglTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.track.toggl.com/api/v9";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiToken = context.getCredentialString("apiToken", "");
		String event = context.getParameter("event", "newTimeEntry");

		String credentials = Base64.getEncoder().encodeToString((apiToken + ":api_token").getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/me/time_entries", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("timeEntries", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Toggl Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("newTimeEntry")
						.options(List.of(
								ParameterOption.builder().name("New Time Entry").value("newTimeEntry").build()
						)).build()
		);
	}
}
