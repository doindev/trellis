package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * Affinity Trigger — starts the workflow when events occur in Affinity CRM.
 */
@Node(
		type = "affinityTrigger",
		displayName = "Affinity Trigger",
		description = "Starts the workflow when events occur in Affinity CRM",
		category = "CRM",
		icon = "affinity",
		credentials = {"affinityApi"},
		trigger = true,
		triggerCategory = "Other"
)
public class AffinityTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).required(true).defaultValue("listEntry.created")
						.options(List.of(
								ParameterOption.builder().name("List Entry Created").value("listEntry.created")
										.description("Triggered when a new list entry is created").build(),
								ParameterOption.builder().name("List Entry Deleted").value("listEntry.deleted")
										.description("Triggered when a list entry is deleted").build(),
								ParameterOption.builder().name("Field Value Changed").value("fieldValue.changed")
										.description("Triggered when a field value changes").build(),
								ParameterOption.builder().name("File Added").value("file.added")
										.description("Triggered when a file is added").build(),
								ParameterOption.builder().name("Note Created").value("note.created")
										.description("Triggered when a note is created").build(),
								ParameterOption.builder().name("Organization Created").value("organization.created")
										.description("Triggered when an organization is created").build(),
								ParameterOption.builder().name("Person Created").value("person.created")
										.description("Triggered when a person is created").build()
						)).build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter events to a specific list.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String event = context.getParameter("event", "listEntry.created");
		String listId = context.getParameter("listId", "");

		// Webhook-based trigger — returns configuration for webhook registration
		Map<String, Object> webhookData = new LinkedHashMap<>();
		webhookData.put("event", event);
		if (!listId.isEmpty()) {
			webhookData.put("listId", listId);
		}

		return NodeExecutionResult.success(List.of(createTriggerItem(webhookData)));
	}
}
