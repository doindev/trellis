package io.cwc.nodes.impl.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiSubNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.OutputParser;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
		type = "outputParserStructured",
		displayName = "Structured Output Parser",
		description = "Return data in a defined JSON format",
		category = "AI / Output Parsers",
		icon = "code"
)
public class StructuredOutputParserNode extends AbstractAiSubNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String DEFAULT_RETRY_PROMPT =
			"Instructions:\n--------------\n{instructions}\n--------------\n" +
			"Completion:\n--------------\n{completion}\n--------------\n\n" +
			"Above, the Completion did not satisfy the constraints given in the Instructions.\n" +
			"Error:\n--------------\n{error}\n--------------\n\n" +
			"Please try again. Please only respond with an answer that satisfies the constraints laid out in the Instructions:";

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		String schemaType = context.getParameter("schemaType", "fromJson");
		boolean autoFix = toBoolean(context.getParameter("autoFix", false), false);

		// Build the JSON schema
		String jsonSchema;
		if ("fromJson".equals(schemaType)) {
			String example = context.getParameter("jsonSchemaExample",
					"{\n  \"state\": \"California\",\n  \"cities\": [\"Los Angeles\", \"San Francisco\"]\n}");
			jsonSchema = generateSchemaFromExample(example);
		} else {
			jsonSchema = context.getParameter("inputSchema", "{}");
			// Validate it's valid JSON
			MAPPER.readTree(jsonSchema);
		}

		// Get auto-fix model if enabled
		ChatModel fixModel = null;
		String retryPrompt = DEFAULT_RETRY_PROMPT;
		if (autoFix) {
			fixModel = context.getAiInput("ai_languageModel", ChatModel.class);
			if (fixModel == null) {
				throw new IllegalStateException("Auto-fix is enabled but no language model is connected. " +
						"Connect a Chat Model to the Model input.");
			}
			if (toBoolean(context.getParameter("customizeRetryPrompt", false), false)) {
				retryPrompt = context.getParameter("retryPrompt", DEFAULT_RETRY_PROMPT);
			}
		}

		return new StructuredOutputParser(jsonSchema, fixModel, retryPrompt);
	}

	private String generateSchemaFromExample(String jsonExample) throws Exception {
		Object example = MAPPER.readValue(jsonExample, Object.class);
		Map<String, Object> schema = buildSchemaForValue(example);
		return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> buildSchemaForValue(Object value) {
		if (value instanceof Map) {
			Map<String, Object> map = (Map<String, Object>) value;
			Map<String, Object> schema = new LinkedHashMap<>();
			schema.put("type", "object");
			Map<String, Object> properties = new LinkedHashMap<>();
			List<String> required = new ArrayList<>();

			for (Map.Entry<String, Object> entry : map.entrySet()) {
				properties.put(entry.getKey(), buildSchemaForValue(entry.getValue()));
				required.add(entry.getKey());
			}

			schema.put("properties", properties);
			schema.put("required", required);
			return schema;
		} else if (value instanceof List) {
			List<?> list = (List<?>) value;
			Map<String, Object> schema = new LinkedHashMap<>();
			schema.put("type", "array");
			schema.put("items", list.isEmpty() ? Map.of("type", "string") : buildSchemaForValue(list.get(0)));
			return schema;
		} else if (value instanceof Number) {
			return Map.of("type", "number");
		} else if (value instanceof Boolean) {
			return Map.of("type", "boolean");
		} else {
			return Map.of("type", "string");
		}
	}

	@Override
	public List<NodeInput> getInputs() {
		// Optional AI Language Model input for auto-fix
		return List.of(
				NodeInput.builder().name("ai_languageModel").displayName("Model")
						.type(NodeInput.InputType.AI_LANGUAGE_MODEL)
						.required(false).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_outputParser")
						.displayName("Output Parser")
						.type(NodeOutput.OutputType.AI_OUTPUT_PARSER)
						.build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Schema type
		params.add(NodeParameter.builder()
				.name("schemaType").displayName("Schema Type")
				.type(ParameterType.OPTIONS)
				.defaultValue("fromJson")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Generate From JSON Example").value("fromJson")
								.description("Generate a schema from an example JSON object").build(),
						ParameterOption.builder().name("Define using JSON Schema").value("manual")
								.description("Define the JSON schema manually").build()
				))
				.build());

		// JSON example (when schemaType = fromJson)
		params.add(NodeParameter.builder()
				.name("jsonSchemaExample").displayName("JSON Example")
				.type(ParameterType.JSON)
				.typeOptions(Map.of("rows", 10))
				.defaultValue("{\n  \"state\": \"California\",\n  \"cities\": [\"Los Angeles\", \"San Francisco\", \"San Diego\"]\n}")
				.noDataExpression(true)
				.description("Example JSON object to use to generate the schema. All properties will be required.")
				.displayOptions(Map.of("show", Map.of("schemaType", List.of("fromJson"))))
				.build());

		// Manual JSON schema (when schemaType = manual)
		params.add(NodeParameter.builder()
				.name("inputSchema").displayName("JSON Schema")
				.type(ParameterType.JSON)
				.typeOptions(Map.of("rows", 10))
				.defaultValue("{\n  \"type\": \"object\",\n  \"properties\": {\n    \"state\": {\n      \"type\": \"string\"\n    },\n    \"cities\": {\n      \"type\": \"array\",\n      \"items\": {\n        \"type\": \"string\"\n      }\n    }\n  }\n}")
				.description("Schema to use for the output (JSON Schema format)")
				.displayOptions(Map.of("show", Map.of("schemaType", List.of("manual"))))
				.build());

		// Auto-fix
		params.add(NodeParameter.builder()
				.name("autoFix").displayName("Auto-Fix Format")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.description("Whether to automatically fix the output when it is not in the correct format. " +
						"Will cause another LLM call. Requires a connected language model.")
				.build());

		// Customize retry prompt
		params.add(NodeParameter.builder()
				.name("customizeRetryPrompt").displayName("Customize Retry Prompt")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.description("Whether to customize the prompt used for retrying the output parsing")
				.displayOptions(Map.of("show", Map.of("autoFix", List.of(true))))
				.build());

		// Retry prompt
		params.add(NodeParameter.builder()
				.name("retryPrompt").displayName("Retry Prompt")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 10))
				.defaultValue(DEFAULT_RETRY_PROMPT)
				.description("Prompt template for fixing the output. Use {instructions}, {completion}, and {error} placeholders.")
				.displayOptions(Map.of("show", Map.of("autoFix", List.of(true), "customizeRetryPrompt", List.of(true))))
				.build());

		return params;
	}

	/**
	 * Output parser that validates LLM text output against a JSON schema.
	 * Optionally uses an LLM to auto-fix malformed output.
	 */
	static class StructuredOutputParser implements OutputParser {
		private final String jsonSchema;
		private final ChatModel fixModel;
		private final String retryPromptTemplate;

		StructuredOutputParser(String jsonSchema, ChatModel fixModel, String retryPromptTemplate) {
			this.jsonSchema = jsonSchema;
			this.fixModel = fixModel;
			this.retryPromptTemplate = retryPromptTemplate;
		}

		@Override
		public String getFormatInstructions() {
			return "You must format your output as a JSON object that conforms to the following JSON Schema:\n\n" +
					jsonSchema + "\n\n" +
					"IMPORTANT: Respond with ONLY the JSON object. Do not include any explanation, " +
					"markdown code fences, or other text before or after the JSON.";
		}

		@Override
		@SuppressWarnings("unchecked")
		public Object parse(String text) throws OutputParserException {
			String jsonText = extractJson(text);

			try {
				// Parse as JSON
				Object parsed = MAPPER.readValue(jsonText, Object.class);

				// Unwrap if nested in an "output" key
				if (parsed instanceof Map) {
					Map<String, Object> map = (Map<String, Object>) parsed;
					if (map.containsKey("output") && map.size() == 1) {
						return map.get("output");
					}
				}

				return parsed;
			} catch (Exception e) {
				// If auto-fix is available, try to fix the output
				if (fixModel != null) {
					return autoFix(text, e.getMessage());
				}
				throw new OutputParserException(
						"Model output doesn't fit required format: " + e.getMessage(), text);
			}
		}

		@SuppressWarnings("unchecked")
		private Object autoFix(String completion, String error) throws OutputParserException {
			try {
				String prompt = retryPromptTemplate
						.replace("{instructions}", getFormatInstructions())
						.replace("{completion}", completion)
						.replace("{error}", error);

				List<ChatMessage> messages = List.of(UserMessage.from(prompt));
				ChatResponse response = fixModel.chat(messages);
				String fixedText = extractJson(response.aiMessage().text());

				Object parsed = MAPPER.readValue(fixedText, Object.class);

				// Unwrap if nested
				if (parsed instanceof Map) {
					Map<String, Object> map = (Map<String, Object>) parsed;
					if (map.containsKey("output") && map.size() == 1) {
						return map.get("output");
					}
				}

				return parsed;
			} catch (Exception retryError) {
				throw new OutputParserException(
						"Auto-fix failed. Original error: " + error +
								". Retry error: " + retryError.getMessage(), completion);
			}
		}

		/**
		 * Extract JSON from text, handling markdown code fences.
		 */
		private String extractJson(String text) {
			String trimmed = text.trim();

			// Look for markdown code fences
			String[] lines = trimmed.split("\n");
			int fenceStart = -1;
			int fenceEnd = -1;

			for (int i = 0; i < lines.length; i++) {
				String line = lines[i].trim();
				if (fenceStart == -1 && line.matches("^```(?:json)?$")) {
					fenceStart = i;
				} else if (fenceStart != -1 && line.equals("```")) {
					fenceEnd = i;
					break;
				}
			}

			if (fenceStart != -1 && fenceEnd != -1) {
				StringBuilder sb = new StringBuilder();
				for (int i = fenceStart + 1; i < fenceEnd; i++) {
					if (sb.length() > 0) sb.append("\n");
					sb.append(lines[i]);
				}
				return sb.toString().trim();
			}

			return trimmed;
		}
	}
}
