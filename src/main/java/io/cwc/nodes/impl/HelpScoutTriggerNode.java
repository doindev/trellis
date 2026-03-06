package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Help Scout Trigger — starts a workflow when a Help Scout webhook event fires.
 */
@Slf4j
@Node(
		type = "helpScoutTrigger",
		displayName = "Help Scout Trigger",
		description = "Starts the workflow when a Help Scout webhook event is received",
		category = "Customer Support",
		icon = "helpScout",
		credentials = {"helpScoutOAuth2Api"},
		trigger = true
)
public class HelpScoutTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("events").displayName("Events")
						.type(ParameterType.MULTI_OPTIONS).required(true)
						.description("The webhook events to listen for")
						.options(List.of(
								ParameterOption.builder().name("Conversation Created").value("convo.created")
										.description("Fires when a new conversation is created").build(),
								ParameterOption.builder().name("Conversation Assigned").value("convo.assigned")
										.description("Fires when a conversation is assigned").build(),
								ParameterOption.builder().name("Conversation Moved").value("convo.moved")
										.description("Fires when a conversation is moved to another mailbox").build(),
								ParameterOption.builder().name("Conversation Status Changed").value("convo.status")
										.description("Fires when a conversation status changes").build(),
								ParameterOption.builder().name("Conversation Tags Changed").value("convo.tags")
										.description("Fires when conversation tags are updated").build(),
								ParameterOption.builder().name("Conversation Customer Reply").value("convo.customer.reply.created")
										.description("Fires when a customer replies to a conversation").build(),
								ParameterOption.builder().name("Conversation Agent Reply").value("convo.agent.reply.created")
										.description("Fires when an agent replies to a conversation").build(),
								ParameterOption.builder().name("Conversation Note Created").value("convo.note.created")
										.description("Fires when a note is added to a conversation").build(),
								ParameterOption.builder().name("Customer Created").value("customer.created")
										.description("Fires when a new customer is created").build(),
								ParameterOption.builder().name("Satisfaction Rating").value("satisfaction.ratings")
										.description("Fires when a satisfaction rating is submitted").build()
						)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null || inputData.isEmpty()) {
			// Return trigger-ready placeholder when no webhook data present
			return NodeExecutionResult.success(List.of(createEmptyTriggerItem()));
		}

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> data = unwrapJson(item);
			results.add(createTriggerItem(data));
		}

		return NodeExecutionResult.success(results);
	}
}
