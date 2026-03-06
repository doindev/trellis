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
	type = "mistralAi",
	displayName = "Mistral AI",
	description = "Interact with the Mistral AI API for chat completions, embeddings, and model listing.",
	category = "Standalone AI Services",
	icon = "mistral",
	credentials = {"mistralAiApi"}
)
public class MistralAiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.mistral.ai/v1";

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
				ParameterOption.builder().name("Chat").value("chat").description("Create chat completions").build(),
				ParameterOption.builder().name("Embedding").value("embedding").description("Create embeddings").build(),
				ParameterOption.builder().name("Model").value("model").description("List available models").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addChatParameters(params);
		addEmbeddingParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Chat operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a chat completion").build()
			)).build());

		// Embedding operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an embedding").build()
			)).build());

		// Model operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("list")
			.displayOptions(Map.of("show", Map.of("resource", List.of("model"))))
			.options(List.of(
				ParameterOption.builder().name("List").value("list").description("List available models").build()
			)).build());
	}

	// ========================= Chat Parameters =========================

	private void addChatParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("model").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("mistral-large-latest")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Mistral Large").value("mistral-large-latest").build(),
				ParameterOption.builder().name("Mistral Small").value("mistral-small-latest").build(),
				ParameterOption.builder().name("Open Mistral Nemo").value("open-mistral-nemo").build(),
				ParameterOption.builder().name("Codestral").value("codestral-latest").build(),
				ParameterOption.builder().name("Mistral Medium").value("mistral-medium-latest").build(),
				ParameterOption.builder().name("Pixtral Large").value("pixtral-large-latest").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("prompt").displayName("User Message")
			.type(ParameterType.STRING).required(true)
			.description("The user message to send.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("systemPrompt").displayName("System Prompt")
			.type(ParameterType.STRING)
			.description("Optional system prompt to guide the model's behavior.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("temperature").displayName("Temperature")
			.type(ParameterType.NUMBER).defaultValue(0.7)
			.description("Controls randomness. Range: 0.0 to 1.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("maxTokens").displayName("Max Tokens")
			.type(ParameterType.NUMBER).defaultValue(0)
			.description("Maximum tokens to generate. 0 for model default.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("topP").displayName("Top P")
			.type(ParameterType.NUMBER).defaultValue(1.0)
			.description("Nucleus sampling parameter.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("safePrompt").displayName("Safe Prompt")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to inject a safety prompt before all conversations.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("randomSeed").displayName("Random Seed")
			.type(ParameterType.NUMBER)
			.description("Set a random seed for reproducible outputs.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());
	}

	// ========================= Embedding Parameters =========================

	private void addEmbeddingParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("embeddingModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("mistral-embed")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Mistral Embed").value("mistral-embed").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("embeddingInput").displayName("Input Text")
			.type(ParameterType.STRING).required(true)
			.description("The text to generate embeddings for.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("embeddingEncodingFormat").displayName("Encoding Format")
			.type(ParameterType.OPTIONS).defaultValue("float")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Float").value("float").build()
			)).build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "chat");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "chat" -> executeChat(context, headers);
				case "embedding" -> executeEmbedding(context, headers);
				case "model" -> executeModelList(headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Mistral AI API error: " + e.getMessage(), e);
		}
	}

	// ========================= Chat Execute =========================

	private NodeExecutionResult executeChat(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("model", "mistral-large-latest");
		String prompt = context.getParameter("prompt", "");
		String systemPrompt = context.getParameter("systemPrompt", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);
		double topP = toDouble(context.getParameters().get("topP"), 1.0);
		boolean safePrompt = toBoolean(context.getParameters().get("safePrompt"), false);
		Object randomSeedObj = context.getParameters().get("randomSeed");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("temperature", temperature);
		body.put("top_p", topP);
		body.put("safe_prompt", safePrompt);

		List<Map<String, Object>> messages = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			messages.add(Map.of("role", "system", "content", systemPrompt));
		}
		messages.add(Map.of("role", "user", "content", prompt));
		body.put("messages", messages);

		if (maxTokens > 0) {
			body.put("max_tokens", maxTokens);
		}
		if (randomSeedObj != null) {
			int seed = toInt(randomSeedObj, -1);
			if (seed >= 0) {
				body.put("random_seed", seed);
			}
		}

		String url = BASE_URL + "/chat/completions";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("Mistral AI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Embedding Execute =========================

	private NodeExecutionResult executeEmbedding(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("embeddingModel", "mistral-embed");
		String input = context.getParameter("embeddingInput", "");
		String encodingFormat = context.getParameter("embeddingEncodingFormat", "float");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("input", List.of(input));
		body.put("encoding_format", encodingFormat);

		String url = BASE_URL + "/embeddings";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("Mistral AI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Model List =========================

	private NodeExecutionResult executeModelList(Map<String, String> headers) throws Exception {
		String url = BASE_URL + "/models";
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("Mistral AI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get("data");
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		headers.put("Authorization", "Bearer " + apiKey);
		return headers;
	}

	private NodeExecutionResult apiError(String service, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 500) body = body.substring(0, 500) + "...";
		return NodeExecutionResult.error(service + " API error (HTTP " + response.statusCode() + "): " + body);
	}
}
