package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Postmark Trigger — receive webhooks for Postmark email delivery events.
 */
@Node(
		type = "postmarkTrigger",
		displayName = "Postmark Trigger",
		description = "Starts the workflow when a Postmark email event occurs",
		category = "Communication / Email",
		icon = "postmark",
		credentials = {"postmarkApi"},
		trigger = true
)
public class PostmarkTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.postmarkapp.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String serverToken = context.getCredentialString("serverToken", "");
		String events = context.getParameter("events", "bounce");
		boolean firstOpen = toBoolean(context.getParameters().get("firstOpen"), false);
		boolean includeContent = toBoolean(context.getParameters().get("includeContent"), false);

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Postmark-Server-Token", serverToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// List existing webhooks
				HttpResponse<String> response = get(BASE_URL + "/webhooks", headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("events", events);
				result.put("firstOpen", firstOpen);
				result.put("includeContent", includeContent);
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
						.name("events").displayName("Events")
						.type(ParameterType.OPTIONS)
						.defaultValue("bounce")
						.options(List.of(
								ParameterOption.builder().name("Bounce").value("bounce").build(),
								ParameterOption.builder().name("Click").value("click").build(),
								ParameterOption.builder().name("Delivery").value("delivery").build(),
								ParameterOption.builder().name("Open").value("open").build(),
								ParameterOption.builder().name("Spam Complaint").value("spamComplaint").build(),
								ParameterOption.builder().name("Subscription Change").value("subscriptionChange").build()
						)).build(),
				NodeParameter.builder()
						.name("firstOpen").displayName("First Open Only")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Only trigger on the first open event.").build(),
				NodeParameter.builder()
						.name("includeContent").displayName("Include Content")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Include message content in the webhook payload.").build()
		);
	}
}
