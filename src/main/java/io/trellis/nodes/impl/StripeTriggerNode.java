package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Stripe Trigger — polls for new events (charges, payments, subscriptions, etc.)
 * using the Stripe /v1/events endpoint with a created filter.
 */
@Node(
		type = "stripeTrigger",
		displayName = "Stripe Trigger",
		description = "Starts the workflow when a new Stripe event occurs",
		category = "E-Commerce / Payments",
		icon = "stripe",
		credentials = {"stripeApi"},
		trigger = true,
		polling = true
)
public class StripeTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.stripe.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String secretKey = (String) credentials.getOrDefault("secretKey", "");
		String eventType = context.getParameter("event", "charge.succeeded");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + secretKey);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			// Use static data to track last poll timestamp
			Map<String, Object> staticData = context.getStaticData();
			Object lastPollObj = staticData.get("lastPollTimestamp");
			long lastPollTimestamp;
			if (lastPollObj != null) {
				lastPollTimestamp = Long.parseLong(lastPollObj.toString());
			} else {
				lastPollTimestamp = Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond();
			}

			String url = BASE_URL + "/events?type=" + encode(eventType)
					+ "&created[gt]=" + lastPollTimestamp
					+ "&limit=100";

			HttpResponse<String> response = get(url, headers);
			Map<String, Object> result = parseResponse(response);

			// Update last poll timestamp
			staticData.put("lastPollTimestamp", Instant.now().getEpochSecond());

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Stripe Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("charge.succeeded")
						.description("The Stripe event type to poll for.")
						.options(List.of(
								ParameterOption.builder().name("Charge Succeeded").value("charge.succeeded").build(),
								ParameterOption.builder().name("Charge Failed").value("charge.failed").build(),
								ParameterOption.builder().name("Charge Refunded").value("charge.refunded").build(),
								ParameterOption.builder().name("Customer Created").value("customer.created").build(),
								ParameterOption.builder().name("Customer Updated").value("customer.updated").build(),
								ParameterOption.builder().name("Customer Deleted").value("customer.deleted").build(),
								ParameterOption.builder().name("Invoice Created").value("invoice.created").build(),
								ParameterOption.builder().name("Invoice Paid").value("invoice.paid").build(),
								ParameterOption.builder().name("Invoice Payment Failed").value("invoice.payment_failed").build(),
								ParameterOption.builder().name("Invoice Finalized").value("invoice.finalized").build(),
								ParameterOption.builder().name("Payment Intent Succeeded").value("payment_intent.succeeded").build(),
								ParameterOption.builder().name("Payment Intent Failed").value("payment_intent.payment_failed").build(),
								ParameterOption.builder().name("Payment Intent Created").value("payment_intent.created").build(),
								ParameterOption.builder().name("Subscription Created").value("customer.subscription.created").build(),
								ParameterOption.builder().name("Subscription Updated").value("customer.subscription.updated").build(),
								ParameterOption.builder().name("Subscription Deleted").value("customer.subscription.deleted").build(),
								ParameterOption.builder().name("Payout Paid").value("payout.paid").build(),
								ParameterOption.builder().name("Payout Failed").value("payout.failed").build(),
								ParameterOption.builder().name("Product Created").value("product.created").build(),
								ParameterOption.builder().name("Product Updated").value("product.updated").build(),
								ParameterOption.builder().name("Price Created").value("price.created").build(),
								ParameterOption.builder().name("Price Updated").value("price.updated").build()
						)).build()
		);
	}
}
