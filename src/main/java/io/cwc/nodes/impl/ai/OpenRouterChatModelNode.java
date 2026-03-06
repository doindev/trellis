package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractChatModelNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * OpenRouter Chat Model — OpenAI-compatible chat model backed by the OpenRouter API,
 * which provides a unified gateway to many LLM providers.
 */
@Node(
		type = "openRouterChatModel",
		displayName = "OpenRouter Chat Model",
		description = "OpenRouter unified LLM gateway chat model",
		category = "AI / Chat Models",
		icon = "route",
		credentials = {"openRouterApi"},
		searchOnly = true
)
public class OpenRouterChatModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String baseUrl = context.getCredentialString("baseUrl", DEFAULT_BASE_URL);
		String model = context.getParameter("model", "openai/gpt-4o-mini");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);
		double topP = toDouble(context.getParameters().get("topP"), 1.0);
		double frequencyPenalty = toDouble(context.getParameters().get("frequencyPenalty"), 0.0);
		double presencePenalty = toDouble(context.getParameters().get("presencePenalty"), 0.0);

		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.baseUrl(baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
				.modelName(model)
				.temperature(temperature)
				.topP(topP)
				.frequencyPenalty(frequencyPenalty)
				.presencePenalty(presencePenalty);

		if (maxTokens > 0) {
			builder.maxTokens(maxTokens);
		}

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.STRING)
						.defaultValue("openai/gpt-4o-mini")
						.required(true)
						.description("Model identifier (e.g. openai/gpt-4o, anthropic/claude-3-haiku)")
						.build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Controls randomness (0–2).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Maximum tokens to generate. 0 for model default.").build(),
				NodeParameter.builder()
						.name("topP").displayName("Top P")
						.type(ParameterType.NUMBER).defaultValue(1.0)
						.description("Nucleus sampling (0–1).").build(),
				NodeParameter.builder()
						.name("frequencyPenalty").displayName("Frequency Penalty")
						.type(ParameterType.NUMBER).defaultValue(0.0)
						.description("Penalizes repeated tokens (0–2).").build(),
				NodeParameter.builder()
						.name("presencePenalty").displayName("Presence Penalty")
						.type(ParameterType.NUMBER).defaultValue(0.0)
						.description("Penalizes tokens already present (0–2).").build()
		);
	}
}
