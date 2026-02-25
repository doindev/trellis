package io.trellis.nodes.base;

import io.trellis.nodes.core.NodeOutput;

import java.util.List;

/**
 * Base class for text splitter sub-nodes. Outputs an ai_textSplitter connection.
 * Subclasses implement supplyData() to return a text splitter configuration or function
 * that parent nodes (e.g., vector stores, document loaders) use to chunk text.
 */
public abstract class AbstractTextSplitterNode extends AbstractAiSubNode {

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_textSplitter")
						.displayName("Text Splitter")
						.type(NodeOutput.OutputType.AI_TEXT_SPLITTER)
						.build()
		);
	}
}
