package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * ConvertKit Trigger — starts workflow on ConvertKit subscriber events.
 */
@Node(
		type = "convertKitTrigger",
		displayName = "ConvertKit Trigger",
		description = "Starts the workflow when a ConvertKit event occurs",
		category = "Marketing",
		icon = "convertKit",
		credentials = {"convertKitApi"},
		trigger = true,
		searchOnly = true,
		other = true
)
public class ConvertKitTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.convertkit.com/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiSecret = context.getCredentialString("apiSecret", "");
		String event = context.getParameter("event", "subscriberActivate");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			Map<String, String> headers = Map.of("Content-Type", "application/json", "Accept", "application/json");
			HttpResponse<String> response = get(BASE_URL + "/automations/hooks?api_secret=" + encode(apiSecret), headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("hooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "ConvertKit Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("subscriberActivate")
						.options(List.of(
								ParameterOption.builder().name("Form Subscribe").value("formSubscribe").build(),
								ParameterOption.builder().name("Link Click").value("linkClick").build(),
								ParameterOption.builder().name("Product Purchase").value("productPurchase").build(),
								ParameterOption.builder().name("Purchase Created").value("purchaseCreate").build(),
								ParameterOption.builder().name("Subscriber Activate").value("subscriberActivate").build(),
								ParameterOption.builder().name("Subscriber Unsubscribe").value("subscriberUnsubscribe").build(),
								ParameterOption.builder().name("Tag Add").value("tagAdd").build(),
								ParameterOption.builder().name("Tag Remove").value("tagRemove").build()
						)).build()
		);
	}
}
