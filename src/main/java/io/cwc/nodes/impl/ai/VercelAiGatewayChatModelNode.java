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
 * Vercel AI Gateway Chat Model — uses the Vercel AI Gateway (OpenAI-compatible)
 * to route requests to various LLM providers through a unified endpoint.
 */
@Node(
		type = "lmChatVercelAiGateway",
		displayName = "Vercel AI Gateway Chat Model",
		description = "Chat model via Vercel AI Gateway",
		category = "AI / Chat Models",
		icon = "triangle",
		credentials = {"vercelAiGatewayApi"},
		searchOnly = true
)
public class VercelAiGatewayChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String baseUrl = context.getCredentialString("url", "");
		String model = context.getParameter("model", "openai/gpt-4o");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);
		double topP = toDouble(context.getParameters().get("topP"), 1.0);
		double frequencyPenalty = toDouble(context.getParameters().get("frequencyPenalty"), 0.0);
		double presencePenalty = toDouble(context.getParameters().get("presencePenalty"), 0.0);
		int maxRetries = toInt(context.getParameters().get("maxRetries"), 2);

		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.baseUrl(baseUrl)
				.modelName(model)
				.temperature(temperature)
				.topP(topP)
				.frequencyPenalty(frequencyPenalty)
				.presencePenalty(presencePenalty)
				.maxRetries(maxRetries);

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
						.defaultValue("openai/gpt-4o")
						.required(true)
						.description("Model identifier (e.g. openai/gpt-4o, anthropic/claude-3-haiku).")
						.build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Sampling temperature (0–2).").build(),
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
						.description("Penalizes repeated tokens (-2 to 2).").build(),
				NodeParameter.builder()
						.name("presencePenalty").displayName("Presence Penalty")
						.type(ParameterType.NUMBER).defaultValue(0.0)
						.description("Penalizes tokens already present (-2 to 2).").build(),
				NodeParameter.builder()
						.name("maxRetries").displayName("Max Retries")
						.type(ParameterType.NUMBER).defaultValue(2)
						.description("Maximum number of retries on failure.").build()
		);
	}
}
