package io.trellis.nodes.impl.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiToolNode;
import io.trellis.nodes.core.DynamicTool;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;

@Node(
		type = "mcpClientTool",
		displayName = "MCP Client Tool",
		description = "Connect to an MCP (Model Context Protocol) server and expose its tools to an AI agent",
		category = "AI / Tools",
		icon = "mcp",
		searchOnly = true
)
@Slf4j
public class McpClientToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		String sseOrStreamable = context.getParameter("sseOrStreamable", "streamableHttp");
		String url = context.getParameter("url", "");
		String authType = context.getParameter("authType", "none");
		String toolSelectionMode = context.getParameter("toolSelectionMode", "all");
		int timeout = toInt(context.getParameters().get("timeout"), 60);

		// STDIO parameters
		String command = context.getParameter("command", "");
		String args = context.getParameter("args", "");
		String envVars = context.getParameter("envVars", "");

		// Build custom headers from auth configuration
		Map<String, String> headers = buildHeaders(context, authType);

		// Create transport based on type
		McpTransport transport = createTransport(sseOrStreamable, url, command, args, envVars, headers, timeout);

		// Create MCP client and connect
		McpClient client = DefaultMcpClient.builder()
				.transport(transport)
				.toolExecutionTimeout(Duration.ofSeconds(timeout))
				.initializationTimeout(Duration.ofSeconds(timeout))
				.build();

		try {
			// List available tools from the MCP server
			List<ToolSpecification> allTools = client.listTools();

			// Apply tool filtering
			List<ToolSpecification> filteredTools = filterTools(allTools, context, toolSelectionMode);

			if (filteredTools.isEmpty()) {
				log.warn("MCP server returned no tools (or all were filtered out)");
			}

			// Convert MCP tools to DynamicTools backed by the MCP client
			// Keep a reference to the client for tool execution — the client is kept alive
			// as long as the execution context lives
			List<DynamicTool> dynamicTools = new ArrayList<>();
			for (ToolSpecification spec : filteredTools) {
				dynamicTools.add(new DynamicTool(spec,
						(ToolExecutionRequest request, Object memoryId) -> {
							try {
								var result = client.executeTool(request);
								return result.resultText() != null ? result.resultText() : result.toString();
							} catch (Exception e) {
								return "MCP tool execution failed: " + e.getMessage();
							}
						}));
			}

			log.info("MCP Client connected — {} tools available", dynamicTools.size());
			return dynamicTools;

		} catch (Exception e) {
			// Close the client on setup failure
			try {
				client.close();
			} catch (Exception closeEx) {
				log.debug("Error closing MCP client after failure", closeEx);
			}
			throw new RuntimeException("Failed to connect to MCP server: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings({ "removal", "deprecation" }) // HttpMcpTransport is deprecated but needed for legacy SSE servers
	private McpTransport createTransport(String type, String url, String command,
										 String args, String envVars,
										 Map<String, String> headers, int timeout) {
		return switch (type) {
			case "stdio" -> {
				if (command == null || command.isBlank()) {
					throw new IllegalArgumentException("Command is required for STDIO transport");
				}
				List<String> commandList = new ArrayList<>();
				commandList.add(command);
				if (args != null && !args.isBlank()) {
					// Split arguments, respecting quoted strings
					Collections.addAll(commandList, splitArgs(args));
				}

				var builder = StdioMcpTransport.builder()
						.command(commandList);

				if (envVars != null && !envVars.isBlank()) {
					builder.environment(parseEnvVars(envVars));
				}

				yield builder.build();
			}
			case "streamableHttp" -> {
				if (url == null || url.isBlank()) {
					throw new IllegalArgumentException("URL is required for Streamable HTTP transport");
				}
				var builder = StreamableHttpMcpTransport.builder()
						.url(url)
						.timeout(Duration.ofSeconds(timeout));
				if (!headers.isEmpty()) {
					builder.customHeaders(headers);
				}
				yield builder.build();
			}
			case "sse" -> {
				if (url == null || url.isBlank()) {
					throw new IllegalArgumentException("URL is required for SSE transport");
				}
				var builder = HttpMcpTransport.builder()
						.sseUrl(url)
						.timeout(Duration.ofSeconds(timeout));
				if (!headers.isEmpty()) {
					builder.customHeaders(headers);
				}
				yield builder.build();
			}
			default -> throw new IllegalArgumentException("Unknown transport type: " + type);
		};
	}

	private Map<String, String> buildHeaders(NodeExecutionContext context, String authType) {
		Map<String, String> headers = new LinkedHashMap<>();

		switch (authType) {
			case "bearerToken" -> {
				String token = context.getParameter("bearerToken", "");
				if (token != null && !token.isBlank()) {
					headers.put("Authorization", "Bearer " + token);
				}
			}
			case "headerAuth" -> {
				String headerName = context.getParameter("headerName", "");
				String headerValue = context.getParameter("headerValue", "");
				if (headerName != null && !headerName.isBlank()) {
					headers.put(headerName, headerValue != null ? headerValue : "");
				}
			}
			case "customHeaders" -> {
				String customHeaders = context.getParameter("customHeaders", "");
				if (customHeaders != null && !customHeaders.isBlank()) {
					for (String line : customHeaders.split("\n")) {
						String[] parts = line.split(":", 2);
						if (parts.length == 2) {
							headers.put(parts[0].trim(), parts[1].trim());
						}
					}
				}
			}
			default -> {
				// "none" — no auth headers
			}
		}
		return headers;
	}

	private List<ToolSpecification> filterTools(List<ToolSpecification> allTools,
												NodeExecutionContext context,
												String mode) {
		if ("all".equals(mode) || allTools.isEmpty()) {
			return allTools;
		}

		String toolNamesParam = context.getParameter("toolNames", "");
		if (toolNamesParam == null || toolNamesParam.isBlank()) {
			return allTools;
		}

		Set<String> toolNames = new HashSet<>();
		for (String name : toolNamesParam.split(",")) {
			String trimmed = name.trim();
			if (!trimmed.isEmpty()) {
				toolNames.add(trimmed);
			}
		}

		if (toolNames.isEmpty()) {
			return allTools;
		}

		return switch (mode) {
			case "include" -> allTools.stream()
					.filter(t -> toolNames.contains(t.name()))
					.toList();
			case "exclude" -> allTools.stream()
					.filter(t -> !toolNames.contains(t.name()))
					.toList();
			default -> allTools;
		};
	}

	private String[] splitArgs(String args) {
		List<String> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		char quoteChar = 0;

		for (int i = 0; i < args.length(); i++) {
			char c = args.charAt(i);
			if (inQuotes) {
				if (c == quoteChar) {
					inQuotes = false;
				} else {
					current.append(c);
				}
			} else if (c == '"' || c == '\'') {
				inQuotes = true;
				quoteChar = c;
			} else if (Character.isWhitespace(c)) {
				if (!current.isEmpty()) {
					result.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (!current.isEmpty()) {
			result.add(current.toString());
		}
		return result.toArray(new String[0]);
	}

	private Map<String, String> parseEnvVars(String envVars) {
		Map<String, String> env = new LinkedHashMap<>();
		for (String line : envVars.split("\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			int eq = trimmed.indexOf('=');
			if (eq > 0) {
				env.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
			}
		}
		return env;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("sseOrStreamable").displayName("Transport Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("streamableHttp")
						.description("How to connect to the MCP server")
						.options(List.of(
								ParameterOption.builder()
										.name("Streamable HTTP (Recommended)")
										.value("streamableHttp")
										.description("Modern HTTP transport with streaming support")
										.build(),
								ParameterOption.builder()
										.name("SSE (Legacy)")
										.value("sse")
										.description("Server-Sent Events transport (MCP spec 2024-11-05)")
										.build(),
								ParameterOption.builder()
										.name("STDIO")
										.value("stdio")
										.description("Launch a local process and communicate via stdin/stdout")
										.build()
						)).build(),

				// HTTP parameters (shown for streamableHttp and sse)
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("http://localhost:3000/mcp")
						.description("The MCP server endpoint URL")
						.displayOptions(Map.of("show", Map.of("sseOrStreamable", List.of("streamableHttp", "sse"))))
						.build(),

				// STDIO parameters
				NodeParameter.builder()
						.name("command").displayName("Command")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("npx")
						.description("The command to launch the MCP server process")
						.displayOptions(Map.of("show", Map.of("sseOrStreamable", List.of("stdio"))))
						.build(),
				NodeParameter.builder()
						.name("args").displayName("Arguments")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("-y @modelcontextprotocol/server-filesystem /tmp")
						.description("Command arguments, space-separated. Use quotes for arguments with spaces.")
						.displayOptions(Map.of("show", Map.of("sseOrStreamable", List.of("stdio"))))
						.build(),
				NodeParameter.builder()
						.name("envVars").displayName("Environment Variables")
						.type(ParameterType.STRING)
						.defaultValue("")
						.typeOptions(Map.of("rows", 3))
						.description("Environment variables for the process, one per line: KEY=value")
						.displayOptions(Map.of("show", Map.of("sseOrStreamable", List.of("stdio"))))
						.build(),

				// Authentication (shown for HTTP transports)
				NodeParameter.builder()
						.name("authType").displayName("Authentication")
						.type(ParameterType.OPTIONS)
						.defaultValue("none")
						.options(List.of(
								ParameterOption.builder().name("None").value("none").build(),
								ParameterOption.builder().name("Bearer Token").value("bearerToken").build(),
								ParameterOption.builder().name("Header Auth").value("headerAuth").build(),
								ParameterOption.builder().name("Custom Headers").value("customHeaders").build()
						))
						.displayOptions(Map.of("show", Map.of("sseOrStreamable", List.of("streamableHttp", "sse"))))
						.build(),
				NodeParameter.builder()
						.name("bearerToken").displayName("Bearer Token")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Bearer token for Authorization header")
						.displayOptions(Map.of("show", Map.of("authType", List.of("bearerToken"))))
						.build(),
				NodeParameter.builder()
						.name("headerName").displayName("Header Name")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("X-API-Key")
						.displayOptions(Map.of("show", Map.of("authType", List.of("headerAuth"))))
						.build(),
				NodeParameter.builder()
						.name("headerValue").displayName("Header Value")
						.type(ParameterType.STRING)
						.defaultValue("")
						.displayOptions(Map.of("show", Map.of("authType", List.of("headerAuth"))))
						.build(),
				NodeParameter.builder()
						.name("customHeaders").displayName("Custom Headers")
						.type(ParameterType.STRING)
						.defaultValue("")
						.typeOptions(Map.of("rows", 3))
						.description("One header per line in 'Key: Value' format")
						.displayOptions(Map.of("show", Map.of("authType", List.of("customHeaders"))))
						.build(),

				// Tool selection
				NodeParameter.builder()
						.name("toolSelectionMode").displayName("Tool Selection")
						.type(ParameterType.OPTIONS)
						.defaultValue("all")
						.description("Which tools from the MCP server to expose to the agent")
						.options(List.of(
								ParameterOption.builder()
										.name("All").value("all")
										.description("Expose all tools from the MCP server")
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
						.placeHolder("read_file, write_file, list_directory")
						.description("Comma-separated list of tool names to include or exclude")
						.displayOptions(Map.of("show", Map.of("toolSelectionMode", List.of("include", "exclude"))))
						.build(),

				// Timeout
				NodeParameter.builder()
						.name("timeout").displayName("Timeout (seconds)")
						.type(ParameterType.NUMBER)
						.defaultValue(60)
						.description("Maximum time in seconds for MCP operations (connection, tool execution)")
						.isNodeSetting(true)
						.build()
		);
	}
}
