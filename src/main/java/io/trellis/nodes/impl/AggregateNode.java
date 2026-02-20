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
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Aggregate Node - groups separate items or field values together into individual items.
 *
 * Two modes:
 * - "aggregateIndividualFields": For each specified field, collects all values across
 *   items into an array, producing one output item with the aggregated arrays.
 * - "aggregateAllItemData": Collects all items into a single array field.
 */
@Slf4j
@Node(
	type = "aggregate",
	displayName = "Aggregate",
	description = "Combine separate items or field values together into individual items.",
	category = "Data Transformation",
	icon = "layers"
)
public class AggregateNode extends AbstractNode {

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
				.name("aggregate")
				.displayName("Aggregate")
				.description("How to aggregate the items.")
				.type(ParameterType.OPTIONS)
				.defaultValue("aggregateIndividualFields")
				.options(List.of(
					ParameterOption.builder()
						.name("Individual Fields")
						.value("aggregateIndividualFields")
						.description("For each specified field, collect all values across items into an array")
						.build(),
					ParameterOption.builder()
						.name("All Item Data")
						.value("aggregateAllItemData")
						.description("Collect all input items into a single array")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("fieldsToAggregate")
				.displayName("Fields To Aggregate")
				.description("Fields to aggregate. Each field's values across all items will become an array.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("aggregate", List.of("aggregateIndividualFields"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("fieldToAggregate")
						.displayName("Input Field Name")
						.description("The field to collect values from.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. email")
						.build(),
					NodeParameter.builder()
						.name("renameField")
						.displayName("Output Field Name")
						.description("Optional: rename the field in the output. Leave empty to keep the original name.")
						.type(ParameterType.STRING)
						.placeHolder("e.g. allEmails")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("destinationFieldName")
				.displayName("Put Output in Field")
				.description("The field name for the aggregated data array.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.displayOptions(Map.of("show", Map.of("aggregate", List.of("aggregateAllItemData"))))
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
						.name("keepOnlyUnique")
						.displayName("Keep Only Unique Values")
						.description("Remove duplicate values from aggregated arrays.")
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

		String aggregate = context.getParameter("aggregate", "aggregateIndividualFields");

		boolean disableDotNotation = false;
		boolean keepOnlyUnique = false;
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			disableDotNotation = toBoolean(opts.get("disableDotNotation"), false);
			keepOnlyUnique = toBoolean(opts.get("keepOnlyUnique"), false);
		}

		if ("aggregateAllItemData".equals(aggregate)) {
			return aggregateAllItems(inputData, context);
		}

		return aggregateIndividualFields(inputData, context, disableDotNotation, keepOnlyUnique);
	}

	private NodeExecutionResult aggregateAllItems(List<Map<String, Object>> inputData,
			NodeExecutionContext context) {
		String fieldName = context.getParameter("destinationFieldName", "data");
		if (fieldName == null || fieldName.isBlank()) fieldName = "data";

		List<Map<String, Object>> allItemData = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			allItemData.add(unwrapJson(item));
		}

		Map<String, Object> outputJson = new LinkedHashMap<>();
		outputJson.put(fieldName, allItemData);

		log.debug("Aggregate: all items mode, {} items -> 1 item (field={})", inputData.size(), fieldName);
		return NodeExecutionResult.success(List.of(wrapInJson(outputJson)));
	}

	private NodeExecutionResult aggregateIndividualFields(List<Map<String, Object>> inputData,
			NodeExecutionContext context, boolean disableDotNotation, boolean keepOnlyUnique) {
		Object fieldsParam = context.getParameter("fieldsToAggregate", null);
		List<FieldSpec> fields = parseFieldSpecs(fieldsParam);

		if (fields.isEmpty()) {
			return NodeExecutionResult.error("No fields specified for aggregation");
		}

		Map<String, Object> outputJson = new LinkedHashMap<>();

		for (FieldSpec spec : fields) {
			List<Object> values = new ArrayList<>();
			for (Map<String, Object> item : inputData) {
				Object value;
				if (disableDotNotation) {
					value = unwrapJson(item).get(spec.inputField);
				} else {
					value = getNestedValue(item, "json." + spec.inputField);
				}
				if (value != null) {
					if (keepOnlyUnique && values.contains(value)) {
						continue;
					}
					values.add(value);
				}
			}

			String outputField = (spec.outputField != null && !spec.outputField.isBlank())
				? spec.outputField : spec.inputField;
			outputJson.put(outputField, values);
		}

		log.debug("Aggregate: individual fields mode, {} items -> 1 item ({} fields)",
				inputData.size(), fields.size());
		return NodeExecutionResult.success(List.of(wrapInJson(outputJson)));
	}

	@SuppressWarnings("unchecked")
	private List<FieldSpec> parseFieldSpecs(Object fieldsParam) {
		List<FieldSpec> specs = new ArrayList<>();
		if (fieldsParam == null) return specs;

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
			return specs;
		}

		for (Object entry : fieldList) {
			if (entry instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) entry;
				String inputField = (String) map.get("fieldToAggregate");
				String outputField = (String) map.get("renameField");
				if (inputField != null && !inputField.isBlank()) {
					specs.add(new FieldSpec(inputField.trim(), outputField != null ? outputField.trim() : null));
				}
			}
		}
		return specs;
	}

	private record FieldSpec(String inputField, String outputField) {}
}
