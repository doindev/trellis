package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Google Gemini Embeddings — generates embeddings using Google AI Gemini embedding models.
 */
@Node(
		type = "embeddingsGoogleGemini",
		displayName = "Google Gemini Embeddings",
		description = "Generate embeddings using Google Gemini models",
		category = "AI / Embeddings",
		icon = "google",
		credentials = {"googleAiApi"}
)
public class GeminiEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "text-embedding-004");

		return GoogleAiEmbeddingModel.builder()
				.apiKey(apiKey)
				.modelName(model)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("text-embedding-004")
						.options(List.of(
								ParameterOption.builder().name("text-embedding-004").value("text-embedding-004").build(),
								ParameterOption.builder().name("embedding-001").value("embedding-001").build()
						)).build()
		);
	}
}
