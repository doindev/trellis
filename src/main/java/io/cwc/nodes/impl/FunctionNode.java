package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Function Node - executes custom JavaScript code with full access to input items.
 * The code receives an 'items' variable containing all input items and should
 * return the modified items array. Runs once for all items.
 */
@Slf4j
@Node(
	type = "function",
	displayName = "Function",
	description = "Run custom JavaScript code. The code receives 'items' and should return the modified items array.",
	category = "Core",
	icon = "code"
)
public class FunctionNode extends AbstractNode {

	private static final String DEFAULT_CODE =
		"// Code here runs once for all items\n" +
		"// 'items' is an array of objects with the format: [{json: {...}}, ...]\n" +
		"// Modify the items as needed and return the array.\n" +
		"\n" +
		"return items;";

	private final ObjectMapper objectMapper = new ObjectMapper();

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
				.name("functionCode")
				.displayName("JavaScript Code")
				.description("The JavaScript code to execute. Use 'items' to access input data. Must return an array of items.")
				.type(ParameterType.STRING)
				.defaultValue(DEFAULT_CODE)
				.typeOptions(Map.of("rows", 15, "editor", "codeNodeEditor"))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String code = context.getParameter("functionCode", DEFAULT_CODE);
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null) {
			inputData = List.of();
		}

		try {
			// Serialize input data to JSON
			String inputJson = objectMapper.writeValueAsString(inputData);

			// Wrap user code in a function that provides the 'items' binding
			String wrappedCode = "(function() {\n" +
				"  var items = JSON.parse('" + escapeForJs(inputJson) + "');\n" +
				"  " + code + "\n" +
				"  return JSON.stringify(items);\n" +
				"})();\n";

			// Execute in GraalVM sandbox
			String resultJson;
			try (Context graalContext = Context.newBuilder("js")
					.allowHostAccess(HostAccess.SCOPED)
					.allowExperimentalOptions(true)
					.option("engine.WarnInterpreterOnly", "false")
					.build()) {

				Value result = graalContext.eval("js", wrappedCode);

				if (result.isString()) {
					resultJson = result.asString();
				} else {
					graalContext.getBindings("js").putMember("__result__", result);
					Value jsonStr = graalContext.eval("js", "JSON.stringify(__result__)");
					resultJson = jsonStr.asString();
				}
			}

			// Parse result back into items
			List<Map<String, Object>> outputItems = new ArrayList<>();
			if (resultJson != null && !resultJson.isBlank()) {
				Object parsed = objectMapper.readValue(resultJson, Object.class);
				if (parsed instanceof List) {
					for (Object item : (List<?>) parsed) {
						if (item instanceof Map) {
							outputItems.add((Map<String, Object>) item);
						} else {
							outputItems.add(wrapInJson(Map.of("value", item)));
						}
					}
				} else if (parsed instanceof Map) {
					outputItems.add((Map<String, Object>) parsed);
				} else {
					outputItems.add(wrapInJson(Map.of("value", parsed)));
				}
			}

			log.debug("Function node: processed {} items -> {} items", inputData.size(), outputItems.size());
			return NodeExecutionResult.success(outputItems);

		} catch (Exception e) {
			return handleError(context, "Function execution failed: " + e.getMessage(), e);
		}
	}

	private String escapeForJs(String str) {
		return str.replace("\\", "\\\\")
			.replace("'", "\\'")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
