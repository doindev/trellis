package io.cwc.nodes.core;

import java.util.List;
import java.util.Map;

public interface NodeInterface {

	// executes the node with the given context
	NodeExecutionResult execute(NodeExecutionContext context);
	
	// returns the node's parameter definitions used for UI rendering and validation
	default List<NodeParameter> getParameters() {
		return List.of();
	}
	
	// returns a node's output definition
	default List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").build());
	}
	
	// returns a list of node's input definitions
	default List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").build());
	}
	
	// called before node execution (hook for setup)
	default void beforeExecute(NodeExecutionContext context) {
		// default: no-op
	}
	
	// called after node execution (hook for cleanup)
	default void afterExecute(NodeExecutionContext context, NodeExecutionResult result) {
		// default: no-op
	}
	
	// validates the node's parameters and returns a list of validation errors, empty if valid
	default List<String> validateParameters(Map<String, Object> parameters) {
		return List.of();
	}
	
	// returns additional metadata for the node
	default Map<String, Object> getMetadata() {
		return Map.of();
	}
}
