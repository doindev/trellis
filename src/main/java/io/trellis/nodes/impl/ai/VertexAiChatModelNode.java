package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Google Vertex AI Chat Model — uses Google Cloud Vertex AI to access
 * Gemini models via the Vertex AI API with service account authentication.
 */
@Node(
		type = "lmChatGoogleVertex",
		displayName = "Google Vertex AI Chat Model",
		description = "Chat model via Google Cloud Vertex AI",
		category = "AI / Chat Models",
		icon = "google",
		credentials = {"googleApi"},
		searchOnly = true
)
public class VertexAiChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String projectId = context.getParameter("projectId", "");
		String location = context.getCredentialString("region", "us-central1");
		String model = context.getParameter("model", "gemini-2.0-flash");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.4);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 2048);
		double topP = toDouble(context.getParameters().get("topP"), 1.0);
		int topK = toInt(context.getParameters().get("topK"), 32);

		var builder = VertexAiGeminiChatModel.builder()
				.project(projectId)
				.location(location)
				.modelName(model)
				.temperature((float) temperature)
				.topP((float) topP)
				.topK(topK)
				.maxOutputTokens(maxTokens);

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("projectId").displayName("Project ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("Google Cloud project ID.").build(),
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
						.type(ParameterType.NUMBER).defaultValue(0.4)
						.description("Sampling temperature (0–1).").build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Output Tokens")
						.type(ParameterType.NUMBER).defaultValue(2048)
						.description("Maximum number of tokens to generate.").build(),
				NodeParameter.builder()
						.name("topP").displayName("Top P")
						.type(ParameterType.NUMBER).defaultValue(1.0)
						.description("Nucleus sampling (0–1).").build(),
				NodeParameter.builder()
						.name("topK").displayName("Top K")
						.type(ParameterType.NUMBER).defaultValue(32)
						.description("Top-K sampling (1–40).").build()
		);
	}
}
