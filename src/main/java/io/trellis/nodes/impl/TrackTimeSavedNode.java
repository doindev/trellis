package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Track Time Saved — records estimated time saved by a workflow execution.
 * This is a pass-through node that annotates the execution with time-saved
 * metadata and passes input data through unchanged.
 */
@Node(
		type = "timeSaved",
		displayName = "Track Time Saved",
		description = "Track estimated time saved by this workflow",
		category = "Miscellaneous",
		icon = "clock"
)
public class TrackTimeSavedNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			int savedTime = toInt(context.getParameters().get("savedTime"), 0);
			String unit = context.getParameter("unit", "minutes");
			String description = context.getParameter("description", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				Map<String, Object> result = new LinkedHashMap<>();
				// Pass through the original data
				Object json = item.get("json");
				if (json instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> jsonMap = (Map<String, Object>) json;
					result.putAll(jsonMap);
				}
				// Add time-saved metadata
				result.put("timeSaved", savedTime);
				result.put("timeSavedUnit", unit);
				if (!description.isBlank()) {
					result.put("timeSavedDescription", description);
				}
				results.add(wrapInJson(result));
			}

			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("savedTime").displayName("Time Saved")
						.type(ParameterType.NUMBER).defaultValue(0)
						.required(true)
						.description("Estimated amount of time saved.").build(),
				NodeParameter.builder()
						.name("unit").displayName("Unit")
						.type(ParameterType.STRING).defaultValue("minutes")
						.description("Time unit (seconds, minutes, hours).").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("Description of the time saved.").build()
		);
	}
}
