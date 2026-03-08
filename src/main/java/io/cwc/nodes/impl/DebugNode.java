package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Debug / Inspect Node - logs item details and passes data through unchanged.
 * Useful for inspecting workflow data at any point in the pipeline.
 */
@Slf4j
@Node(
	type = "debug",
	displayName = "Debug",
	description = "Inspect and log workflow data. Passes items through unchanged while logging details.",
	category = "Flow",
	icon = "bug"
)
public class DebugNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

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
				.name("logLevel")
				.displayName("Log Level")
				.description("The log level to use for debug output.")
				.type(ParameterType.OPTIONS)
				.defaultValue("info")
				.options(List.of(
					ParameterOption.builder().name("Info").value("info").build(),
					ParameterOption.builder().name("Debug").value("debug").build(),
					ParameterOption.builder().name("Warn").value("warn").build()
				))
				.build(),

			NodeParameter.builder()
				.name("logMessage")
				.displayName("Log Message")
				.description("Optional custom message to include in the log output.")
				.type(ParameterType.STRING)
				.defaultValue("")
				.placeHolder("Debug checkpoint")
				.build(),

			NodeParameter.builder()
				.name("showItemCount")
				.displayName("Show Item Count")
				.description("Log the number of items passing through.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build(),

			NodeParameter.builder()
				.name("showFieldNames")
				.displayName("Show Field Names")
				.description("Log the field names of each item.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build(),

			NodeParameter.builder()
				.name("showFieldValues")
				.displayName("Show Field Values")
				.description("Log the full field values of each item.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("showItemSize")
				.displayName("Show Item Size")
				.description("Log the approximate JSON size of each item.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("pauseExecution")
				.displayName("Pause Execution")
				.description("Pause execution for a specified duration (useful for debugging timing issues).")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("pauseMs")
				.displayName("Pause Duration (ms)")
				.description("How long to pause execution in milliseconds.")
				.type(ParameterType.NUMBER)
				.defaultValue(1000)
				.minValue(0)
				.maxValue(30000)
				.displayOptions(Map.of("show", Map.of("pauseExecution", List.of(true))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			logAtLevel(context, "Debug node: no input data");
			return NodeExecutionResult.empty();
		}

		String logMessage = context.getParameter("logMessage", "");
		boolean showItemCount = toBoolean(context.getParameter("showItemCount", true), true);
		boolean showFieldNames = toBoolean(context.getParameter("showFieldNames", true), true);
		boolean showFieldValues = toBoolean(context.getParameter("showFieldValues", false), false);
		boolean showItemSize = toBoolean(context.getParameter("showItemSize", false), false);
		boolean pauseExecution = toBoolean(context.getParameter("pauseExecution", false), false);
		int pauseMs = toInt(context.getParameter("pauseMs", 1000), 1000);

		StringBuilder sb = new StringBuilder();
		if (logMessage != null && !logMessage.isEmpty()) {
			sb.append("[").append(logMessage).append("] ");
		}

		if (showItemCount) {
			sb.append("Items: ").append(inputData.size()).append(" | ");
		}

		if (showFieldNames || showFieldValues || showItemSize) {
			for (int i = 0; i < inputData.size(); i++) {
				Map<String, Object> item = inputData.get(i);
				Map<String, Object> json = unwrapJson(item);

				sb.append("Item ").append(i).append(": ");

				if (showFieldNames) {
					sb.append("fields=").append(json.keySet()).append(" ");
				}

				if (showFieldValues) {
					try {
						sb.append("values=").append(MAPPER.writeValueAsString(json)).append(" ");
					} catch (Exception e) {
						sb.append("values=[serialization error] ");
					}
				}

				if (showItemSize) {
					try {
						String jsonStr = MAPPER.writeValueAsString(json);
						sb.append("size=").append(jsonStr.length()).append("b ");
					} catch (Exception e) {
						sb.append("size=[unknown] ");
					}
				}

				sb.append("| ");
			}
		}

		logAtLevel(context, sb.toString().trim());

		// Add debug metadata to each item
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = new LinkedHashMap<>(unwrapJson(item));
			Map<String, Object> debugMeta = new LinkedHashMap<>();
			debugMeta.put("_timestamp", System.currentTimeMillis());
			debugMeta.put("_nodeId", context.getNodeId());
			debugMeta.put("_itemCount", inputData.size());
			debugMeta.put("_fieldNames", new ArrayList<>(json.keySet()));
			json.put("_debug", debugMeta);
			result.add(wrapInJson(json));
		}

		if (pauseExecution && pauseMs > 0) {
			try {
				Thread.sleep(pauseMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		return NodeExecutionResult.success(result);
	}

	private void logAtLevel(NodeExecutionContext context, String message) {
		String level = context.getParameter("logLevel", "info");
		String prefix = "Debug [" + context.getNodeId() + "]: ";
		switch (level) {
			case "debug":
				log.debug("{}{}", prefix, message);
				break;
			case "warn":
				log.warn("{}{}", prefix, message);
				break;
			default:
				log.info("{}{}", prefix, message);
				break;
		}
	}
}
