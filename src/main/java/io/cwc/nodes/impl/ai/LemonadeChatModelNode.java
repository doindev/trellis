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
 * Lemonade Chat Model — OpenAI-compatible chat model backed by a self-hosted
 * Lemonade (LM Studio / LocalAI / compatible) inference server.
 */
@Node(
		type = "lmChatLemonade",
		displayName = "Lemonade Chat Model",
		description = "Lemonade self-hosted chat model (OpenAI-compatible)",
		category = "AI / Chat Models",
		icon = "robot",
		credentials = {"lemonadeApi"},
		searchOnly = true
)
public class LemonadeChatModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "lm-studio");
		String baseUrl = context.getCredentialString("baseUrl", DEFAULT_BASE_URL);
		String model = context.getParameter("model", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);

		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.baseUrl(baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
				.modelName(model)
				.temperature(temperature);

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
						.defaultValue("")
						.required(true)
						.description("Model identifier served by the Lemonade-compatible endpoint")
						.build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Controls randomness (0–2).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Maximum tokens to generate. 0 for model default.").build()
		);
	}
}
