package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Cal.com Trigger — starts workflow on Cal.com booking events.
 */
@Node(
		type = "calTrigger",
		displayName = "Cal.com Trigger",
		description = "Starts the workflow when a Cal.com event occurs",
		category = "Scheduling / Calendar",
		icon = "cal",
		credentials = {"calApi"},
		trigger = true,
		searchOnly = true,
		triggerCategory = "Other"
)
public class CalTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.cal.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String event = context.getParameter("event", "BOOKING_CREATED");

		Map<String, String> headers = Map.of("Accept", "application/json", "Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/webhooks?apiKey=" + encode(apiKey), headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("webhooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Cal.com Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("BOOKING_CREATED")
						.options(List.of(
								ParameterOption.builder().name("Booking Cancelled").value("BOOKING_CANCELLED").build(),
								ParameterOption.builder().name("Booking Created").value("BOOKING_CREATED").build(),
								ParameterOption.builder().name("Booking Rescheduled").value("BOOKING_RESCHEDULED").build(),
								ParameterOption.builder().name("Meeting Ended").value("MEETING_ENDED").build(),
								ParameterOption.builder().name("Meeting Started").value("MEETING_STARTED").build()
						)).build()
		);
	}
}
