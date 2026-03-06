package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * MultiQuery Retriever — automates prompt tuning by generating multiple diverse
 * reformulations of the user's query and combining their retrieval results.
 *
 * Takes a base retriever and a language model as inputs. The LLM generates N different
 * versions of the original query, each is run against the base retriever, and the
 * results are deduplicated and combined for enhanced recall.
 */
@Slf4j
@Node(
		type = "retrieverMultiQuery",
		displayName = "MultiQuery Retriever",
		description = "Automates prompt tuning, generates diverse queries and expands document pool for enhanced retrieval.",
		category = "AI / Retrievers",
		icon = "search"
)
public class MultiQueryRetrieverNode extends AbstractRetrieverNode {

	private static final String QUERY_GENERATION_PROMPT = """
			You are an AI language model assistant. Your task is to generate %d different \
			versions of the given user question to retrieve relevant documents from a vector \
			database. By generating multiple perspectives on the user question, your goal is to \
			help the user overcome some of the limitations of distance-based similarity search.

			Provide these alternative questions separated by newlines. Do not number them. \
			Do not include any other text besides the questions.""";

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

		int queryCount = toInt(context.getParameters().get("queryCount"), 3);

		return (Query query) -> {
			// Generate query variations using the LLM
			List<String> queries = generateQueryVariations(model, query.text(), queryCount);
			queries.add(0, query.text()); // include original query first

			// Retrieve from each variation, deduplicate by text content
			Set<String> seenTexts = new LinkedHashSet<>();
			List<Content> allResults = new ArrayList<>();

			for (String q : queries) {
				try {
					List<Content> results = baseRetriever.retrieve(new Query(q));
					for (Content c : results) {
						String text = c.textSegment().text();
						if (seenTexts.add(text)) {
							allResults.add(c);
						}
					}
				} catch (Exception e) {
					log.warn("Multi-query retrieval failed for variant '{}': {}", q, e.getMessage());
				}
			}

			return allResults;
		};
	}

	private List<String> generateQueryVariations(ChatModel model, String originalQuery, int count) {
		try {
			String systemPrompt = String.format(QUERY_GENERATION_PROMPT, count);
			ChatResponse response = model.chat(
					SystemMessage.from(systemPrompt),
					UserMessage.from(originalQuery)
			);
			String text = response.aiMessage().text();
			if (text == null || text.isBlank()) return new ArrayList<>();

			List<String> variations = new ArrayList<>();
			for (String line : text.split("\n")) {
				String trimmed = line.trim();
				// Strip leading numbering if present (e.g. "1. ", "1) ")
				trimmed = trimmed.replaceFirst("^\\d+[.)\\-]\\s*", "");
				if (!trimmed.isEmpty()) {
					variations.add(trimmed);
				}
			}
			return variations;
		} catch (Exception e) {
			log.warn("Failed to generate query variations: {}", e.getMessage());
			return new ArrayList<>();
		}
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
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("queryCount")
				.displayName("Query Count")
				.description("Number of different versions of the given question to generate.")
				.type(ParameterType.NUMBER)
				.defaultValue(3)
				.build());

		return params;
	}
}
