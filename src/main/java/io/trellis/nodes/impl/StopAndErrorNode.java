package io.trellis.nodes.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Stop and Error Node — a terminal node that stops workflow execution
 * and throws an error. Supports two modes: a simple error message string
 * or a structured JSON error object.
 *
 * When using the error object mode, the node extracts the message from
 * the object's 'message', 'description', or 'error' fields (in priority order).
 * Additional fields like 'description' and 'type' are included in the error context.
 */
@Slf4j
@Node(
	type = "stopAndError",
	displayName = "Stop and Error",
	description = "Stops the workflow execution and returns an error.",
	category = "Flow",
	icon = "exclamation-triangle"
)
public class StopAndErrorNode extends AbstractNode {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		// Terminal node — no outputs
		return List.of();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("errorType")
				.displayName("Error Type")
				.description("How to specify the error.")
				.type(ParameterType.OPTIONS)
				.defaultValue("errorMessage")
				.noDataExpression(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Error Message")
						.value("errorMessage")
						.description("Provide a simple text error message.")
						.build(),
					ParameterOption.builder()
						.name("Error Object")
						.value("errorObject")
						.description("Provide a JSON object with error details.")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("errorMessage")
				.displayName("Error Message")
				.description("The error message to throw.")
				.type(ParameterType.STRING)
				.required(true)
				.defaultValue("")
				.placeHolder("An error occurred!")
				.displayOptions(Map.of("show", Map.of("errorType", List.of("errorMessage"))))
				.build(),

			NodeParameter.builder()
				.name("errorObject")
				.displayName("Error Object")
				.description("A JSON object describing the error. Should contain a 'message' field.")
				.type(ParameterType.JSON)
				.required(true)
				.defaultValue("")
				.placeHolder("{\"code\": \"404\", \"description\": \"The resource could not be fetched\"}")
				.displayOptions(Map.of("show", Map.of("errorType", List.of("errorObject"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String errorType = context.getParameter("errorType", "errorMessage");

		String message;
		Map<String, Object> errorContext = null;

		if ("errorObject".equals(errorType)) {
			String errorObjectJson = context.getParameter("errorObject", "{}");
			ErrorParseResult parsed = parseErrorObject(errorObjectJson);
			message = parsed.message;
			errorContext = parsed.context;
		} else {
			message = context.getParameter("errorMessage", "");
			if (message == null || message.isBlank()) {
				message = "Workflow stopped with error";
			}
		}

		log.info("Stop and Error node triggered: {}", message);

		return NodeExecutionResult.builder()
				.error(NodeExecutionResult.NodeExecutionError.builder()
						.message(message)
						.node(context.getNodeId())
						.context(errorContext)
						.build())
				.continueExecution(false)
				.build();
	}

	/**
	 * Parses a JSON error object and extracts the message using priority order:
	 * 'message' > 'description' > 'error' > stringified object.
	 * Additional fields are included as error context metadata.
	 */
	@SuppressWarnings("unchecked")
	private ErrorParseResult parseErrorObject(String json) {
		if (json == null || json.isBlank()) {
			return new ErrorParseResult("Unknown error", null);
		}

		try {
			Object parsed = objectMapper.readValue(json, Object.class);
			if (!(parsed instanceof Map)) {
				return new ErrorParseResult(String.valueOf(parsed), null);
			}

			Map<String, Object> obj = (Map<String, Object>) parsed;
			Map<String, Object> errorContext = new LinkedHashMap<>(obj);

			// Extract message with priority: message > description > error > stringified
			String message = extractStringField(obj, "message");
			if (message == null) {
				message = extractStringField(obj, "description");
			}
			if (message == null) {
				message = extractStringField(obj, "error");
			}
			if (message == null) {
				message = json;
			}

			return new ErrorParseResult(message, errorContext);

		} catch (Exception e) {
			log.warn("Failed to parse error object JSON: {}", e.getMessage());
			return new ErrorParseResult("Invalid error object: " + json, null);
		}
	}

	/**
	 * Extracts a non-empty string from a map field, returning null if absent or blank.
	 */
	private String extractStringField(Map<String, Object> obj, String field) {
		Object value = obj.get(field);
		if (value instanceof String s && !s.isBlank()) {
			return s;
		}
		return null;
	}

	private static class ErrorParseResult {
		final String message;
		final Map<String, Object> context;

		ErrorParseResult(String message, Map<String, Object> context) {
			this.message = message;
			this.context = context;
		}
	}
}
