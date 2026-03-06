package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractRetrieverNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeInput.InputType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Contextual Compression Retriever — enhances document similarity search by using
 * an LLM to extract only the relevant portions from retrieved documents.
 *
 * Takes a base retriever and a language model as inputs. For each retrieved document,
 * the LLM extracts content that is relevant to the query, discarding irrelevant text.
 * Documents that have no relevant content are filtered out entirely.
 */
@Slf4j
@Node(
		type = "retrieverContextualCompression",
		displayName = "Contextual Compression Retriever",
		description = "Enhances document similarity search by contextual compression.",
		category = "AI / Retrievers",
		icon = "search"
)
public class ContextualCompressionRetrieverNode extends AbstractRetrieverNode {

	private static final String EXTRACTION_PROMPT = """
			Given the following question and context, extract any part of the context that is relevant to answering the question.
			If no part of the context is relevant, respond with "NO_RELEVANT_CONTENT".
			Do not add any explanations or commentary — return only the extracted relevant text.

			Question: %s

			Context:
			>>>
			%s
			>>>

			Extracted relevant parts:""";

	@Override
	protected ContentRetriever createRetriever(NodeExecutionContext context) throws Exception {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			throw new IllegalStateException("No language model connected.");
		}

		ContentRetriever baseRetriever = context.getAiInput("ai_retriever", ContentRetriever.class);
		if (baseRetriever == null) {
			throw new IllegalStateException("No base retriever connected.");
		}

		return (Query query) -> {
			List<Content> docs = baseRetriever.retrieve(query);
			List<Content> compressed = new ArrayList<>();

			for (Content doc : docs) {
				String text = doc.textSegment().text();
				if (text == null || text.isBlank()) continue;

				try {
					String prompt = String.format(EXTRACTION_PROMPT, query.text(), text);
					ChatResponse response = model.chat(
							SystemMessage.from("You are an expert at extracting relevant information from documents."),
							UserMessage.from(prompt)
					);
					String extracted = response.aiMessage().text();

					if (extracted != null && !extracted.isBlank()
							&& !extracted.contains("NO_RELEVANT_CONTENT")) {
						compressed.add(Content.from(
								TextSegment.from(extracted.trim(), doc.textSegment().metadata())
						));
					}
				} catch (Exception e) {
					log.warn("Contextual compression failed for document, keeping original: {}", e.getMessage());
					compressed.add(doc);
				}
			}

			return compressed;
		};
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder()
						.name("ai_languageModel").displayName("Model")
						.type(InputType.AI_LANGUAGE_MODEL)
						.required(true).maxConnections(1).build(),
				NodeInput.builder()
						.name("ai_retriever").displayName("Retriever")
						.type(InputType.AI_RETRIEVER)
						.required(true).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of();
	}
}
