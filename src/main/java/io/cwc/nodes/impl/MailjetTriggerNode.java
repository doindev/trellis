package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Mailjet Trigger — receive webhooks for Mailjet email events.
 */
@Node(
		type = "mailjetTrigger",
		displayName = "Mailjet Trigger",
		description = "Starts the workflow when a Mailjet email event occurs",
		category = "Communication / Email",
		icon = "mailjet",
		credentials = {"mailjetEmailApi"},
		trigger = true
)
public class MailjetTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.mailjet.com/v3/rest/eventcallbackurl";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKeyPublic = context.getCredentialString("apiKey", "");
		String apiKeyPrivate = context.getCredentialString("secretKey", "");
		String event = context.getParameter("event", "sent");

		String credentials = Base64.getEncoder().encodeToString((apiKeyPublic + ":" + apiKeyPrivate).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// List existing event callbacks
				HttpResponse<String> response = get(BASE_URL, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("event", event);
				result.put("webhooks", parseResponse(response));
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
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS)
						.defaultValue("sent")
						.options(List.of(
								ParameterOption.builder().name("Blocked").value("blocked").build(),
								ParameterOption.builder().name("Bounce").value("bounce").build(),
								ParameterOption.builder().name("Open").value("open").build(),
								ParameterOption.builder().name("Sent").value("sent").build(),
								ParameterOption.builder().name("Spam").value("spam").build(),
								ParameterOption.builder().name("Unsubscribe").value("unsub").build()
						)).build()
		);
	}
}
