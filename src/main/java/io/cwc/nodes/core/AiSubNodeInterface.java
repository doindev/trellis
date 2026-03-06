package io.cwc.nodes.core;

/**
 * Interface for AI sub-nodes that supply LangChain4j objects (models, memory, tools)
 * to parent AI nodes (agents, chains) via typed connections.
 * Sub-nodes implement supplyData() instead of execute() for their primary logic.
 */
public interface AiSubNodeInterface {
	Object supplyData(NodeExecutionContext context) throws Exception;

	/**
	 * Determines whether the engine should route this node to supplyData() or execute().
	 * Returns true by default (pure sub-nodes always supply data).
	 * Hybrid nodes (e.g. vector stores) override this based on their operation mode.
	 */
	default boolean shouldSupplyData(NodeExecutionContext context) {
		return true;
	}
}
