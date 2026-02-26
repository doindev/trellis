package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * WooCommerce Trigger — starts workflow when WooCommerce webhook events occur.
 */
@Node(
		type = "wooCommerceTrigger",
		displayName = "WooCommerce Trigger",
		description = "Starts the workflow when a WooCommerce event occurs",
		category = "E-Commerce",
		icon = "wooCommerce",
		credentials = {"wooCommerceApi"},
		trigger = true
)
public class WooCommerceTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String consumerKey = (String) credentials.getOrDefault("consumerKey", "");
		String consumerSecret = (String) credentials.getOrDefault("consumerSecret", "");
		String storeUrl = (String) credentials.getOrDefault("url", "");

		// Remove trailing slash if present
		if (storeUrl.endsWith("/")) {
			storeUrl = storeUrl.substring(0, storeUrl.length() - 1);
		}

		String event = context.getParameter("event", "order.created");

		String baseUrl = storeUrl + "/wp-json/wc/v3";

		// WooCommerce uses Basic Auth with consumer key and secret
		String basicAuth = Base64.getEncoder().encodeToString((consumerKey + ":" + consumerSecret).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + basicAuth);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(baseUrl + "/webhooks", headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("webhooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "WooCommerce Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("order.created")
						.options(List.of(
								ParameterOption.builder().name("Customer Created").value("customer.created").build(),
								ParameterOption.builder().name("Customer Deleted").value("customer.deleted").build(),
								ParameterOption.builder().name("Customer Updated").value("customer.updated").build(),
								ParameterOption.builder().name("Order Created").value("order.created").build(),
								ParameterOption.builder().name("Order Deleted").value("order.deleted").build(),
								ParameterOption.builder().name("Order Updated").value("order.updated").build(),
								ParameterOption.builder().name("Product Created").value("product.created").build(),
								ParameterOption.builder().name("Product Deleted").value("product.deleted").build(),
								ParameterOption.builder().name("Product Updated").value("product.updated").build(),
								ParameterOption.builder().name("Coupon Created").value("coupon.created").build(),
								ParameterOption.builder().name("Coupon Deleted").value("coupon.deleted").build(),
								ParameterOption.builder().name("Coupon Updated").value("coupon.updated").build()
						)).build()
		);
	}
}
