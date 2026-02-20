package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Limit Node - restricts the number of items passed through.
 * Can keep the first N or last N items from the input.
 */
@Slf4j
@Node(
	type = "limit",
	displayName = "Limit",
	description = "Limit the number of items to a specified maximum.",
	category = "Flow",
	icon = "list-end"
)
public class LimitNode extends AbstractNode {

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
		return List.of(
			NodeParameter.builder()
				.name("maxItems")
				.displayName("Max Items")
				.description("If there are more items than this number, some are removed.")
				.type(ParameterType.NUMBER)
				.defaultValue(1)
				.minValue(1)
				.required(true)
				.build(),

			NodeParameter.builder()
				.name("keep")
				.displayName("Keep")
				.description("When removing items, whether to keep the ones at the start or the end.")
				.type(ParameterType.OPTIONS)
				.defaultValue("firstItems")
				.options(List.of(
					ParameterOption.builder()
						.name("First Items")
						.value("firstItems")
						.description("Keep items from the beginning of the list")
						.build(),
					ParameterOption.builder()
						.name("Last Items")
						.value("lastItems")
						.description("Keep items from the end of the list")
						.build()
				))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		int maxItems = Math.max(1, toInt(context.getParameter("maxItems", 1), 1));
		String keep = context.getParameter("keep", "firstItems");

		// If maxItems >= total items, return all unchanged
		if (maxItems >= inputData.size()) {
			log.debug("Limit: maxItems={} >= total={}, returning all items", maxItems, inputData.size());
			return NodeExecutionResult.success(new ArrayList<>(inputData));
		}

		List<Map<String, Object>> result;
		if ("lastItems".equals(keep)) {
			result = new ArrayList<>(inputData.subList(inputData.size() - maxItems, inputData.size()));
		} else {
			result = new ArrayList<>(inputData.subList(0, maxItems));
		}

		log.debug("Limit: {} -> {} items (keep={})", inputData.size(), result.size(), keep);
		return NodeExecutionResult.success(result);
	}
}
