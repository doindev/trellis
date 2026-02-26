package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Calendly Trigger — starts workflow on Calendly scheduling events.
 */
@Node(
		type = "calendlyTrigger",
		displayName = "Calendly Trigger",
		description = "Starts the workflow when a Calendly event occurs",
		category = "Scheduling / Calendar",
		icon = "calendly",
		credentials = {"calendlyApi"},
		trigger = true
)
public class CalendlyTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.calendly.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String event = context.getParameter("event", "invitee.created");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			// Get current user to find organization
			HttpResponse<String> meResponse = get(BASE_URL + "/users/me", headers);
			Map<String, Object> meData = parseResponse(meResponse);
			@SuppressWarnings("unchecked")
			Map<String, Object> resource = (Map<String, Object>) meData.getOrDefault("resource", Map.of());
			String organization = (String) resource.getOrDefault("current_organization", "");

			HttpResponse<String> response = get(BASE_URL + "/webhook_subscriptions?organization=" + encode(organization) + "&scope=organization", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("webhooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Calendly Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("invitee.created")
						.options(List.of(
								ParameterOption.builder().name("Invitee Cancelled").value("invitee.canceled").build(),
								ParameterOption.builder().name("Invitee Created").value("invitee.created").build()
						)).build()
		);
	}
}
