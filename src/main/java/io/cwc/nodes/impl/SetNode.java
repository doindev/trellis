package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Set Node (Edit Fields) - sets or modifies fields on items flowing through the workflow.
 * Supports manual field assignment with name/value/type triples, or raw JSON replacement.
 * Can optionally keep or discard other fields not explicitly set.
 */
@Slf4j
@Node(
	type = "set",
	displayName = "Edit Fields",
	subtitle = "={{$parameter.mode}}",
	description = "Modify, add, or replace fields on items. Use manual mode to set individual fields or raw mode to replace with JSON.",
	category = "Core",
	icon = "pen"
)
public class SetNode extends AbstractNode {

	private final ObjectMapper objectMapper = new ObjectMapper();

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
				.name("mode")
				.displayName("Mode")
				.description("How to set the fields.")
				.type(ParameterType.OPTIONS)
				.defaultValue("manual")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Manual Mapping")
						.value("manual")
						.description("Manually define fields with name, value, and type")
						.build(),
					ParameterOption.builder()
						.name("Raw JSON")
						.value("raw")
						.description("Replace item data with raw JSON")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("fields")
				.displayName("Fields to Set")
				.description("The fields to set on each item.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("mode", List.of("manual"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("name")
						.displayName("Field Name")
						.description("The name of the field to set. Supports dot notation for nested fields (e.g., 'user.email').")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("fieldName")
						.build(),
					NodeParameter.builder()
						.name("value")
						.displayName("Value")
						.description("The value to set for the field.")
						.type(ParameterType.STRING)
						.required(true)
						.build(),
					NodeParameter.builder()
						.name("type")
						.displayName("Type")
						.description("The type to cast the value to.")
						.type(ParameterType.OPTIONS)
						.defaultValue("string")
						.options(List.of(
							ParameterOption.builder().name("String").value("string").build(),
							ParameterOption.builder().name("Number").value("number").build(),
							ParameterOption.builder().name("Boolean").value("boolean").build(),
							ParameterOption.builder().name("JSON").value("json").build()
						))
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("rawJson")
				.displayName("JSON")
				.description("The raw JSON to replace item data with.")
				.type(ParameterType.JSON)
				.defaultValue("{}")
				.displayOptions(Map.of("show", Map.of("mode", List.of("raw"))))
				.build(),

			NodeParameter.builder()
				.name("includeOtherFields")
				.displayName("Include Other Fields")
				.description("Whether to keep existing fields that are not explicitly set.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.displayOptions(Map.of("show", Map.of("mode", List.of("manual"))))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String mode = context.getParameter("mode", "manual");
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		try {
			List<Map<String, Object>> outputItems = new ArrayList<>();

			if ("manual".equals(mode)) {
				boolean includeOtherFields = toBoolean(context.getParameter("includeOtherFields", true), true);
				Object fieldsObj = context.getParameter("fields", null);
				List<Map<String, Object>> fieldsList = new ArrayList<>();

				if (fieldsObj instanceof List) {
					for (Object f : (List<?>) fieldsObj) {
						if (f instanceof Map) {
							fieldsList.add((Map<String, Object>) f);
						}
					}
				}

				for (Map<String, Object> item : inputData) {
					Map<String, Object> json;
					if (includeOtherFields) {
						json = new HashMap<>(unwrapJson(deepClone(item)));
					} else {
						json = new HashMap<>();
					}

					for (Map<String, Object> field : fieldsList) {
						String name = toString(field.get("name"));
						String valueStr = toString(field.get("value"));
						String type = toString(field.get("type"));

						if (name.isEmpty()) {
							continue;
						}

						Object typedValue = castValue(valueStr, type);
						setNestedValue(json, name, typedValue);
					}

					outputItems.add(wrapInJson(json));
				}
			} else {
				// Raw JSON mode
				String rawJson = context.getParameter("rawJson", "{}");

				for (Map<String, Object> item : inputData) {
					try {
						Object parsed = objectMapper.readValue(rawJson, Object.class);
						if (parsed instanceof Map) {
							outputItems.add(wrapInJson(parsed));
						} else if (parsed instanceof List) {
							for (Object element : (List<?>) parsed) {
								if (element instanceof Map) {
									outputItems.add(wrapInJson(element));
								} else {
									outputItems.add(wrapInJson(Map.of("value", element)));
								}
							}
							break; // Only process the JSON once regardless of input items
						} else {
							outputItems.add(wrapInJson(Map.of("value", parsed)));
						}
					} catch (Exception e) {
						log.warn("Failed to parse raw JSON for item, using original: {}", e.getMessage());
						outputItems.add(deepClone(item));
					}
				}
			}

			return NodeExecutionResult.success(outputItems);

		} catch (Exception e) {
			return handleError(context, "Edit Fields failed: " + e.getMessage(), e);
		}
	}

	private Object castValue(String value, String type) {
		if (value == null) {
			return null;
		}

		switch (type != null ? type : "string") {
			case "number":
				try {
					if (value.contains(".")) {
						return Double.parseDouble(value);
					}
					return Long.parseLong(value);
				} catch (NumberFormatException e) {
					return 0;
				}
			case "boolean":
				return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
			case "json":
				try {
					return objectMapper.readValue(value, Object.class);
				} catch (Exception e) {
					return value;
				}
			default:
				return value;
		}
	}
}
