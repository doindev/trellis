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
	type = "anthropic",
	displayName = "Anthropic",
	description = "Interact with the Anthropic API to create messages and list models.",
	category = "AI / Vendor Nodes",
	icon = "anthropic",
	credentials = {"anthropicApi"}
)
public class AnthropicNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.anthropic.com/v1";
	private static final String ANTHROPIC_VERSION = "2023-06-01";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("message")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Message").value("message").description("Create messages using Anthropic models").build(),
				ParameterOption.builder().name("Model").value("model").description("List available models").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Message parameters
		addMessageParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Message operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a message").build()
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

	// ========================= Message Parameters =========================

	private void addMessageParameters(List<NodeParameter> params) {
		// Model selection
		params.add(NodeParameter.builder()
			.name("model").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("claude-sonnet-4-20250514")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Claude Sonnet 4").value("claude-sonnet-4-20250514").build(),
				ParameterOption.builder().name("Claude 3.5 Haiku").value("claude-3-5-haiku-20241022").build(),
				ParameterOption.builder().name("Claude 3.5 Sonnet").value("claude-3-5-sonnet-20241022").build(),
				ParameterOption.builder().name("Claude 3 Opus").value("claude-3-opus-20240229").build(),
				ParameterOption.builder().name("Claude 3 Haiku").value("claude-3-haiku-20240307").build()
			)).build());

		// Messages (user prompt)
		params.add(NodeParameter.builder()
			.name("prompt").displayName("User Message")
			.type(ParameterType.STRING).required(true)
			.description("The user message to send to the model.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.build());

		// System prompt
		params.add(NodeParameter.builder()
			.name("systemPrompt").displayName("System Prompt")
			.type(ParameterType.STRING)
			.description("Optional system prompt to guide the model's behavior.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.build());

		// Max tokens
		params.add(NodeParameter.builder()
			.name("maxTokens").displayName("Max Tokens")
			.type(ParameterType.NUMBER).required(true).defaultValue(1024)
			.description("The maximum number of tokens to generate.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.build());

		// Temperature
		params.add(NodeParameter.builder()
			.name("temperature").displayName("Temperature")
			.type(ParameterType.NUMBER).defaultValue(1.0)
			.description("Controls randomness. Range: 0.0 to 1.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.build());

		// Stop sequences
		params.add(NodeParameter.builder()
			.name("stopSequences").displayName("Stop Sequences")
			.type(ParameterType.STRING)
			.description("Comma-separated list of sequences where the model will stop generating.")
			.placeHolder("\\n\\nHuman:,\\n\\nAssistant:")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.build());

		// Top P
		params.add(NodeParameter.builder()
			.name("topP").displayName("Top P")
			.type(ParameterType.NUMBER)
			.description("Nucleus sampling parameter. Range: 0.0 to 1.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.build());

		// Top K
		params.add(NodeParameter.builder()
			.name("topK").displayName("Top K")
			.type(ParameterType.NUMBER)
			.description("Only sample from the top K options for each token.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "message");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "message" -> executeMessage(context, headers);
				case "model" -> executeModelList(headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Anthropic API error: " + e.getMessage(), e);
		}
	}

	// ========================= Message Execute =========================

	private NodeExecutionResult executeMessage(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("model", "claude-sonnet-4-20250514");
		String prompt = context.getParameter("prompt", "");
		String systemPrompt = context.getParameter("systemPrompt", "");
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 1024);
		double temperature = toDouble(context.getParameters().get("temperature"), 1.0);
		String stopSequences = context.getParameter("stopSequences", "");
		Object topPObj = context.getParameters().get("topP");
		Object topKObj = context.getParameters().get("topK");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("max_tokens", maxTokens);
		body.put("temperature", temperature);

		// Build messages array
		List<Map<String, Object>> messages = new ArrayList<>();
		messages.add(Map.of("role", "user", "content", prompt));
		body.put("messages", messages);

		// System prompt
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			body.put("system", systemPrompt);
		}

		// Stop sequences
		if (stopSequences != null && !stopSequences.isBlank()) {
			List<String> stops = new ArrayList<>();
			for (String s : stopSequences.split(",")) {
				String trimmed = s.trim();
				if (!trimmed.isEmpty()) {
					stops.add(trimmed);
				}
			}
			if (!stops.isEmpty()) {
				body.put("stop_sequences", stops);
			}
		}

		// Top P
		if (topPObj != null) {
			double topP = toDouble(topPObj, -1);
			if (topP >= 0) {
				body.put("top_p", topP);
			}
		}

		// Top K
		if (topKObj != null) {
			int topK = toInt(topKObj, -1);
			if (topK >= 0) {
				body.put("top_k", topK);
			}
		}

		String url = BASE_URL + "/messages";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("Anthropic", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Model List Execute =========================

	private NodeExecutionResult executeModelList(Map<String, String> headers) throws Exception {
		String url = BASE_URL + "/models";
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("Anthropic", response);
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
		headers.put("anthropic-version", ANTHROPIC_VERSION);

		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		headers.put("x-api-key", apiKey);

		return headers;
	}

	private NodeExecutionResult apiError(String service, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 500) body = body.substring(0, 500) + "...";
		return NodeExecutionResult.error(service + " API error (HTTP " + response.statusCode() + "): " + body);
	}
}
