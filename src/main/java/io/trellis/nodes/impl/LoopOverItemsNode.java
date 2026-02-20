package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
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
 * Loop Over Items Node (Split In Batches) - processes items in batches by
 * splitting input items into configurable batch sizes. Works with the workflow
 * engine's loop support to re-execute downstream "loop body" nodes for each batch.
 *
 * Outputs:
 * - Output 0 ("Done"): receives all accumulated processed items when all batches are complete
 * - Output 1 ("Loop"): receives the current batch of items for processing
 *
 * State is persisted across re-executions via nodeContextData:
 * - items: remaining items to process
 * - processedItems: accumulated results from all iterations
 * - currentRunIndex: current iteration count
 * - done: whether all items have been processed
 */
@Slf4j
@Node(
	type = "loopOverItems",
	displayName = "Loop Over Items",
	description = "Process items in batches. Connect the 'Loop' output through processing nodes back to this node's input to iterate.",
	category = "Flow",
	icon = "repeat"
)
public class LoopOverItemsNode extends AbstractNode {

	private static final String CTX_ITEMS = "items";
	private static final String CTX_PROCESSED = "processedItems";
	private static final String CTX_RUN_INDEX = "currentRunIndex";
	private static final String CTX_DONE = "done";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("done").displayName("Done").build(),
			NodeOutput.builder().name("loop").displayName("Loop").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("batchSize")
				.displayName("Batch Size")
				.description("The number of items to return with each iteration.")
				.type(ParameterType.NUMBER)
				.defaultValue(1)
				.minValue(1)
				.required(true)
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("reset")
						.displayName("Reset")
						.description("Start again from the beginning of the input items, even if items are still remaining from a previous run.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build()
				))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputItems = context.getInputData();
		if (inputItems == null) inputItems = List.of();

		int batchSize = Math.max(1, toInt(context.getParameter("batchSize", 1), 1));

		// Read options
		boolean reset = false;
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			reset = toBoolean(((Map<String, Object>) optionsObj).get("reset"), false);
		}

		// Get persistent node context (survives across loop iterations)
		Map<String, Object> nodeCtx = context.getNodeContextData();
		if (nodeCtx == null) {
			nodeCtx = new HashMap<>();
		}

		List<Map<String, Object>> returnItems = new ArrayList<>();

		Object storedItems = nodeCtx.get(CTX_ITEMS);
		if (storedItems == null || reset) {
			// ═══ FIRST RUN (or reset) ═══
			// Clone input items so we can splice without modifying originals
			List<Map<String, Object>> allItems = new ArrayList<>();
			for (Map<String, Object> item : inputItems) {
				allItems.add(deepClone(item));
			}

			nodeCtx.put(CTX_RUN_INDEX, 0);

			// Extract first batch
			int end = Math.min(batchSize, allItems.size());
			for (int i = 0; i < end; i++) {
				returnItems.add(allItems.get(i));
			}

			// Store remaining items
			List<Map<String, Object>> remaining = new ArrayList<>(allItems.subList(end, allItems.size()));
			nodeCtx.put(CTX_ITEMS, remaining);

			// Initialize processed items (empty on first run)
			nodeCtx.put(CTX_PROCESSED, new ArrayList<>());

			log.debug("LoopOverItems: first run, total={}, batchSize={}, batch={}, remaining={}",
					allItems.size(), batchSize, returnItems.size(), remaining.size());
		} else {
			// ═══ SUBSEQUENT RUNS ═══
			int runIndex = toInt(nodeCtx.get(CTX_RUN_INDEX), 0) + 1;
			nodeCtx.put(CTX_RUN_INDEX, runIndex);

			// The inputItems on subsequent runs are the PROCESSED items from
			// the loop body (downstream nodes that processed the previous batch)
			List<Map<String, Object>> processedItems = (List<Map<String, Object>>) nodeCtx.get(CTX_PROCESSED);
			if (processedItems == null) processedItems = new ArrayList<>();

			// Accumulate processed items from loop body
			for (Map<String, Object> item : inputItems) {
				processedItems.add(deepClone(item));
			}
			nodeCtx.put(CTX_PROCESSED, processedItems);

			// Extract next batch from remaining items
			List<Map<String, Object>> remaining = (List<Map<String, Object>>) nodeCtx.get(CTX_ITEMS);
			if (remaining == null) remaining = new ArrayList<>();

			int end = Math.min(batchSize, remaining.size());
			for (int i = 0; i < end; i++) {
				returnItems.add(remaining.get(i));
			}

			// Update remaining
			remaining = new ArrayList<>(remaining.subList(end, remaining.size()));
			nodeCtx.put(CTX_ITEMS, remaining);

			log.debug("LoopOverItems: iteration {}, batch={}, remaining={}",
					runIndex, returnItems.size(), remaining.size());
		}

		if (returnItems.isEmpty()) {
			// ═══ ALL BATCHES PROCESSED ═══
			nodeCtx.put(CTX_DONE, true);

			// processedItems were already accumulated in the SUBSEQUENT RUNS block above
			// (inputItems from this call were added there), so no need to re-add them here.
			List<Map<String, Object>> processedItems = (List<Map<String, Object>>) nodeCtx.get(CTX_PROCESSED);
			if (processedItems == null) processedItems = new ArrayList<>();

			log.debug("LoopOverItems: done, total processed items={}", processedItems.size());

			// Output 0 (Done) = all accumulated processed items
			// Output 1 (Loop) = empty (signals engine to stop looping)
			return NodeExecutionResult.successMultiOutput(List.of(processedItems, List.of()));
		}

		// ═══ MORE BATCHES REMAIN ═══
		nodeCtx.put(CTX_DONE, false);

		// Output 0 (Done) = empty (no data yet)
		// Output 1 (Loop) = current batch
		return NodeExecutionResult.successMultiOutput(List.of(List.of(), returnItems));
	}
}
