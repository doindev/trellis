package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.core.NodeParameter.ParameterOption;

import java.util.List;

@Node(
		type = "openAiChatModel",
		displayName = "OpenAI Chat Model",
		description = "OpenAI chat model (GPT-4o, GPT-4o-mini, o3-mini, etc.)",
		category = "AI / Chat Models",
		icon = "openai",
		credentials = {"openAiApi"}
)
public class OpenAiChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "gpt-4o");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);
		String baseUrl = context.getParameter("baseUrl", "");

		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName(model)
				.temperature(temperature);

		if (maxTokens > 0) {
			builder.maxTokens(maxTokens);
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
						.defaultValue("gpt-4o")
						.options(List.of(
								ParameterOption.builder().name("GPT-4o").value("gpt-4o").build(),
								ParameterOption.builder().name("GPT-4o Mini").value("gpt-4o-mini").build(),
								ParameterOption.builder().name("o3-mini").value("o3-mini").build(),
								ParameterOption.builder().name("GPT-4 Turbo").value("gpt-4-turbo").build(),
								ParameterOption.builder().name("GPT-3.5 Turbo").value("gpt-3.5-turbo").build()
						)).build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER)
						.defaultValue(0.7)
						.description("Controls randomness. Lower is more focused, higher is more creative.")
						.build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Maximum number of tokens to generate. 0 for model default.")
						.build(),
				NodeParameter.builder()
						.name("baseUrl").displayName("Base URL")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Override the default API base URL (for proxies or compatible APIs)")
						.build()
		);
	}
}
