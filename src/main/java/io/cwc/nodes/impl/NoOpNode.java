package io.cwc.nodes.impl;

import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import lombok.extern.slf4j.Slf4j;

/**
 * No Operation Node - passes input data through unchanged.
 * Useful as a placeholder, for workflow organization, or as a connection
 * point when routing logic needs a pass-through branch.
 */
@Slf4j
@Node(
	type = "noOp",
	displayName = "No Operation",
	description = "Passes data through without any changes. Useful as a placeholder or pass-through node.",
	category = "Flow",
	icon = "arrow-right"
)
public class NoOpNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of();
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null || inputData.isEmpty()) {
			log.debug("NoOp node: no input data, returning empty result");
			return NodeExecutionResult.empty();
		}

		log.debug("NoOp node: passing through {} items", inputData.size());
		return NodeExecutionResult.success(inputData);
	}
}
