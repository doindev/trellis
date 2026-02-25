package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Execution Data Node — saves important key-value metadata on the current execution
 * for later search and review. Data is stored in the execution's context and passed
 * through to the next node unchanged.
 *
 * Keys are truncated to 50 characters, values to 512 characters.
 */
@Slf4j
@Node(
	type = "executionData",
	displayName = "Execution Data",
	description = "Add execution data for search and review.",
	category = "Core",
	icon = "clipboard-list"
)
public class ExecutionDataNode extends AbstractNode {

	private static final int MAX_KEY_LENGTH = 50;
	private static final int MAX_VALUE_LENGTH = 512;

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("notice")
				.displayName("")
				.description("Save important data using this node. It will be displayed on each execution for easy reference and you can filter by it.")
				.type(ParameterType.NOTICE)
				.defaultValue("")
				.build(),

			NodeParameter.builder()
				.name("operation")
				.displayName("Operation")
				.description("What to do.")
				.type(ParameterType.OPTIONS)
				.defaultValue("save")
				.noDataExpression(true)
				.options(List.of(
					NodeParameter.ParameterOption.builder()
						.name("Save Highlight Data (for Search/review)")
						.value("save")
						.action("Save Highlight Data (for search/review)")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("dataToSave")
				.displayName("Data to Save")
				.description("Key-value pairs to save as execution metadata.")
				.type(ParameterType.FIXED_COLLECTION)
				.defaultValue(Map.of())
				.placeHolder("Add Saved Field")
				.displayOptions(Map.of("show", Map.of("operation", List.of("save"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("key")
						.displayName("Key")
						.description("The key under which to save the data.")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("e.g. myKey")
						.maxLength(MAX_KEY_LENGTH)
						.build(),
					NodeParameter.builder()
						.name("value")
						.displayName("Value")
						.description("The value to save.")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("e.g. myValue")
						.maxLength(MAX_VALUE_LENGTH)
						.build()
				))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null) {
			inputData = List.of();
		}

		String operation = context.getParameter("operation", "save");
		if (!"save".equals(operation)) {
			return NodeExecutionResult.success(inputData);
		}

		List<Map<String, Object>> returnData = new ArrayList<>();
		Map<String, String> savedData = new LinkedHashMap<>();
		List<String> hints = new ArrayList<>();

		for (int i = 0; i < inputData.size(); i++) {
			try {
				Object dataToSaveRaw = context.getParameters().get("dataToSave");
				List<Map<String, Object>> values = extractValues(dataToSaveRaw);

				for (Map<String, Object> entry : values) {
					String key = toString(entry.get("key"));
					String value = toString(entry.get("value"));

					// Truncate key and value to limits
					if (key.length() > MAX_KEY_LENGTH) {
						key = key.substring(0, MAX_KEY_LENGTH);
						if (!hints.contains("Some keys were truncated to " + MAX_KEY_LENGTH + " characters.")) {
							hints.add("Some keys were truncated to " + MAX_KEY_LENGTH + " characters.");
						}
					}
					if (value.length() > MAX_VALUE_LENGTH) {
						value = value.substring(0, MAX_VALUE_LENGTH);
						if (!hints.contains("Some values were truncated to " + MAX_VALUE_LENGTH + " characters.")) {
							hints.add("Some values were truncated to " + MAX_VALUE_LENGTH + " characters.");
						}
					}

					savedData.put(key, value);
				}

				returnData.add(inputData.get(i));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					returnData.add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return NodeExecutionResult.error("Failed to save execution data: " + e.getMessage(), e);
				}
			}
		}

		// Store the saved data in the execution's node context
		if (!savedData.isEmpty()) {
			Map<String, Object> contextData = context.getNodeContextData();
			if (contextData == null) {
				contextData = new LinkedHashMap<>();
				context.setNodeContextData(contextData);
			}
			contextData.put("customData", savedData);
			log.debug("Execution Data node saved {} key-value pairs: {}", savedData.size(), savedData.keySet());
		}

		NodeExecutionResult.NodeExecutionResultBuilder builder = NodeExecutionResult.builder()
				.output(List.of(returnData));
		if (!hints.isEmpty()) {
			builder.hints(hints);
		}
		return builder.build();
	}

	/**
	 * Extracts the values list from the dataToSave parameter,
	 * handling both map-wrapped and list formats.
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> extractValues(Object dataToSaveRaw) {
		if (dataToSaveRaw == null) {
			return List.of();
		}
		if (dataToSaveRaw instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) dataToSaveRaw;
			Object values = map.get("values");
			if (values instanceof List) {
				return (List<Map<String, Object>>) values;
			}
			return List.of();
		}
		if (dataToSaveRaw instanceof List) {
			return (List<Map<String, Object>>) dataToSaveRaw;
		}
		return List.of();
	}
}
