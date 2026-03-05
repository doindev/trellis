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
 * Shopify Trigger — polls for new orders, products, or customers in Shopify.
 * Uses list endpoints with created_at_min filter to detect new items.
 */
@Node(
		type = "shopifyTrigger",
		displayName = "Shopify Trigger",
		description = "Starts the workflow when new orders, products, or customers are created in Shopify",
		category = "E-Commerce / Payments",
		icon = "shopify",
		credentials = {"shopifyApi"},
		trigger = true,
		polling = true
)
public class ShopifyTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String shopName = (String) credentials.getOrDefault("shopName", "");
		String resource = context.getParameter("resource", "order");

		String baseUrl = "https://" + shopName + ".myshopify.com/admin/api/2024-01";

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Shopify-Access-Token", accessToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			// Use static data to track last poll time
			Map<String, Object> staticData = context.getStaticData();
			String lastPollTime = (String) staticData.get("lastPollTime");
			if (lastPollTime == null) {
				lastPollTime = Instant.now().minus(1, ChronoUnit.HOURS).toString();
			}

			String endpoint = switch (resource) {
				case "order" -> "/orders.json?status=any&created_at_min=" + encode(lastPollTime) + "&limit=50";
				case "product" -> "/products.json?created_at_min=" + encode(lastPollTime) + "&limit=50";
				case "customer" -> "/customers.json?created_at_min=" + encode(lastPollTime) + "&limit=50";
				default -> throw new IllegalArgumentException("Unknown resource: " + resource);
			};

			HttpResponse<String> response = get(baseUrl + endpoint, headers);
			Map<String, Object> result = parseResponse(response);

			// Update last poll time
			staticData.put("lastPollTime", Instant.now().toString());

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Shopify Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("order")
						.description("The resource type to poll for new items.")
						.options(List.of(
								ParameterOption.builder().name("Order").value("order").build(),
								ParameterOption.builder().name("Product").value("product").build(),
								ParameterOption.builder().name("Customer").value("customer").build()
						)).build()
		);
	}
}
