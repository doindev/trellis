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
 * Keap Trigger Node -- webhook-based trigger that fires when events
 * occur in Keap (Infusionsoft).
 */
@Slf4j
@Node(
	type = "keapTrigger",
	displayName = "Keap Trigger",
	description = "Starts the workflow when events occur in Keap (Infusionsoft)",
	category = "CRM",
	icon = "keap",
	trigger = true,
	credentials = {"keapOAuth2Api"}
)
public class KeapTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.infusionsoft.com/crm/rest/v1";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("contact.add")
				.options(List.of(
					ParameterOption.builder().name("Contact Added").value("contact.add").description("Trigger when a contact is added").build(),
					ParameterOption.builder().name("Contact Updated").value("contact.edit").description("Trigger when a contact is updated").build(),
					ParameterOption.builder().name("Contact Deleted").value("contact.delete").description("Trigger when a contact is deleted").build(),
					ParameterOption.builder().name("Tag Applied").value("tag.apply").description("Trigger when a tag is applied to a contact").build(),
					ParameterOption.builder().name("Tag Removed").value("tag.remove").description("Trigger when a tag is removed from a contact").build(),
					ParameterOption.builder().name("Order Created").value("order.add").description("Trigger when an order is created").build(),
					ParameterOption.builder().name("Opportunity Created").value("opportunity.add").description("Trigger when an opportunity is created").build(),
					ParameterOption.builder().name("Invoice Paid").value("invoice.paid").description("Trigger when an invoice is paid").build()
				)).build(),

			NodeParameter.builder()
				.name("webhookUrl").displayName("Webhook URL")
				.type(ParameterType.STRING)
				.description("The webhook URL to register with Keap. Leave empty for auto-configuration.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String event = context.getParameter("event", "contact.add");

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
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			String webhookUrl = context.getParameter("webhookUrl", "");

			if (!webhookUrl.isEmpty() && !accessToken.isEmpty()) {
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("Authorization", "Bearer " + accessToken);
				headers.put("Content-Type", "application/json");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("eventKey", event);
				body.put("hookUrl", webhookUrl);

				HttpResponse<String> response = post(BASE_URL + "/hooks", body, headers);

				if (response.statusCode() >= 400) {
					log.warn("Failed to register Keap webhook: {}", response.body());
				} else {
					log.debug("Keap webhook registered for event: {}", event);
				}
			}

			// Return empty trigger item
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerEvent", event);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Keap Trigger error: " + e.getMessage(), e);
		}
	}
}
