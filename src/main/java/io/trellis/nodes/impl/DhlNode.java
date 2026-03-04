package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * DHL — track shipments using the DHL API.
 */
@Node(
		type = "dhl",
		displayName = "DHL",
		description = "Track shipments using the DHL API",
		category = "Miscellaneous",
		icon = "dhl",
		credentials = {"dhlApi"},
		searchOnly = true
)
public class DhlNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api-eu.dhl.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("DHL-API-Key", apiKey);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				String trackingNumber = context.getParameter("trackingNumber", "");
				String recipientPostalCode = context.getParameter("recipientPostalCode", "");

				StringBuilder url = new StringBuilder(BASE_URL + "/track/shipments?trackingNumber=" + encode(trackingNumber));
				if (!recipientPostalCode.isEmpty()) {
					url.append("&recipientPostalCode=").append(encode(recipientPostalCode));
				}

				HttpResponse<String> response = get(url.toString(), headers);
				Map<String, Object> result = parseResponse(response);
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("trackingNumber").displayName("Tracking Number")
						.type(ParameterType.STRING).defaultValue("")
						.description("The shipment tracking number.").required(true).build(),
				NodeParameter.builder()
						.name("recipientPostalCode").displayName("Recipient Postal Code")
						.type(ParameterType.STRING).defaultValue("")
						.description("Recipient postal code for more detailed tracking info.").build()
		);
	}
}
