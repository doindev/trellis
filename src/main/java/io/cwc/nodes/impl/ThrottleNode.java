package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Throttle Node - batches items across executions using static data.
 * Items are buffered and released when a batch size or time window threshold is met.
 * Two outputs: "released" (batch ready) and "buffered" (items stored, not yet released).
 */
@Slf4j
@Node(
	type = "throttle",
	displayName = "Throttle",
	description = "Buffer items across executions and release them in batches when a size or time threshold is met.",
	category = "Flow",
	icon = "timer"
)
public class ThrottleNode extends AbstractNode {

	private static final String BUFFER_KEY = "throttle_buffer";
	private static final String FIRST_ITEM_TIME_KEY = "throttle_firstItemTime";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
			NodeOutput.builder().name("released").displayName("Released").build(),
			NodeOutput.builder().name("buffered").displayName("Buffered").build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("mode")
				.displayName("Mode")
				.description("When to release buffered items.")
				.type(ParameterType.OPTIONS)
				.defaultValue("batchSize")
				.options(List.of(
					ParameterOption.builder()
						.name("Batch Size")
						.value("batchSize")
						.description("Release when N items have accumulated")
						.build(),
					ParameterOption.builder()
						.name("Time Window")
						.value("timeWindow")
						.description("Release when a time window expires since the first buffered item")
						.build(),
					ParameterOption.builder()
						.name("Both")
						.value("both")
						.description("Release when either batch size or time window is met")
						.build()
				))
				.build(),
			NodeParameter.builder()
				.name("batchSize")
				.displayName("Batch Size")
				.description("Number of items to accumulate before releasing.")
				.type(ParameterType.NUMBER)
				.defaultValue(10)
				.displayOptions(Map.of("show", Map.of("mode", List.of("batchSize", "both"))))
				.build(),
			NodeParameter.builder()
				.name("windowSeconds")
				.displayName("Window (seconds)")
				.description("Time window in seconds since the first buffered item before releasing.")
				.type(ParameterType.NUMBER)
				.defaultValue(60)
				.displayOptions(Map.of("show", Map.of("mode", List.of("timeWindow", "both"))))
				.build(),
			NodeParameter.builder()
				.name("flushOnEmpty")
				.displayName("Flush On Empty Input")
				.description("Release all remaining buffered items when input is empty (end of run cleanup).")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		Map<String, Object> staticData = context.getWorkflowStaticData();
		String mode = context.getParameter("mode", "batchSize");
		int batchSize = toInt(context.getParameter("batchSize", 10), 10);
		int windowSeconds = toInt(context.getParameter("windowSeconds", 60), 60);
		boolean flushOnEmpty = toBoolean(context.getParameter("flushOnEmpty", true), true);

		// Load existing buffer from static data
		List<Map<String, Object>> buffer = loadBuffer(staticData);
		long firstItemTime = loadFirstItemTime(staticData);

		boolean inputEmpty = inputData == null || inputData.isEmpty();

		// Flush on empty input if enabled
		if (inputEmpty && flushOnEmpty && !buffer.isEmpty()) {
			List<Map<String, Object>> released = new ArrayList<>(buffer);
			buffer.clear();
			saveBuffer(staticData, buffer);
			staticData.remove(FIRST_ITEM_TIME_KEY);
			log.debug("Throttle: flushed {} items (empty input)", released.size());
			return NodeExecutionResult.successMultiOutput(List.of(released, List.of()));
		}

		if (inputEmpty) {
			return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of()));
		}

		// Add incoming items to buffer
		buffer.addAll(inputData);
		if (firstItemTime == 0) {
			firstItemTime = System.currentTimeMillis();
		}

		// Check release conditions
		boolean shouldRelease = false;
		if ("batchSize".equals(mode)) {
			shouldRelease = buffer.size() >= batchSize;
		} else if ("timeWindow".equals(mode)) {
			long elapsed = (System.currentTimeMillis() - firstItemTime) / 1000;
			shouldRelease = elapsed >= windowSeconds;
		} else { // "both"
			long elapsed = (System.currentTimeMillis() - firstItemTime) / 1000;
			shouldRelease = buffer.size() >= batchSize || elapsed >= windowSeconds;
		}

		if (shouldRelease) {
			List<Map<String, Object>> released = new ArrayList<>(buffer);
			buffer.clear();
			saveBuffer(staticData, buffer);
			staticData.remove(FIRST_ITEM_TIME_KEY);
			log.debug("Throttle: released {} items", released.size());
			return NodeExecutionResult.successMultiOutput(List.of(released, List.of()));
		}

		// Not yet ready to release — save buffer state
		saveBuffer(staticData, buffer);
		staticData.put(FIRST_ITEM_TIME_KEY, firstItemTime);

		// Output 1: status info about buffered items
		Map<String, Object> statusItem = wrapInJson(Map.of(
			"bufferedCount", buffer.size(),
			"firstItemTime", firstItemTime,
			"mode", mode
		));

		log.debug("Throttle: buffered {} items (waiting for release)", buffer.size());
		return NodeExecutionResult.successMultiOutput(List.of(List.of(), List.of(statusItem)));
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> loadBuffer(Map<String, Object> staticData) {
		Object bufferObj = staticData.get(BUFFER_KEY);
		if (bufferObj == null) {
			return new ArrayList<>();
		}
		try {
			if (bufferObj instanceof List) {
				return new ArrayList<>((List<Map<String, Object>>) bufferObj);
			}
			if (bufferObj instanceof String) {
				return MAPPER.readValue((String) bufferObj, new TypeReference<List<Map<String, Object>>>() {});
			}
		} catch (Exception e) {
			log.warn("Failed to load throttle buffer, starting fresh: {}", e.getMessage());
		}
		return new ArrayList<>();
	}

	private long loadFirstItemTime(Map<String, Object> staticData) {
		Object timeObj = staticData.get(FIRST_ITEM_TIME_KEY);
		if (timeObj instanceof Number) {
			return ((Number) timeObj).longValue();
		}
		if (timeObj instanceof String) {
			try {
				return Long.parseLong((String) timeObj);
			} catch (NumberFormatException ignored) {
				// fall through
			}
		}
		return 0;
	}

	private void saveBuffer(Map<String, Object> staticData, List<Map<String, Object>> buffer) {
		try {
			staticData.put(BUFFER_KEY, MAPPER.writeValueAsString(buffer));
		} catch (Exception e) {
			log.warn("Failed to serialize throttle buffer: {}", e.getMessage());
			staticData.put(BUFFER_KEY, List.copyOf(buffer));
		}
	}
}
