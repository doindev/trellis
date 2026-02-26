package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Vonage — send SMS messages via the Vonage (formerly Nexmo) API.
 */
@Node(
		type = "vonage",
		displayName = "Vonage",
		description = "Send SMS messages with Vonage",
		category = "Communication / Chat & Messaging",
		icon = "vonage",
		credentials = {"vonageApi"}
)
public class VonageNode extends AbstractApiNode {

	private static final String BASE_URL = "https://rest.nexmo.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String apiSecret = context.getCredentialString("apiSecret", "");
		String from = context.getParameter("from", "");
		String to = context.getParameter("to", "");
		String message = context.getParameter("message", "");

		// Additional fields
		String clientRef = context.getParameter("clientRef", "");
		String callback = context.getParameter("callback", "");
		boolean statusReportReq = toBoolean(context.getParameters().get("statusReportReq"), false);
		int ttl = toInt(context.getParameters().get("ttl"), 0);

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("api_key", apiKey);
				body.put("api_secret", apiSecret);
				body.put("from", from);
				body.put("to", to);
				body.put("text", message);
				body.put("type", "text");

				if (!clientRef.isBlank()) {
					body.put("client-ref", clientRef);
				}
				if (!callback.isBlank()) {
					body.put("callback", callback);
				}
				if (statusReportReq) {
					body.put("status-report-req", true);
				}
				if (ttl > 0) {
					// Vonage expects TTL in milliseconds
					body.put("ttl", ttl * 60000);
				}

				HttpResponse<String> response = post(BASE_URL + "/sms/json", body, headers);
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
						.name("from").displayName("From")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender name or number.").build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Recipient phone number in E.164 format.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("The SMS message body.").build(),
				NodeParameter.builder()
						.name("clientRef").displayName("Client Reference")
						.type(ParameterType.STRING).defaultValue("")
						.description("Client reference string (up to 40 chars).").build(),
				NodeParameter.builder()
						.name("callback").displayName("Callback URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("Webhook URL for delivery receipts.").build(),
				NodeParameter.builder()
						.name("statusReportReq").displayName("Status Report Request")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Request a delivery receipt.").build(),
				NodeParameter.builder()
						.name("ttl").displayName("TTL (minutes)")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Time-to-live in minutes (0 = default).").build()
		);
	}
}
