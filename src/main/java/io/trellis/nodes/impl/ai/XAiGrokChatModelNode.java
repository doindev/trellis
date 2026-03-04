package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * xAI Grok Chat Model — OpenAI-compatible chat model backed by the xAI Grok API.
 */
@Node(
		type = "xAiGrokChatModel",
		displayName = "xAI Grok Chat Model",
		description = "xAI Grok chat model",
		category = "AI / Chat Models",
		icon = "robot",
		credentials = {"xAiApi"},
		searchOnly = true
)
public class XAiGrokChatModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "https://api.x.ai/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String baseUrl = context.getCredentialString("baseUrl", DEFAULT_BASE_URL);
		String model = context.getParameter("model", "grok-2");
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
						.type(ParameterType.OPTIONS)
						.defaultValue("grok-2")
						.options(List.of(
								ParameterOption.builder().name("Grok 2").value("grok-2").build(),
								ParameterOption.builder().name("Grok 2 Vision").value("grok-2-vision-1212").build(),
								ParameterOption.builder().name("Grok Beta").value("grok-beta").build()
						)).build(),
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
