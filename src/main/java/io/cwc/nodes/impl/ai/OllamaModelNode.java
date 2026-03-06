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
 * Ollama Model (Completion) — uses Ollama's OpenAI-compatible API for text generation.
 * This is the non-chat completion model variant that connects via the OpenAI compatibility
 * endpoint at /v1.
 */
@Node(
		type = "lmOllama",
		displayName = "Ollama Model",
		description = "Text generation model via Ollama's OpenAI-compatible API",
		category = "AI / Completion Models",
		icon = "ollama",
		credentials = {"ollamaApi"}
)
public class OllamaModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "http://localhost:11434/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("baseUrl", DEFAULT_BASE_URL);
		String apiKey = context.getCredentialString("apiKey", "ollama");
		String model = context.getParameter("model", "llama3");
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
						.defaultValue("llama3")
						.required(true)
						.description("Name of the Ollama model to use").build(),
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
