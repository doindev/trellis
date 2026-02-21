package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.core.NodeParameter.ParameterOption;

import java.util.List;

@Node(
		type = "anthropicChatModel",
		displayName = "Anthropic Chat Model",
		description = "Anthropic chat model (Claude Sonnet, Haiku, Opus)",
		category = "AI / Chat Models",
		icon = "anthropic",
		credentials = {"anthropicApi"}
)
public class AnthropicChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String baseUrl = context.getCredentialString("baseUrl", "");
		String model = context.getParameter("model", "claude-sonnet-4-20250514");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 1024);

		var builder = AnthropicChatModel.builder()
				.apiKey(apiKey)
				.modelName(model)
				.temperature(temperature)
				.maxTokens(maxTokens);

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
						.defaultValue("claude-sonnet-4-20250514")
						.options(List.of(
								ParameterOption.builder().name("Claude Sonnet 4").value("claude-sonnet-4-20250514").build(),
								ParameterOption.builder().name("Claude 3.7 Sonnet").value("claude-3-7-sonnet-20250219").build(),
								ParameterOption.builder().name("Claude 3.5 Haiku").value("claude-3-5-haiku-20241022").build(),
								ParameterOption.builder().name("Claude 3 Opus").value("claude-3-opus-20240229").build()
						)).build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER)
						.defaultValue(0.7)
						.build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER)
						.defaultValue(1024)
						.description("Maximum number of tokens to generate")
						.build()
		);
	}
}
