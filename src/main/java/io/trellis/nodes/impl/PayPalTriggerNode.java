package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * PayPal Trigger — starts workflow when PayPal webhook events occur.
 * Event types are loaded dynamically from the PayPal API.
 */
@Node(
		type = "payPalTrigger",
		displayName = "PayPal Trigger",
		description = "Starts the workflow when a PayPal event occurs",
		category = "E-Commerce / Payments",
		icon = "payPal",
		credentials = {"payPalApi"},
		trigger = true
)
public class PayPalTriggerNode extends AbstractApiNode {

	private static final String PRODUCTION_BASE_URL = "https://api-m.paypal.com/v1";
	private static final String SANDBOX_BASE_URL = "https://api-m.sandbox.paypal.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String clientId = (String) credentials.getOrDefault("clientId", "");
		String clientSecret = (String) credentials.getOrDefault("clientSecret", "");
		boolean useSandbox = toBoolean(credentials.get("sandbox"), true);
		String event = context.getParameter("event", "PAYMENT.SALE.COMPLETED");

		String baseUrl = useSandbox ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			// Obtain OAuth2 access token
			String accessToken = getAccessToken(baseUrl, clientId, clientSecret);

			Map<String, String> headers = new HashMap<>();
			headers.put("Authorization", "Bearer " + accessToken);
			headers.put("Content-Type", "application/json");
			headers.put("Accept", "application/json");

			HttpResponse<String> response = get(baseUrl + "/notifications/webhooks", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("webhooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "PayPal Trigger error: " + e.getMessage(), e);
		}
	}

	private String getAccessToken(String baseUrl, String clientId, String clientSecret) throws Exception {
		String basicAuth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + basicAuth);
		headers.put("Content-Type", "application/x-www-form-urlencoded");
		headers.put("Accept", "application/json");

		HttpResponse<String> response = post(baseUrl + "/oauth2/token", "grant_type=client_credentials", headers);
		Map<String, Object> tokenResponse = parseResponse(response);
		return (String) tokenResponse.getOrDefault("access_token", "");
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("PAYMENT.SALE.COMPLETED")
						.description("The PayPal webhook event to listen for. Common events are provided below.")
						.options(List.of(
								ParameterOption.builder().name("Payment Sale Completed").value("PAYMENT.SALE.COMPLETED").build(),
								ParameterOption.builder().name("Payment Sale Denied").value("PAYMENT.SALE.DENIED").build(),
								ParameterOption.builder().name("Payment Sale Refunded").value("PAYMENT.SALE.REFUNDED").build(),
								ParameterOption.builder().name("Payment Sale Reversed").value("PAYMENT.SALE.REVERSED").build(),
								ParameterOption.builder().name("Payment Capture Completed").value("PAYMENT.CAPTURE.COMPLETED").build(),
								ParameterOption.builder().name("Payment Capture Denied").value("PAYMENT.CAPTURE.DENIED").build(),
								ParameterOption.builder().name("Payment Capture Refunded").value("PAYMENT.CAPTURE.REFUNDED").build(),
								ParameterOption.builder().name("Checkout Order Approved").value("CHECKOUT.ORDER.APPROVED").build(),
								ParameterOption.builder().name("Checkout Order Completed").value("CHECKOUT.ORDER.COMPLETED").build(),
								ParameterOption.builder().name("Billing Subscription Created").value("BILLING.SUBSCRIPTION.CREATED").build(),
								ParameterOption.builder().name("Billing Subscription Activated").value("BILLING.SUBSCRIPTION.ACTIVATED").build(),
								ParameterOption.builder().name("Billing Subscription Cancelled").value("BILLING.SUBSCRIPTION.CANCELLED").build(),
								ParameterOption.builder().name("Billing Subscription Expired").value("BILLING.SUBSCRIPTION.EXPIRED").build(),
								ParameterOption.builder().name("Billing Subscription Suspended").value("BILLING.SUBSCRIPTION.SUSPENDED").build(),
								ParameterOption.builder().name("Customer Dispute Created").value("CUSTOMER.DISPUTE.CREATED").build(),
								ParameterOption.builder().name("Customer Dispute Resolved").value("CUSTOMER.DISPUTE.RESOLVED").build()
						)).build()
		);
	}
}
