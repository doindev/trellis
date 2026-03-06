package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Lemonade Embeddings — generates embeddings using a self-hosted
 * Lemonade (LM Studio / LocalAI / compatible) inference server via the
 * OpenAI-compatible embeddings endpoint.
 */
@Node(
		type = "embeddingsLemonade",
		displayName = "Lemonade Embeddings",
		description = "Generate embeddings using a Lemonade-compatible endpoint",
		category = "AI / Embeddings",
		icon = "robot",
		credentials = {"lemonadeApi"}
)
public class LemonadeEmbeddingNode extends AbstractEmbeddingNode {

	private static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "lm-studio");
		String baseUrl = context.getCredentialString("baseUrl", DEFAULT_BASE_URL);
		String model = context.getParameter("model", "");

		return OpenAiEmbeddingModel.builder()
				.apiKey(apiKey)
				.baseUrl(baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
				.modelName(model)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.STRING)
						.defaultValue("")
						.required(true)
						.description("Embedding model identifier served by the Lemonade-compatible endpoint")
						.build()
		);
	}
}
