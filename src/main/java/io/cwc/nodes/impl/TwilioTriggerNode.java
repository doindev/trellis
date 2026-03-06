package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Twilio Trigger — receive webhook events from Twilio for
 * incoming SMS messages and voice calls.
 */
@Slf4j
@Node(
		type = "twilioTrigger",
		displayName = "Twilio Trigger",
		description = "Starts the workflow when a Twilio event is received",
		category = "Communication",
		icon = "twilio",
		credentials = {"twilioApi"},
		trigger = true
)
public class TwilioTriggerNode extends AbstractTriggerNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null || inputData.isEmpty()) {
			log.debug("Twilio Trigger: no input data received");
			return NodeExecutionResult.success(List.of(createEmptyTriggerItem()));
		}

		String event = context.getParameter("event", "sms");

		List<Map<String, Object>> results = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> data = unwrapJson(item);

				// Add event type metadata
				Map<String, Object> enriched = new LinkedHashMap<>(data);
				enriched.put("_eventType", event);

				results.add(createTriggerItem(enriched));
			} catch (Exception e) {
				log.error("Twilio Trigger error processing item", e);
				return handleError(context, "Twilio Trigger error: " + e.getMessage(), e);
			}
		}

		if (results.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS)
						.defaultValue("sms")
						.description("The type of Twilio event to listen for.")
						.options(List.of(
								ParameterOption.builder().name("Incoming SMS").value("sms")
										.description("Trigger on incoming SMS messages.").build(),
								ParameterOption.builder().name("Incoming Call").value("call")
										.description("Trigger on incoming voice calls.").build(),
								ParameterOption.builder().name("SMS Status Callback").value("smsStatus")
										.description("Trigger on SMS delivery status updates.").build(),
								ParameterOption.builder().name("Call Status Callback").value("callStatus")
										.description("Trigger on call status updates.").build(),
								ParameterOption.builder().name("Incoming WhatsApp").value("whatsapp")
										.description("Trigger on incoming WhatsApp messages.").build()
						)).build()
		);
	}
}
