package io.cwc.nodes.impl.ai;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "ollama",
	displayName = "Ollama",
	description = "Interact with a local Ollama instance for chat, completions, embeddings, and model management.",
	category = "AI / Vendor Nodes",
	icon = "ollama",
	credentials = {"ollamaApi"}
)
public class OllamaNode extends AbstractApiNode {

	private static final String DEFAULT_BASE_URL = "http://localhost:11434";

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
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("chat")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Chat").value("chat").description("Generate chat completions").build(),
				ParameterOption.builder().name("Completion").value("completion").description("Generate text completions").build(),
				ParameterOption.builder().name("Embedding").value("embedding").description("Generate embeddings").build(),
				ParameterOption.builder().name("Model").value("model").description("Manage models").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addChatParameters(params);
		addCompletionParameters(params);
		addEmbeddingParameters(params);
		addModelParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Chat operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("generate")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"))))
			.options(List.of(
				ParameterOption.builder().name("Generate").value("generate").description("Generate a chat response").build()
			)).build());

		// Completion operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("generate")
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"))))
			.options(List.of(
				ParameterOption.builder().name("Generate").value("generate").description("Generate a text completion").build()
			)).build());

		// Embedding operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("generate")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"))))
			.options(List.of(
				ParameterOption.builder().name("Generate").value("generate").description("Generate embeddings").build()
			)).build());

		// Model operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("list")
			.displayOptions(Map.of("show", Map.of("resource", List.of("model"))))
			.options(List.of(
				ParameterOption.builder().name("List").value("list").description("List locally available models").build(),
				ParameterOption.builder().name("Pull").value("pull").description("Pull a model from the registry").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a local model").build(),
				ParameterOption.builder().name("Show").value("show").description("Show model information").build()
			)).build());
	}

	// ========================= Chat Parameters =========================

	private void addChatParameters(List<NodeParameter> params) {
		// Model name for chat
		params.add(NodeParameter.builder()
			.name("model").displayName("Model")
			.type(ParameterType.STRING).required(true).defaultValue("llama3.2")
			.placeHolder("llama3.2")
			.description("Name of the Ollama model to use.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("generate"))))
			.build());

		// Chat prompt
		params.add(NodeParameter.builder()
			.name("prompt").displayName("User Message")
			.type(ParameterType.STRING).required(true)
			.description("The user message to send.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("generate"))))
			.build());

		// System prompt for chat
		params.add(NodeParameter.builder()
			.name("systemPrompt").displayName("System Prompt")
			.type(ParameterType.STRING)
			.description("Optional system prompt.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("generate"))))
			.build());

		// Temperature for chat
		params.add(NodeParameter.builder()
			.name("temperature").displayName("Temperature")
			.type(ParameterType.NUMBER).defaultValue(0.8)
			.description("Controls randomness. Range: 0.0 to 2.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("generate"))))
			.build());

		// Keep alive
		params.add(NodeParameter.builder()
			.name("keepAlive").displayName("Keep Alive")
			.type(ParameterType.STRING).defaultValue("5m")
			.description("How long to keep the model loaded (e.g. 5m, 1h, 0 to unload immediately).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("generate"))))
			.build());
	}

	// ========================= Completion Parameters =========================

	private void addCompletionParameters(List<NodeParameter> params) {
		// Model name
		params.add(NodeParameter.builder()
			.name("completionModel").displayName("Model")
			.type(ParameterType.STRING).required(true).defaultValue("llama3.2")
			.placeHolder("llama3.2")
			.description("Name of the Ollama model to use.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("generate"))))
			.build());

		// Prompt
		params.add(NodeParameter.builder()
			.name("completionPrompt").displayName("Prompt")
			.type(ParameterType.STRING).required(true)
			.description("The prompt for text completion.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("generate"))))
			.build());

		// System prompt
		params.add(NodeParameter.builder()
			.name("completionSystemPrompt").displayName("System Prompt")
			.type(ParameterType.STRING)
			.description("Optional system prompt for completion.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("generate"))))
			.build());

		// Temperature
		params.add(NodeParameter.builder()
			.name("completionTemperature").displayName("Temperature")
			.type(ParameterType.NUMBER).defaultValue(0.8)
			.description("Controls randomness. Range: 0.0 to 2.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("generate"))))
			.build());
	}

	// ========================= Embedding Parameters =========================

	private void addEmbeddingParameters(List<NodeParameter> params) {
		// Model name for embedding
		params.add(NodeParameter.builder()
			.name("embeddingModel").displayName("Model")
			.type(ParameterType.STRING).required(true).defaultValue("nomic-embed-text")
			.placeHolder("nomic-embed-text")
			.description("Name of the embedding model.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("generate"))))
			.build());

		// Input text
		params.add(NodeParameter.builder()
			.name("embeddingInput").displayName("Input Text")
			.type(ParameterType.STRING).required(true)
			.description("The text to generate embeddings for.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("generate"))))
			.build());
	}

	// ========================= Model Parameters =========================

	private void addModelParameters(List<NodeParameter> params) {
		// Model name for pull/delete/show
		params.add(NodeParameter.builder()
			.name("modelName").displayName("Model Name")
			.type(ParameterType.STRING).required(true)
			.placeHolder("llama3.2")
			.description("The name of the model.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("model"), "operation", List.of("pull", "delete", "show"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "chat");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getOllamaBaseUrl(credentials);
			Map<String, String> headers = Map.of("Content-Type", "application/json");

			return switch (resource) {
				case "chat" -> executeChat(context, baseUrl, headers);
				case "completion" -> executeCompletion(context, baseUrl, headers);
				case "embedding" -> executeEmbedding(context, baseUrl, headers);
				case "model" -> executeModel(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Ollama API error: " + e.getMessage(), e);
		}
	}

	// ========================= Chat Execute =========================

	private NodeExecutionResult executeChat(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String model = context.getParameter("model", "llama3.2");
		String prompt = context.getParameter("prompt", "");
		String systemPrompt = context.getParameter("systemPrompt", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.8);
		String keepAlive = context.getParameter("keepAlive", "5m");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("stream", false);

		List<Map<String, Object>> messages = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			messages.add(Map.of("role", "system", "content", systemPrompt));
		}
		messages.add(Map.of("role", "user", "content", prompt));
		body.put("messages", messages);

		Map<String, Object> options = new LinkedHashMap<>();
		options.put("temperature", temperature);
		body.put("options", options);

		if (keepAlive != null && !keepAlive.isBlank()) {
			body.put("keep_alive", keepAlive);
		}

		String url = baseUrl + "/api/chat";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("Ollama", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Completion Execute =========================

	private NodeExecutionResult executeCompletion(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String model = context.getParameter("completionModel", "llama3.2");
		String prompt = context.getParameter("completionPrompt", "");
		String systemPrompt = context.getParameter("completionSystemPrompt", "");
		double temperature = toDouble(context.getParameters().get("completionTemperature"), 0.8);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("prompt", prompt);
		body.put("stream", false);

		if (systemPrompt != null && !systemPrompt.isBlank()) {
			body.put("system", systemPrompt);
		}

		Map<String, Object> options = new LinkedHashMap<>();
		options.put("temperature", temperature);
		body.put("options", options);

		String url = baseUrl + "/api/generate";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("Ollama", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Embedding Execute =========================

	private NodeExecutionResult executeEmbedding(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String model = context.getParameter("embeddingModel", "nomic-embed-text");
		String input = context.getParameter("embeddingInput", "");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("input", input);

		String url = baseUrl + "/api/embed";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("Ollama", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Model Execute =========================

	private NodeExecutionResult executeModel(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "list");

		switch (operation) {
			case "list": {
				String url = baseUrl + "/api/tags";
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError("Ollama", response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object models = parsed.get("models");
				if (models instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object item : (List<?>) models) {
						if (item instanceof Map) {
							items.add(wrapInJson(item));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "pull": {
				String modelName = context.getParameter("modelName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", modelName);
				body.put("stream", false);

				String url = baseUrl + "/api/pull";
				HttpResponse<String> response = post(url, body, headers);
				if (response.statusCode() >= 400) {
					return apiError("Ollama", response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "delete": {
				String modelName = context.getParameter("modelName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", modelName);

				String url = baseUrl + "/api/delete";
				HttpResponse<String> response = deleteWithBody(url, body, headers);
				if (response.statusCode() >= 400) {
					return apiError("Ollama", response);
				}
				if (response.body() == null || response.body().isBlank()) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "model", modelName))));
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "show": {
				String modelName = context.getParameter("modelName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", modelName);

				String url = baseUrl + "/api/show";
				HttpResponse<String> response = post(url, body, headers);
				if (response.statusCode() >= 400) {
					return apiError("Ollama", response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown model operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private String getOllamaBaseUrl(Map<String, Object> credentials) {
		if (credentials != null) {
			Object baseUrl = credentials.get("baseUrl");
			if (baseUrl != null && !String.valueOf(baseUrl).isBlank()) {
				String url = String.valueOf(baseUrl);
				// Remove trailing slash
				return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
			}
		}
		return DEFAULT_BASE_URL;
	}

	private NodeExecutionResult apiError(String service, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 500) body = body.substring(0, 500) + "...";
		return NodeExecutionResult.error(service + " API error (HTTP " + response.statusCode() + "): " + body);
	}
}
