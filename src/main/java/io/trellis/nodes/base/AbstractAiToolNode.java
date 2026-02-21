package io.trellis.nodes.base;

import io.trellis.nodes.core.NodeOutput;

import java.util.List;

/**
 * Base class for AI tool sub-nodes. Outputs an ai_tool connection.
 * Subclasses implement supplyData() to return a tool specification object.
 */
public abstract class AbstractAiToolNode extends AbstractAiSubNode {

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_tool")
						.displayName("Tool")
						.type(NodeOutput.OutputType.AI_TOOL)
						.build()
		);
	}
}
