package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Currents Trigger — starts workflow on Currents CI test events.
 */
@Node(
		type = "currentsTrigger",
		displayName = "Currents Trigger",
		description = "Starts the workflow when a Currents test event occurs",
		category = "Miscellaneous",
		icon = "currents",
		credentials = {"currentsApi"},
		trigger = true,
		searchOnly = true,
		triggerCategory = "Other"
)
public class CurrentsTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.currents.dev/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String projectId = context.getParameter("projectId", "");
		String event = context.getParameter("event", "run:finish");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/webhooks?projectId=" + encode(projectId), headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("webhooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Currents Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("projectId").displayName("Project ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Currents project ID.").build(),
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("run:finish")
						.options(List.of(
								ParameterOption.builder().name("Instance Finish").value("instance:finish").build(),
								ParameterOption.builder().name("Run Finish").value("run:finish").build(),
								ParameterOption.builder().name("Run Start").value("run:start").build(),
								ParameterOption.builder().name("Run Timeout").value("run:timeout").build()
						)).build()
		);
	}
}
