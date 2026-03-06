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
 * Hugging Face Inference Model — text generation using the HuggingFace Inference API.
 * Uses the OpenAI-compatible Text Generation Inference (TGI) endpoint.
 */
@Node(
		type = "lmOpenHuggingFaceInference",
		displayName = "Hugging Face Inference Model",
		description = "Text generation model via Hugging Face Inference API",
		category = "AI / Completion Models",
		icon = "huggingface",
		credentials = {"huggingFaceApi"}
)
public class HuggingFaceInferenceModelNode extends AbstractChatModelNode {

	private static final String DEFAULT_BASE_URL = "https://api-inference.huggingface.co/models/";

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "tiiuae/falcon-7b-instruct");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);
		String endpointUrl = context.getParameter("endpointUrl", "");

		String baseUrl;
		if (endpointUrl != null && !endpointUrl.isBlank()) {
			baseUrl = endpointUrl;
		} else {
			baseUrl = DEFAULT_BASE_URL + model + "/v1";
		}

		var builder = OpenAiChatModel.builder()
				.apiKey(accessToken)
				.baseUrl(baseUrl)
				.modelName(model)
				.temperature(temperature)
				.maxRetries(2);

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
						.defaultValue("tiiuae/falcon-7b-instruct")
						.description("The model ID from HuggingFace hub.").build(),
				NodeParameter.builder()
						.name("endpointUrl").displayName("Custom Endpoint URL")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Optional custom inference endpoint URL (e.g., for dedicated endpoints).").build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER).defaultValue(0.7)
						.description("Sampling temperature (0–2).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Maximum tokens to generate. 0 for model default.").build()
		);
	}
}
