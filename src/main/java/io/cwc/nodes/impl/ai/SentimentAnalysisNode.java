package io.cwc.nodes.impl.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Node(
		type = "sentimentAnalysis",
		displayName = "Sentiment Analysis",
		description = "Analyze the sentiment of text and route to category-specific outputs",
		category = "AI / Chains",
		icon = "balance-scale"
)
public class SentimentAnalysisNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String DEFAULT_SYSTEM_PROMPT =
			"You are highly intelligent and accurate sentiment analyzer. " +
			"Analyze the sentiment of the provided text. " +
			"Categorize it into one of the following: {categories}. " +
			"Use the provided formatting instructions. Only output the JSON.";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		String categoriesStr = context.getParameter("categories", "Positive, Neutral, Negative");
		List<String> categories = Arrays.stream(categoriesStr.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());

		if (categories.isEmpty()) {
			return NodeExecutionResult.error("No sentiment categories provided");
		}

		boolean includeDetailedResults = toBoolean(context.getParameter("includeDetailedResults", false), false);
		boolean enableAutoFixing = toBoolean(context.getParameter("enableAutoFixing", true), true);
		String systemPromptTemplate = context.getParameter("systemPromptTemplate", DEFAULT_SYSTEM_PROMPT);

		// Build format instructions
		String formatInstructions = buildFormatInstructions(categories, includeDetailedResults);

		// Build system prompt
		String systemPrompt = systemPromptTemplate.replace("{categories}", String.join(", ", categories))
				+ "\n\n" + formatInstructions;

		// Initialize output arrays (one per category)
		List<List<Map<String, Object>>> outputs = new ArrayList<>();
		for (int i = 0; i < categories.size(); i++) {
			outputs.add(new ArrayList<>());
		}

		for (Map<String, Object> item : context.getInputData()) {
			try {
				Map<String, Object> json = unwrapJson(item);
				String text = resolveTextInput(context, json, "inputText");

				if (text == null || text.isBlank()) {
					// Route to first output with error
					outputs.get(0).add(wrapInJson(Map.of("error", "Text input is empty")));
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
					String sentiment = toString(parsed.get("sentiment"));

					// Find the matching category (case-insensitive)
					int idx = -1;
					for (int i = 0; i < categories.size(); i++) {
						if (categories.get(i).equalsIgnoreCase(sentiment)) {
							idx = i;
							break;
						}
					}

					// Build output data
					Map<String, Object> outputData = new LinkedHashMap<>(json);
					Map<String, Object> sentimentData = new LinkedHashMap<>();
					sentimentData.put("category", sentiment);

					if (includeDetailedResults) {
						Object strength = parsed.get("strength");
						Object confidence = parsed.get("confidence");
						if (strength != null) sentimentData.put("strength", strength);
						if (confidence != null) sentimentData.put("confidence", confidence);
					}

					outputData.put("sentimentAnalysis", sentimentData);

					if (idx >= 0) {
						outputs.get(idx).add(wrapInJson(outputData));
					}
					// If no match found, item is discarded
				} else {
					// Parsing completely failed
					if (context.isContinueOnFail()) {
						outputs.get(0).add(wrapInJson(Map.of("error", "Failed to parse sentiment response")));
					} else {
						return NodeExecutionResult.error("Error during parsing of LLM output, please check your LLM model and configuration");
					}
				}

			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					outputs.get(0).add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return handleError(context, "Sentiment analysis failed: " + e.getMessage(), e);
				}
			}
		}

		return NodeExecutionResult.successMultiOutput(outputs);
	}

	private String buildFormatInstructions(List<String> categories, boolean includeDetailed) {
		StringBuilder sb = new StringBuilder();
		sb.append("Return a JSON object with the following fields:\n");
		sb.append("- \"sentiment\": must be one of [");
		sb.append(categories.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", ")));
		sb.append("]\n");

		if (includeDetailed) {
			sb.append("- \"strength\": a number between 0.0 and 1.0 indicating the strength of the sentiment\n");
			sb.append("- \"confidence\": a number between 0.0 and 1.0 indicating confidence in the classification\n");
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
					"Broken output:\n" + brokenOutput + "\n\nOriginal text to analyze:\n" + originalText;

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
		// Default outputs — actual routing depends on the categories parameter at runtime
		return List.of(
				NodeOutput.builder().name("positive").displayName("Positive").build(),
				NodeOutput.builder().name("neutral").displayName("Neutral").build(),
				NodeOutput.builder().name("negative").displayName("Negative").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("inputText").displayName("Text to Analyze")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 2))
				.required(true)
				.defaultValue("")
				.description("Use an expression to reference data in previous nodes or enter static text")
				.build());

		params.add(NodeParameter.builder()
				.name("categories").displayName("Sentiment Categories")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 2))
				.defaultValue("Positive, Neutral, Negative")
				.noDataExpression(true)
				.description("A comma-separated list of sentiment categories to analyze")
				.build());

		params.add(NodeParameter.builder()
				.name("includeDetailedResults").displayName("Include Detailed Results")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.description("Whether to include sentiment strength and confidence scores in the output. " +
						"Note: Sentiment scores are LLM-generated estimates, not statistically rigorous measurements.")
				.build());

		params.add(NodeParameter.builder()
				.name("systemPromptTemplate").displayName("System Prompt Template")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 6))
				.defaultValue(DEFAULT_SYSTEM_PROMPT)
				.description("String to use directly as the system prompt template. Use {categories} for the category list.")
				.build());

		params.add(NodeParameter.builder()
				.name("enableAutoFixing").displayName("Enable Auto-Fixing")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.description("Whether to enable auto-fixing (may trigger an additional LLM call if output is broken)")
				.build());

		return params;
	}
}
