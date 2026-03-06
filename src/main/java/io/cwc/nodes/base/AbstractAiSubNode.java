package io.cwc.nodes.base;

import java.util.List;

import io.cwc.nodes.core.*;

/**
 * Base class for AI sub-nodes that supply LangChain4j objects (models, memory, tools)
 * to parent AI nodes. Sub-nodes have no main inputs and their primary method is
 * supplyData() rather than execute().
 */
public abstract class AbstractAiSubNode extends AbstractNode implements AiSubNodeInterface {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		// Sub-nodes produce their data via supplyData(), not execute()
		return NodeExecutionResult.empty();
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // No main inputs
	}
}
