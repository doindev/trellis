package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractEmbeddingNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Hugging Face Inference Embeddings — generates embeddings using Hugging Face
 * Inference API with any sentence-transformers compatible model.
 */
@Node(
		type = "embeddingsHuggingFaceInference",
		displayName = "Hugging Face Inference Embeddings",
		description = "Generate embeddings using Hugging Face Inference API",
		category = "AI / Embeddings",
		icon = "huggingface",
		credentials = {"huggingFaceApi"}
)
public class HuggingFaceEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "sentence-transformers/distilbert-base-nli-mean-tokens");
		String endpointUrl = context.getParameter("endpointUrl", "");

		var builder = HuggingFaceEmbeddingModel.builder()
				.accessToken(accessToken)
				.modelId(model)
				.waitForModel(true);

		if (endpointUrl != null && !endpointUrl.isBlank()) {
			builder.baseUrl(endpointUrl);
		}

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.STRING)
						.defaultValue("sentence-transformers/distilbert-base-nli-mean-tokens")
						.description("The model name from HuggingFace hub.").build(),
				NodeParameter.builder()
						.name("endpointUrl").displayName("Custom Endpoint URL")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Optional custom inference endpoint URL.").build()
		);
	}
}
