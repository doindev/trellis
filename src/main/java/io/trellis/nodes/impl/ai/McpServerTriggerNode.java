package io.trellis.nodes.impl.ai;

import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "mcpTrigger",
	displayName = "MCP Server Trigger",
	description = "Implements a Model Context Protocol (MCP) server endpoint that accepts tool calls and resource requests from MCP clients.",
	category = "AI",
	icon = "mcp",
	trigger = true,
	triggerCategory = "Other"
)
public class McpServerTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Server name
		params.add(NodeParameter.builder()
			.name("serverName").displayName("Server Name")
			.type(ParameterType.STRING).required(true).defaultValue("trellis-mcp-server")
			.description("The name of this MCP server.")
			.build());

		// Server version
		params.add(NodeParameter.builder()
			.name("serverVersion").displayName("Server Version")
			.type(ParameterType.STRING).defaultValue("1.0.0")
			.description("The version of this MCP server.")
			.build());

		// Message type to handle
		params.add(NodeParameter.builder()
			.name("messageType").displayName("Message Type")
			.type(ParameterType.OPTIONS).required(true).defaultValue("toolCall")
			.description("The type of MCP message this trigger handles.")
			.options(List.of(
				ParameterOption.builder().name("Tool Call").value("toolCall").description("Handle tool call requests from MCP clients").build(),
				ParameterOption.builder().name("Resource Request").value("resourceRequest").description("Handle resource read requests from MCP clients").build(),
				ParameterOption.builder().name("All Messages").value("all").description("Handle all incoming MCP protocol messages").build()
			)).build());

		// Tools definition
		params.add(NodeParameter.builder()
			.name("tools").displayName("Tool Definitions")
			.type(ParameterType.STRING)
			.description("JSON array of tool definitions. Each tool should have 'name', 'description', and optional 'inputSchema'.")
			.typeOptions(Map.of("rows", 8))
			.placeHolder("[{\"name\": \"get_weather\", \"description\": \"Get weather for a location\", \"inputSchema\": {\"type\": \"object\", \"properties\": {\"location\": {\"type\": \"string\"}}}}]")
			.displayOptions(Map.of("show", Map.of("messageType", List.of("toolCall", "all"))))
			.build());

		// Resources definition
		params.add(NodeParameter.builder()
			.name("resources").displayName("Resource Definitions")
			.type(ParameterType.STRING)
			.description("JSON array of resource definitions. Each resource should have 'uri', 'name', and optional 'description' and 'mimeType'.")
			.typeOptions(Map.of("rows", 8))
			.placeHolder("[{\"uri\": \"file:///data/config.json\", \"name\": \"Config\", \"description\": \"Application configuration\", \"mimeType\": \"application/json\"}]")
			.displayOptions(Map.of("show", Map.of("messageType", List.of("resourceRequest", "all"))))
			.build());

		// Response timeout
		params.add(NodeParameter.builder()
			.name("timeout").displayName("Response Timeout (seconds)")
			.type(ParameterType.NUMBER).defaultValue(30)
			.description("Maximum time in seconds to wait for the workflow to produce a response.")
			.isNodeSetting(true)
			.build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String serverName = context.getParameter("serverName", "trellis-mcp-server");
		String serverVersion = context.getParameter("serverVersion", "1.0.0");
		String messageType = context.getParameter("messageType", "toolCall");
		String toolsJson = context.getParameter("tools", "");
		String resourcesJson = context.getParameter("resources", "");

		try {
			// Get the incoming trigger data (the MCP protocol message)
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData == null || inputData.isEmpty()) {
				// No incoming data yet; return server capability info as initialization response
				Map<String, Object> serverInfo = buildServerInfo(serverName, serverVersion, messageType, toolsJson, resourcesJson);
				return NodeExecutionResult.success(List.of(createTriggerItem(serverInfo)));
			}

			// Process the incoming MCP message
			Map<String, Object> incomingMessage = inputData.get(0);
			Map<String, Object> messageData = unwrapJson(incomingMessage);

			String method = String.valueOf(messageData.getOrDefault("method", ""));

			return switch (method) {
				case "initialize" -> handleInitialize(serverName, serverVersion, messageType, toolsJson, resourcesJson);
				case "tools/list" -> handleToolsList(toolsJson);
				case "tools/call" -> handleToolCall(messageData);
				case "resources/list" -> handleResourcesList(resourcesJson);
				case "resources/read" -> handleResourceRead(messageData);
				default -> handleGenericMessage(messageData, serverName);
			};
		} catch (Exception e) {
			log.error("MCP Server Trigger error: {}", e.getMessage(), e);
			Map<String, Object> errorResponse = new LinkedHashMap<>();
			errorResponse.put("jsonrpc", "2.0");
			errorResponse.put("error", Map.of(
				"code", -32603,
				"message", "Internal error: " + e.getMessage()
			));
			return NodeExecutionResult.success(List.of(createTriggerItem(errorResponse)));
		}
	}

	// ========================= MCP Message Handlers =========================

	private NodeExecutionResult handleInitialize(String serverName, String serverVersion,
			String messageType, String toolsJson, String resourcesJson) {
		Map<String, Object> serverInfo = buildServerInfo(serverName, serverVersion, messageType, toolsJson, resourcesJson);

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("jsonrpc", "2.0");
		response.put("result", serverInfo);

		return NodeExecutionResult.success(List.of(createTriggerItem(response)));
	}

	private NodeExecutionResult handleToolsList(String toolsJson) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("jsonrpc", "2.0");

		List<Object> tools = parseJsonArraySafe(toolsJson);
		response.put("result", Map.of("tools", tools));

		return NodeExecutionResult.success(List.of(createTriggerItem(response)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult handleToolCall(Map<String, Object> messageData) {
		Map<String, Object> params = messageData.get("params") instanceof Map
			? (Map<String, Object>) messageData.get("params")
			: Map.of();

		String toolName = String.valueOf(params.getOrDefault("name", ""));
		Object arguments = params.getOrDefault("arguments", Map.of());

		// Pass through the tool call to the workflow for processing
		Map<String, Object> triggerData = new LinkedHashMap<>();
		triggerData.put("mcpMessageType", "toolCall");
		triggerData.put("toolName", toolName);
		triggerData.put("arguments", arguments);
		triggerData.put("messageId", messageData.getOrDefault("id", ""));

		return NodeExecutionResult.success(List.of(createTriggerItem(triggerData)));
	}

	private NodeExecutionResult handleResourcesList(String resourcesJson) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("jsonrpc", "2.0");

		List<Object> resources = parseJsonArraySafe(resourcesJson);
		response.put("result", Map.of("resources", resources));

		return NodeExecutionResult.success(List.of(createTriggerItem(response)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult handleResourceRead(Map<String, Object> messageData) {
		Map<String, Object> params = messageData.get("params") instanceof Map
			? (Map<String, Object>) messageData.get("params")
			: Map.of();

		String uri = String.valueOf(params.getOrDefault("uri", ""));

		// Pass through the resource request to the workflow for processing
		Map<String, Object> triggerData = new LinkedHashMap<>();
		triggerData.put("mcpMessageType", "resourceRead");
		triggerData.put("resourceUri", uri);
		triggerData.put("messageId", messageData.getOrDefault("id", ""));

		return NodeExecutionResult.success(List.of(createTriggerItem(triggerData)));
	}

	private NodeExecutionResult handleGenericMessage(Map<String, Object> messageData, String serverName) {
		Map<String, Object> triggerData = new LinkedHashMap<>();
		triggerData.put("mcpMessageType", "generic");
		triggerData.put("serverName", serverName);
		triggerData.put("rawMessage", messageData);
		triggerData.put("messageId", messageData.getOrDefault("id", ""));

		return NodeExecutionResult.success(List.of(createTriggerItem(triggerData)));
	}

	// ========================= Helpers =========================

	private Map<String, Object> buildServerInfo(String serverName, String serverVersion,
			String messageType, String toolsJson, String resourcesJson) {
		Map<String, Object> serverInfo = new LinkedHashMap<>();
		serverInfo.put("protocolVersion", "2024-11-05");
		serverInfo.put("serverInfo", Map.of("name", serverName, "version", serverVersion));

		// Build capabilities based on what's configured
		Map<String, Object> capabilities = new LinkedHashMap<>();
		if ("toolCall".equals(messageType) || "all".equals(messageType)) {
			List<Object> tools = parseJsonArraySafe(toolsJson);
			capabilities.put("tools", Map.of("listChanged", true));
			serverInfo.put("toolDefinitions", tools);
		}
		if ("resourceRequest".equals(messageType) || "all".equals(messageType)) {
			List<Object> resources = parseJsonArraySafe(resourcesJson);
			capabilities.put("resources", Map.of("subscribe", false, "listChanged", true));
			serverInfo.put("resourceDefinitions", resources);
		}
		serverInfo.put("capabilities", capabilities);

		return serverInfo;
	}

	private List<Object> parseJsonArraySafe(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			String trimmed = json.trim();
			if (trimmed.startsWith("[")) {
				com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
				return mapper.readValue(trimmed, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {});
			}
			return List.of();
		} catch (Exception e) {
			log.warn("Failed to parse JSON array: {}", e.getMessage());
			return List.of();
		}
	}
}
