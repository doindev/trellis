package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mocean — send SMS and voice messages via the Mocean API.
 */
@Node(
		type = "mocean",
		displayName = "Mocean",
		description = "Send SMS and voice messages via Mocean",
		category = "Communication",
		icon = "comment",
		credentials = {"moceanApi"}
)
public class MoceanNode extends AbstractApiNode {

	private static final String BASE_URL = "https://rest.moceanapi.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String apiSecret = context.getCredentialString("apiSecret");
			String resource = context.getParameter("resource", "sms");
			String from = context.getParameter("from", "");
			String to = context.getParameter("to", "");
			String message = context.getParameter("message", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> body = new HashMap<>();
					body.put("mocean-api-key", apiKey);
					body.put("mocean-api-secret", apiSecret);
					body.put("mocean-from", from);
					body.put("mocean-to", to);

					String endpoint;
					if ("voice".equals(resource)) {
						String language = context.getParameter("language", "en-US");
						String command = objectMapper.writeValueAsString(
								List.of(Map.of("action", "say", "language", language, "text", message)));
						body.put("mocean-command", command);
						endpoint = BASE_URL + "/rest/2/voice/dial";
					} else {
						body.put("mocean-text", message);
						endpoint = BASE_URL + "/rest/2/sms";
					}

					var response = post(endpoint, body,
							Map.of("Content-Type", "application/json"));
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
								ParameterOption.builder().name("Voice").value("voice").build()
						)).build(),
				NodeParameter.builder()
						.name("from").displayName("From")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Sender ID or phone number.").build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Recipient phone number.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Message content.").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.OPTIONS).defaultValue("en-US")
						.options(List.of(
								ParameterOption.builder().name("English (US)").value("en-US").build(),
								ParameterOption.builder().name("English (GB)").value("en-GB").build(),
								ParameterOption.builder().name("Chinese (Mandarin)").value("cmn-CN").build(),
								ParameterOption.builder().name("Japanese").value("ja-JP").build(),
								ParameterOption.builder().name("Korean").value("ko-KR").build()
						))
						.description("Language for voice message (voice resource only).").build()
		);
	}
}
