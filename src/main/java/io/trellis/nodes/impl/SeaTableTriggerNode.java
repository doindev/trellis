package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * SeaTable Trigger — receive events when SeaTable rows are created, updated, or deleted.
 */
@Node(
		type = "seaTableTrigger",
		displayName = "SeaTable Trigger",
		description = "Triggers when a SeaTable row event occurs",
		category = "Spreadsheets",
		icon = "seaTable",
		trigger = true,
		credentials = {"seaTableApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class SeaTableTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"event", context.getParameter("event", "rowCreated"),
				"tableName", context.getParameter("tableName", ""),
				"message", "SeaTable trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("rowCreated")
						.options(List.of(
								ParameterOption.builder().name("Row Created").value("rowCreated").build(),
								ParameterOption.builder().name("Row Updated").value("rowUpdated").build(),
								ParameterOption.builder().name("Row Deleted").value("rowDeleted").build()
						)).build(),
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The SeaTable table name to watch.").build()
		);
	}
}
