package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * seven (Sms77) — send SMS and voice messages via the seven.io API.
 */
@Node(
		type = "sms77",
		displayName = "seven",
		description = "Send SMS and voice messages via seven.io",
		category = "Communication",
		icon = "comment",
		credentials = {"sms77Api"}
)
public class Sms77Node extends AbstractApiNode {

	private static final String BASE_URL = "https://gateway.seven.io/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String resource = context.getParameter("resource", "sms");
			String to = context.getParameter("to", "");
			String message = context.getParameter("message", "");
			String from = context.getParameter("from", "");

			Map<String, String> headers = Map.of(
					"X-Api-Key", apiKey,
					"SentWith", "cwc",
					"Content-Type", "application/json");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> body = new HashMap<>();
					body.put("to", to);
					body.put("text", message);
					if (!from.isBlank()) body.put("from", from);

					String endpoint = "voice".equals(resource) ? "/voice" : "/sms";
					var response = post(BASE_URL + endpoint, body, headers);
					results.add(wrapInJson(parseResponse(response)));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("sms")
						.options(List.of(
								ParameterOption.builder().name("SMS").value("sms").build(),
								ParameterOption.builder().name("Voice Call").value("voice").build()
						)).build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Recipient phone number.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Message content (max 1520 chars).").build(),
				NodeParameter.builder()
						.name("from").displayName("From")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender ID or phone number.").build()
		);
	}
}
