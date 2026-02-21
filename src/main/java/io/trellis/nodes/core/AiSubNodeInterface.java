package io.trellis.nodes.core;

/**
 * Interface for AI sub-nodes that supply LangChain4j objects (models, memory, tools)
 * to parent AI nodes (agents, chains) via typed connections.
 * Sub-nodes implement supplyData() instead of execute() for their primary logic.
 */
public interface AiSubNodeInterface {
	Object supplyData(NodeExecutionContext context) throws Exception;
}
