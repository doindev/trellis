package io.trellis.nodes.base;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeOutput;

import java.util.List;

/**
 * Base class for retriever sub-nodes. Outputs an ai_retriever connection.
 * Subclasses implement createRetriever() to build the provider-specific retriever instance.
 */
public abstract class AbstractRetrieverNode extends AbstractAiSubNode {

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		return createRetriever(context);
	}

	protected abstract ContentRetriever createRetriever(NodeExecutionContext context) throws Exception;

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_retriever")
						.displayName("Retriever")
						.type(NodeOutput.OutputType.AI_RETRIEVER)
						.build()
		);
	}
}
