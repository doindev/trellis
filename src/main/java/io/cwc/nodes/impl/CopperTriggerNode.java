package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Copper Trigger — starts the workflow when events occur in Copper CRM.
 */
@Node(
		type = "copperTrigger",
		displayName = "Copper Trigger",
		description = "Starts the workflow when events occur in Copper CRM",
		category = "CRM",
		icon = "copper",
		credentials = {"copperApi"},
		trigger = true
)
public class CopperTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).required(true).defaultValue("person")
						.options(List.of(
								ParameterOption.builder().name("Person").value("person").build(),
								ParameterOption.builder().name("Lead").value("lead").build(),
								ParameterOption.builder().name("Opportunity").value("opportunity").build(),
								ParameterOption.builder().name("Company").value("company").build(),
								ParameterOption.builder().name("Project").value("project").build(),
								ParameterOption.builder().name("Task").value("task").build()
						)).build(),
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).required(true).defaultValue("new")
						.options(List.of(
								ParameterOption.builder().name("New").value("new")
										.description("Triggered when a new record is created").build(),
								ParameterOption.builder().name("Updated").value("update")
										.description("Triggered when a record is updated").build(),
								ParameterOption.builder().name("Deleted").value("delete")
										.description("Triggered when a record is deleted").build()
						)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "person");
		String event = context.getParameter("event", "new");

		// Webhook-based trigger — returns configuration for webhook registration
		Map<String, Object> webhookData = new LinkedHashMap<>();
		webhookData.put("resource", resource);
		webhookData.put("event", event);

		return NodeExecutionResult.success(List.of(createTriggerItem(webhookData)));
	}
}
