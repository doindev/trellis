package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Workable Trigger — starts workflow when candidate events occur in Workable.
 */
@Node(
		type = "workableTrigger",
		displayName = "Workable Trigger",
		description = "Starts the workflow when a candidate event occurs in Workable",
		category = "HR",
		icon = "workable",
		trigger = true,
		credentials = {"workableApi"}
)
public class WorkableTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String subdomain = (String) credentials.getOrDefault("subdomain", "");
		String baseUrl = "https://" + subdomain + ".workable.com/spi/v3";

		String event = context.getParameter("event", "candidateCreated");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			// List existing subscriptions for informational purposes
			HttpResponse<String> response = get(baseUrl + "/subscriptions", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("subscriptions", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Workable Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("candidateCreated")
						.options(List.of(
								ParameterOption.builder().name("Candidate Created").value("candidateCreated").build(),
								ParameterOption.builder().name("Candidate Moved").value("candidateMoved").build()
						)).build(),
				NodeParameter.builder()
						.name("job").displayName("Job")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter by job shortcode (optional).").build(),
				NodeParameter.builder()
						.name("stage").displayName("Stage")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter by stage slug (optional).").build()
		);
	}
}
