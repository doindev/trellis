package io.cwc.nodes.impl.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiToolNode;
import io.cwc.nodes.core.DynamicTool;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import io.cwc.service.McpSystemToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

@Node(
		type = "cwcPlatformTool",
		displayName = "CWC Platform Tool",
		description = "Gives an AI agent direct access to CWC platform capabilities — create/update/list workflows, agents, projects, execute workflows, inspect node types, and more. No network round-trip required.",
		category = "AI / Tools",
		icon = "cwc",
		searchOnly = true
)
@Slf4j
public class CwcPlatformToolNode extends AbstractAiToolNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	// Tools that require a browser session — not usable from within a workflow execution
	private static final Set<String> EXCLUDED_TOOLS = Set.of(
			"cwc_browser_control",
			"cwc_push_to_canvas",
			"cwc_list_browser_sessions"
	);

	@Autowired
	private McpSystemToolService mcpSystemToolService;

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		String toolSelectionMode = context.getParameter("toolSelectionMode", "all");

		// Get all tool definitions from the MCP system tool service
		List<Map<String, Object>> toolDefs = mcpSystemToolService.getSystemToolDefinitions();

		// Filter out browser-session tools and apply user selection
		List<Map<String, Object>> filteredDefs = filterToolDefs(toolDefs, context, toolSelectionMode);

		List<DynamicTool> dynamicTools = new ArrayList<>();
		for (Map<String, Object> toolDef : filteredDefs) {
			String name = (String) toolDef.get("name");
			String description = (String) toolDef.get("description");

			@SuppressWarnings("unchecked")
			Map<String, Object> inputSchema = (Map<String, Object>) toolDef.get("inputSchema");

			ToolSpecification spec = ToolSpecification.builder()
					.name(name)
					.description(description)
					.parameters(convertToJsonObjectSchema(inputSchema))
					.build();

			dynamicTools.add(new DynamicTool(spec,
					(ToolExecutionRequest request, Object memoryId) -> {
						try {
							Map<String, Object> args = parseArguments(request.arguments());
							return mcpSystemToolService.executeTool(name, args);
						} catch (Exception e) {
							return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
						}
					}));
		}

		log.info("CWC Platform Tool initialized — {} tools available", dynamicTools.size());
		return dynamicTools;
	}

	private List<Map<String, Object>> filterToolDefs(List<Map<String, Object>> allDefs,
													 NodeExecutionContext context,
													 String mode) {
		// Always exclude browser-session tools
		List<Map<String, Object>> defs = allDefs.stream()
				.filter(d -> !EXCLUDED_TOOLS.contains(d.get("name")))
				.toList();

		if ("all".equals(mode) || defs.isEmpty()) {
			return defs;
		}

		String toolNamesParam = context.getParameter("toolNames", "");
		if (toolNamesParam == null || toolNamesParam.isBlank()) {
			return defs;
		}

		Set<String> toolNames = new HashSet<>();
		for (String name : toolNamesParam.split(",")) {
			String trimmed = name.trim();
			if (!trimmed.isEmpty()) {
				toolNames.add(trimmed);
			}
		}

		if (toolNames.isEmpty()) {
			return defs;
		}

		return switch (mode) {
			case "include" -> defs.stream()
					.filter(d -> toolNames.contains(d.get("name")))
					.toList();
			case "exclude" -> defs.stream()
					.filter(d -> !toolNames.contains(d.get("name")))
					.toList();
			default -> defs;
		};
	}

	@SuppressWarnings("unchecked")
	private JsonObjectSchema convertToJsonObjectSchema(Map<String, Object> inputSchema) {
		if (inputSchema == null) {
			return JsonObjectSchema.builder().build();
		}

		var builder = JsonObjectSchema.builder();

		Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
		if (properties != null) {
			for (Map.Entry<String, Object> entry : properties.entrySet()) {
				String propName = entry.getKey();
				Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
				String propType = (String) propDef.get("type");
				String propDesc = (String) propDef.get("description");

				JsonSchemaElement element = switch (propType != null ? propType : "string") {
					case "integer" -> JsonIntegerSchema.builder()
							.description(propDesc)
							.build();
					case "array" -> JsonArraySchema.builder()
							.description(propDesc)
							.build();
					case "object" -> JsonObjectSchema.builder()
							.description(propDesc)
							.build();
					default -> JsonStringSchema.builder()
							.description(propDesc)
							.build();
				};
				builder.addProperty(propName, element);
			}
		}

		List<String> required = (List<String>) inputSchema.get("required");
		if (required != null && !required.isEmpty()) {
			builder.required(required);
		}

		return builder.build();
	}

	private Map<String, Object> parseArguments(String json) {
		if (json == null || json.isBlank()) {
			return Map.of();
		}
		try {
			return MAPPER.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			log.warn("Failed to parse tool arguments: {}", e.getMessage());
			return Map.of();
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("toolSelectionMode").displayName("Tool Selection")
						.type(ParameterType.OPTIONS)
						.defaultValue("all")
						.description("Which CWC platform tools to expose to the agent")
						.options(List.of(
								ParameterOption.builder()
										.name("All").value("all")
										.description("Expose all available platform tools")
										.build(),
								ParameterOption.builder()
										.name("Include Specific").value("include")
										.description("Only include the specified tools")
										.build(),
								ParameterOption.builder()
										.name("Exclude Specific").value("exclude")
										.description("Expose all tools except the specified ones")
										.build()
						)).build(),
				NodeParameter.builder()
						.name("toolNames").displayName("Tool Names")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("cwc_create_workflow, cwc_list_workflows, cwc_execute_workflow")
						.description("Comma-separated list of tool names to include or exclude")
						.displayOptions(Map.of("show", Map.of("toolSelectionMode", List.of("include", "exclude"))))
						.build()
		);
	}
}
