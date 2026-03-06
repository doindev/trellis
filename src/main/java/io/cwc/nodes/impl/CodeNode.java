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
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Code Node - executes custom JavaScript or Python code within the workflow.
 * Uses GraalVM Polyglot for sandboxed code execution. Supports two modes:
 * run once for all items, or run once per each item.
 */
@Slf4j
@Node(
	type = "code",
	displayName = "Code",
	description = "Run custom JavaScript or Python code to transform data.",
	category = "Core",
	icon = "code"
)
public class CodeNode extends AbstractNode {

	private static final String DEFAULT_JS_CODE =
		"// Loop over input items and add a new field called 'myNewField'\n" +
		"for (const item of $input.all()) {\n" +
		"  item.json.myNewField = 1;\n" +
		"}\n" +
		"\n" +
		"return $input.all();";

	private static final String DEFAULT_JS_EACH_CODE =
		"// Add a new field called 'myNewField' to the current item\n" +
		"$input.item.json.myNewField = 1;\n" +
		"\n" +
		"return $input.item;";

	private static final String DEFAULT_PYTHON_CODE =
		"# Loop over input items and add a new field called 'myNewField'\n" +
		"for item in _input.all():\n" +
		"    item['json']['myNewField'] = 1\n" +
		"\n" +
		"return _input.all()";

	private static final String DEFAULT_PYTHON_EACH_CODE =
		"# Add a new field called 'myNewField' to the current item\n" +
		"_input['item']['json']['myNewField'] = 1\n" +
		"\n" +
		"return _input['item']";

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
				.name("language")
				.displayName("Language")
				.description("The programming language to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("javaScript")
				.options(List.of(
					ParameterOption.builder().name("JavaScript").value("javaScript").build(),
					ParameterOption.builder().name("Python").value("python").build()
				))
				.build(),

			NodeParameter.builder()
				.name("mode")
				.displayName("Mode")
				.description("How to run the code.")
				.type(ParameterType.OPTIONS)
				.defaultValue("runOnceForAllItems")
				.options(List.of(
					ParameterOption.builder()
						.name("Run Once for All Items")
						.value("runOnceForAllItems")
						.description("Execute the code once with all input items available")
						.build(),
					ParameterOption.builder()
						.name("Run Once for Each Item")
						.value("runOnceForEachItem")
						.description("Execute the code once for each input item")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("jsCode")
				.displayName("JavaScript Code")
				.description("The JavaScript code to execute.")
				.type(ParameterType.STRING)
				.defaultValue(DEFAULT_JS_CODE)
				.typeOptions(Map.of("rows", 15, "editor", "codeNodeEditor"))
				.displayOptions(Map.of("show", Map.of("language", List.of("javaScript"))))
				.build(),

			NodeParameter.builder()
				.name("pythonCode")
				.displayName("Python Code")
				.description("The Python code to execute.")
				.type(ParameterType.STRING)
				.defaultValue(DEFAULT_PYTHON_CODE)
				.typeOptions(Map.of("rows", 15, "editor", "codeNodeEditor"))
				.displayOptions(Map.of("show", Map.of("language", List.of("python"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String language = context.getParameter("language", "javaScript");
		String mode = context.getParameter("mode", "runOnceForAllItems");
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null) {
			inputData = List.of();
		}

		try {
			if ("runOnceForAllItems".equals(mode)) {
				return executeForAllItems(language, context, inputData);
			} else {
				return executeForEachItem(language, context, inputData);
			}
		} catch (Exception e) {
			return handleError(context, "Code execution failed: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeForAllItems(String language, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {

		String langId = getGraalLanguageId(language);
		String code = getCode(language, context);

		// Serialize input data to JSON for passing into the sandbox
		String inputJson = objectMapper.writeValueAsString(inputData);

		String wrappedCode;
		if ("js".equals(langId)) {
			wrappedCode = buildJsAllItemsWrapper(code, inputJson);
		} else {
			wrappedCode = buildPythonAllItemsWrapper(code, inputJson);
		}

		String resultJson = executeInSandbox(langId, wrappedCode);
		List<Map<String, Object>> result = parseResultItems(resultJson);

		return NodeExecutionResult.success(result);
	}

	private NodeExecutionResult executeForEachItem(String language, NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {

		String langId = getGraalLanguageId(language);
		List<Map<String, Object>> allResults = new ArrayList<>();

		for (int i = 0; i < inputData.size(); i++) {
			Map<String, Object> item = inputData.get(i);
			String itemJson = objectMapper.writeValueAsString(item);

			String code = getCodeForEachItem(language, context);
			String wrappedCode;
			if ("js".equals(langId)) {
				wrappedCode = buildJsEachItemWrapper(code, itemJson, i);
			} else {
				wrappedCode = buildPythonEachItemWrapper(code, itemJson, i);
			}

			String resultJson = executeInSandbox(langId, wrappedCode);
			Map<String, Object> resultItem = parseResultItem(resultJson);
			if (resultItem != null) {
				allResults.add(resultItem);
			}
		}

		return NodeExecutionResult.success(allResults);
	}

	private String executeInSandbox(String langId, String code) throws Exception {
		try (Context graalContext = Context.newBuilder(langId)
				.allowHostAccess(HostAccess.SCOPED)
				.allowExperimentalOptions(true)
				.option("engine.WarnInterpreterOnly", "false")
				.build()) {

			Value result = graalContext.eval(langId, code);

			if (result.isString()) {
				return result.asString();
			} else if (result.hasMembers() || result.hasArrayElements()) {
				// Convert polyglot value to JSON string via JS/Python
				graalContext.getBindings(langId).putMember("__result__", result);
				Value jsonStr;
				if ("js".equals(langId)) {
					jsonStr = graalContext.eval(langId, "JSON.stringify(__result__)");
				} else {
					jsonStr = graalContext.eval(langId, "import json\njson.dumps(__result__)");
				}
				return jsonStr.asString();
			}
			return result.toString();
		}
	}

	private String buildJsAllItemsWrapper(String userCode, String inputJson) {
		return "(function() {\n" +
			"  const __inputData = JSON.parse('" + escapeForJs(inputJson) + "');\n" +
			"  const $input = {\n" +
			"    all: function() { return __inputData; },\n" +
			"    first: function() { return __inputData.length > 0 ? __inputData[0] : null; },\n" +
			"    last: function() { return __inputData.length > 0 ? __inputData[__inputData.length - 1] : null; },\n" +
			"    length: __inputData.length\n" +
			"  };\n" +
			"  " + userCode + "\n" +
			"  return JSON.stringify(__inputData);\n" +
			"})();\n";
	}

	private String buildJsEachItemWrapper(String userCode, String itemJson, int index) {
		return "(function() {\n" +
			"  const __item = JSON.parse('" + escapeForJs(itemJson) + "');\n" +
			"  const $input = {\n" +
			"    item: __item,\n" +
			"    index: " + index + "\n" +
			"  };\n" +
			"  " + userCode + "\n" +
			"  return JSON.stringify(__item);\n" +
			"})();\n";
	}

	private String buildPythonAllItemsWrapper(String userCode, String inputJson) {
		return "import json\n" +
			"__input_data = json.loads('" + escapeForPython(inputJson) + "')\n" +
			"class _InputHelper:\n" +
			"    def all(self):\n" +
			"        return __input_data\n" +
			"    def first(self):\n" +
			"        return __input_data[0] if len(__input_data) > 0 else None\n" +
			"    def last(self):\n" +
			"        return __input_data[-1] if len(__input_data) > 0 else None\n" +
			"    @property\n" +
			"    def length(self):\n" +
			"        return len(__input_data)\n" +
			"_input = _InputHelper()\n" +
			userCode + "\n" +
			"json.dumps(__input_data)\n";
	}

	private String buildPythonEachItemWrapper(String userCode, String itemJson, int index) {
		return "import json\n" +
			"__item = json.loads('" + escapeForPython(itemJson) + "')\n" +
			"_input = {'item': __item, 'index': " + index + "}\n" +
			userCode + "\n" +
			"json.dumps(__item)\n";
	}

	private String getCode(String language, NodeExecutionContext context) {
		if ("javaScript".equals(language)) {
			return context.getParameter("jsCode", DEFAULT_JS_CODE);
		}
		return context.getParameter("pythonCode", DEFAULT_PYTHON_CODE);
	}

	private String getCodeForEachItem(String language, NodeExecutionContext context) {
		if ("javaScript".equals(language)) {
			return context.getParameter("jsCode", DEFAULT_JS_EACH_CODE);
		}
		return context.getParameter("pythonCode", DEFAULT_PYTHON_EACH_CODE);
	}

	private String getGraalLanguageId(String language) {
		return "python".equals(language) ? "python" : "js";
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> parseResultItems(String json) throws Exception {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		Object parsed = objectMapper.readValue(json, Object.class);
		if (parsed instanceof List) {
			List<Map<String, Object>> result = new ArrayList<>();
			for (Object item : (List<?>) parsed) {
				if (item instanceof Map) {
					result.add((Map<String, Object>) item);
				} else {
					result.add(wrapInJson(Map.of("value", item)));
				}
			}
			return result;
		} else if (parsed instanceof Map) {
			return List.of((Map<String, Object>) parsed);
		}
		return List.of(wrapInJson(Map.of("value", parsed)));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseResultItem(String json) throws Exception {
		if (json == null || json.isBlank()) {
			return null;
		}
		Object parsed = objectMapper.readValue(json, Object.class);
		if (parsed instanceof Map) {
			return (Map<String, Object>) parsed;
		}
		return wrapInJson(Map.of("value", parsed));
	}

	private String escapeForJs(String str) {
		return str.replace("\\", "\\\\")
			.replace("'", "\\'")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

	private String escapeForPython(String str) {
		return str.replace("\\", "\\\\")
			.replace("'", "\\'")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
