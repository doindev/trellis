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
import java.util.stream.Collectors;

@Slf4j
@Node(
		type = "textClassifier",
		displayName = "Text Classifier",
		description = "Classify text into user-defined categories and route to category-specific outputs",
		category = "AI / Chains",
		icon = "tag"
)
public class TextClassifierNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String DEFAULT_SYSTEM_PROMPT =
			"Please classify the text provided by the user into one of the following categories: {categories}, " +
			"and use the provided formatting instructions below. Don't explain, and only output the json.";

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		// Parse categories
		Object categoriesObj = context.getParameter("categories", null);
		List<Map<String, Object>> categories = new ArrayList<>();
		if (categoriesObj instanceof List) {
			for (Object c : (List<?>) categoriesObj) {
				if (c instanceof Map) categories.add((Map<String, Object>) c);
			}
		}

		if (categories.isEmpty()) {
			return NodeExecutionResult.error("At least one category must be defined");
		}

		List<String> categoryNames = categories.stream()
				.map(c -> toString(c.get("category")))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());

		boolean multiClass = toBoolean(context.getParameter("multiClass", false), false);
		String fallback = context.getParameter("fallback", "discard");
		boolean enableAutoFixing = toBoolean(context.getParameter("enableAutoFixing", true), true);
		String systemPromptTemplate = context.getParameter("systemPromptTemplate", DEFAULT_SYSTEM_PROMPT);

		// Build format instructions
		String formatInstructions = buildFormatInstructions(categories, multiClass, fallback);

		// Multi-class / fallback prompt additions
		String multiClassPrompt = multiClass
				? "Categories are not mutually exclusive, and multiple can be true"
				: "Categories are mutually exclusive, and only one can be true";

		String fallbackPrompt = "other".equals(fallback)
				? "If no categories apply, select the \"fallback\" option."
				: "If there is not a very fitting category, select none of the categories.";

		String systemPrompt = systemPromptTemplate.replace("{categories}", String.join(", ", categoryNames))
				+ "\n" + formatInstructions
				+ "\n" + multiClassPrompt
				+ "\n" + fallbackPrompt;

		// Initialize output arrays: one per category + optional "Other"
		int outputCount = categoryNames.size() + ("other".equals(fallback) ? 1 : 0);
		List<List<Map<String, Object>>> outputs = new ArrayList<>();
		for (int i = 0; i < outputCount; i++) {
			outputs.add(new ArrayList<>());
		}

		for (Map<String, Object> item : context.getInputData()) {
			try {
				Map<String, Object> json = unwrapJson(item);
				String text = resolveTextInput(context, json, "inputText");

				if (text == null || text.isBlank()) {
					if (context.isContinueOnFail()) {
						outputs.get(0).add(wrapInJson(Map.of("error", "Text to classify is empty")));
					} else {
						return NodeExecutionResult.error("Text to classify is empty");
					}
					continue;
				}

				List<ChatMessage> messages = List.of(
						SystemMessage.from(systemPrompt),
						UserMessage.from(text)
				);

				ChatResponse response = model.chat(messages);
				String responseText = response.aiMessage().text();

				Map<String, Object> parsed = parseJsonResponse(responseText);

				// Auto-fix if needed
				if (parsed == null && enableAutoFixing) {
					parsed = retryWithFix(model, systemPrompt, text, responseText);
				}

				if (parsed != null) {
					// Route to matching category outputs
					Map<String, Object> outputData = new LinkedHashMap<>(json);
					outputData.put("classification", parsed);

					for (int i = 0; i < categoryNames.size(); i++) {
						String catName = categoryNames.get(i);
						Object val = parsed.get(catName);
						if (toBoolean(val, false)) {
							outputs.get(i).add(wrapInJson(outputData));
						}
					}

					// Handle fallback — if no categories matched and fallback is "discard",
					// the item is simply not added to any output (silently dropped)
					if ("other".equals(fallback)) {
						Object fallbackVal = parsed.get("fallback");
						if (toBoolean(fallbackVal, false)) {
							outputs.get(outputs.size() - 1).add(wrapInJson(outputData));
						}
					}
				} else {
					if (context.isContinueOnFail()) {
						outputs.get(0).add(wrapInJson(Map.of("error", "Failed to parse classification response")));
					} else {
						return NodeExecutionResult.error("Error during parsing of LLM output, please check your LLM model and configuration");
					}
				}

			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					outputs.get(0).add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return handleError(context, "Text classification failed: " + e.getMessage(), e);
				}
			}
		}

		return NodeExecutionResult.successMultiOutput(outputs);
	}

	private String buildFormatInstructions(List<Map<String, Object>> categories, boolean multiClass, String fallback) {
		StringBuilder sb = new StringBuilder();
		sb.append("Return a JSON object with the following boolean fields:\n");

		for (Map<String, Object> cat : categories) {
			String name = toString(cat.get("category"));
			String desc = toString(cat.get("description"));
			if (name.isEmpty()) continue;

			sb.append("- \"").append(name).append("\": boolean");
			if (!desc.isEmpty()) {
				sb.append(" (description: ").append(desc).append(")");
			}
			sb.append("\n");
		}

		if ("other".equals(fallback)) {
			sb.append("- \"fallback\": boolean (should be true if none of the other categories apply)\n");
		}

		sb.append("\nRespond with ONLY the JSON object, no markdown, no explanation.");
		return sb.toString();
	}

	private String resolveTextInput(NodeExecutionContext context, Map<String, Object> json, String paramName) {
		String textParam = context.getParameter(paramName, "");
		if (textParam == null || textParam.isBlank()) return "";

		Object resolved = getNestedValue(json, textParam);
		if (resolved != null) return String.valueOf(resolved);

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
			String fixPrompt = "The previous response was not valid JSON. Please fix the following output to be valid JSON:\n\n" +
					"Broken output:\n" + brokenOutput + "\n\nOriginal text to classify:\n" + originalText;

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
	public List<NodeOutput> getOutputs() {
		// Default outputs — at runtime these are driven by the categories parameter
		return List.of(
				NodeOutput.builder().name("output_0").displayName("Category 1").build(),
				NodeOutput.builder().name("output_1").displayName("Category 2").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Text input
		params.add(NodeParameter.builder()
				.name("inputText").displayName("Text to Classify")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 2))
				.required(true)
				.defaultValue("")
				.description("Use an expression to reference data in previous nodes or enter static text")
				.build());

		// Categories
		params.add(NodeParameter.builder()
				.name("categories").displayName("Categories")
				.type(ParameterType.FIXED_COLLECTION)
				.nestedParameters(List.of(
						NodeParameter.builder().name("category").displayName("Category")
								.type(ParameterType.STRING).required(true)
								.description("Category name").build(),
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).defaultValue("")
								.description("Describe your category if it's not obvious").build()
				))
				.build());

		// Multi-class
		params.add(NodeParameter.builder()
				.name("multiClass").displayName("Allow Multiple Classes To Be True")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.description("Whether multiple categories can be true simultaneously")
				.build());

		// Fallback
		params.add(NodeParameter.builder()
				.name("fallback").displayName("When No Clear Match")
				.type(ParameterType.OPTIONS)
				.defaultValue("discard")
				.description("What to do with items that don't match the categories exactly")
				.options(List.of(
						ParameterOption.builder().name("Discard Item").value("discard")
								.description("Ignore the item and drop it from the output").build(),
						ParameterOption.builder().name("Output on Extra 'Other' Branch").value("other")
								.description("Create a separate output branch called 'Other'").build()
				))
				.build());

		// System prompt
		params.add(NodeParameter.builder()
				.name("systemPromptTemplate").displayName("System Prompt Template")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 6))
				.defaultValue(DEFAULT_SYSTEM_PROMPT)
				.description("String to use directly as the system prompt template. Use {categories} for the category list.")
				.build());

		// Enable auto-fixing
		params.add(NodeParameter.builder()
				.name("enableAutoFixing").displayName("Enable Auto-Fixing")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.description("Whether to enable auto-fixing (may trigger an additional LLM call if output is broken)")
				.build());

		return params;
	}
}
