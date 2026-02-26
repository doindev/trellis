package io.trellis.nodes.impl.ai;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Client — Model Context Protocol client node that connects to an MCP server.
 * Supports listing tools, calling tools, listing resources, and reading resources
 * via the MCP protocol over HTTP/SSE or STDIO transports.
 */
@Slf4j
@Node(
		type = "mcpClient",
		displayName = "MCP Client",
		description = "Connect to an MCP server and interact with its tools and resources",
		category = "AI / MCP",
		icon = "mcp",
		credentials = {"mcpClientApi"}
)
public class McpClientNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String serverUrl = context.getParameter("serverUrl", "");
		String transport = context.getParameter("transport", "sse");
		String operation = context.getParameter("operation", "listTools");

		if (serverUrl.isBlank()) {
			return NodeExecutionResult.error("MCP Server URL is required");
		}

		String apiKey = context.getCredentialString("apiKey", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		if (!apiKey.isBlank()) {
			headers.put("Authorization", "Bearer " + apiKey);
		}

		try {
			return switch (operation) {
				case "listTools" -> handleListTools(serverUrl, headers);
				case "callTool" -> handleCallTool(context, serverUrl, headers);
				case "listResources" -> handleListResources(serverUrl, headers);
				case "readResource" -> handleReadResource(context, serverUrl, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "MCP Client error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult handleListTools(String serverUrl, Map<String, String> headers) throws Exception {
		Map<String, Object> request = createJsonRpcRequest("tools/list", Map.of());
		HttpResponse<String> response = post(serverUrl, request, headers);
		Map<String, Object> result = parseResponse(response);

		@SuppressWarnings("unchecked")
		Map<String, Object> rpcResult = (Map<String, Object>) result.get("result");
		if (rpcResult != null) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> tools = (List<Map<String, Object>>) rpcResult.get("tools");
			if (tools != null) {
				List<Map<String, Object>> items = new ArrayList<>();
				for (Map<String, Object> tool : tools) {
					items.add(wrapInJson(tool));
				}
				return NodeExecutionResult.success(items);
			}
		}

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult handleCallTool(NodeExecutionContext context,
			String serverUrl, Map<String, String> headers) throws Exception {
		String toolName = context.getParameter("toolName", "");
		String toolArguments = context.getParameter("toolArguments", "{}");

		if (toolName.isBlank()) {
			return NodeExecutionResult.error("Tool name is required for callTool operation");
		}

		Map<String, Object> args;
		try {
			args = parseJson(toolArguments);
		} catch (Exception e) {
			args = Map.of();
		}

		Map<String, Object> params = new HashMap<>();
		params.put("name", toolName);
		params.put("arguments", args);

		Map<String, Object> request = createJsonRpcRequest("tools/call", params);
		HttpResponse<String> response = post(serverUrl, request, headers);
		Map<String, Object> result = parseResponse(response);

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult handleListResources(String serverUrl,
			Map<String, String> headers) throws Exception {
		Map<String, Object> request = createJsonRpcRequest("resources/list", Map.of());
		HttpResponse<String> response = post(serverUrl, request, headers);
		Map<String, Object> result = parseResponse(response);

		@SuppressWarnings("unchecked")
		Map<String, Object> rpcResult = (Map<String, Object>) result.get("result");
		if (rpcResult != null) {
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> resources = (List<Map<String, Object>>) rpcResult.get("resources");
			if (resources != null) {
				List<Map<String, Object>> items = new ArrayList<>();
				for (Map<String, Object> resource : resources) {
					items.add(wrapInJson(resource));
				}
				return NodeExecutionResult.success(items);
			}
		}

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult handleReadResource(NodeExecutionContext context,
			String serverUrl, Map<String, String> headers) throws Exception {
		String resourceUri = context.getParameter("resourceUri", "");

		if (resourceUri.isBlank()) {
			return NodeExecutionResult.error("Resource URI is required for readResource operation");
		}

		Map<String, Object> params = new HashMap<>();
		params.put("uri", resourceUri);

		Map<String, Object> request = createJsonRpcRequest("resources/read", params);
		HttpResponse<String> response = post(serverUrl, request, headers);
		Map<String, Object> result = parseResponse(response);

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private Map<String, Object> createJsonRpcRequest(String method, Map<String, Object> params) {
		Map<String, Object> request = new HashMap<>();
		request.put("jsonrpc", "2.0");
		request.put("id", System.currentTimeMillis());
		request.put("method", method);
		request.put("params", params);
		return request;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("serverUrl").displayName("Server URL")
						.type(ParameterType.STRING)
						.defaultValue("")
						.required(true)
						.placeHolder("http://localhost:3000/mcp")
						.description("The MCP server endpoint URL").build(),
				NodeParameter.builder()
						.name("transport").displayName("Transport")
						.type(ParameterType.OPTIONS)
						.defaultValue("sse")
						.options(List.of(
								ParameterOption.builder().name("SSE (Server-Sent Events)").value("sse").build(),
								ParameterOption.builder().name("STDIO").value("stdio").build()
						))
						.description("Transport protocol for communicating with the MCP server").build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("listTools")
						.options(List.of(
								ParameterOption.builder().name("List Tools").value("listTools")
										.description("List all tools available on the MCP server").build(),
								ParameterOption.builder().name("Call Tool").value("callTool")
										.description("Execute a specific tool on the MCP server").build(),
								ParameterOption.builder().name("List Resources").value("listResources")
										.description("List all resources available on the MCP server").build(),
								ParameterOption.builder().name("Read Resource").value("readResource")
										.description("Read a specific resource from the MCP server").build()
						)).build(),
				NodeParameter.builder()
						.name("toolName").displayName("Tool Name")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("read_file")
						.description("The name of the tool to call")
						.displayOptions(Map.of("show", Map.of("operation", List.of("callTool")))).build(),
				NodeParameter.builder()
						.name("toolArguments").displayName("Tool Arguments")
						.type(ParameterType.JSON)
						.defaultValue("{}")
						.description("JSON object with the arguments to pass to the tool")
						.displayOptions(Map.of("show", Map.of("operation", List.of("callTool")))).build(),
				NodeParameter.builder()
						.name("resourceUri").displayName("Resource URI")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("file:///path/to/resource")
						.description("The URI of the resource to read")
						.displayOptions(Map.of("show", Map.of("operation", List.of("readResource")))).build()
		);
	}
}
