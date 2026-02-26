package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Pushcut Trigger — receive webhooks from Pushcut when server actions are triggered.
 */
@Node(
		type = "pushcutTrigger",
		displayName = "Pushcut Trigger",
		description = "Starts the workflow when a Pushcut notification action is triggered",
		category = "Communication / Chat & Messaging",
		icon = "pushcut",
		credentials = {"pushcutApi"},
		trigger = true
)
public class PushcutTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.pushcut.io/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String actionName = context.getParameter("actionName", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("API-Key", apiKey);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// List existing subscriptions
				HttpResponse<String> listResp = get(BASE_URL + "/subscriptions", headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("actionName", actionName);
				result.put("subscriptions", parseResponse(listResp));
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
						.name("actionName").displayName("Action Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the server action in the Pushcut app.").build()
		);
	}
}
