package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Summarize Node - aggregate items together, similar to pivot tables.
 * Supports: count, countUnique, sum, average, min, max, concatenate.
 * Can group results by one or more "split by" fields.
 */
@Slf4j
@Node(
	type = "summarize",
	displayName = "Summarize",
	description = "Aggregate items with operations like count, sum, average, min, max. Optionally group by fields.",
	category = "Data Transformation",
	icon = "sigma"
)
public class SummarizeNode extends AbstractNode {

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
				.name("fieldsToSummarize")
				.displayName("Fields to Summarize")
				.description("Fields and operations to apply.")
				.type(ParameterType.FIXED_COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("field")
						.displayName("Field")
						.description("The field to aggregate.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. amount")
						.build(),
					NodeParameter.builder()
						.name("aggregation")
						.displayName("Aggregation")
						.description("The aggregation operation to perform.")
						.type(ParameterType.OPTIONS)
						.defaultValue("count")
						.required(true)
						.options(List.of(
							ParameterOption.builder().name("Count").value("count").build(),
							ParameterOption.builder().name("Count Unique").value("countUnique").build(),
							ParameterOption.builder().name("Sum").value("sum").build(),
							ParameterOption.builder().name("Average").value("average").build(),
							ParameterOption.builder().name("Min").value("min").build(),
							ParameterOption.builder().name("Max").value("max").build(),
							ParameterOption.builder().name("Concatenate").value("concatenate").build()
						))
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("splitBy")
				.displayName("Fields to Split By")
				.description("Comma-separated field names to group results by (like GROUP BY in SQL).")
				.type(ParameterType.STRING)
				.placeHolder("e.g. category, region")
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("disableDotNotation")
						.displayName("Disable Dot Notation")
						.description("When enabled, field names with dots are treated literally.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build(),
					NodeParameter.builder()
						.name("continueIfFieldNotFound")
						.displayName("Continue If Field Not Found")
						.description("Continue processing if a specified field does not exist in an item.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(true)
						.build(),
					NodeParameter.builder()
						.name("separator")
						.displayName("Concatenate Separator")
						.description("Separator to use when concatenating values.")
						.type(ParameterType.STRING)
						.defaultValue(", ")
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

		Object fieldsParam = context.getParameter("fieldsToSummarize", null);
		List<SummarizeField> fields = parseSummarizeFields(fieldsParam);

		if (fields.isEmpty()) {
			return NodeExecutionResult.error("No fields to summarize specified");
		}

		String splitByStr = context.getParameter("splitBy", "");
		List<String> splitByFields = parseSplitBy(splitByStr);

		boolean disableDotNotation = false;
		boolean continueIfFieldNotFound = true;
		String separator = ", ";
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			disableDotNotation = toBoolean(opts.get("disableDotNotation"), false);
			continueIfFieldNotFound = toBoolean(opts.get("continueIfFieldNotFound"), true);
			Object sep = opts.get("separator");
			if (sep != null) separator = String.valueOf(sep);
		}

		// Group items by split-by fields
		Map<String, List<Map<String, Object>>> groups = groupItems(inputData, splitByFields, disableDotNotation);

		// Calculate aggregations for each group
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map.Entry<String, List<Map<String, Object>>> group : groups.entrySet()) {
			List<Map<String, Object>> groupItems = group.getValue();
			Map<String, Object> outputJson = new LinkedHashMap<>();

			// Add split-by field values from first item in group
			if (!splitByFields.isEmpty() && !groupItems.isEmpty()) {
				Map<String, Object> firstJson = unwrapJson(groupItems.get(0));
				for (String field : splitByFields) {
					Object val = disableDotNotation ? firstJson.get(field) : getNestedValue(groupItems.get(0), "json." + field);
					outputJson.put(field, val);
				}
			}

			// Calculate each aggregation
			for (SummarizeField sf : fields) {
				List<Object> values = new ArrayList<>();
				boolean fieldFound = false;
				for (Map<String, Object> item : groupItems) {
					Map<String, Object> json = unwrapJson(item);
					boolean exists = disableDotNotation
						? json.containsKey(sf.field)
						: getNestedValue(item, "json." + sf.field) != null || json.containsKey(sf.field);
					if (exists) {
						fieldFound = true;
						Object val = disableDotNotation
							? json.get(sf.field)
							: getNestedValue(item, "json." + sf.field);
						if (val != null) {
							values.add(val);
						}
					}
				}

				if (!fieldFound && !continueIfFieldNotFound) {
					return NodeExecutionResult.error(
						"Field '" + sf.field + "' not found in any item. "
						+ "Enable 'Continue If Field Not Found' to skip missing fields.");
				}

				String outputKey = sf.aggregation + "_" + sf.field;
				Object aggregated = computeAggregation(sf.aggregation, values, groupItems.size(), separator);
				outputJson.put(outputKey, aggregated);
			}

			result.add(wrapInJson(outputJson));
		}

		log.debug("Summarize: {} items -> {} groups ({} fields, {} split-by)",
				inputData.size(), result.size(), fields.size(), splitByFields.size());
		return NodeExecutionResult.success(result);
	}

	private Object computeAggregation(String operation, List<Object> values, int totalCount, String separator) {
		switch (operation) {
			case "count":
				return totalCount;

			case "countUnique":
				return new LinkedHashSet<>(values).size();

			case "sum": {
				double sum = 0;
				for (Object v : values) {
					sum += toDouble(v);
				}
				return sum;
			}

			case "average": {
				if (values.isEmpty()) return 0.0;
				double sum = 0;
				for (Object v : values) {
					sum += toDouble(v);
				}
				return sum / values.size();
			}

			case "min": {
				if (values.isEmpty()) return null;
				double min = Double.MAX_VALUE;
				for (Object v : values) {
					double d = toDouble(v);
					if (d < min) min = d;
				}
				return min;
			}

			case "max": {
				if (values.isEmpty()) return null;
				double max = -Double.MAX_VALUE;
				for (Object v : values) {
					double d = toDouble(v);
					if (d > max) max = d;
				}
				return max;
			}

			case "concatenate":
				return values.stream()
					.map(String::valueOf)
					.collect(Collectors.joining(separator));

			default:
				return null;
		}
	}

	private double toDouble(Object value) {
		if (value instanceof Number) return ((Number) value).doubleValue();
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	private Map<String, List<Map<String, Object>>> groupItems(List<Map<String, Object>> items,
			List<String> splitByFields, boolean disableDotNotation) {
		Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();

		for (Map<String, Object> item : items) {
			String groupKey;
			if (splitByFields.isEmpty()) {
				groupKey = "__all__";
			} else {
				StringBuilder keyBuilder = new StringBuilder();
				for (String field : splitByFields) {
					Object val = disableDotNotation
						? unwrapJson(item).get(field)
						: getNestedValue(item, "json." + field);
					keyBuilder.append(val).append("|||");
				}
				groupKey = keyBuilder.toString();
			}

			groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(item);
		}

		return groups;
	}

	@SuppressWarnings("unchecked")
	private List<SummarizeField> parseSummarizeFields(Object fieldsParam) {
		List<SummarizeField> fields = new ArrayList<>();
		if (fieldsParam == null) return fields;

		List<?> fieldList;
		if (fieldsParam instanceof Map) {
			Object values = ((Map<String, Object>) fieldsParam).get("values");
			if (values instanceof List) {
				fieldList = (List<?>) values;
			} else {
				fieldList = List.of(fieldsParam);
			}
		} else if (fieldsParam instanceof List) {
			fieldList = (List<?>) fieldsParam;
		} else {
			return fields;
		}

		for (Object entry : fieldList) {
			if (entry instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) entry;
				String field = (String) map.get("field");
				String aggregation = (String) map.getOrDefault("aggregation", "count");
				if (field != null && !field.isBlank()) {
					fields.add(new SummarizeField(field.trim(), aggregation));
				}
			}
		}
		return fields;
	}

	private List<String> parseSplitBy(String splitByStr) {
		if (splitByStr == null || splitByStr.isBlank()) return List.of();
		return Arrays.stream(splitByStr.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toList();
	}

	private record SummarizeField(String field, String aggregation) {}
}
