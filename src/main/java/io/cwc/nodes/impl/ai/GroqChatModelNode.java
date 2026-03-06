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
 * Groq Chat Model — OpenAI-compatible chat model backed by the Groq API
 * for ultra-fast LLM inference.
 */
@Node(
		type = "groqChatModel",
		displayName = "Groq Chat Model",
		description = "Groq ultra-fast inference chat model",
		category = "AI / Chat Models",
		icon = "bolt",
		credentials = {"groqApi"},
		searchOnly = true
)
public class GroqChatModelNode extends AbstractChatModelNode {

	private static final String BASE_URL = "https://api.groq.com/openai/v1";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "llama3-8b-8192");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 4096);

		var builder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.baseUrl(BASE_URL)
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
						.defaultValue("llama3-8b-8192")
						.options(List.of(
								ParameterOption.builder().name("Llama 3 8B").value("llama3-8b-8192").build(),
								ParameterOption.builder().name("Llama 3 70B").value("llama3-70b-8192").build(),
								ParameterOption.builder().name("Mixtral 8x7B").value("mixtral-8x7b-32768").build(),
								ParameterOption.builder().name("Gemma 7B").value("gemma-7b-it").build()
						)).build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Controls randomness (0–1).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER).defaultValue(4096)
						.description("Maximum tokens to generate.").build()
		);
	}
}
