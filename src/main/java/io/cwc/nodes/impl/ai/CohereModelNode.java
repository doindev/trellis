package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractChatModelNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Cohere Model (Completion) — uses Cohere's API for text generation.
 * This is the non-chat completion model variant.
 */
@Node(
		type = "lmCohere",
		displayName = "Cohere Model",
		description = "Text generation model via Cohere API",
		category = "AI / Completion Models",
		icon = "brain",
		credentials = {"cohereApi"}
)
public class CohereModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "https://api.cohere.com/compatibility/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "command-r");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);
		int maxRetries = toInt(context.getParameters().get("maxRetries"), 2);

		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.baseUrl(DEFAULT_BASE_URL)
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
						.type(ParameterType.OPTIONS)
						.defaultValue("command-r")
						.options(List.of(
								ParameterOption.builder().name("Command R").value("command-r").build(),
								ParameterOption.builder().name("Command R+").value("command-r-plus").build(),
								ParameterOption.builder().name("Command Light").value("command-light").build(),
								ParameterOption.builder().name("Command").value("command").build(),
								ParameterOption.builder().name("Command Nightly").value("command-nightly").build(),
								ParameterOption.builder().name("Command Light Nightly").value("command-light-nightly").build()
						)).build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Sampling temperature (0–2).").build(),
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
