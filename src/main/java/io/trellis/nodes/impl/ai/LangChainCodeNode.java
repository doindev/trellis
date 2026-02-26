package io.trellis.nodes.impl.ai;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

/**
 * LangChain Code — allows running custom JavaScript/Python code that uses LangChain.
 * Currently implemented as a stub that returns an informational error message,
 * as custom LangChain code execution requires a configured runtime environment.
 */
@Node(
		type = "code-langchain",
		displayName = "LangChain Code",
		description = "Run custom code using LangChain libraries",
		category = "AI / Miscellaneous",
		icon = "code"
)
public class LangChainCodeNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String language = context.getParameter("language", "javascript");
		String code = context.getParameter("code", "");

		if (code == null || code.isBlank()) {
			return NodeExecutionResult.error("No code provided. Please enter code to execute.");
		}

		// Stub implementation — custom LangChain code execution is not yet supported
		return NodeExecutionResult.error(
				"Custom LangChain code execution requires a configured runtime. "
						+ "Language: " + language + ". "
						+ "Please configure a JavaScript or Python runtime environment to enable this node.");
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.OPTIONS)
						.defaultValue("javascript")
						.options(List.of(
								ParameterOption.builder().name("JavaScript").value("javascript").build(),
								ParameterOption.builder().name("Python").value("python").build()
						)).build(),
				NodeParameter.builder()
						.name("code").displayName("Code")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 10))
						.defaultValue("")
						.required(true)
						.description("The LangChain code to execute").build()
		);
	}
}
