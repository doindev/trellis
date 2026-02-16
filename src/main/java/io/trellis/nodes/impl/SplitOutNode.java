package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Split Out Node - splits an array field within each item into individual output items.
 * For each element in the specified array field, a new item is created.
 * Optionally includes other fields from the original item.
 */
@Slf4j
@Node(
	type = "splitOut",
	displayName = "Split Out",
	description = "Split an array field in each item into separate output items.",
	category = "Flow",
	icon = "unfold-vertical"
)
public class SplitOutNode extends AbstractNode {

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
				.name("fieldToSplit")
				.displayName("Field to Split Out")
				.description("The name of the array field to split. Supports dot notation (e.g., 'json.items').")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("items")
				.build(),

			NodeParameter.builder()
				.name("include")
				.displayName("Include")
				.description("Which other fields from the original item to include alongside each split element.")
				.type(ParameterType.OPTIONS)
				.defaultValue("noOtherFields")
				.options(List.of(
					ParameterOption.builder()
						.name("No Other Fields")
						.value("noOtherFields")
						.description("Only include the split element")
						.build(),
					ParameterOption.builder()
						.name("All Other Fields")
						.value("allOtherFields")
						.description("Include all other fields from the original item")
						.build(),
					ParameterOption.builder()
						.name("Selected Fields")
						.value("selectedFields")
						.description("Include only specified fields from the original item")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("fieldsToInclude")
				.displayName("Fields to Include")
				.description("Comma-separated list of field names to include from the original item.")
				.type(ParameterType.STRING)
				.placeHolder("field1, field2, field3")
				.displayOptions(Map.of("show", Map.of("include", List.of("selectedFields"))))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String fieldToSplit = context.getParameter("fieldToSplit", "");
		String include = context.getParameter("include", "noOtherFields");
		String fieldsToInclude = context.getParameter("fieldsToInclude", "");

		if (fieldToSplit == null || fieldToSplit.isBlank()) {
			return NodeExecutionResult.error("Field to split is required");
		}

		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		Set<String> selectedFields = null;
		if ("selectedFields".equals(include) && fieldsToInclude != null && !fieldsToInclude.isBlank()) {
			selectedFields = Arrays.stream(fieldsToInclude.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
		}

		List<Map<String, Object>> outputItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Object arrayValue = getNestedValue(item, fieldToSplit);

			if (arrayValue instanceof List) {
				List<?> arrayList = (List<?>) arrayValue;
				Map<String, Object> originalJson = unwrapJson(item);

				for (Object element : arrayList) {
					Map<String, Object> newItem = new HashMap<>();

					// Include other fields based on mode
					if ("allOtherFields".equals(include)) {
						newItem.putAll(deepClone(originalJson));
						removeNestedField(newItem, fieldToSplit);
					} else if ("selectedFields".equals(include) && selectedFields != null) {
						for (String field : selectedFields) {
							Object fieldValue = originalJson.get(field);
							if (fieldValue != null) {
								newItem.put(field, fieldValue);
							}
						}
					}

					// Add the split element
					if (element instanceof Map) {
						newItem.putAll((Map<String, Object>) element);
					} else {
						newItem.put("value", element);
					}

					outputItems.add(wrapInJson(newItem));
				}
			} else {
				// Field is not an array, pass item through unchanged
				outputItems.add(deepClone(item));
			}
		}

		log.debug("SplitOut node: {} items -> {} items (field={})", inputData.size(), outputItems.size(), fieldToSplit);
		return NodeExecutionResult.success(outputItems);
	}

	/**
	 * Removes a field from a map, supporting simple (non-nested) field names.
	 * For dot-notation fields, removes the leaf key from the nested structure.
	 */
	@SuppressWarnings("unchecked")
	private void removeNestedField(Map<String, Object> map, String field) {
		if (field == null || field.isBlank()) {
			return;
		}

		// Strip leading "json." prefix since we're already working with unwrapped data
		String actualField = field.startsWith("json.") ? field.substring(5) : field;

		String[] parts = actualField.split("\\.");
		if (parts.length == 1) {
			map.remove(parts[0]);
			return;
		}

		// Navigate to the parent of the leaf
		Map<String, Object> current = map;
		for (int i = 0; i < parts.length - 1; i++) {
			Object next = current.get(parts[i]);
			if (next instanceof Map) {
				current = (Map<String, Object>) next;
			} else {
				return; // Path doesn't exist
			}
		}
		current.remove(parts[parts.length - 1]);
	}
}
