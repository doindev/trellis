package io.cwc.nodes.impl.ai;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractEmbeddingNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;

import java.util.List;

@Node(
		type = "localEmbedding",
		displayName = "Local Embeddings (MiniLM)",
		description = "Generate embeddings locally using the all-MiniLM-L6-v2 model (no API key required)",
		category = "AI / Embeddings",
		icon = "hard-drive-upload"
)
public class LocalEmbeddingNode extends AbstractEmbeddingNode {

	@Override
	protected EmbeddingModel createEmbeddingModel(NodeExecutionContext context) {
		return new AllMiniLmL6V2EmbeddingModel();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of();
	}
}
