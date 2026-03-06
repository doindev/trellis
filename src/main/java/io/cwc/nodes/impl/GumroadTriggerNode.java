package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Gumroad Trigger — starts workflow when Gumroad sales events occur.
 */
@Node(
		type = "gumroadTrigger",
		displayName = "Gumroad Trigger",
		description = "Starts the workflow when a Gumroad event occurs",
		category = "Miscellaneous",
		icon = "gumroad",
		credentials = {"gumroadApi"},
		trigger = true,
		searchOnly = true,
		triggerCategory = "Other"
)
public class GumroadTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.gumroad.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String resourceName = context.getParameter("resourceName", "sale");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			Map<String, String> headers = Map.of("Accept", "application/json", "Content-Type", "application/json");
			Map<String, Object> body = Map.of("access_token", accessToken);
			HttpResponse<String> response = post(BASE_URL + "/resource_subscriptions", body, headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("resource", resourceName);
			result.put("subscriptions", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Gumroad Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resourceName").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("sale")
						.options(List.of(
								ParameterOption.builder().name("Cancellation").value("cancellation").build(),
								ParameterOption.builder().name("Dispute").value("dispute").build(),
								ParameterOption.builder().name("Dispute Won").value("dispute_won").build(),
								ParameterOption.builder().name("Refund").value("refund").build(),
								ParameterOption.builder().name("Sale").value("sale").build(),
								ParameterOption.builder().name("Subscription Ended").value("subscription_ended").build(),
								ParameterOption.builder().name("Subscription Restarted").value("subscription_restarted").build(),
								ParameterOption.builder().name("Subscription Updated").value("subscription_updated").build()
						)).build()
		);
	}
}
