package io.cwc.nodes.impl.ai;

import dev.langchain4j.agent.tool.Tool;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiToolNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;

@Node(
		type = "codeTool",
		displayName = "Code Tool",
		description = "Tool that executes JavaScript or Python code",
		category = "AI / Tools",
		icon = "code",
		searchOnly = true,
		implementationNotes = "JavaScript runs on GraalVM which only supports pure ECMAScript (ES2022+). " +
			"Node.js APIs (require, fs, path, Buffer, process, setTimeout, etc.) are NOT available. " +
			"Use only standard JS built-ins: JSON, Math, Array, Object, Map, Set, Date, RegExp, Promise, String methods."
)
public class CodeToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String code = context.getParameter("code", "");
		String language = context.getParameter("language", "js");

		return new CodeExecutionTool(code, language);
	}

	public static class CodeExecutionTool {
		private final String code;
		private final String language;

		public CodeExecutionTool(String code, String language) {
			this.code = code;
			this.language = language;
		}

		@Tool("Execute code with the given input. The input string will be available as the variable 'input' in the code. Returns the result of execution.")
		public String executeCode(String input) {
			String lang = "python".equalsIgnoreCase(language) ? "python" : "js";
			try (Context ctx = Context.newBuilder(lang)
					.option("engine.WarnInterpreterOnly", "false")
					.build()) {
				ctx.getBindings(lang).putMember("input", input);
				Value result = ctx.eval(lang, code);
				return result.toString();
			} catch (Exception e) {
				return "Code execution error: " + e.getMessage();
			}
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.OPTIONS)
						.defaultValue("js")
						.options(List.of(
								ParameterOption.builder().name("JavaScript").value("js").build(),
								ParameterOption.builder().name("Python").value("python").build()
						)).build(),
				NodeParameter.builder()
						.name("code").displayName("Code")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 10, "editor", "codeNodeEditor"))
						.required(true)
						.description("Code to execute. Use 'input' variable to access the AI-provided input.")
						.build()
		);
	}
}
