package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.core.NodeParameter.ParameterOption;

import java.util.List;

@Node(
		type = "mistralChatModel",
		displayName = "Mistral AI Chat Model",
		description = "Mistral AI chat model",
		category = "AI / Chat Models",
		icon = "mistral",
		credentials = {"mistralApi"}
)
public class MistralChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "mistral-large-latest");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);

		var builder = MistralAiChatModel.builder()
				.apiKey(apiKey)
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
						.type(ParameterType.OPTIONS)
						.defaultValue("mistral-large-latest")
						.options(List.of(
								ParameterOption.builder().name("Mistral Large").value("mistral-large-latest").build(),
								ParameterOption.builder().name("Mistral Medium").value("mistral-medium-latest").build(),
								ParameterOption.builder().name("Mistral Small").value("mistral-small-latest").build(),
								ParameterOption.builder().name("Codestral").value("codestral-latest").build()
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
