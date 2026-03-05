package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Template Node - multi-line string interpolation with Mustache-like syntax.
 * Supports simple variable substitution ({{fieldName}}), conditionals
 * ({{#if field}}...{{/if}}), iteration ({{#each array}}...{{/each}}),
 * and default values ({{field|default}}).
 */
@Slf4j
@Node(
	type = "template",
	displayName = "Template",
	description = "Render text templates with Mustache-like {{variable}} syntax. Supports conditionals, iteration, and default values.",
	category = "Data Transformation",
	icon = "braces"
)
public class TemplateNode extends AbstractNode {

	private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(.+?)\\s*\\}\\}");
	private static final Pattern IF_PATTERN = Pattern.compile(
		"\\{\\{#if\\s+(.+?)\\}\\}(.*?)\\{\\{/if\\}\\}", Pattern.DOTALL);
	private static final Pattern EACH_PATTERN = Pattern.compile(
		"\\{\\{#each\\s+(.+?)\\}\\}(.*?)\\{\\{/each\\}\\}", Pattern.DOTALL);

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
				.name("template")
				.displayName("Template")
				.description("The template text. Use {{fieldName}} for variables, {{#if field}}...{{/if}} for conditionals, {{#each array}}...{{/each}} for iteration.")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 10, "editor", "codeNodeEditor"))
				.required(true)
				.placeHolder("Hello {{name}}, welcome!")
				.build(),
			NodeParameter.builder()
				.name("outputField")
				.displayName("Output Field")
				.description("The field to store the rendered template result.")
				.type(ParameterType.STRING)
				.defaultValue("output")
				.build(),
			NodeParameter.builder()
				.name("syntax")
				.displayName("Syntax")
				.description("Template syntax to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("mustache")
				.options(List.of(
					ParameterOption.builder()
						.name("Mustache")
						.value("mustache")
						.description("Mustache-like: {{fieldName}}, {{#if}}, {{#each}}, {{field|default}}")
						.build(),
					ParameterOption.builder()
						.name("Expression")
						.value("expression")
						.description("Simple dot-notation lookup: {{ $json.field }}")
						.build()
				))
				.build(),
			NodeParameter.builder()
				.name("preserveNewlines")
				.displayName("Preserve Newlines")
				.description("Keep newlines in the rendered output.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String template = context.getParameter("template", "");
		String outputField = context.getParameter("outputField", "output");
		String syntax = context.getParameter("syntax", "mustache");
		boolean preserveNewlines = toBoolean(context.getParameter("preserveNewlines", true), true);

		List<Map<String, Object>> outputItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = deepClone(item);
				Map<String, Object> json = unwrapJson(item);

				String rendered;
				if ("expression".equals(syntax)) {
					rendered = renderExpression(template, item);
				} else {
					rendered = renderMustache(template, json, item);
				}

				if (!preserveNewlines) {
					rendered = rendered.replace("\n", " ").replace("\r", "");
				}

				setNestedValue(result, "json." + outputField, rendered);
				outputItems.add(result);
			} catch (Exception e) {
				return handleError(context, "Error rendering template: " + e.getMessage(), e);
			}
		}

		log.debug("Template: rendered {} items", outputItems.size());
		return NodeExecutionResult.success(outputItems);
	}

	/**
	 * Expression mode: resolve {{ $json.field }} or {{ expr }} via dot-notation lookup.
	 */
	private String renderExpression(String template, Map<String, Object> item) {
		Matcher matcher = VARIABLE_PATTERN.matcher(template);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String expr = matcher.group(1).trim();
			// Strip leading $json. prefix if present
			if (expr.startsWith("$json.")) {
				expr = "json." + expr.substring(6);
			} else if (!expr.startsWith("json.")) {
				expr = "json." + expr;
			}
			Object value = getNestedValue(item, expr);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Mustache mode: supports {{field}}, {{field|default}}, {{#if field}}...{{/if}},
	 * {{#each array}}{{this.prop}}{{/each}}.
	 */
	private String renderMustache(String template, Map<String, Object> json, Map<String, Object> item) {
		String result = template;

		// Process {{#each array}}...{{/each}} blocks
		result = processEachBlocks(result, json, item);

		// Process {{#if field}}...{{/if}} blocks
		result = processIfBlocks(result, json, item);

		// Process simple {{variable}} and {{variable|default}} substitutions
		result = processVariables(result, json, item);

		return result;
	}

	private String processEachBlocks(String template, Map<String, Object> json, Map<String, Object> item) {
		Matcher matcher = EACH_PATTERN.matcher(template);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String arrayField = matcher.group(1).trim();
			String body = matcher.group(2);

			Object arrayObj = getNestedValue(item, "json." + arrayField);
			StringBuilder rendered = new StringBuilder();

			if (arrayObj instanceof List<?> list) {
				for (Object element : list) {
					String iteration = body;
					if (element instanceof Map<?, ?> map) {
						// Replace {{this.prop}} references
						@SuppressWarnings("unchecked")
						Map<String, Object> elementMap = (Map<String, Object>) map;
						iteration = replaceThisReferences(iteration, elementMap);
					} else {
						// Replace {{this}} with the element value
						iteration = iteration.replace("{{this}}", String.valueOf(element));
					}
					rendered.append(iteration);
				}
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(rendered.toString()));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private String replaceThisReferences(String template, Map<String, Object> elementMap) {
		Matcher matcher = VARIABLE_PATTERN.matcher(template);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String ref = matcher.group(1).trim();
			Object value = null;
			if ("this".equals(ref)) {
				value = elementMap;
			} else if (ref.startsWith("this.")) {
				String field = ref.substring(5);
				value = elementMap.get(field);
			}
			matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private String processIfBlocks(String template, Map<String, Object> json, Map<String, Object> item) {
		Matcher matcher = IF_PATTERN.matcher(template);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String field = matcher.group(1).trim();
			String body = matcher.group(2);

			Object value = getNestedValue(item, "json." + field);
			boolean truthy = isTruthy(value);

			String replacement = truthy ? processVariables(body, json, item) : "";
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private String processVariables(String template, Map<String, Object> json, Map<String, Object> item) {
		Matcher matcher = VARIABLE_PATTERN.matcher(template);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String expr = matcher.group(1).trim();

			// Check for default value: {{field|default}}
			String defaultValue = "";
			int pipeIdx = expr.indexOf('|');
			if (pipeIdx > 0) {
				defaultValue = expr.substring(pipeIdx + 1).trim();
				expr = expr.substring(0, pipeIdx).trim();
			}

			Object value = getNestedValue(item, "json." + expr);
			String replacement = value != null ? String.valueOf(value) : defaultValue;
			matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private boolean isTruthy(Object value) {
		if (value == null) return false;
		if (value instanceof Boolean) return (Boolean) value;
		if (value instanceof Number) return ((Number) value).doubleValue() != 0;
		if (value instanceof String) return !((String) value).isEmpty();
		if (value instanceof List<?>) return !((List<?>) value).isEmpty();
		if (value instanceof Map<?, ?>) return !((Map<?, ?>) value).isEmpty();
		return true;
	}
}
