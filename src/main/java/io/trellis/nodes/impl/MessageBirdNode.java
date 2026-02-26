package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * MessageBird — send SMS messages and check balance via the MessageBird API.
 */
@Node(
		type = "messageBird",
		displayName = "MessageBird",
		description = "Send SMS messages and manage balance with MessageBird",
		category = "Communication / Chat & Messaging",
		icon = "messageBird",
		credentials = {"messageBirdApi"}
)
public class MessageBirdNode extends AbstractApiNode {

	private static final String BASE_URL = "https://rest.messagebird.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("accessKey", "");
		String resource = context.getParameter("resource", "sms");
		String operation = context.getParameter("operation", "send");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "AccessKey " + apiKey);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "sms" -> handleSms(context, operation, headers);
					case "balance" -> handleBalance(headers);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
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

	private Map<String, Object> handleSms(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		if (!"send".equals(operation)) {
			throw new IllegalArgumentException("Unknown SMS operation: " + operation);
		}

		String originator = context.getParameter("originator", "");
		String recipients = context.getParameter("recipients", "");
		String message = context.getParameter("message", "");
		String datacoding = context.getParameter("datacoding", "auto");
		String reference = context.getParameter("reference", "");
		String reportUrl = context.getParameter("reportUrl", "");
		int validity = toInt(context.getParameters().get("validity"), 0);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("originator", originator);
		body.put("body", message);
		body.put("type", "sms");

		// Parse recipients as integers (MessageBird expects numbers)
		String[] recipientParts = recipients.split(",");
		List<Long> recipientList = new ArrayList<>();
		for (String r : recipientParts) {
			String trimmed = r.trim();
			if (!trimmed.isEmpty()) {
				recipientList.add(Long.parseLong(trimmed.replaceAll("[^0-9]", "")));
			}
		}
		body.put("recipients", recipientList);

		if (!"auto".equals(datacoding)) {
			body.put("datacoding", datacoding);
		}
		if (!reference.isBlank()) {
			body.put("reference", reference);
		}
		if (!reportUrl.isBlank()) {
			body.put("reportUrl", reportUrl);
		}
		if (validity > 0) {
			body.put("validity", validity);
		}

		HttpResponse<String> response = post(BASE_URL + "/messages", body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleBalance(Map<String, String> headers) throws Exception {
		HttpResponse<String> response = get(BASE_URL + "/balance", headers);
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("sms")
						.options(List.of(
								ParameterOption.builder().name("SMS").value("sms").build(),
								ParameterOption.builder().name("Balance").value("balance").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Get").value("get").build()
						)).build(),
				NodeParameter.builder()
						.name("originator").displayName("Originator")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender of the message (phone number or alphanumeric).").build(),
				NodeParameter.builder()
						.name("recipients").displayName("Recipients")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of recipient phone numbers.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("The body of the SMS message.").build(),
				NodeParameter.builder()
						.name("datacoding").displayName("Data Coding")
						.type(ParameterType.OPTIONS)
						.defaultValue("auto")
						.options(List.of(
								ParameterOption.builder().name("Auto").value("auto").build(),
								ParameterOption.builder().name("Plain").value("plain").build(),
								ParameterOption.builder().name("Unicode").value("unicode").build()
						)).build(),
				NodeParameter.builder()
						.name("reference").displayName("Reference")
						.type(ParameterType.STRING).defaultValue("")
						.description("A client reference string.").build(),
				NodeParameter.builder()
						.name("reportUrl").displayName("Report URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("Webhook URL for delivery reports.").build(),
				NodeParameter.builder()
						.name("validity").displayName("Validity (seconds)")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Message validity period in seconds (0 = no expiry).").build()
		);
	}
}
