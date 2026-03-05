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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Node(
		type = "basicLlmChain",
		displayName = "Basic LLM Chain",
		description = "Send a prompt to a language model and get a response",
		category = "AI",
		icon = "link"
)
public class BasicLlmChainNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		String prompt = context.getParameter("prompt", "");
		String systemMessage = context.getParameter("systemMessage", "");
		List<Map<String, Object>> inputData = context.getInputData();

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> json = unwrapJson(item);

				// Resolve prompt with input data
				String resolvedPrompt = prompt;
				for (Map.Entry<String, Object> entry : json.entrySet()) {
					resolvedPrompt = resolvedPrompt.replace(
							"{{ " + entry.getKey() + " }}",
							String.valueOf(entry.getValue()));
					resolvedPrompt = resolvedPrompt.replace(
							"{{" + entry.getKey() + "}}",
							String.valueOf(entry.getValue()));
				}

				List<ChatMessage> messages = new ArrayList<>();
				if (systemMessage != null && !systemMessage.isBlank()) {
					messages.add(SystemMessage.from(systemMessage));
				}
				messages.add(UserMessage.from(resolvedPrompt));

				ChatResponse response = model.chat(messages);
				String text = response.aiMessage().text();

				results.add(wrapInJson(Map.of("output", text)));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					results.add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return handleError(context, "LLM chain failed: " + e.getMessage(), e);
				}
			}
		}

		if (results.isEmpty()) {
			// No input data — run the prompt directly
			try {
				List<ChatMessage> messages = new ArrayList<>();
				if (systemMessage != null && !systemMessage.isBlank()) {
					messages.add(SystemMessage.from(systemMessage));
				}
				messages.add(UserMessage.from(prompt));
				ChatResponse response = model.chat(messages);
				results.add(wrapInJson(Map.of("output", response.aiMessage().text())));
			} catch (Exception e) {
				return handleError(context, "LLM chain failed: " + e.getMessage(), e);
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
						.name("prompt").displayName("Prompt")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 6))
						.required(true)
						.description("The prompt to send to the model. Use {{fieldName}} to inject input data.")
						.build(),
				NodeParameter.builder()
						.name("systemMessage").displayName("System Message")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 4))
						.defaultValue("")
						.description("Optional system message to set the model's behavior")
						.build()
		);
	}
}
