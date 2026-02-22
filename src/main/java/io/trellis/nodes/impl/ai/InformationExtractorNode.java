package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Node(
		type = "informationExtractor",
		displayName = "Information Extractor",
		description = "Extract structured information from text using a language model",
		category = "AI / Chains",
		icon = "scan-text"
)
public class InformationExtractorNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String DEFAULT_SYSTEM_PROMPT =
			"You are an expert extraction algorithm.\n" +
			"Only extract relevant information from the text.\n" +
			"If you do not know the value of an attribute asked to extract, you may omit the attribute's value.";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		String schemaType = context.getParameter("schemaType", "fromAttributes");
		String systemPromptTemplate = context.getParameter("systemPromptTemplate", DEFAULT_SYSTEM_PROMPT);

		// Build the JSON schema from the selected mode
		String jsonSchema;
		try {
			jsonSchema = buildJsonSchema(context, schemaType);
		} catch (Exception e) {
			return NodeExecutionResult.error("Failed to build extraction schema: " + e.getMessage(), e);
		}

		// Build format instructions
		String formatInstructions = "You must output ONLY valid JSON matching this schema (no markdown, no explanation):\n" + jsonSchema;

		String systemPrompt = systemPromptTemplate + "\n\n" + formatInstructions;

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : context.getInputData()) {
			try {
				Map<String, Object> json = unwrapJson(item);
				String text = resolveTextInput(context, json, "text");

				if (text == null || text.isBlank()) {
					results.add(wrapInJson(Map.of("error", "Text input is empty")));
					continue;
				}

				List<ChatMessage> messages = List.of(
						SystemMessage.from(systemPrompt),
						UserMessage.from(text)
				);

				ChatResponse response = model.chat(messages);
				String responseText = response.aiMessage().text();

				Map<String, Object> extracted = parseJsonResponse(responseText);

				if (extracted != null) {
					results.add(wrapInJson(Map.of("output", extracted)));
				} else {
					// Auto-fix: retry with error feedback
					extracted = retryWithFix(model, systemPrompt, text, responseText);
					if (extracted != null) {
						results.add(wrapInJson(Map.of("output", extracted)));
					} else {
						results.add(wrapInJson(Map.of("output", responseText)));
					}
				}
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					results.add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return handleError(context, "Information extraction failed: " + e.getMessage(), e);
				}
			}
		}

		return NodeExecutionResult.success(results);
	}

	@SuppressWarnings("unchecked")
	private String buildJsonSchema(NodeExecutionContext context, String schemaType) throws Exception {
		switch (schemaType) {
			case "fromAttributes": {
				Object attrsObj = context.getParameter("attributes", null);
				List<Map<String, Object>> attrs = new ArrayList<>();
				if (attrsObj instanceof List) {
					for (Object a : (List<?>) attrsObj) {
						if (a instanceof Map) attrs.add((Map<String, Object>) a);
					}
				}
				if (attrs.isEmpty()) {
					throw new IllegalArgumentException("At least one attribute must be specified");
				}
				// Build JSON Schema from attributes
				Map<String, Object> schema = new LinkedHashMap<>();
				schema.put("type", "object");
				Map<String, Object> properties = new LinkedHashMap<>();
				List<String> required = new ArrayList<>();

				for (Map<String, Object> attr : attrs) {
					String name = toString(attr.get("name"));
					String type = toString(attr.get("type"));
					String description = toString(attr.get("description"));
					boolean isRequired = toBoolean(attr.get("required"), false);

					if (name.isEmpty()) continue;

					Map<String, Object> prop = new LinkedHashMap<>();
					prop.put("type", mapAttributeType(type));
					if (!description.isEmpty()) {
						prop.put("description", description);
					}
					properties.put(name, prop);

					if (isRequired) {
						required.add(name);
					}
				}

				schema.put("properties", properties);
				if (!required.isEmpty()) {
					schema.put("required", required);
				}
				return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
			}
			case "fromJson": {
				String jsonExample = context.getParameter("jsonSchemaExample",
						"{\"state\": \"California\", \"cities\": [\"Los Angeles\", \"San Francisco\"]}");
				// Parse the example and generate a schema
				Object example = MAPPER.readValue(jsonExample, Object.class);
				Map<String, Object> schema = generateSchemaFromExample(example);
				return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
			}
			case "manual": {
				String inputSchema = context.getParameter("inputSchema", "{}");
				// Validate it's valid JSON
				MAPPER.readTree(inputSchema);
				return inputSchema;
			}
			default:
				throw new IllegalArgumentException("Unknown schema type: " + schemaType);
		}
	}

	private String mapAttributeType(String type) {
		return switch (type) {
			case "number" -> "number";
			case "boolean" -> "boolean";
			case "date" -> "string";
			default -> "string";
		};
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> generateSchemaFromExample(Object example) {
		if (example instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) example;
			Map<String, Object> schema = new LinkedHashMap<>();
			schema.put("type", "object");
			Map<String, Object> properties = new LinkedHashMap<>();
			List<String> required = new ArrayList<>();

			for (Map.Entry<String, Object> entry : map.entrySet()) {
				properties.put(entry.getKey(), generateSchemaFromExample(entry.getValue()));
				required.add(entry.getKey());
			}

			schema.put("properties", properties);
			schema.put("required", required);
			return schema;
		} else if (example instanceof List) {
			List<?> list = (List<?>) example;
			Map<String, Object> schema = new LinkedHashMap<>();
			schema.put("type", "array");
			if (!list.isEmpty()) {
				schema.put("items", generateSchemaFromExample(list.get(0)));
			} else {
				schema.put("items", Map.of("type", "string"));
			}
			return schema;
		} else if (example instanceof Number) {
			return Map.of("type", "number");
		} else if (example instanceof Boolean) {
			return Map.of("type", "boolean");
		} else {
			return Map.of("type", "string");
		}
	}

	private String resolveTextInput(NodeExecutionContext context, Map<String, Object> json, String paramName) {
		String textParam = context.getParameter(paramName, "");
		if (textParam == null || textParam.isBlank()) return "";

		// Check if the parameter value matches a field name in the input data
		Object resolved = getNestedValue(json, textParam);
		if (resolved != null) return String.valueOf(resolved);

		// Template substitution
		String result = textParam;
		for (Map.Entry<String, Object> entry : json.entrySet()) {
			result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
			result = result.replace("{{ " + entry.getKey() + " }}", String.valueOf(entry.getValue()));
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseJsonResponse(String responseText) {
		try {
			String jsonText = responseText.trim();
			if (jsonText.startsWith("```")) {
				jsonText = jsonText.replaceFirst("```(?:json)?\\s*", "");
				jsonText = jsonText.replaceFirst("\\s*```$", "");
			}
			return MAPPER.readValue(jsonText, Map.class);
		} catch (Exception e) {
			return null;
		}
	}

	private Map<String, Object> retryWithFix(ChatModel model, String systemPrompt, String originalText, String brokenOutput) {
		try {
			String fixPrompt = "The previous extraction attempt produced invalid JSON output. " +
					"Please fix the following output to be valid JSON matching the schema:\n\n" +
					"Broken output:\n" + brokenOutput + "\n\nOriginal text:\n" + originalText;

			List<ChatMessage> messages = List.of(
					SystemMessage.from(systemPrompt),
					UserMessage.from(fixPrompt)
			);

			ChatResponse response = model.chat(messages);
			return parseJsonResponse(response.aiMessage().text());
		} catch (Exception e) {
			log.warn("Auto-fix retry failed: {}", e.getMessage());
			return null;
		}
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder().name("main").displayName("Main").type(NodeInput.InputType.MAIN).build(),
				NodeInput.builder().name("ai_languageModel").displayName("Model")
						.type(NodeInput.InputType.AI_LANGUAGE_MODEL).required(true).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Text input
		params.add(NodeParameter.builder()
				.name("text").displayName("Text")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 2))
				.defaultValue("")
				.description("The text to extract information from")
				.build());

		// Schema type
		params.add(NodeParameter.builder()
				.name("schemaType").displayName("Schema Type")
				.type(ParameterType.OPTIONS)
				.defaultValue("fromAttributes")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("From Attribute Descriptions").value("fromAttributes")
								.description("Extract specific attributes from the text based on types and descriptions").build(),
						ParameterOption.builder().name("Generate From JSON Example").value("fromJson")
								.description("Generate a schema from an example JSON object").build(),
						ParameterOption.builder().name("Define using JSON Schema").value("manual")
								.description("Define the JSON schema manually").build()
				))
				.build());

		// Attributes (when schemaType = fromAttributes)
		params.add(NodeParameter.builder()
				.name("attributes").displayName("Attributes")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("schemaType", List.of("fromAttributes"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name")
								.type(ParameterType.STRING).required(true)
								.placeHolder("e.g. company_name")
								.description("Attribute to extract").build(),
						NodeParameter.builder().name("type").displayName("Type")
								.type(ParameterType.OPTIONS).defaultValue("string")
								.description("Data type of the attribute")
								.options(List.of(
										ParameterOption.builder().name("String").value("string").build(),
										ParameterOption.builder().name("Number").value("number").build(),
										ParameterOption.builder().name("Boolean").value("boolean").build(),
										ParameterOption.builder().name("Date").value("date").build()
								)).build(),
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).required(true)
								.placeHolder("Describe the attribute")
								.description("Describe your attribute").build(),
						NodeParameter.builder().name("required").displayName("Required")
								.type(ParameterType.BOOLEAN).defaultValue(false)
								.description("Whether attribute is required").build()
				))
				.build());

		// JSON example (when schemaType = fromJson)
		params.add(NodeParameter.builder()
				.name("jsonSchemaExample").displayName("JSON Example")
				.type(ParameterType.JSON)
				.typeOptions(Map.of("rows", 10))
				.defaultValue("{\n  \"state\": \"California\",\n  \"cities\": [\"Los Angeles\", \"San Francisco\", \"San Diego\"]\n}")
				.description("Example JSON object to use to generate the schema. All properties will be required.")
				.displayOptions(Map.of("show", Map.of("schemaType", List.of("fromJson"))))
				.build());

		// Manual JSON schema (when schemaType = manual)
		params.add(NodeParameter.builder()
				.name("inputSchema").displayName("JSON Schema")
				.type(ParameterType.JSON)
				.typeOptions(Map.of("rows", 10))
				.defaultValue("{\n  \"type\": \"object\",\n  \"properties\": {\n    \"state\": { \"type\": \"string\" },\n    \"cities\": {\n      \"type\": \"array\",\n      \"items\": { \"type\": \"string\" }\n    }\n  }\n}")
				.description("Schema to use for extraction (JSON Schema format)")
				.displayOptions(Map.of("show", Map.of("schemaType", List.of("manual"))))
				.build());

		// System prompt template (options)
		params.add(NodeParameter.builder()
				.name("systemPromptTemplate").displayName("System Prompt Template")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 6))
				.defaultValue(DEFAULT_SYSTEM_PROMPT)
				.description("String to use directly as the system prompt template")
				.build());

		return params;
	}
}
