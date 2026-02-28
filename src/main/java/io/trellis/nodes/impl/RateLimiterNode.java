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
 * Rate Limiter Node - controls the flow of items by limiting throughput.
 * In 'delay' mode, processes items in batches with a delay between each batch.
 * In 'drop' mode, only passes through maxItems per window, dropping the rest.
 */
@Slf4j
@Node(
	type = "rateLimiter",
	displayName = "Rate Limiter",
	description = "Control item throughput by delaying or dropping items that exceed a rate limit.",
	category = "Flow",
	icon = "gauge"
)
public class RateLimiterNode extends AbstractNode {

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
				.displayName("Max Items Per Window")
				.description("Maximum number of items to process per time window.")
				.type(ParameterType.NUMBER)
				.defaultValue(10)
				.required(true)
				.minValue(1)
				.build(),

			NodeParameter.builder()
				.name("timeWindow")
				.displayName("Time Window (ms)")
				.description("The time window in milliseconds.")
				.type(ParameterType.NUMBER)
				.defaultValue(1000)
				.minValue(100)
				.build(),

			NodeParameter.builder()
				.name("strategy")
				.displayName("Strategy")
				.description("How to handle items that exceed the rate limit.")
				.type(ParameterType.OPTIONS)
				.defaultValue("delay")
				.options(List.of(
					ParameterOption.builder()
						.name("Delay")
						.value("delay")
						.description("Process all items but delay between batches to stay within the limit")
						.build(),
					ParameterOption.builder()
						.name("Drop")
						.value("drop")
						.description("Only pass through maxItems per window, dropping excess items")
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

		int maxItems = toInt(context.getParameter("maxItems", 10), 10);
		int timeWindow = toInt(context.getParameter("timeWindow", 1000), 1000);
		String strategy = context.getParameter("strategy", "delay");

		if ("drop".equals(strategy)) {
			// Drop: only pass through maxItems
			List<Map<String, Object>> limited = inputData.size() <= maxItems
				? inputData
				: new ArrayList<>(inputData.subList(0, maxItems));

			log.debug("Rate limiter (drop): {} items -> {} passed, {} dropped",
				inputData.size(), limited.size(), inputData.size() - limited.size());
			return NodeExecutionResult.success(limited);
		}

		// Delay: process in batches of maxItems with timeWindow delay between batches
		List<Map<String, Object>> result = new ArrayList<>();
		int totalBatches = (inputData.size() + maxItems - 1) / maxItems;

		for (int batch = 0; batch < totalBatches; batch++) {
			int start = batch * maxItems;
			int end = Math.min(start + maxItems, inputData.size());
			result.addAll(inputData.subList(start, end));

			// Sleep between batches (not after the last batch)
			if (batch < totalBatches - 1 && timeWindow > 0) {
				try {
					Thread.sleep(timeWindow);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		log.debug("Rate limiter (delay): {} items in {} batches of {} with {}ms delay",
			inputData.size(), totalBatches, maxItems, timeWindow);
		return NodeExecutionResult.success(result);
	}
}
