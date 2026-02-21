package io.trellis.nodes.base;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeOutput;

import java.util.List;

/**
 * Base class for embedding model sub-nodes. Outputs an ai_embedding connection.
 * Subclasses implement createEmbeddingModel() to build the provider-specific model instance.
 */
public abstract class AbstractEmbeddingNode extends AbstractAiSubNode {

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		return createEmbeddingModel(context);
	}

	protected abstract EmbeddingModel createEmbeddingModel(NodeExecutionContext context);

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_embedding")
						.displayName("Embedding")
						.type(NodeOutput.OutputType.AI_EMBEDDING)
						.build()
		);
	}
}
