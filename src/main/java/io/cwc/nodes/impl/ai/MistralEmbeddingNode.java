package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Mistral Cloud Embeddings — generates embeddings using Mistral AI embedding models.
 */
@Node(
		type = "embeddingsMistralCloud",
		displayName = "Mistral Cloud Embeddings",
		description = "Generate embeddings using Mistral AI models",
		category = "AI / Embeddings",
		icon = "mistral",
		credentials = {"mistralApi"}
)
public class MistralEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "mistral-embed");

		return MistralAiEmbeddingModel.builder()
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
						.defaultValue("mistral-embed")
						.options(List.of(
								ParameterOption.builder().name("Mistral Embed").value("mistral-embed").build()
						)).build()
		);
	}
}
