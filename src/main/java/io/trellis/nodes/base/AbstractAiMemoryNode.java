package io.trellis.nodes.base;

import io.trellis.nodes.core.NodeOutput;

import java.util.List;

/**
 * Base class for AI memory sub-nodes. Outputs an ai_memory connection.
 * Subclasses implement supplyData() to return a ChatMemory instance.
 */
public abstract class AbstractAiMemoryNode extends AbstractAiSubNode {

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_memory")
						.displayName("Memory")
						.type(NodeOutput.OutputType.AI_MEMORY)
						.build()
		);
	}
}
