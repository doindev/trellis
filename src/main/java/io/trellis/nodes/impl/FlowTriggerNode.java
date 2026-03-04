package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Flow Trigger — starts workflow when Flow task events occur.
 */
@Node(
		type = "flowTrigger",
		displayName = "Flow Trigger",
		description = "Starts the workflow when a Flow task event occurs",
		category = "Miscellaneous",
		icon = "flow",
		credentials = {"flowApi"},
		trigger = true,
		searchOnly = true,
		other = true
)
public class FlowTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.getflow.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String organizationId = (String) credentials.getOrDefault("organizationId", "");
		String event = context.getParameter("event", "taskCreated");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/integration_webhooks?organization_id=" + encode(organizationId), headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("webhooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Flow Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("taskCreated")
						.options(List.of(
								ParameterOption.builder().name("Task Created").value("taskCreated").build(),
								ParameterOption.builder().name("Task Completed").value("taskCompleted").build(),
								ParameterOption.builder().name("Task Updated").value("taskUpdated").build(),
								ParameterOption.builder().name("Task Deleted").value("taskDeleted").build()
						)).build()
		);
	}
}
