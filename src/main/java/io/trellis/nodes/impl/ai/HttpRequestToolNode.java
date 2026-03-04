package io.trellis.nodes.impl.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiToolNode;
import io.trellis.nodes.core.DynamicTool;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.core.NodeParameter.ParameterOption;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Node(
		type = "httpRequestTool",
		displayName = "HTTP Request Tool",
		description = "Tool that makes HTTP requests to a specified URL",
		category = "AI / Tools",
		icon = "globe",
		searchOnly = true
)
public class HttpRequestToolNode extends AbstractAiToolNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String toolName = context.getParameter("toolName", "http_request");
		String toolDescription = context.getParameter("toolDescription",
				"Make an HTTP request to a URL and return the response");
		String method = context.getParameter("method", "GET");
		String url = context.getParameter("url", "");
		String headers = context.getParameter("headers", "");

		ToolSpecification spec = ToolSpecification.builder()
				.name(toolName)
				.description(toolDescription)
				.parameters(JsonObjectSchema.builder()
						.addStringProperty("url", "The URL to send the request to")
						.required("url")
						.build())
				.build();

		ToolExecutor executor = (ToolExecutionRequest request, Object memoryId) -> {
			try {
				String argsJson = request.arguments();
				// Parse the url argument from the JSON arguments
				String targetUrl = extractJsonString(argsJson, "url");
				if (targetUrl == null || targetUrl.isBlank()) {
					targetUrl = url;
				}

				HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
						.uri(URI.create(targetUrl));

				switch (method.toUpperCase()) {
					case "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
					case "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.noBody());
					case "DELETE" -> requestBuilder.DELETE();
					default -> requestBuilder.GET();
				}

				if (headers != null && !headers.isBlank()) {
					for (String header : headers.split("\n")) {
						String[] parts = header.split(":", 2);
						if (parts.length == 2) {
							requestBuilder.header(parts[0].trim(), parts[1].trim());
						}
					}
				}

				HttpClient client = HttpClient.newHttpClient();
				HttpResponse<String> response = client.send(requestBuilder.build(),
						HttpResponse.BodyHandlers.ofString());
				return response.body();
			} catch (Exception e) {
				return "HTTP request failed: " + e.getMessage();
			}
		};

		return new DynamicTool(spec, executor);
	}

	private static String extractJsonString(String json, String key) {
		if (json == null) return null;
		// Simple JSON string extraction — handles {"url":"value"}
		String search = "\"" + key + "\"";
		int idx = json.indexOf(search);
		if (idx < 0) return null;
		idx = json.indexOf(':', idx + search.length());
		if (idx < 0) return null;
		idx++;
		while (idx < json.length() && json.charAt(idx) == ' ') idx++;
		if (idx >= json.length() || json.charAt(idx) != '"') return null;
		idx++;
		int end = json.indexOf('"', idx);
		if (end < 0) return null;
		return json.substring(idx, end);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("toolName").displayName("Tool Name")
						.type(ParameterType.STRING)
						.defaultValue("http_request")
						.description("Name the AI agent will use to reference this tool")
						.build(),
				NodeParameter.builder()
						.name("toolDescription").displayName("Tool Description")
						.type(ParameterType.STRING)
						.defaultValue("Make an HTTP request to a URL and return the response")
						.typeOptions(java.util.Map.of("rows", 3))
						.build(),
				NodeParameter.builder()
						.name("method").displayName("Method")
						.type(ParameterType.OPTIONS)
						.defaultValue("GET")
						.options(List.of(
								ParameterOption.builder().name("GET").value("GET").build(),
								ParameterOption.builder().name("POST").value("POST").build(),
								ParameterOption.builder().name("PUT").value("PUT").build(),
								ParameterOption.builder().name("DELETE").value("DELETE").build()
						)).build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Default URL for the request")
						.build(),
				NodeParameter.builder()
						.name("headers").displayName("Headers")
						.type(ParameterType.STRING)
						.defaultValue("")
						.typeOptions(java.util.Map.of("rows", 3))
						.description("One header per line in 'Key: Value' format")
						.build()
		);
	}
}
