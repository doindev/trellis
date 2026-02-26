package io.trellis.nodes.impl.ai;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "openAi",
	displayName = "OpenAI",
	description = "Standalone OpenAI node for assistants, chat, images, audio, embeddings, files, and models.",
	category = "Standalone AI Services",
	icon = "openai",
	credentials = {"openAiApi"}
)
public class OpenAiStandaloneNode extends AbstractApiNode {

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
				ParameterOption.builder().name("Assistant").value("assistant").description("Manage assistants").build(),
				ParameterOption.builder().name("Chat").value("chat").description("Create chat completions").build(),
				ParameterOption.builder().name("Image").value("image").description("Generate or edit images").build(),
				ParameterOption.builder().name("Audio").value("audio").description("Transcribe, translate, or generate speech").build(),
				ParameterOption.builder().name("Embedding").value("embedding").description("Create embeddings").build(),
				ParameterOption.builder().name("File").value("file").description("Upload and manage files").build(),
				ParameterOption.builder().name("Model").value("model").description("List available models").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addAssistantParameters(params);
		addChatParameters(params);
		addImageParameters(params);
		addAudioParameters(params);
		addEmbeddingParameters(params);
		addFileParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Assistant operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an assistant").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an assistant").build(),
				ParameterOption.builder().name("List").value("list").description("List assistants").build()
			)).build());

		// Chat operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a chat completion").build()
			)).build());

		// Image operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("generate")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"))))
			.options(List.of(
				ParameterOption.builder().name("Generate").value("generate").description("Generate an image").build(),
				ParameterOption.builder().name("Edit").value("edit").description("Edit an image").build()
			)).build());

		// Audio operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("transcribe")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"))))
			.options(List.of(
				ParameterOption.builder().name("Transcribe").value("transcribe").description("Transcribe audio to text").build(),
				ParameterOption.builder().name("Translate").value("translate").description("Translate audio to English").build(),
				ParameterOption.builder().name("Speech").value("speech").description("Generate speech from text").build()
			)).build());

		// Embedding operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an embedding").build()
			)).build());

		// File operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("list")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.options(List.of(
				ParameterOption.builder().name("Upload").value("upload").description("Upload a file").build(),
				ParameterOption.builder().name("List").value("list").description("List uploaded files").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a file").build()
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

	// ========================= Assistant Parameters =========================

	private void addAssistantParameters(List<NodeParameter> params) {
		// Assistant > Create: model
		params.add(NodeParameter.builder()
			.name("assistantModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("gpt-4o")
			.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("GPT-4o").value("gpt-4o").build(),
				ParameterOption.builder().name("GPT-4o Mini").value("gpt-4o-mini").build(),
				ParameterOption.builder().name("GPT-4 Turbo").value("gpt-4-turbo").build(),
				ParameterOption.builder().name("GPT-4").value("gpt-4").build(),
				ParameterOption.builder().name("GPT-3.5 Turbo").value("gpt-3.5-turbo").build()
			)).build());

		// Assistant > Create: name
		params.add(NodeParameter.builder()
			.name("assistantName").displayName("Name")
			.type(ParameterType.STRING).required(true)
			.description("The name of the assistant.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"), "operation", List.of("create"))))
			.build());

		// Assistant > Create: instructions
		params.add(NodeParameter.builder()
			.name("assistantInstructions").displayName("Instructions")
			.type(ParameterType.STRING)
			.description("The system instructions for the assistant.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"), "operation", List.of("create"))))
			.build());

		// Assistant > Create: description
		params.add(NodeParameter.builder()
			.name("assistantDescription").displayName("Description")
			.type(ParameterType.STRING)
			.description("A description of the assistant.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"), "operation", List.of("create"))))
			.build());

		// Assistant > Delete: ID
		params.add(NodeParameter.builder()
			.name("assistantId").displayName("Assistant ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the assistant to delete.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"), "operation", List.of("delete"))))
			.build());
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

		params.add(NodeParameter.builder()
			.name("chatResponseFormat").displayName("Response Format")
			.type(ParameterType.OPTIONS).defaultValue("text")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chat"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Text").value("text").build(),
				ParameterOption.builder().name("JSON Object").value("json_object").build()
			)).build());
	}

	// ========================= Image Parameters =========================

	private void addImageParameters(List<NodeParameter> params) {
		// Image > Generate
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
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate", "edit"))))
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
			.name("imageStyle").displayName("Style")
			.type(ParameterType.OPTIONS).defaultValue("vivid")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate"))))
			.options(List.of(
				ParameterOption.builder().name("Vivid").value("vivid").build(),
				ParameterOption.builder().name("Natural").value("natural").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("imageN").displayName("Number of Images")
			.type(ParameterType.NUMBER).defaultValue(1)
			.description("Number of images to generate.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("generate"))))
			.build());

		// Image > Edit
		params.add(NodeParameter.builder()
			.name("imageEditPrompt").displayName("Prompt")
			.type(ParameterType.STRING).required(true)
			.description("A text description of the desired edit.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("edit"))))
			.build());

		params.add(NodeParameter.builder()
			.name("imageEditUrl").displayName("Image URL")
			.type(ParameterType.STRING).required(true)
			.description("URL of the image to edit.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("edit"))))
			.build());

		params.add(NodeParameter.builder()
			.name("imageEditN").displayName("Number of Images")
			.type(ParameterType.NUMBER).defaultValue(1)
			.description("Number of edited images to generate.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("image"), "operation", List.of("edit"))))
			.build());
	}

	// ========================= Audio Parameters =========================

	private void addAudioParameters(List<NodeParameter> params) {
		// Audio > Transcribe / Translate
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
			.description("The language of the audio in ISO-639-1 format.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("transcribe"))))
			.build());

		// Audio > Speech
		params.add(NodeParameter.builder()
			.name("speechModel").displayName("Model")
			.type(ParameterType.OPTIONS).required(true).defaultValue("tts-1")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("speech"))))
			.options(List.of(
				ParameterOption.builder().name("TTS-1").value("tts-1").build(),
				ParameterOption.builder().name("TTS-1 HD").value("tts-1-hd").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("speechInput").displayName("Input Text")
			.type(ParameterType.STRING).required(true)
			.description("The text to generate audio for.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("speech"))))
			.build());

		params.add(NodeParameter.builder()
			.name("speechVoice").displayName("Voice")
			.type(ParameterType.OPTIONS).required(true).defaultValue("alloy")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("speech"))))
			.options(List.of(
				ParameterOption.builder().name("Alloy").value("alloy").build(),
				ParameterOption.builder().name("Echo").value("echo").build(),
				ParameterOption.builder().name("Fable").value("fable").build(),
				ParameterOption.builder().name("Onyx").value("onyx").build(),
				ParameterOption.builder().name("Nova").value("nova").build(),
				ParameterOption.builder().name("Shimmer").value("shimmer").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("speechResponseFormat").displayName("Response Format")
			.type(ParameterType.OPTIONS).defaultValue("mp3")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("speech"))))
			.options(List.of(
				ParameterOption.builder().name("MP3").value("mp3").build(),
				ParameterOption.builder().name("Opus").value("opus").build(),
				ParameterOption.builder().name("AAC").value("aac").build(),
				ParameterOption.builder().name("FLAC").value("flac").build(),
				ParameterOption.builder().name("WAV").value("wav").build(),
				ParameterOption.builder().name("PCM").value("pcm").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("speechSpeed").displayName("Speed")
			.type(ParameterType.NUMBER).defaultValue(1.0)
			.description("The speed of the generated audio. Range: 0.25 to 4.0.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("audio"), "operation", List.of("speech"))))
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

		params.add(NodeParameter.builder()
			.name("embeddingDimensions").displayName("Dimensions")
			.type(ParameterType.NUMBER)
			.description("The number of dimensions for the embedding. Leave empty for default.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("embedding"), "operation", List.of("create"))))
			.build());
	}

	// ========================= File Parameters =========================

	private void addFileParameters(List<NodeParameter> params) {
		// File > Upload
		params.add(NodeParameter.builder()
			.name("fileUrl").displayName("File URL")
			.type(ParameterType.STRING).required(true)
			.description("URL of the file to upload.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"))))
			.build());

		params.add(NodeParameter.builder()
			.name("filePurpose").displayName("Purpose")
			.type(ParameterType.OPTIONS).required(true).defaultValue("assistants")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"))))
			.options(List.of(
				ParameterOption.builder().name("Assistants").value("assistants").build(),
				ParameterOption.builder().name("Fine-tune").value("fine-tune").build(),
				ParameterOption.builder().name("Batch").value("batch").build()
			)).build());

		// File > Delete
		params.add(NodeParameter.builder()
			.name("fileId").displayName("File ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the file to delete.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("delete"))))
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
				case "assistant" -> executeAssistant(context, headers);
				case "chat" -> executeChat(context, headers);
				case "image" -> executeImage(context, headers);
				case "audio" -> executeAudio(context, headers);
				case "embedding" -> executeEmbedding(context, headers);
				case "file" -> executeFile(context, headers);
				case "model" -> executeModelList(headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "OpenAI API error: " + e.getMessage(), e);
		}
	}

	// ========================= Assistant Execute =========================

	private NodeExecutionResult executeAssistant(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");
		// Assistants API requires beta header
		Map<String, String> assistantHeaders = new LinkedHashMap<>(headers);
		assistantHeaders.put("OpenAI-Beta", "assistants=v2");

		switch (operation) {
			case "create": {
				String model = context.getParameter("assistantModel", "gpt-4o");
				String name = context.getParameter("assistantName", "");
				String instructions = context.getParameter("assistantInstructions", "");
				String description = context.getParameter("assistantDescription", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("model", model);
				body.put("name", name);
				if (instructions != null && !instructions.isBlank()) {
					body.put("instructions", instructions);
				}
				if (description != null && !description.isBlank()) {
					body.put("description", description);
				}

				String url = BASE_URL + "/assistants";
				HttpResponse<String> response = post(url, body, assistantHeaders);
				if (response.statusCode() >= 400) {
					return apiError("OpenAI", response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "delete": {
				String assistantId = context.getParameter("assistantId", "");
				String url = BASE_URL + "/assistants/" + encode(assistantId);
				HttpResponse<String> response = delete(url, assistantHeaders);
				if (response.statusCode() >= 400) {
					return apiError("OpenAI", response);
				}
				if (response.body() == null || response.body().isBlank()) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", assistantId))));
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "list": {
				String url = BASE_URL + "/assistants";
				HttpResponse<String> response = get(url, assistantHeaders);
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
			default:
				return NodeExecutionResult.error("Unknown assistant operation: " + operation);
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
		String responseFormat = context.getParameter("chatResponseFormat", "text");

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
		if ("json_object".equals(responseFormat)) {
			body.put("response_format", Map.of("type", "json_object"));
		}

		String url = BASE_URL + "/chat/completions";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Image Execute =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeImage(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "generate");

		if ("generate".equals(operation)) {
			String model = context.getParameter("imageModel", "dall-e-3");
			String prompt = context.getParameter("imagePrompt", "");
			String size = context.getParameter("imageSize", "1024x1024");
			String quality = context.getParameter("imageQuality", "standard");
			String style = context.getParameter("imageStyle", "vivid");
			int n = toInt(context.getParameters().get("imageN"), 1);

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("model", model);
			body.put("prompt", prompt);
			body.put("size", size);
			body.put("quality", quality);
			body.put("style", style);
			body.put("n", n);

			String url = BASE_URL + "/images/generations";
			HttpResponse<String> response = post(url, body, headers);

			if (response.statusCode() >= 400) {
				return apiError("OpenAI", response);
			}

			Map<String, Object> parsed = parseResponse(response);
			return extractDataArray(parsed);
		} else if ("edit".equals(operation)) {
			String prompt = context.getParameter("imageEditPrompt", "");
			String imageUrl = context.getParameter("imageEditUrl", "");
			String size = context.getParameter("imageSize", "1024x1024");
			int n = toInt(context.getParameters().get("imageEditN"), 1);

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("prompt", prompt);
			body.put("image", imageUrl);
			body.put("size", size);
			body.put("n", n);

			String url = BASE_URL + "/images/edits";
			HttpResponse<String> response = post(url, body, headers);

			if (response.statusCode() >= 400) {
				return apiError("OpenAI", response);
			}

			Map<String, Object> parsed = parseResponse(response);
			return extractDataArray(parsed);
		}

		return NodeExecutionResult.error("Unknown image operation: " + operation);
	}

	// ========================= Audio Execute =========================

	private NodeExecutionResult executeAudio(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "transcribe");

		if ("speech".equals(operation)) {
			String model = context.getParameter("speechModel", "tts-1");
			String input = context.getParameter("speechInput", "");
			String voice = context.getParameter("speechVoice", "alloy");
			String responseFormat = context.getParameter("speechResponseFormat", "mp3");
			double speed = toDouble(context.getParameters().get("speechSpeed"), 1.0);

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("model", model);
			body.put("input", input);
			body.put("voice", voice);
			body.put("response_format", responseFormat);
			body.put("speed", speed);

			String url = BASE_URL + "/audio/speech";
			HttpResponse<String> response = post(url, body, headers);

			if (response.statusCode() >= 400) {
				return apiError("OpenAI", response);
			}

			// Speech returns binary audio data; return metadata about the response
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"statusCode", response.statusCode(),
				"contentLength", response.body() != null ? response.body().length() : 0,
				"format", responseFormat,
				"model", model,
				"voice", voice
			))));
		}

		// Transcribe or Translate
		String audioFileUrl = context.getParameter("audioFileUrl", "");

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

	// ========================= Embedding Execute =========================

	private NodeExecutionResult executeEmbedding(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String model = context.getParameter("embeddingModel", "text-embedding-3-small");
		String input = context.getParameter("embeddingInput", "");
		Object dimensionsObj = context.getParameters().get("embeddingDimensions");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("input", input);

		if (dimensionsObj != null) {
			int dimensions = toInt(dimensionsObj, 0);
			if (dimensions > 0) {
				body.put("dimensions", dimensions);
			}
		}

		String url = BASE_URL + "/embeddings";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= File Execute =========================

	private NodeExecutionResult executeFile(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "list");

		switch (operation) {
			case "list": {
				String url = BASE_URL + "/files";
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError("OpenAI", response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return extractDataArray(parsed);
			}
			case "upload": {
				String fileUrl = context.getParameter("fileUrl", "");
				String purpose = context.getParameter("filePurpose", "assistants");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("file", fileUrl);
				body.put("purpose", purpose);

				String url = BASE_URL + "/files";
				HttpResponse<String> response = post(url, body, headers);
				if (response.statusCode() >= 400) {
					return apiError("OpenAI", response);
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "delete": {
				String fileId = context.getParameter("fileId", "");
				String url = BASE_URL + "/files/" + encode(fileId);
				HttpResponse<String> response = delete(url, headers);
				if (response.statusCode() >= 400) {
					return apiError("OpenAI", response);
				}
				if (response.body() == null || response.body().isBlank()) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", fileId))));
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown file operation: " + operation);
		}
	}

	// ========================= Model List =========================

	private NodeExecutionResult executeModelList(Map<String, String> headers) throws Exception {
		String url = BASE_URL + "/models";
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError("OpenAI", response);
		}

		Map<String, Object> parsed = parseResponse(response);
		return extractDataArray(parsed);
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		headers.put("Authorization", "Bearer " + apiKey);
		return headers;
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult extractDataArray(Map<String, Object> parsed) {
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

	private NodeExecutionResult apiError(String service, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 500) body = body.substring(0, 500) + "...";
		return NodeExecutionResult.error(service + " API error (HTTP " + response.statusCode() + "): " + body);
	}
}
