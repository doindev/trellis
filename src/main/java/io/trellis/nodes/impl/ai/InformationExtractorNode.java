package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Node(
		type = "informationExtractor",
		displayName = "Information Extractor",
		description = "Extract structured information from text using a language model",
		category = "AI / Chains",
		icon = "scan-text"
)
public class InformationExtractorNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		String textField = context.getParameter("textField", "text");
		String extractionSchema = context.getParameter("extractionSchema", "{}");
		String systemInstructions = context.getParameter("systemInstructions", "");

		String systemPrompt = "You are an information extraction assistant. Extract structured data from the provided text according to the JSON schema below. " +
				"Always respond with valid JSON matching the schema.\n\nSchema:\n" + extractionSchema;

		if (systemInstructions != null && !systemInstructions.isBlank()) {
			systemPrompt += "\n\nAdditional instructions: " + systemInstructions;
		}

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : context.getInputData()) {
			try {
				Map<String, Object> json = unwrapJson(item);
				Object textObj = json.get(textField);
				String text = textObj != null ? String.valueOf(textObj) : "";

				List<ChatMessage> messages = List.of(
						SystemMessage.from(systemPrompt),
						UserMessage.from("Extract information from this text:\n\n" + text)
				);

				ChatResponse response = model.chat(messages);
				String responseText = response.aiMessage().text();

				// Try to parse the response as JSON
				try {
					// Strip markdown code fences if present
					String jsonText = responseText.trim();
					if (jsonText.startsWith("```")) {
						jsonText = jsonText.replaceFirst("```(?:json)?\\s*", "");
						jsonText = jsonText.replaceFirst("\\s*```$", "");
					}
					@SuppressWarnings("unchecked")
					Map<String, Object> extracted = MAPPER.readValue(jsonText, Map.class);
					results.add(wrapInJson(extracted));
				} catch (Exception parseErr) {
					results.add(wrapInJson(Map.of("output", responseText)));
				}
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					results.add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return handleError(context, "Information extraction failed: " + e.getMessage(), e);
				}
			}
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder().name("main").displayName("Main").type(NodeInput.InputType.MAIN).build(),
				NodeInput.builder().name("ai_languageModel").displayName("Model")
						.type(NodeInput.InputType.AI_LANGUAGE_MODEL).required(true).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("textField").displayName("Text Field")
						.type(ParameterType.STRING)
						.defaultValue("text")
						.description("The field containing the text to extract from")
						.build(),
				NodeParameter.builder()
						.name("extractionSchema").displayName("Extraction Schema")
						.type(ParameterType.JSON)
						.defaultValue("{}")
						.description("JSON schema defining the structure to extract")
						.build(),
				NodeParameter.builder()
						.name("systemInstructions").displayName("Additional Instructions")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 4))
						.defaultValue("")
						.description("Optional additional instructions for the extraction")
						.build()
		);
	}
}
