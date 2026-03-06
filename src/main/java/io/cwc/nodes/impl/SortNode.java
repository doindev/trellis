package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Sort Node - sorts items by field values, randomly, or using custom code.
 * Supports multi-field sorting with ascending/descending per field,
 * case-insensitive string comparison, and dot notation for nested fields.
 */
@Slf4j
@Node(
	type = "sort",
	displayName = "Sort",
	description = "Sort items by field values, randomly, or using custom code.",
	category = "Flow",
	icon = "arrow-up-narrow-wide"
)
public class SortNode extends AbstractNode {

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
				.name("type")
				.displayName("Type")
				.description("How to sort the items.")
				.type(ParameterType.OPTIONS)
				.defaultValue("simple")
				.options(List.of(
					ParameterOption.builder()
						.name("Simple")
						.value("simple")
						.description("Sort by field values")
						.build(),
					ParameterOption.builder()
						.name("Random")
						.value("random")
						.description("Shuffle items into random order")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("sortFieldsUi")
				.displayName("Fields To Sort By")
				.description("Add fields to sort by. Fields are evaluated in order — earlier fields take priority.")
				.type(ParameterType.FIXED_COLLECTION)
				.defaultValue(List.of())
				.displayOptions(Map.of("show", Map.of("type", List.of("simple"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("fieldName")
						.displayName("Field Name")
						.description("The field to sort by. Supports dot notation for nested fields (e.g., 'user.name').")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. name")
						.build(),
					NodeParameter.builder()
						.name("order")
						.displayName("Order")
						.description("The sort direction.")
						.type(ParameterType.OPTIONS)
						.defaultValue("ascending")
						.options(List.of(
							ParameterOption.builder()
								.name("Ascending")
								.value("ascending")
								.description("Sort from lowest to highest (A-Z, 0-9)")
								.build(),
							ParameterOption.builder()
								.name("Descending")
								.value("descending")
								.description("Sort from highest to lowest (Z-A, 9-0)")
								.build()
						))
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("type", List.of("simple"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("disableDotNotation")
						.displayName("Disable Dot Notation")
						.description("When enabled, field names with dots are treated literally instead of as nested paths.")
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
			return NodeExecutionResult.empty();
		}

		String type = context.getParameter("type", "simple");

		// Make a mutable copy for sorting
		List<Map<String, Object>> items = new ArrayList<>(inputData);

		if ("random".equals(type)) {
			shuffleArray(items);
			log.debug("Sort: shuffled {} items randomly", items.size());
			return NodeExecutionResult.success(items);
		}

		// Simple sort mode
		boolean disableDotNotation = false;
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			disableDotNotation = toBoolean(((Map<String, Object>) optionsObj).get("disableDotNotation"), false);
		}

		// Parse sort fields from the fixedCollection parameter
		List<SortField> sortFields = parseSortFields(context.getParameter("sortFieldsUi", null));

		if (sortFields.isEmpty()) {
			log.debug("Sort: no sort fields specified, returning items unchanged");
			return NodeExecutionResult.success(items);
		}

		final boolean dotNotationDisabled = disableDotNotation;
		items.sort(buildComparator(sortFields, dotNotationDisabled));

		log.debug("Sort: sorted {} items by {} fields", items.size(), sortFields.size());
		return NodeExecutionResult.success(items);
	}

	/**
	 * Build a composite comparator from the list of sort fields.
	 */
	private Comparator<Map<String, Object>> buildComparator(List<SortField> sortFields,
			boolean disableDotNotation) {
		return (a, b) -> {
			for (SortField sf : sortFields) {
				Object valueA = getFieldValue(a, sf.fieldName, disableDotNotation);
				Object valueB = getFieldValue(b, sf.fieldName, disableDotNotation);

				int cmp = compareValues(valueA, valueB);
				if (cmp != 0) {
					return sf.descending ? -cmp : cmp;
				}
			}
			return 0;
		};
	}

	/**
	 * Get a field value from an item, unwrapping json and supporting dot notation.
	 */
	private Object getFieldValue(Map<String, Object> item, String fieldName, boolean disableDotNotation) {
		Map<String, Object> json = unwrapJson(item);
		if (disableDotNotation) {
			return json.get(fieldName);
		}
		// Use dot notation via getNestedValue (prepend "json." since that's the wrapper format)
		return getNestedValue(item, "json." + fieldName);
	}

	/**
	 * Compare two values with type-aware logic.
	 * Handles null, Number, String (case-insensitive), Boolean, and falls back to toString.
	 */
	private int compareValues(Object a, Object b) {
		// Nulls sort last
		if (a == null && b == null) return 0;
		if (a == null) return 1;
		if (b == null) return -1;

		// Number comparison
		if (a instanceof Number && b instanceof Number) {
			return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
		}

		// Boolean comparison (false < true)
		if (a instanceof Boolean && b instanceof Boolean) {
			return Boolean.compare((Boolean) a, (Boolean) b);
		}

		// String comparison (case-insensitive)
		String strA = String.valueOf(a).toLowerCase();
		String strB = String.valueOf(b).toLowerCase();

		// Try numeric comparison if both look like numbers
		try {
			double numA = Double.parseDouble(strA);
			double numB = Double.parseDouble(strB);
			return Double.compare(numA, numB);
		} catch (NumberFormatException ignored) {
			// Fall through to string comparison
		}

		return strA.compareTo(strB);
	}

	/**
	 * Parse sort fields from the fixedCollection parameter.
	 * The parameter value can be a List of Maps (each with fieldName and order).
	 */
	@SuppressWarnings("unchecked")
	private List<SortField> parseSortFields(Object sortFieldsParam) {
		List<SortField> fields = new ArrayList<>();
		if (sortFieldsParam == null) return fields;

		List<?> fieldList;
		if (sortFieldsParam instanceof Map) {
			// fixedCollection may wrap values in a map with a "values" key
			Map<String, Object> map = (Map<String, Object>) sortFieldsParam;
			Object values = map.get("values");
			if (values instanceof List) {
				fieldList = (List<?>) values;
			} else {
				// Single entry as a map
				fieldList = List.of(sortFieldsParam);
			}
		} else if (sortFieldsParam instanceof List) {
			fieldList = (List<?>) sortFieldsParam;
		} else {
			return fields;
		}

		for (Object entry : fieldList) {
			if (entry instanceof Map) {
				Map<String, Object> fieldMap = (Map<String, Object>) entry;
				String name = (String) fieldMap.get("fieldName");
				String order = (String) fieldMap.getOrDefault("order", "ascending");
				if (name != null && !name.isBlank()) {
					fields.add(new SortField(name.trim(), "descending".equals(order)));
				}
			}
		}
		return fields;
	}

	/**
	 * Fisher-Yates shuffle.
	 */
	private <T> void shuffleArray(List<T> list) {
		for (int i = list.size() - 1; i > 0; i--) {
			int j = ThreadLocalRandom.current().nextInt(i + 1);
			T temp = list.get(i);
			list.set(i, list.get(j));
			list.set(j, temp);
		}
	}

	private record SortField(String fieldName, boolean descending) {}
}
