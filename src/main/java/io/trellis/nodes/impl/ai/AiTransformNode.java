package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI Transform — takes input data and transforms it using an AI prompt.
 * Each input item is processed according to the provided prompt instruction,
 * using an OpenAI-compatible ChatModel built internally.
 */
@Node(
		type = "aiTransform",
		displayName = "AI Transform",
		description = "Transform input data using an AI prompt",
		category = "AI / Miscellaneous",
		icon = "wand-magic-sparkles",
		credentials = {"openAiApi"}
)
public class AiTransformNode extends AbstractNode {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String baseUrl = context.getCredentialString("baseUrl", "");
		String model = context.getParameter("model", "gpt-4o-mini");
		String prompt = context.getParameter("prompt", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);

		if (apiKey == null || apiKey.isBlank()) {
			return NodeExecutionResult.error("OpenAI API key is required. Configure credentials.");
		}

		if (prompt == null || prompt.isBlank()) {
			return NodeExecutionResult.error("A prompt instruction is required.");
		}

		// Build ChatModel internally
		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName(model)
				.temperature(temperature);

		if (baseUrl != null && !baseUrl.isBlank()) {
			builder.baseUrl(baseUrl);
		}

		ChatModel chatModel = builder.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		if (inputData == null || inputData.isEmpty()) {
			// No input data — just run the prompt directly
			try {
				String response = chatModel.chat(prompt);
				results.add(wrapInJson(Map.of("output", response)));
			} catch (Exception e) {
				return handleError(context, "AI Transform failed: " + e.getMessage(), e);
			}
		} else {
			for (Map<String, Object> item : inputData) {
				try {
					Map<String, Object> json = unwrapJson(item);
					String itemJson = objectMapper.writeValueAsString(json);

					String fullPrompt = prompt + "\n\nInput data:\n" + itemJson;
					String response = chatModel.chat(fullPrompt);

					// Try to parse the response as JSON; if it fails, wrap it as a string
					try {
						Map<String, Object> parsed = objectMapper.readValue(response,
								new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
						results.add(wrapInJson(parsed));
					} catch (Exception parseEx) {
						results.add(wrapInJson(Map.of("output", response)));
					}
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, "AI Transform failed: " + e.getMessage(), e);
					}
				}
			}
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("prompt").displayName("Prompt")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 6))
						.defaultValue("")
						.required(true)
						.description("The instruction for how to transform the input data. "
								+ "The input data will be appended to this prompt.").build(),
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("gpt-4o-mini")
						.options(List.of(
								ParameterOption.builder().name("GPT-4o").value("gpt-4o").build(),
								ParameterOption.builder().name("GPT-4o Mini").value("gpt-4o-mini").build(),
								ParameterOption.builder().name("GPT-3.5 Turbo").value("gpt-3.5-turbo").build()
						)).build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Controls randomness. Lower is more focused, higher is more creative.").build()
		);
	}
}
