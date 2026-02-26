package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Chargebee Trigger — starts workflow when Chargebee webhook events occur.
 */
@Node(
		type = "chargebeeTrigger",
		displayName = "Chargebee Trigger",
		description = "Starts the workflow when a Chargebee event occurs",
		category = "E-Commerce",
		icon = "chargebee",
		credentials = {"chargebeeApi"},
		trigger = true
)
public class ChargebeeTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");
		String accountName = (String) credentials.getOrDefault("accountName", "");
		String event = context.getParameter("event", "customer_created");

		String baseUrl = "https://" + accountName + ".chargebee.com/api/v2";

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			String basicAuth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
			Map<String, String> headers = new HashMap<>();
			headers.put("Authorization", "Basic " + basicAuth);
			headers.put("Accept", "application/json");

			HttpResponse<String> response = get(baseUrl + "/events?event_type=" + encode(event) + "&limit=1", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("data", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Chargebee Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("customer_created")
						.options(List.of(
								ParameterOption.builder().name("Customer Created").value("customer_created").build(),
								ParameterOption.builder().name("Customer Changed").value("customer_changed").build(),
								ParameterOption.builder().name("Customer Deleted").value("customer_deleted").build(),
								ParameterOption.builder().name("Subscription Created").value("subscription_created").build(),
								ParameterOption.builder().name("Subscription Changed").value("subscription_changed").build(),
								ParameterOption.builder().name("Subscription Cancelled").value("subscription_cancelled").build(),
								ParameterOption.builder().name("Subscription Activated").value("subscription_activated").build(),
								ParameterOption.builder().name("Subscription Renewed").value("subscription_renewed").build(),
								ParameterOption.builder().name("Invoice Created").value("invoice_created").build(),
								ParameterOption.builder().name("Invoice Updated").value("invoice_updated").build(),
								ParameterOption.builder().name("Invoice Deleted").value("invoice_deleted").build(),
								ParameterOption.builder().name("Payment Succeeded").value("payment_succeeded").build(),
								ParameterOption.builder().name("Payment Failed").value("payment_failed").build(),
								ParameterOption.builder().name("Payment Refunded").value("payment_refunded").build()
						)).build()
		);
	}
}
