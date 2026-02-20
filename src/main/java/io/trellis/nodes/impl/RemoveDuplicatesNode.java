package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Remove Duplicates Node - removes duplicate items from the input.
 * Supports comparing all fields, all fields except specified ones,
 * or only selected fields. Outputs unique items and optionally the duplicates.
 *
 * Outputs:
 * - Output 0 (Kept): unique items (first occurrence kept)
 * - Output 1 (Duplicates): duplicate items that were removed
 */
@Slf4j
@Node(
	type = "removeDuplicates",
	displayName = "Remove Duplicates",
	description = "Remove duplicate items from the input based on field comparison.",
	category = "Flow",
	icon = "copy-minus"
)
public class RemoveDuplicatesNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("kept").displayName("Kept").build(),
			NodeOutput.builder().name("duplicates").displayName("Duplicates").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("compare")
				.displayName("Compare")
				.description("How to identify duplicate items.")
				.type(ParameterType.OPTIONS)
				.defaultValue("allFields")
				.options(List.of(
					ParameterOption.builder()
						.name("All Fields")
						.value("allFields")
						.description("Compare all fields of each item")
						.build(),
					ParameterOption.builder()
						.name("All Fields Except")
						.value("allFieldsExcept")
						.description("Compare all fields except the specified ones")
						.build(),
					ParameterOption.builder()
						.name("Selected Fields")
						.value("selectedFields")
						.description("Compare only the specified fields")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("fieldsToExclude")
				.displayName("Fields to Exclude")
				.description("Comma-separated list of field names to exclude from comparison.")
				.type(ParameterType.STRING)
				.placeHolder("field1, field2")
				.displayOptions(Map.of("show", Map.of("compare", List.of("allFieldsExcept"))))
				.build(),

			NodeParameter.builder()
				.name("fieldsToCompare")
				.displayName("Fields to Compare")
				.description("Comma-separated list of field names to use for comparison.")
				.type(ParameterType.STRING)
				.placeHolder("email, name")
				.displayOptions(Map.of("show", Map.of("compare", List.of("selectedFields"))))
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("disableDotNotation")
						.displayName("Disable Dot Notation")
						.description("When enabled, field names with dots are treated literally instead of as nested paths.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build(),
					NodeParameter.builder()
						.name("removeOtherFields")
						.displayName("Remove Other Fields")
						.description("When enabled, only the comparison fields are kept in the output.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build()
				))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		String compare = context.getParameter("compare", "allFields");
		String fieldsToExclude = context.getParameter("fieldsToExclude", "");
		String fieldsToCompare = context.getParameter("fieldsToCompare", "");

		// Read options
		boolean disableDotNotation = false;
		boolean removeOtherFields = false;
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			disableDotNotation = toBoolean(opts.get("disableDotNotation"), false);
			removeOtherFields = toBoolean(opts.get("removeOtherFields"), false);
		}

		Set<String> excludeFields = parseFieldList(fieldsToExclude);
		Set<String> compareFields = parseFieldList(fieldsToCompare);

		List<Map<String, Object>> kept = new ArrayList<>();
		List<Map<String, Object>> duplicates = new ArrayList<>();
		Set<String> seenKeys = new LinkedHashSet<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);

			// Build comparison key based on mode
			String key = buildComparisonKey(json, compare, compareFields, excludeFields, disableDotNotation);

			if (seenKeys.add(key)) {
				// First occurrence - keep
				if (removeOtherFields && "selectedFields".equals(compare) && !compareFields.isEmpty()) {
					kept.add(wrapInJson(pickFields(json, compareFields, disableDotNotation)));
				} else {
					kept.add(deepClone(item));
				}
			} else {
				// Duplicate
				duplicates.add(deepClone(item));
			}
		}

		log.debug("RemoveDuplicates: {} items -> {} kept, {} duplicates (compare={})",
				inputData.size(), kept.size(), duplicates.size(), compare);

		return NodeExecutionResult.successMultiOutput(List.of(kept, duplicates));
	}

	/**
	 * Build a string key for an item based on the comparison mode.
	 * Items with identical keys are considered duplicates.
	 */
	private String buildComparisonKey(Map<String, Object> json, String compare,
			Set<String> compareFields, Set<String> excludeFields, boolean disableDotNotation) {
		Map<String, Object> keyMap;

		switch (compare) {
			case "selectedFields":
				keyMap = pickFields(json, compareFields, disableDotNotation);
				break;

			case "allFieldsExcept":
				keyMap = new LinkedHashMap<>(json);
				for (String field : excludeFields) {
					if (disableDotNotation) {
						keyMap.remove(field);
					} else {
						removeField(keyMap, field);
					}
				}
				break;

			default: // allFields
				keyMap = json;
				break;
		}

		return stableStringify(keyMap);
	}

	/**
	 * Pick selected fields from a map, supporting dot notation for nested fields.
	 */
	private Map<String, Object> pickFields(Map<String, Object> json, Set<String> fields,
			boolean disableDotNotation) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String field : fields) {
			Object value;
			if (disableDotNotation) {
				value = json.get(field);
			} else {
				value = getNestedValue(wrapInJson(json), "json." + field);
			}
			if (value != null) {
				result.put(field, value);
			}
		}
		return result;
	}

	/**
	 * Remove a field from a map, supporting dot notation.
	 */
	@SuppressWarnings("unchecked")
	private void removeField(Map<String, Object> map, String field) {
		String[] parts = field.split("\\.");
		if (parts.length == 1) {
			map.remove(parts[0]);
			return;
		}
		Map<String, Object> current = map;
		for (int i = 0; i < parts.length - 1; i++) {
			Object next = current.get(parts[i]);
			if (next instanceof Map) {
				current = (Map<String, Object>) next;
			} else {
				return;
			}
		}
		current.remove(parts[parts.length - 1]);
	}

	/**
	 * Create a stable string representation of a map for comparison.
	 * Keys are sorted to ensure consistent comparison regardless of insertion order.
	 */
	@SuppressWarnings("unchecked")
	private String stableStringify(Map<String, Object> map) {
		if (map == null || map.isEmpty()) return "{}";

		StringBuilder sb = new StringBuilder("{");
		List<String> keys = new ArrayList<>(map.keySet());
		java.util.Collections.sort(keys);

		for (int i = 0; i < keys.size(); i++) {
			if (i > 0) sb.append(",");
			String key = keys.get(i);
			Object value = map.get(key);
			sb.append("\"").append(key).append("\":");
			sb.append(stableStringifyValue(value));
		}
		sb.append("}");
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private String stableStringifyValue(Object value) {
		if (value == null) return "null";
		if (value instanceof Map) return stableStringify((Map<String, Object>) value);
		if (value instanceof List) {
			List<?> list = (List<?>) value;
			return "[" + list.stream()
				.map(this::stableStringifyValue)
				.collect(Collectors.joining(",")) + "]";
		}
		if (value instanceof String) return "\"" + value + "\"";
		return Objects.toString(value);
	}

	private Set<String> parseFieldList(String fieldList) {
		if (fieldList == null || fieldList.isBlank()) return Set.of();
		return Arrays.stream(fieldList.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}
