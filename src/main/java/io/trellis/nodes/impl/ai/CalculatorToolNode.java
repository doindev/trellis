package io.trellis.nodes.impl.ai;

import dev.langchain4j.agent.tool.Tool;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiToolNode;
import io.trellis.nodes.core.NodeExecutionContext;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

@Node(
		type = "calculatorTool",
		displayName = "Calculator",
		description = "Tool for evaluating mathematical expressions",
		category = "AI / Tools",
		icon = "calculator"
)
public class CalculatorToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		return new CalculatorTool();
	}

	public static class CalculatorTool {

		@Tool("Evaluate a mathematical expression. Input should be a valid math expression string like '2 + 3 * 4' or 'Math.sqrt(16)'. Returns the numeric result.")
		public String calculate(String expression) {
			try {
				ScriptEngine engine = new ScriptEngineManager().getEngineByName("js");
				if (engine != null) {
					Object result = engine.eval(expression);
					return String.valueOf(result);
				}
				// Fallback: simple expression evaluation
				double result = evaluateSimple(expression);
				return String.valueOf(result);
			} catch (Exception e) {
				return "Error evaluating expression: " + e.getMessage();
			}
		}

		private double evaluateSimple(String expr) {
			expr = expr.trim();
			try {
				return Double.parseDouble(expr);
			} catch (NumberFormatException e) {
				throw new RuntimeException("Cannot evaluate expression: " + expr);
			}
		}
	}
}
