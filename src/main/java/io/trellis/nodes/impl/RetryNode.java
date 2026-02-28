package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Retry Node - re-emits items with errors for retry, up to a maximum number of attempts.
 * Items without errors pass through to the success output.
 * Items that have exhausted retries go to the exhausted output.
 * Tracks attempt count via _retryCount metadata in each item.
 */
@Slf4j
@Node(
	type = "retry",
	displayName = "Retry",
	description = "Retry failed items up to a maximum number of attempts. Items without errors pass through.",
	category = "Flow",
	icon = "rotate-cw"
)
public class RetryNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("success").displayName("success").build(),
			NodeOutput.builder().name("exhausted").displayName("exhausted").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("maxAttempts")
				.displayName("Max Attempts")
				.description("Maximum number of retry attempts before giving up.")
				.type(ParameterType.NUMBER)
				.defaultValue(3)
				.required(true)
				.minValue(1)
				.maxValue(100)
				.build(),

			NodeParameter.builder()
				.name("waitBetweenRetries")
				.displayName("Wait Between Retries (ms)")
				.description("Time to wait between retry attempts in milliseconds.")
				.type(ParameterType.NUMBER)
				.defaultValue(1000)
				.minValue(0)
				.build(),

			NodeParameter.builder()
				.name("backoffStrategy")
				.displayName("Backoff Strategy")
				.description("How to increase the wait time between retries.")
				.type(ParameterType.OPTIONS)
				.defaultValue("fixed")
				.options(List.of(
					ParameterOption.builder()
						.name("Fixed")
						.value("fixed")
						.description("Same wait time between each retry")
						.build(),
					ParameterOption.builder()
						.name("Exponential")
						.value("exponential")
						.description("Double the wait time after each retry (1s, 2s, 4s, ...)")
						.build(),
					ParameterOption.builder()
						.name("Linear")
						.value("linear")
						.description("Increase wait time by the base amount each retry (1s, 2s, 3s, ...)")
						.build()
				))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		int maxAttempts = toInt(context.getParameter("maxAttempts", 3), 3);
		int waitMs = toInt(context.getParameter("waitBetweenRetries", 1000), 1000);
		String backoff = context.getParameter("backoffStrategy", "fixed");

		List<Map<String, Object>> successItems = new ArrayList<>();
		List<Map<String, Object>> exhaustedItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);

			if (!json.containsKey("error")) {
				// No error — pass through to success
				successItems.add(item);
				continue;
			}

			// Item has an error — check retry count
			int retryCount = toInt(json.get("_retryCount"), 0);
			retryCount++;

			if (retryCount >= maxAttempts) {
				// Exhausted all retries
				Map<String, Object> exhaustedJson = new LinkedHashMap<>(json);
				exhaustedJson.put("_retryCount", retryCount);
				exhaustedJson.put("_retriesExhausted", true);
				exhaustedItems.add(wrapInJson(exhaustedJson));

				log.debug("Retry exhausted for item after {} attempts", retryCount);
			} else {
				// Apply backoff delay
				long delay = calculateDelay(waitMs, retryCount, backoff);
				if (delay > 0) {
					try {
						Thread.sleep(delay);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}

				// Re-emit for retry — remove error, increment retry count
				Map<String, Object> retryJson = new LinkedHashMap<>(json);
				retryJson.remove("error");
				retryJson.remove("message");
				retryJson.put("_retryCount", retryCount);
				retryJson.put("_retryAttempt", retryCount);
				successItems.add(wrapInJson(retryJson));

				log.debug("Retry attempt {} of {} (delay={}ms, strategy={})",
					retryCount, maxAttempts, delay, backoff);
			}
		}

		return NodeExecutionResult.successMultiOutput(List.of(successItems, exhaustedItems));
	}

	private long calculateDelay(int baseMs, int attempt, String strategy) {
		return switch (strategy) {
			case "exponential" -> (long) baseMs * (1L << (attempt - 1)); // 2^(n-1) * base
			case "linear" -> (long) baseMs * attempt;
			default -> baseMs; // fixed
		};
	}
}
