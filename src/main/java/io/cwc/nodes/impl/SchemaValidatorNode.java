package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * Schema Validator Node - validates items against a JSON schema or field checks.
 * Valid items go to output 0, invalid items go to output 1.
 */
@Slf4j
@Node(
	type = "schemaValidator",
	displayName = "Schema Validator",
	description = "Validate items against a schema or field checks. Valid items output on 'valid', invalid on 'invalid'.",
	category = "Flow",
	icon = "shield-check"
)
public class SchemaValidatorNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("valid").displayName("valid").build(),
			NodeOutput.builder().name("invalid").displayName("invalid").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("mode")
				.displayName("Validation Mode")
				.description("How to validate items.")
				.type(ParameterType.OPTIONS)
				.defaultValue("fieldChecks")
				.required(true)
				.options(List.of(
					ParameterOption.builder().name("Field Checks").value("fieldChecks")
						.description("Validate specific fields with built-in checks").build(),
					ParameterOption.builder().name("JSON Schema").value("jsonSchema")
						.description("Validate against a JSON schema definition").build(),
					ParameterOption.builder().name("Field Checks + JSON Schema").value("both")
						.description("Run both field checks and JSON schema validation").build()
				))
				.build(),

			NodeParameter.builder()
				.name("schema")
				.displayName("JSON Schema")
				.description("The JSON schema to validate against.")
				.type(ParameterType.JSON)
				.displayOptions(Map.of("show", Map.of("mode", List.of("jsonSchema", "both"))))
				.required(true)
				.placeHolder("{\"type\": \"object\", \"required\": [\"name\", \"email\"]}")
				.build(),

			NodeParameter.builder()
				.name("checks")
				.displayName("Field Checks")
				.description("Field validation rules.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("mode", List.of("fieldChecks", "both"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("fieldName")
						.displayName("Field Name")
						.description("The field to validate.")
						.type(ParameterType.STRING)
						.required(true)
						.build(),
					NodeParameter.builder()
						.name("checkType")
						.displayName("Check Type")
						.description("The type of validation to perform.")
						.type(ParameterType.OPTIONS)
						.defaultValue("required")
						.options(List.of(
							ParameterOption.builder().name("Required").value("required").description("Field must exist and not be null").build(),
							ParameterOption.builder().name("Type").value("type").description("Field must be a specific type").build(),
							ParameterOption.builder().name("Not Empty").value("notEmpty").description("Field must not be empty/blank").build(),
							ParameterOption.builder().name("Min Length").value("minLength").description("String must have minimum length").build(),
							ParameterOption.builder().name("Max Length").value("maxLength").description("String must not exceed max length").build(),
							ParameterOption.builder().name("Pattern").value("pattern").description("String must match regex pattern").build(),
							ParameterOption.builder().name("Min Value").value("minValue").description("Number must be >= value").build(),
							ParameterOption.builder().name("Max Value").value("maxValue").description("Number must be <= value").build(),
							ParameterOption.builder().name("In List").value("inList").description("Value must be one of the listed values").build()
						))
						.build(),
					NodeParameter.builder()
						.name("checkValue")
						.displayName("Check Value")
						.description("The value for the check (e.g., type name, min length, pattern, comma-separated list).")
						.type(ParameterType.STRING)
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("includeErrors")
				.displayName("Include Errors")
				.description("Add validation error details to invalid items.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		String mode = context.getParameter("mode", "fieldChecks");
		boolean includeErrors = toBoolean(context.getParameter("includeErrors", true), true);

		List<Map<String, Object>> validItems = new ArrayList<>();
		List<Map<String, Object>> invalidItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			List<String> errors;

			if ("both".equals(mode)) {
				errors = validateFieldChecks(json, context);
				errors.addAll(validateJsonSchema(json, context));
			} else if ("jsonSchema".equals(mode)) {
				errors = validateJsonSchema(json, context);
			} else {
				errors = validateFieldChecks(json, context);
			}

			if (errors.isEmpty()) {
				validItems.add(item);
			} else {
				if (includeErrors) {
					Map<String, Object> invalidJson = new LinkedHashMap<>(json);
					invalidJson.put("_validationErrors", errors);
					invalidItems.add(wrapInJson(invalidJson));
				} else {
					invalidItems.add(item);
				}
			}
		}

		log.debug("Schema validator: {} items -> {} valid, {} invalid",
			inputData.size(), validItems.size(), invalidItems.size());
		return NodeExecutionResult.successMultiOutput(List.of(validItems, invalidItems));
	}

	@SuppressWarnings("unchecked")
	private List<String> validateJsonSchema(Map<String, Object> json, NodeExecutionContext context) {
		List<String> errors = new ArrayList<>();
		Object schemaObj = context.getParameter("schema", null);

		Map<String, Object> schema;
		try {
			if (schemaObj instanceof String) {
				schema = MAPPER.readValue((String) schemaObj, new TypeReference<Map<String, Object>>() {});
			} else if (schemaObj instanceof Map) {
				schema = (Map<String, Object>) schemaObj;
			} else {
				errors.add("Invalid schema definition");
				return errors;
			}
		} catch (Exception e) {
			errors.add("Failed to parse schema: " + e.getMessage());
			return errors;
		}

		validateObjectSchema(json, schema, "", errors);
		return errors;
	}

	@SuppressWarnings("unchecked")
	private void validateObjectSchema(Map<String, Object> data, Map<String, Object> schema,
									  String pathPrefix, List<String> errors) {
		Object typeObj = schema.get("type");
		if (!"object".equals(typeObj)) return;

		String prefix = pathPrefix.isEmpty() ? "" : pathPrefix + ".";

		// Check required fields
		Object requiredObj = schema.get("required");
		if (requiredObj instanceof List) {
			for (Object req : (List<?>) requiredObj) {
				String fieldName = String.valueOf(req);
				if (!data.containsKey(fieldName) || data.get(fieldName) == null) {
					errors.add("Missing required field: " + prefix + fieldName);
				}
			}
		}

		// Check properties
		Object propsObj = schema.get("properties");
		if (propsObj instanceof Map) {
			Map<String, Object> properties = (Map<String, Object>) propsObj;
			for (Map.Entry<String, Object> entry : properties.entrySet()) {
				String fieldName = entry.getKey();
				Object value = data.get(fieldName);
				if (value != null && entry.getValue() instanceof Map) {
					Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();
					Object propTypeObj = propSchema.get("type");
					if (propTypeObj != null && !matchesType(value, propTypeObj)) {
						errors.add("Field '" + prefix + fieldName + "' expected type '" + propTypeObj
							+ "' but got '" + value.getClass().getSimpleName() + "'");
					}
					validatePropertyConstraints(prefix + fieldName, value, propSchema, errors);

					// Recurse into nested object properties
					if (typeIncludes(propTypeObj, "object") && value instanceof Map
							&& propSchema.get("properties") != null) {
						validateObjectSchema((Map<String, Object>) value, propSchema,
							prefix + fieldName, errors);
					}
				}
			}
		}
	}

	private void validatePropertyConstraints(String fieldName, Object value, Map<String, Object> propSchema, List<String> errors) {
		String strVal = String.valueOf(value);

		// pattern
		Object patternObj = propSchema.get("pattern");
		if (patternObj instanceof String patternStr && !patternStr.isEmpty()) {
			try {
				if (!Pattern.matches(patternStr, strVal)) {
					errors.add("Field '" + fieldName + "' does not match pattern '" + patternStr + "'");
				}
			} catch (Exception e) {
				errors.add("Field '" + fieldName + "' has invalid pattern '" + patternStr + "': " + e.getMessage());
			}
		}

		// minLength
		Object minLenObj = propSchema.get("minLength");
		if (minLenObj instanceof Number && value instanceof String) {
			int minLen = ((Number) minLenObj).intValue();
			if (((String) value).length() < minLen) {
				errors.add("Field '" + fieldName + "' must be at least " + minLen + " characters");
			}
		}

		// maxLength
		Object maxLenObj = propSchema.get("maxLength");
		if (maxLenObj instanceof Number && value instanceof String) {
			int maxLen = ((Number) maxLenObj).intValue();
			if (((String) value).length() > maxLen) {
				errors.add("Field '" + fieldName + "' must not exceed " + maxLen + " characters");
			}
		}

		// minimum
		Object minObj = propSchema.get("minimum");
		if (minObj instanceof Number && value instanceof Number) {
			if (((Number) value).doubleValue() < ((Number) minObj).doubleValue()) {
				errors.add("Field '" + fieldName + "' must be >= " + minObj);
			}
		}

		// maximum
		Object maxObj = propSchema.get("maximum");
		if (maxObj instanceof Number && value instanceof Number) {
			if (((Number) value).doubleValue() > ((Number) maxObj).doubleValue()) {
				errors.add("Field '" + fieldName + "' must be <= " + maxObj);
			}
		}

		// enum
		Object enumObj = propSchema.get("enum");
		if (enumObj instanceof List<?> enumList && !enumList.isEmpty()) {
			boolean found = false;
			for (Object allowed : enumList) {
				if (strVal.equals(String.valueOf(allowed))) {
					found = true;
					break;
				}
			}
			if (!found) {
				errors.add("Field '" + fieldName + "' must be one of: " + enumList);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private List<String> validateFieldChecks(Map<String, Object> json, NodeExecutionContext context) {
		List<String> errors = new ArrayList<>();
		Object checksObj = context.getParameter("checks", null);

		if (checksObj == null) return errors;

		List<Map<String, Object>> checks;
		if (checksObj instanceof List) {
			checks = (List<Map<String, Object>>) checksObj;
		} else {
			return errors;
		}

		for (Map<String, Object> check : checks) {
			String fieldName = toString(check.get("fieldName"));
			String checkType = toString(check.get("checkType"));
			String checkValue = toString(check.get("checkValue"));

			if (fieldName == null || fieldName.isEmpty()) continue;

			Object value = resolveValue(json, fieldName);
			String fieldError = validateField(fieldName, value, checkType, checkValue);
			if (fieldError != null) {
				errors.add(fieldError);
			}
		}

		return errors;
	}

	@SuppressWarnings("unchecked")
	private Object resolveValue(Map<String, Object> json, String fieldName) {
		if (!fieldName.contains(".")) return json.get(fieldName);
		String[] parts = fieldName.split("\\.");
		Object current = json;
		for (String part : parts) {
			if (current instanceof Map) {
				current = ((Map<String, Object>) current).get(part);
			} else {
				return null;
			}
			if (current == null) return null;
		}
		return current;
	}

	private String validateField(String fieldName, Object value, String checkType, String checkValue) {
		switch (checkType != null ? checkType : "required") {
			case "required":
				if (value == null) {
					return "Field '" + fieldName + "' is required";
				}
				break;

			case "type":
				if (value != null && !matchesType(value, checkValue)) {
					return "Field '" + fieldName + "' expected type '" + checkValue + "'";
				}
				break;

			case "notEmpty":
				if (value == null || String.valueOf(value).trim().isEmpty()) {
					return "Field '" + fieldName + "' must not be empty";
				}
				break;

			case "minLength":
				if (value != null) {
					int minLen = Integer.parseInt(checkValue);
					if (String.valueOf(value).length() < minLen) {
						return "Field '" + fieldName + "' must be at least " + minLen + " characters";
					}
				}
				break;

			case "maxLength":
				if (value != null) {
					int maxLen = Integer.parseInt(checkValue);
					if (String.valueOf(value).length() > maxLen) {
						return "Field '" + fieldName + "' must not exceed " + maxLen + " characters";
					}
				}
				break;

			case "pattern":
				if (value != null && !Pattern.matches(checkValue, String.valueOf(value))) {
					return "Field '" + fieldName + "' does not match pattern '" + checkValue + "'";
				}
				break;

			case "minValue":
				if (value instanceof Number) {
					double min = Double.parseDouble(checkValue);
					if (((Number) value).doubleValue() < min) {
						return "Field '" + fieldName + "' must be >= " + checkValue;
					}
				}
				break;

			case "maxValue":
				if (value instanceof Number) {
					double max = Double.parseDouble(checkValue);
					if (((Number) value).doubleValue() > max) {
						return "Field '" + fieldName + "' must be <= " + checkValue;
					}
				}
				break;

			case "inList":
				if (value != null && checkValue != null) {
					String[] allowed = checkValue.split(",");
					String valStr = String.valueOf(value).trim();
					boolean found = false;
					for (String a : allowed) {
						if (a.trim().equals(valStr)) {
							found = true;
							break;
						}
					}
					if (!found) {
						return "Field '" + fieldName + "' must be one of: " + checkValue;
					}
				}
				break;
		}

		return null;
	}

	private boolean matchesType(Object value, Object expectedType) {
		if (expectedType == null) return true;
		if (expectedType instanceof String s) return matchesSingleType(value, s);
		if (expectedType instanceof List<?> types) {
			return types.stream().anyMatch(t -> t instanceof String s && matchesSingleType(value, s));
		}
		return true;
	}

	private boolean matchesSingleType(Object value, String expectedType) {
		return switch (expectedType.toLowerCase()) {
			case "string" -> value instanceof String;
			case "number", "integer" -> value instanceof Number;
			case "boolean" -> value instanceof Boolean;
			case "array" -> value instanceof List;
			case "object" -> value instanceof Map;
			default -> true;
		};
	}

	private boolean typeIncludes(Object typeObj, String typeName) {
		if (typeObj instanceof String s) return typeName.equalsIgnoreCase(s);
		if (typeObj instanceof List<?> list) return list.stream()
			.anyMatch(t -> typeName.equalsIgnoreCase(String.valueOf(t)));
		return false;
	}
}
