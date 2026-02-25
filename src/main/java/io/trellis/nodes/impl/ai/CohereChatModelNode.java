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
 * Cohere Chat Model — uses Cohere's OpenAI-compatible endpoint to access
 * Cohere's Command family of models.
 */
@Node(
		type = "lmChatCohere",
		displayName = "Cohere Chat Model",
		description = "Chat model via Cohere API",
		category = "AI / Chat Models",
		icon = "brain",
		credentials = {"cohereApi"}
)
public class CohereChatModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "https://api.cohere.com/compatibility/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "command-a-03-2025");
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
						.defaultValue("command-a-03-2025")
						.options(List.of(
								ParameterOption.builder().name("Command A (03-2025)").value("command-a-03-2025").build(),
								ParameterOption.builder().name("Command R+ (08-2024)").value("command-r-plus-08-2024").build(),
								ParameterOption.builder().name("Command R (08-2024)").value("command-r-08-2024").build(),
								ParameterOption.builder().name("Command R+").value("command-r-plus").build(),
								ParameterOption.builder().name("Command R").value("command-r").build(),
								ParameterOption.builder().name("Command Light").value("command-light").build()
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
