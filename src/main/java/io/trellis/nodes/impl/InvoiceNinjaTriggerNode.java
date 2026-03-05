package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Invoice Ninja Trigger — starts workflow when Invoice Ninja webhook events occur.
 */
@Node(
		type = "invoiceNinjaTrigger",
		displayName = "Invoice Ninja Trigger",
		description = "Starts the workflow when an Invoice Ninja event occurs",
		category = "E-Commerce / Payments",
		icon = "invoiceNinja",
		credentials = {"invoiceNinjaApi"},
		trigger = true
)
public class InvoiceNinjaTriggerNode extends AbstractApiNode {

	private static final String DEFAULT_BASE_URL = "https://invoicing.co/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiToken = (String) credentials.getOrDefault("apiToken", "");
		String baseUrl = (String) credentials.getOrDefault("url", "");
		if (baseUrl.isEmpty()) {
			baseUrl = DEFAULT_BASE_URL;
		}
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}

		String event = context.getParameter("event", "create_client");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Ninja-Token", apiToken);
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
			return handleError(context, "Invoice Ninja Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("create_client")
						.options(List.of(
								ParameterOption.builder().name("Client Created").value("create_client").build(),
								ParameterOption.builder().name("Invoice Created").value("create_invoice").build(),
								ParameterOption.builder().name("Quote Created").value("create_quote").build(),
								ParameterOption.builder().name("Payment Created").value("create_payment").build(),
								ParameterOption.builder().name("Vendor Created").value("create_vendor").build()
						)).build()
		);
	}
}
