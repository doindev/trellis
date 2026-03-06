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
	type = "openAi-langchain",
	displayName = "OpenAI",
	description = "Comprehensive OpenAI node for chat, completions, embeddings, image generation, audio, and models.",
	category = "AI / Vendor Nodes",
	icon = "openai",
	credentials = {"openAiApi"}
)
public class OpenAiLangchainNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.openai.com/v1";

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
				ParameterOption.builder().name("Completion").value("completion").description("Create text completions").build(),
				ParameterOption.builder().name("Embedding").value("embedding").description("Create embeddings").build(),
				ParameterOption.builder().name("Image").value("image").description("Generate images").build(),
				ParameterOption.builder().name("Audio").value("audio").description("Transcribe or translate audio").build(),
				ParameterOption.builder().name("Model").value("model").description("List available models").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addChatParameters(params);
		addCompletionParameters(params);
		addEmbeddingParameters(params);
		addImageParameters(params);
		addAudioParameters(params);

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

		// Completion operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a text completion").build()
			)).build());

		// Embedding operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an embedding").build()
			)).build());

		// Image operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("generate")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"))))
			.options(List.of(
				ParameterOption.builder().name("Generate").value("generate").description("Generate an image from a prompt").build()
			)).build());

		// Audio operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("transcribe")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"))))
			.options(List.of(
				ParameterOption.builder().name("Transcribe").value("transcribe").description("Transcribe audio to text").build(),
				ParameterOption.builder().name("Translate").value("translate").description("Translate audio to English text").build()
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
			.name("chatModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("gpt-4o")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("GPT-4o").value("gpt-4o").build(),
				ParameterOption.builder().name("GPT-4o Mini").value("gpt-4o-mini").build(),
				ParameterOption.builder().name("o3-mini").value("o3-mini").build(),
				ParameterOption.builder().name("GPT-4 Turbo").value("gpt-4-turbo").build(),
				ParameterOption.builder().name("GPT-4").value("gpt-4").build(),
				ParameterOption.builder().name("GPT-3.5 Turbo").value("gpt-3.5-turbo").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("chatPrompt").displayName("User Message")
			.type(ParameterType.STRING).required(true)
			.description("The user message to send.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("chatSystemPrompt").displayName("System Prompt")
			.type(ParameterType.STRING)
			.description("Optional system prompt.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("chatTemperature").displayName("Temperature")
			.type(ParameterType.NUMBER).defaultValue(0.7)
			.description("Controls randomness. Range: 0.0 to 2.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("chatMaxTokens").displayName("Max Tokens")
			.type(ParameterType.NUMBER).defaultValue(0)
			.description("Maximum tokens to generate. 0 for model default.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("chatTopP").displayName("Top P")
			.type(ParameterType.NUMBER)
			.description("Nucleus sampling parameter.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("chatFrequencyPenalty").displayName("Frequency Penalty")
			.type(ParameterType.NUMBER).defaultValue(0)
			.description("Penalize tokens based on frequency. Range: -2.0 to 2.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("chatPresencePenalty").displayName("Presence Penalty")
			.type(ParameterType.NUMBER).defaultValue(0)
			.description("Penalize tokens based on presence. Range: -2.0 to 2.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.build());
	}

	// ========================= Completion Parameters =========================

	private void addCompletionParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("completionModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("gpt-3.5-turbo-instruct")
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("GPT-3.5 Turbo Instruct").value("gpt-3.5-turbo-instruct").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("completionPrompt").displayName("Prompt")
			.type(ParameterType.STRING).required(true)
			.description("The prompt for text completion.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("completionMaxTokens").displayName("Max Tokens")
			.type(ParameterType.NUMBER).defaultValue(256)
			.description("Maximum tokens to generate.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("completionTemperature").displayName("Temperature")
			.type(ParameterType.NUMBER).defaultValue(0.7)
			.description("Controls randomness.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("completion"), "operation", List.of("create"))))
			.build());
	}

	// ========================= Embedding Parameters =========================

	private void addEmbeddingParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("embeddingModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("text-embedding-3-small")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("text-embedding-3-small").value("text-embedding-3-small").build(),
				ParameterOption.builder().name("text-embedding-3-large").value("text-embedding-3-large").build(),
				ParameterOption.builder().name("text-embedding-ada-002").value("text-embedding-ada-002").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("embeddingInput").displayName("Input Text")
			.type(ParameterType.STRING).required(true)
			.description("The text to generate embeddings for.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("create"))))
			.build());
	}

	// ========================= Image Parameters =========================

	private void addImageParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("imageModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("dall-e-3")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate"))))
			.options(List.of(
				ParameterOption.builder().name("DALL-E 3").value("dall-e-3").build(),
				ParameterOption.builder().name("DALL-E 2").value("dall-e-2").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("imagePrompt").displayName("Prompt")
			.type(ParameterType.STRING).required(true)
			.description("A text description of the desired image.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate"))))
			.build());

		params.add(NodeParameter.builder()
			.name("imageSize").displayName("Size")
			.type(ParameterType.OPTIONS).defaultValue("1024x1024")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate"))))
			.options(List.of(
				ParameterOption.builder().name("1024x1024").value("1024x1024").build(),
				ParameterOption.builder().name("1792x1024").value("1792x1024").build(),
				ParameterOption.builder().name("1024x1792").value("1024x1792").build(),
				ParameterOption.builder().name("512x512").value("512x512").build(),
				ParameterOption.builder().name("256x256").value("256x256").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("imageQuality").displayName("Quality")
			.type(ParameterType.OPTIONS).defaultValue("standard")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate"))))
			.options(List.of(
				ParameterOption.builder().name("Standard").value("standard").build(),
				ParameterOption.builder().name("HD").value("hd").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("imageN").displayName("Number of Images")
			.type(ParameterType.NUMBER).defaultValue(1)
			.description("Number of images to generate (1-10).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate"))))
			.build());
	}

	// ========================= Audio Parameters =========================

	private void addAudioParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("audioModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("whisper-1")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("transcribe", "translate"))))
			.options(List.of(
				ParameterOption.builder().name("Whisper 1").value("whisper-1").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("audioFileUrl").displayName("Audio File URL")
			.type(ParameterType.STRING).required(true)
			.description("URL of the audio file to process.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("transcribe", "translate"))))
			.build());

		params.add(NodeParameter.builder()
			.name("audioLanguage").displayName("Language")
			.type(ParameterType.STRING)
			.description("The language of the audio in ISO-639-1 format (e.g. 'en').")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("transcribe"))))
			.build());
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
				case "completion" -> executeCompletion(context, headers);
				case "embedding" -> executeEmbedding(context, headers);
				case "image" -> executeImageGenerate(context, headers);
				case "audio" -> executeAudio(context, headers);
				case "model" -> executeModelList(headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "OpenAI API error: " + e.getMessage(), e);
		}
	}

	// ========================= Chat Execute =========================

	private NodeExecutionResult executeChat(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("chatModel", "gpt-4o");
		String prompt = context.getParameter("chatPrompt", "");
		String systemPrompt = context.getParameter("chatSystemPrompt", "");
		double temperature = toDouble(context.getParameters().get("chatTemperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("chatMaxTokens"), 0);
		double frequencyPenalty = toDouble(context.getParameters().get("chatFrequencyPenalty"), 0);
		double presencePenalty = toDouble(context.getParameters().get("chatPresencePenalty"), 0);
		Object topPObj = context.getParameters().get("chatTopP");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("temperature", temperature);
		body.put("frequency_penalty", frequencyPenalty);
		body.put("presence_penalty", presencePenalty);

		List<Map<String, Object>> messages = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			messages.add(Map.of("role", "system", "content", systemPrompt));
		}
		messages.add(Map.of("role", "user", "content", prompt));
		body.put("messages", messages);

		if (maxTokens > 0) {
			body.put("max_tokens", maxTokens);
		}
		if (topPObj != null) {
			double topP = toDouble(topPObj, -1);
			if (topP >= 0) {
				body.put("top_p", topP);
			}
		}

		String url = BASE_URL + "/chat/completions";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Completion Execute =========================

	private NodeExecutionResult executeCompletion(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("completionModel", "gpt-3.5-turbo-instruct");
		String prompt = context.getParameter("completionPrompt", "");
		int maxTokens = toInt(context.getParameters().get("completionMaxTokens"), 256);
		double temperature = toDouble(context.getParameters().get("completionTemperature"), 0.7);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("prompt", prompt);
		body.put("max_tokens", maxTokens);
		body.put("temperature", temperature);

		String url = BASE_URL + "/completions";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Embedding Execute =========================

	private NodeExecutionResult executeEmbedding(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("embeddingModel", "text-embedding-3-small");
		String input = context.getParameter("embeddingInput", "");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("input", input);

		String url = BASE_URL + "/embeddings";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Image Generate =========================

	private NodeExecutionResult executeImageGenerate(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("imageModel", "dall-e-3");
		String prompt = context.getParameter("imagePrompt", "");
		String size = context.getParameter("imageSize", "1024x1024");
		String quality = context.getParameter("imageQuality", "standard");
		int n = toInt(context.getParameters().get("imageN"), 1);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("prompt", prompt);
		body.put("size", size);
		body.put("quality", quality);
		body.put("n", n);

		String url = BASE_URL + "/images/generations";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
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

	// ========================= Audio Execute =========================

	private NodeExecutionResult executeAudio(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "transcribe");
		String audioFileUrl = context.getParameter("audioFileUrl", "");

		// For audio, we send the URL reference as a JSON body
		// The actual multipart upload requires binary handling; here we provide URL-based approach
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", context.getParameter("audioModel", "whisper-1"));
		body.put("file", audioFileUrl);

		if ("transcribe".equals(operation)) {
			String language = context.getParameter("audioLanguage", "");
			if (language != null && !language.isBlank()) {
				body.put("language", language);
			}
		}

		String endpoint = "transcribe".equals(operation) ? "/audio/transcriptions" : "/audio/translations";
		String url = BASE_URL + endpoint;
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Model List =========================

	private NodeExecutionResult executeModelList(Map<String, String> headers) throws Exception {
		String url = BASE_URL + "/models";
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
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
