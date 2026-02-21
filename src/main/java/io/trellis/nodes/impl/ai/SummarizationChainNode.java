package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.core.NodeParameter.ParameterOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Node(
		type = "summarizationChain",
		displayName = "Summarization Chain",
		description = "Summarize text using a language model",
		category = "AI / Chains",
		icon = "file-text"
)
public class SummarizationChainNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		String textField = context.getParameter("textField", "text");
		String summaryType = context.getParameter("summaryType", "concise");

		String systemPrompt = switch (summaryType) {
			case "detailed" -> "You are a summarization assistant. Provide a detailed summary that captures all key points, arguments, and supporting details from the text.";
			case "bullet_points" -> "You are a summarization assistant. Summarize the text as a clear, well-organized bullet point list capturing all key points.";
			default -> "You are a summarization assistant. Provide a concise summary that captures the main points of the text in a few sentences.";
		};

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : context.getInputData()) {
			try {
				Map<String, Object> json = unwrapJson(item);
				Object textObj = json.get(textField);
				String text = textObj != null ? String.valueOf(textObj) : "";

				if (text.isBlank()) {
					results.add(wrapInJson(Map.of("summary", "")));
					continue;
				}

				List<ChatMessage> messages = List.of(
						SystemMessage.from(systemPrompt),
						UserMessage.from("Please summarize the following text:\n\n" + text)
				);

				ChatResponse response = model.chat(messages);
				results.add(wrapInJson(Map.of("summary", response.aiMessage().text())));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					results.add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return handleError(context, "Summarization failed: " + e.getMessage(), e);
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
						.description("The field name containing the text to summarize")
						.build(),
				NodeParameter.builder()
						.name("summaryType").displayName("Summary Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("concise")
						.options(List.of(
								ParameterOption.builder().name("Concise").value("concise")
										.description("Brief summary in a few sentences").build(),
								ParameterOption.builder().name("Detailed").value("detailed")
										.description("Comprehensive summary with key details").build(),
								ParameterOption.builder().name("Bullet Points").value("bullet_points")
										.description("Summary as a bullet point list").build()
						)).build()
		);
	}
}
