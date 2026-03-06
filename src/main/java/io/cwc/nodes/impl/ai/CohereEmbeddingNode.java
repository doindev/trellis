package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.cohere.CohereEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Cohere Embeddings — generates embeddings using Cohere's embedding models
 * via the native Cohere API.
 */
@Node(
		type = "embeddingsCohere",
		displayName = "Cohere Embeddings",
		description = "Generate embeddings using Cohere models",
		category = "AI / Embeddings",
		icon = "brain",
		credentials = {"cohereApi"}
)
public class CohereEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String model = context.getParameter("model", "embed-english-v3.0");
		String inputType = context.getParameter("inputType", "search_document");

		return CohereEmbeddingModel.builder()
				.apiKey(apiKey)
				.modelName(model)
				.inputType(inputType)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("embed-english-v3.0")
						.options(List.of(
								ParameterOption.builder().name("Embed English v3.0 (1024 dim)").value("embed-english-v3.0").build(),
								ParameterOption.builder().name("Embed English v2.0 (4096 dim)").value("embed-english-v2.0").build(),
								ParameterOption.builder().name("Embed English Light v3.0 (384 dim)").value("embed-english-light-v3.0").build(),
								ParameterOption.builder().name("Embed English Light v2.0 (1024 dim)").value("embed-english-light-v2.0").build(),
								ParameterOption.builder().name("Embed Multilingual v3.0 (1024 dim)").value("embed-multilingual-v3.0").build(),
								ParameterOption.builder().name("Embed Multilingual Light v3.0 (384 dim)").value("embed-multilingual-light-v3.0").build(),
								ParameterOption.builder().name("Embed Multilingual v2.0 (768 dim)").value("embed-multilingual-v2.0").build()
						)).build(),
				NodeParameter.builder()
						.name("inputType").displayName("Input Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("search_document")
						.options(List.of(
								ParameterOption.builder().name("Search Document").value("search_document").build(),
								ParameterOption.builder().name("Search Query").value("search_query").build(),
								ParameterOption.builder().name("Classification").value("classification").build(),
								ParameterOption.builder().name("Clustering").value("clustering").build()
						))
						.description("Specifies the type of input for optimal embeddings.")
						.build()
		);
	}
}
