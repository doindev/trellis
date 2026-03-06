package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractChatModelNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "geminiChatModel",
		displayName = "Google Gemini Chat Model",
		description = "Google AI Gemini chat model",
		category = "AI / Chat Models",
		icon = "google",
		credentials = {"googleAiApi"},
		searchOnly = true
)
public class GeminiChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String baseUrl = context.getCredentialString("baseUrl", "");
		String model = context.getParameter("model", "gemini-2.0-flash");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);

		var builder = GoogleAiGeminiChatModel.builder()
				.apiKey(apiKey)
				.modelName(model)
				.temperature(temperature);

		if (maxTokens > 0) {
			builder.maxOutputTokens(maxTokens);
		}
		if (baseUrl != null && !baseUrl.isBlank()) {
			builder.baseUrl(baseUrl);
		}

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("gemini-2.0-flash")
						.options(List.of(
								ParameterOption.builder().name("Gemini 2.0 Flash").value("gemini-2.0-flash").build(),
								ParameterOption.builder().name("Gemini 2.0 Flash Lite").value("gemini-2.0-flash-lite").build(),
								ParameterOption.builder().name("Gemini 1.5 Pro").value("gemini-1.5-pro").build(),
								ParameterOption.builder().name("Gemini 1.5 Flash").value("gemini-1.5-flash").build()
						)).build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER)
						.defaultValue(0.7)
						.build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Maximum number of tokens to generate. 0 for model default.")
						.build()
		);
	}
}
