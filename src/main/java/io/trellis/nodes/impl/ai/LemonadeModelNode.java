package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Lemonade Model (Completion) — uses Lemonade's OpenAI-compatible API for text generation.
 * This is the non-chat completion model variant, similar to CohereModelNode.
 */
@Node(
		type = "lmLemonade",
		displayName = "Lemonade Model",
		description = "Text generation model via Lemonade's OpenAI-compatible API",
		category = "AI / Completion Models",
		icon = "robot",
		credentials = {"lemonadeApi"}
)
public class LemonadeModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "lm-studio");
		String baseUrl = context.getCredentialString("baseUrl", DEFAULT_BASE_URL);
		String model = context.getParameter("model", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);
		int maxRetries = toInt(context.getParameters().get("maxRetries"), 2);

		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.baseUrl(baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
				.modelName(model)
				.temperature(temperature)
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
						.defaultValue("")
						.required(true)
						.description("Model identifier served by the Lemonade-compatible endpoint").build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Sampling temperature (0-2).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Maximum tokens to generate. 0 for model default.").build(),
				NodeParameter.builder()
						.name("maxRetries").displayName("Max Retries")
						.type(ParameterType.NUMBER).defaultValue(2)
						.description("Maximum number of retries on failure.").build()
		);
	}
}
