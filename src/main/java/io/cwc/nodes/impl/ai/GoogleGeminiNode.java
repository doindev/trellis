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
	type = "googleGemini",
	displayName = "Google Gemini",
	description = "Interact with the Google Gemini API to generate content and list models.",
	category = "AI / Vendor Nodes",
	icon = "googleGemini",
	credentials = {"googleGeminiApi"}
)
public class GoogleGeminiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("content")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Content").value("content").description("Generate content using Gemini models").build(),
				ParameterOption.builder().name("Model").value("model").description("List available models").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Content parameters
		addContentParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Content operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("generateContent")
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"))))
			.options(List.of(
				ParameterOption.builder().name("Generate Content").value("generateContent").description("Generate content from a prompt").build()
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

	// ========================= Content Parameters =========================

	private void addContentParameters(List<NodeParameter> params) {
		// Model selection
		params.add(NodeParameter.builder()
			.name("model").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("gemini-2.0-flash")
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.options(List.of(
				ParameterOption.builder().name("Gemini 2.0 Flash").value("gemini-2.0-flash").build(),
				ParameterOption.builder().name("Gemini 1.5 Pro").value("gemini-1.5-pro").build(),
				ParameterOption.builder().name("Gemini 1.5 Flash").value("gemini-1.5-flash").build(),
				ParameterOption.builder().name("Gemini 1.5 Flash 8B").value("gemini-1.5-flash-8b").build(),
				ParameterOption.builder().name("Gemini 2.0 Flash Lite").value("gemini-2.0-flash-lite").build()
			)).build());

		// Prompt
		params.add(NodeParameter.builder()
			.name("prompt").displayName("Prompt")
			.type(ParameterType.STRING).required(true)
			.description("The text prompt to send to the model.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.build());

		// System instruction
		params.add(NodeParameter.builder()
			.name("systemInstruction").displayName("System Instruction")
			.type(ParameterType.STRING)
			.description("Optional system instruction to guide the model's behavior.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.build());

		// Temperature
		params.add(NodeParameter.builder()
			.name("temperature").displayName("Temperature")
			.type(ParameterType.NUMBER).defaultValue(1.0)
			.description("Controls randomness. Range: 0.0 to 2.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.build());

		// Max output tokens
		params.add(NodeParameter.builder()
			.name("maxOutputTokens").displayName("Max Output Tokens")
			.type(ParameterType.NUMBER).defaultValue(2048)
			.description("Maximum number of tokens in the response.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.build());

		// Top P
		params.add(NodeParameter.builder()
			.name("topP").displayName("Top P")
			.type(ParameterType.NUMBER).defaultValue(0.95)
			.description("Nucleus sampling parameter. Range: 0.0 to 1.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.build());

		// Top K
		params.add(NodeParameter.builder()
			.name("topK").displayName("Top K")
			.type(ParameterType.NUMBER).defaultValue(40)
			.description("Only sample from the top K options for each token.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.build());

		// Stop sequences
		params.add(NodeParameter.builder()
			.name("stopSequences").displayName("Stop Sequences")
			.type(ParameterType.STRING)
			.description("Comma-separated list of sequences where the model will stop generating.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("content"), "operation", List.of("generateContent"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "content");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));

			return switch (resource) {
				case "content" -> executeGenerateContent(context, apiKey);
				case "model" -> executeModelList(apiKey);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Gemini API error: " + e.getMessage(), e);
		}
	}

	// ========================= Generate Content =========================

	private NodeExecutionResult executeGenerateContent(NodeExecutionContext context, String apiKey) throws Exception {
		String model = context.getParameter("model", "gemini-2.0-flash");
		String prompt = context.getParameter("prompt", "");
		String systemInstruction = context.getParameter("systemInstruction", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 1.0);
		int maxOutputTokens = toInt(context.getParameters().get("maxOutputTokens"), 2048);
		double topP = toDouble(context.getParameters().get("topP"), 0.95);
		int topK = toInt(context.getParameters().get("topK"), 40);
		String stopSequences = context.getParameter("stopSequences", "");

		Map<String, Object> body = new LinkedHashMap<>();

		// Build contents array
		List<Map<String, Object>> contents = new ArrayList<>();
		Map<String, Object> userContent = new LinkedHashMap<>();
		userContent.put("role", "user");
		userContent.put("parts", List.of(Map.of("text", prompt)));
		contents.add(userContent);
		body.put("contents", contents);

		// System instruction
		if (systemInstruction != null && !systemInstruction.isBlank()) {
			body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
		}

		// Generation config
		Map<String, Object> generationConfig = new LinkedHashMap<>();
		generationConfig.put("temperature", temperature);
		generationConfig.put("maxOutputTokens", maxOutputTokens);
		generationConfig.put("topP", topP);
		generationConfig.put("topK", topK);

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
				generationConfig.put("stopSequences", stops);
			}
		}

		body.put("generationConfig", generationConfig);

		String url = BASE_URL + "/models/" + encode(model) + ":generateContent?key=" + encode(apiKey);
		Map<String, String> headers = Map.of("Content-Type", "application/json");

		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("Google Gemini", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Model List =========================

	private NodeExecutionResult executeModelList(String apiKey) throws Exception {
		String url = BASE_URL + "/models?key=" + encode(apiKey);
		Map<String, String> headers = Map.of("Content-Type", "application/json");

		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("Google Gemini", response);
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

	// ========================= Helpers =========================

	private NodeExecutionResult apiError(String service, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 500) body = body.substring(0, 500) + "...";
		return NodeExecutionResult.error(service + " API error (HTTP " + response.statusCode() + "): " + body);
	}
}
