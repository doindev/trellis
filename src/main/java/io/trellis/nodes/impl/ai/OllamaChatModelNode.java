package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "ollamaChatModel",
		displayName = "Ollama Chat Model",
		description = "Ollama locally hosted chat model",
		category = "AI / Chat Models",
		icon = "ollama",
		credentials = {"ollamaApi"},
		searchOnly = true
)
public class OllamaChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("baseUrl", "http://localhost:11434");
		String model = context.getParameter("model", "llama3");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int numPredict = toInt(context.getParameters().get("numPredict"), 0);

		var builder = OllamaChatModel.builder()
				.baseUrl(baseUrl)
				.modelName(model)
				.temperature(temperature);

		if (numPredict > 0) {
			builder.numPredict(numPredict);
		}

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.STRING)
						.defaultValue("llama3")
						.required(true)
						.description("Name of the Ollama model to use")
						.build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER)
						.defaultValue(0.7)
						.build(),
				NodeParameter.builder()
						.name("numPredict").displayName("Max Tokens (Num Predict)")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Maximum number of tokens to generate. 0 for model default.")
						.build()
		);
	}
}
