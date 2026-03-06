package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * WhatsApp Trigger — starts the workflow when a WhatsApp Business Cloud
 * webhook event is received (incoming messages, status updates, etc.).
 */
@Slf4j
@Node(
		type = "whatsAppTrigger",
		displayName = "WhatsApp Trigger",
		description = "Starts the workflow when a WhatsApp event is received",
		category = "Communication",
		icon = "whatsApp",
		trigger = true,
		credentials = {"whatsAppBusinessCloudApi"}
)
public class WhatsAppTriggerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("triggerOn").displayName("Trigger On")
						.type(ParameterType.OPTIONS).required(true).defaultValue("message")
						.options(List.of(
								ParameterOption.builder().name("Incoming Message").value("message")
										.description("Trigger when a message is received").build(),
								ParameterOption.builder().name("Message Status Update").value("status")
										.description("Trigger when a message status changes (sent, delivered, read)").build(),
								ParameterOption.builder().name("All Events").value("all")
										.description("Trigger on any webhook event").build()
						)).build(),
				NodeParameter.builder()
						.name("verifyToken").displayName("Verify Token")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The verify token configured in the Meta App webhook settings.").build(),
				NodeParameter.builder()
						.name("phoneNumberId").displayName("Phone Number ID (Filter)")
						.type(ParameterType.STRING).defaultValue("")
						.description("Only trigger for events related to this Phone Number ID. Leave empty for all.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String triggerOn = context.getParameter("triggerOn", "message");
			String phoneNumberIdFilter = context.getParameter("phoneNumberId", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				@SuppressWarnings("unchecked")
				Map<String, Object> json = (Map<String, Object>) item.getOrDefault("json", item);

				// Parse the WhatsApp webhook payload structure
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> entries = (List<Map<String, Object>>) json.getOrDefault("entry", List.of());

				for (Map<String, Object> entry : entries) {
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> changes = (List<Map<String, Object>>) entry.getOrDefault("changes", List.of());

					for (Map<String, Object> change : changes) {
						@SuppressWarnings("unchecked")
						Map<String, Object> value = (Map<String, Object>) change.getOrDefault("value", Map.of());

						// Filter by phone number ID if specified
						String metadataPhoneId = "";
						@SuppressWarnings("unchecked")
						Map<String, Object> metadata = (Map<String, Object>) value.getOrDefault("metadata", Map.of());
						if (metadata != null) {
							metadataPhoneId = String.valueOf(metadata.getOrDefault("phone_number_id", ""));
						}

						if (!phoneNumberIdFilter.isEmpty() && !phoneNumberIdFilter.equals(metadataPhoneId)) {
							continue;
						}

						boolean shouldProcess = switch (triggerOn) {
							case "message" -> value.containsKey("messages");
							case "status" -> value.containsKey("statuses");
							case "all" -> true;
							default -> true;
						};

						if (shouldProcess) {
							Map<String, Object> result = new LinkedHashMap<>(value);
							result.put("_triggerTimestamp", System.currentTimeMillis());
							results.add(wrapInJson(result));
						}
					}
				}
			}

			if (results.isEmpty()) {
				log.debug("WhatsApp trigger: no matching events found");
				return NodeExecutionResult.empty();
			}

			log.debug("WhatsApp trigger: processing {} events", results.size());
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "WhatsApp Trigger error: " + e.getMessage(), e);
		}
	}
}
