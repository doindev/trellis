package io.cwc.nodes.impl.ai;

import dev.langchain4j.agent.tool.Tool;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiToolNode;
import io.cwc.nodes.core.NodeExecutionContext;

@Node(
		type = "calculatorTool",
		displayName = "Calculator",
		description = "Tool for evaluating mathematical expressions",
		category = "AI / Tools",
		icon = "calculator",
		searchOnly = true
)
public class CalculatorToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		return new CalculatorTool();
	}

	public static class CalculatorTool {

		@Tool("Evaluate a mathematical expression. Input should be a valid math expression like '2 + 3 * 4', '(10 - 3) * 2', or 'sqrt(16)'. Supports +, -, *, /, ^, %, parentheses, and functions: sqrt, abs, sin, cos, tan, log, ln, ceil, floor, round, min, max. Returns the numeric result.")
		public String calculate(String expression) {
			try {
				double result = new ExpressionParser(expression).parse();
				if (result == (long) result) {
					return String.valueOf((long) result);
				}
				return String.valueOf(result);
			} catch (Exception e) {
				return "Error evaluating expression: " + e.getMessage();
			}
		}
	}

	/**
	 * Recursive descent parser for mathematical expressions.
	 * Supports: +, -, *, /, ^ (power), % (modulo), parentheses,
	 * and functions: sqrt, abs, sin, cos, tan, log, ln, ceil, floor, round, min, max, pi, e.
	 */
	static class ExpressionParser {
		private final String input;
		private int pos;

		ExpressionParser(String input) {
			this.input = input.trim();
			this.pos = 0;
		}

		double parse() {
			double result = parseExpression();
			skipWhitespace();
			if (pos < input.length()) {
				throw new RuntimeException("Unexpected character: '" + input.charAt(pos) + "' at position " + pos);
			}
			return result;
		}

		// expression = term (('+' | '-') term)*
		private double parseExpression() {
			double result = parseTerm();
			while (pos < input.length()) {
				skipWhitespace();
				if (pos >= input.length()) break;
				char op = input.charAt(pos);
				if (op == '+') { pos++; result += parseTerm(); }
				else if (op == '-') { pos++; result -= parseTerm(); }
				else break;
			}
			return result;
		}

		// term = power (('*' | '/' | '%') power)*
		private double parseTerm() {
			double result = parsePower();
			while (pos < input.length()) {
				skipWhitespace();
				if (pos >= input.length()) break;
				char op = input.charAt(pos);
				if (op == '*') { pos++; result *= parsePower(); }
				else if (op == '/') { pos++; result /= parsePower(); }
				else if (op == '%') { pos++; result %= parsePower(); }
				else break;
			}
			return result;
		}

		// power = unary ('^' unary)*
		private double parsePower() {
			double result = parseUnary();
			skipWhitespace();
			if (pos < input.length() && input.charAt(pos) == '^') {
				pos++;
				result = Math.pow(result, parsePower()); // right-associative
			}
			return result;
		}

		// unary = ('+' | '-') unary | atom
		private double parseUnary() {
			skipWhitespace();
			if (pos < input.length()) {
				if (input.charAt(pos) == '+') { pos++; return parseUnary(); }
				if (input.charAt(pos) == '-') { pos++; return -parseUnary(); }
			}
			return parseAtom();
		}

		// atom = number | '(' expression ')' | function '(' args ')'  | constant
		private double parseAtom() {
			skipWhitespace();
			if (pos >= input.length()) {
				throw new RuntimeException("Unexpected end of expression");
			}

			// Parenthesized expression
			if (input.charAt(pos) == '(') {
				pos++;
				double result = parseExpression();
				skipWhitespace();
				if (pos < input.length() && input.charAt(pos) == ')') {
					pos++;
				} else {
					throw new RuntimeException("Missing closing parenthesis");
				}
				return result;
			}

			// Number
			if (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.') {
				return parseNumber();
			}

			// Function or constant name
			if (Character.isLetter(input.charAt(pos))) {
				return parseFunctionOrConstant();
			}

			throw new RuntimeException("Unexpected character: '" + input.charAt(pos) + "'");
		}

		private double parseNumber() {
			int start = pos;
			while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
				pos++;
			}
			// Handle scientific notation (e.g. 1e5, 2.5E-3)
			if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
				pos++;
				if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
					pos++;
				}
				while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
					pos++;
				}
			}
			return Double.parseDouble(input.substring(start, pos));
		}

		private double parseFunctionOrConstant() {
			int start = pos;
			while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
				pos++;
			}
			String name = input.substring(start, pos).toLowerCase();

			// Strip common prefixes like "Math."
			if (name.startsWith("math.")) {
				name = name.substring(5);
			}

			// Constants
			if ("pi".equals(name)) return Math.PI;
			if ("e".equals(name)) return Math.E;

			// Functions — expect '(' args ')'
			skipWhitespace();
			if (pos >= input.length() || input.charAt(pos) != '(') {
				throw new RuntimeException("Unknown identifier: '" + name + "'");
			}
			pos++; // skip '('

			double arg1 = parseExpression();
			double arg2 = Double.NaN;
			skipWhitespace();

			// Check for second argument (for min, max)
			if (pos < input.length() && input.charAt(pos) == ',') {
				pos++;
				arg2 = parseExpression();
				skipWhitespace();
			}

			if (pos < input.length() && input.charAt(pos) == ')') {
				pos++;
			} else {
				throw new RuntimeException("Missing closing parenthesis for function " + name);
			}

			return switch (name) {
				case "sqrt" -> Math.sqrt(arg1);
				case "abs" -> Math.abs(arg1);
				case "sin" -> Math.sin(arg1);
				case "cos" -> Math.cos(arg1);
				case "tan" -> Math.tan(arg1);
				case "log", "log10" -> Math.log10(arg1);
				case "ln" -> Math.log(arg1);
				case "ceil" -> Math.ceil(arg1);
				case "floor" -> Math.floor(arg1);
				case "round" -> Math.round(arg1);
				case "min" -> Math.min(arg1, arg2);
				case "max" -> Math.max(arg1, arg2);
				case "pow" -> Math.pow(arg1, arg2);
				default -> throw new RuntimeException("Unknown function: '" + name + "'");
			};
		}

		private void skipWhitespace() {
			while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
				pos++;
			}
		}
	}
}
