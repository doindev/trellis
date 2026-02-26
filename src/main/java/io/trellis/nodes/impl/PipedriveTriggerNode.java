package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipedrive Trigger Node -- webhook-based trigger that fires when events
 * occur in Pipedrive CRM (e.g., deal created, person updated).
 */
@Slf4j
@Node(
	type = "pipedriveTrigger",
	displayName = "Pipedrive Trigger",
	description = "Starts the workflow when events occur in Pipedrive CRM",
	category = "CRM",
	icon = "pipedrive",
	trigger = true,
	credentials = {"pipedriveApi"}
)
public class PipedriveTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.pipedrive.com/v1";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("deal.added")
				.options(List.of(
					ParameterOption.builder().name("Deal Created").value("deal.added").description("Trigger when a deal is created").build(),
					ParameterOption.builder().name("Deal Updated").value("deal.updated").description("Trigger when a deal is updated").build(),
					ParameterOption.builder().name("Deal Deleted").value("deal.deleted").description("Trigger when a deal is deleted").build(),
					ParameterOption.builder().name("Person Created").value("person.added").description("Trigger when a person is created").build(),
					ParameterOption.builder().name("Person Updated").value("person.updated").description("Trigger when a person is updated").build(),
					ParameterOption.builder().name("Person Deleted").value("person.deleted").description("Trigger when a person is deleted").build(),
					ParameterOption.builder().name("Organization Created").value("organization.added").description("Trigger when an organization is created").build(),
					ParameterOption.builder().name("Organization Updated").value("organization.updated").description("Trigger when an organization is updated").build(),
					ParameterOption.builder().name("Activity Created").value("activity.added").description("Trigger when an activity is created").build(),
					ParameterOption.builder().name("Activity Updated").value("activity.updated").description("Trigger when an activity is updated").build(),
					ParameterOption.builder().name("Note Created").value("note.added").description("Trigger when a note is created").build(),
					ParameterOption.builder().name("Note Updated").value("note.updated").description("Trigger when a note is updated").build()
				)).build(),

			NodeParameter.builder()
				.name("webhookUrl").displayName("Webhook URL")
				.type(ParameterType.STRING)
				.description("The webhook URL to register with Pipedrive. Leave empty for auto-configuration.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String event = context.getParameter("event", "deal.added");

		try {
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData != null && !inputData.isEmpty()) {
				// Webhook data received -- pass it through
				List<Map<String, Object>> results = new ArrayList<>();
				for (Map<String, Object> item : inputData) {
					Map<String, Object> enriched = new LinkedHashMap<>(unwrapJson(item));
					enriched.put("_triggerEvent", event);
					enriched.put("_triggerTimestamp", System.currentTimeMillis());
					results.add(wrapInJson(enriched));
				}
				return NodeExecutionResult.success(results);
			}

			// No webhook data -- attempt to register webhook
			String apiToken = String.valueOf(credentials.getOrDefault("apiToken", credentials.getOrDefault("apiKey", "")));
			String webhookUrl = context.getParameter("webhookUrl", "");

			if (!webhookUrl.isEmpty()) {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("subscription_url", webhookUrl);
				body.put("event_action", event.split("\\.").length > 1 ? event.split("\\.")[1] : "added");
				body.put("event_object", event.split("\\.")[0]);

				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("Content-Type", "application/json");
				String url = BASE_URL + "/webhooks?api_token=" + encode(apiToken);
				HttpResponse<String> response = post(url, body, headers);

				if (response.statusCode() >= 400) {
					log.warn("Failed to register Pipedrive webhook: {}", response.body());
				} else {
					log.debug("Pipedrive webhook registered for event: {}", event);
				}
			}

			// Return empty trigger item
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerEvent", event);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Pipedrive Trigger error: " + e.getMessage(), e);
		}
	}
}
