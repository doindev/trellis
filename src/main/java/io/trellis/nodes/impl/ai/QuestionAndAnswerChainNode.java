package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Node(
		type = "chainRetrievalQa",
		displayName = "Question and Answer Chain",
		description = "Answer questions about retrieved documents",
		category = "AI / Chains",
		icon = "link"
)
public class QuestionAndAnswerChainNode extends AbstractNode {

	private static final String DEFAULT_SYSTEM_PROMPT =
			"You are an assistant for question-answering tasks. Use the following pieces of retrieved context to answer the question.\n" +
			"If you don't know the answer, just say that you don't know, don't try to make up an answer.\n" +
			"----------------\n" +
			"Context: {context}";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		ContentRetriever retriever = context.getAiInput("ai_retriever", ContentRetriever.class);
		if (retriever == null) {
			return NodeExecutionResult.error("No retriever connected. Please connect a retriever (e.g. Vector Store Retriever) to the Retriever input.");
		}

		String systemPromptTemplate = context.getParameter("systemPromptTemplate", DEFAULT_SYSTEM_PROMPT);

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : context.getInputData()) {
			try {
				Map<String, Object> json = unwrapJson(item);

				// Resolve the query text
				String query = resolveTextInput(context, json, "text");
				if (query == null || query.isBlank()) {
					// Try common field names
					query = toString(json.getOrDefault("chatInput",
							json.getOrDefault("chat_input",
							json.getOrDefault("input",
							json.getOrDefault("query", "")))));
				}

				if (query.isBlank()) {
					results.add(wrapInJson(Map.of("error", "Query is empty")));
					continue;
				}

				// Retrieve relevant documents
				List<Content> contents = retriever.retrieve(new Query(query));
				String retrievedContext = contents.stream()
						.map(c -> c.textSegment().text())
						.collect(Collectors.joining("\n\n"));

				// Build the prompt
				String systemPrompt = systemPromptTemplate.replace("{context}", retrievedContext);

				List<ChatMessage> messages = List.of(
						SystemMessage.from(systemPrompt),
						UserMessage.from(query)
				);

				ChatResponse response = model.chat(messages);
				String answer = response.aiMessage().text();

				Map<String, Object> output = new LinkedHashMap<>();
				output.put("response", answer);
				results.add(wrapInJson(output));

			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					results.add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return handleError(context, "Q&A chain failed: " + e.getMessage(), e);
				}
			}
		}

		return NodeExecutionResult.success(results);
	}

	private String resolveTextInput(NodeExecutionContext context, Map<String, Object> json, String paramName) {
		String textParam = context.getParameter(paramName, "");
		if (textParam == null || textParam.isBlank()) return "";

		Object resolved = getNestedValue(json, textParam);
		if (resolved != null) return String.valueOf(resolved);

		String result = textParam;
		for (Map.Entry<String, Object> entry : json.entrySet()) {
			result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
			result = result.replace("{{ " + entry.getKey() + " }}", String.valueOf(entry.getValue()));
		}
		return result;
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder().name("main").displayName("Main").type(NodeInput.InputType.MAIN).build(),
				NodeInput.builder().name("ai_languageModel").displayName("Model")
						.type(NodeInput.InputType.AI_LANGUAGE_MODEL).required(true).maxConnections(1).build(),
				NodeInput.builder().name("ai_retriever").displayName("Retriever")
						.type(NodeInput.InputType.AI_RETRIEVER).required(true).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("text").displayName("Prompt (User Message)")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 2))
				.defaultValue("")
				.placeHolder("e.g. Hello, how can you help me?")
				.description("The question to answer. Use an expression to reference data or enter static text.")
				.build());

		params.add(NodeParameter.builder()
				.name("systemPromptTemplate").displayName("System Prompt Template")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 6))
				.defaultValue(DEFAULT_SYSTEM_PROMPT)
				.description("System prompt template. Use {context} for retrieved document content.")
				.build());

		return params;
	}
}
