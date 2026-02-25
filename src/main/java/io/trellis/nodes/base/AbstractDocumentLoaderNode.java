package io.trellis.nodes.base;

import io.trellis.nodes.core.NodeOutput;

import java.util.List;

/**
 * Base class for document loader sub-nodes. Outputs an ai_document connection.
 * Subclasses implement supplyData() to return LangChain4j Document objects
 * that parent nodes (e.g., vector stores) ingest.
 */
public abstract class AbstractDocumentLoaderNode extends AbstractAiSubNode {

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_document")
						.displayName("Document")
						.type(NodeOutput.OutputType.AI_DOCUMENT)
						.build()
		);
	}
}
