package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Acuity Scheduling Trigger — starts workflow on appointment events.
 */
@Node(
		type = "acuitySchedulingTrigger",
		displayName = "Acuity Scheduling Trigger",
		description = "Starts the workflow when an Acuity Scheduling event occurs",
		category = "Scheduling / Calendar",
		icon = "acuityScheduling",
		credentials = {"acuitySchedulingApi"},
		trigger = true
)
public class AcuitySchedulingTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://acuityscheduling.com/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String userId = context.getCredentialString("userId", "");
		String apiKey = context.getCredentialString("apiKey", "");
		String event = context.getParameter("event", "appointment.scheduled");

		String credentials = Base64.getEncoder().encodeToString((userId + ":" + apiKey).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
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
			return handleError(context, "Acuity Scheduling Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("appointment.scheduled")
						.options(List.of(
								ParameterOption.builder().name("Appointment Canceled").value("appointment.canceled").build(),
								ParameterOption.builder().name("Appointment Changed").value("appointment.changed").build(),
								ParameterOption.builder().name("Appointment Completed").value("appointment.completed").build(),
								ParameterOption.builder().name("Appointment No Show").value("appointment.no_show").build(),
								ParameterOption.builder().name("Appointment Rescheduled").value("appointment.rescheduled").build(),
								ParameterOption.builder().name("Appointment Scheduled").value("appointment.scheduled").build(),
								ParameterOption.builder().name("Order Completed").value("order.completed").build()
						)).build()
		);
	}
}
