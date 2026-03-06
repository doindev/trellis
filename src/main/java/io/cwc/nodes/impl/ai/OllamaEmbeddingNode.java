package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "ollamaEmbedding",
		displayName = "Ollama Embeddings",
		description = "Generate embeddings using Ollama local models",
		category = "AI / Embeddings",
		icon = "ollama",
		credentials = {"ollamaApi"}
)
public class OllamaEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("baseUrl", "http://localhost:11434");
		String model = context.getParameter("model", "nomic-embed-text");

		return OllamaEmbeddingModel.builder()
				.baseUrl(baseUrl)
				.modelName(model)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("nomic-embed-text")
						.options(List.of(
								ParameterOption.builder().name("nomic-embed-text").value("nomic-embed-text").build(),
								ParameterOption.builder().name("mxbai-embed-large").value("mxbai-embed-large").build(),
								ParameterOption.builder().name("all-minilm").value("all-minilm").build(),
								ParameterOption.builder().name("snowflake-arctic-embed").value("snowflake-arctic-embed").build()
						)).build()
		);
	}
}
