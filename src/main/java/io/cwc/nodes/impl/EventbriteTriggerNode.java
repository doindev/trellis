package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Eventbrite Trigger — starts workflow when Eventbrite events occur.
 */
@Node(
		type = "eventbriteTrigger",
		displayName = "Eventbrite Trigger",
		description = "Starts the workflow when an Eventbrite event occurs",
		category = "Miscellaneous",
		icon = "eventbrite",
		credentials = {"eventbriteApi"},
		trigger = true,
		searchOnly = true,
		triggerCategory = "Other"
)
public class EventbriteTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.eventbriteapi.com/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String event = context.getParameter("event", "order.placed");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
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
			return handleError(context, "Eventbrite Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("order.placed")
						.options(List.of(
								ParameterOption.builder().name("Attendee Checked In").value("barcode.checked_in").build(),
								ParameterOption.builder().name("Attendee Checked Out").value("barcode.un_checked_in").build(),
								ParameterOption.builder().name("Event Created").value("event.created").build(),
								ParameterOption.builder().name("Event Published").value("event.published").build(),
								ParameterOption.builder().name("Event Unpublished").value("event.unpublished").build(),
								ParameterOption.builder().name("Event Updated").value("event.updated").build(),
								ParameterOption.builder().name("Order Placed").value("order.placed").build(),
								ParameterOption.builder().name("Order Refunded").value("order.refunded").build(),
								ParameterOption.builder().name("Order Updated").value("order.updated").build(),
								ParameterOption.builder().name("Organizer Updated").value("organizer.updated").build()
						)).build(),
				NodeParameter.builder()
						.name("organizationId").displayName("Organization ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The organization to receive events for.").build()
		);
	}
}
