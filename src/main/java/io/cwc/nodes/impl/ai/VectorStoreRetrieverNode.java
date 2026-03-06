package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractRetrieverNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeInput.InputType;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Vector Store Retriever — uses a connected vector store as a retriever for
 * RAG (Retrieval-Augmented Generation) workflows.
 *
 * Wraps an EmbeddingStore + EmbeddingModel into a ContentRetriever that performs
 * similarity search and returns the top-K most relevant documents.
 */
@Slf4j
@Node(
		type = "retrieverVectorStore",
		displayName = "Vector Store Retriever",
		description = "Use a Vector Store as Retriever",
		category = "AI / Retrievers",
		icon = "search"
)
public class VectorStoreRetrieverNode extends AbstractRetrieverNode {

	@SuppressWarnings("unchecked")
	@Override
	protected ContentRetriever createRetriever(NodeExecutionContext context) throws Exception {
		EmbeddingStore<TextSegment> store = context.getAiInput("ai_vectorStore", EmbeddingStore.class);
		if (store == null) {
			throw new IllegalStateException("No vector store connected. Please connect a Vector Store node in retrieve mode.");
		}

		EmbeddingModel embeddingModel = context.getAiInput("ai_embedding", EmbeddingModel.class);
		if (embeddingModel == null) {
			throw new IllegalStateException("No embedding model connected. Please connect an Embedding node.");
		}

		int topK = toInt(context.getParameters().get("topK"), 4);
		double minScore = toDouble(context.getParameters().get("minScore"), 0.0);

		EmbeddingStoreContentRetriever.EmbeddingStoreContentRetrieverBuilder builder =
				EmbeddingStoreContentRetriever.builder()
						.embeddingStore(store)
						.embeddingModel(embeddingModel)
						.maxResults(topK);

		if (minScore > 0.0) {
			builder.minScore(minScore);
		}

		return builder.build();
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder()
						.name("ai_vectorStore").displayName("Vector Store")
						.type(InputType.AI_VECTOR_STORE)
						.required(true).maxConnections(1).build(),
				NodeInput.builder()
						.name("ai_embedding").displayName("Embedding")
						.type(InputType.AI_EMBEDDING)
						.required(true).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("topK")
				.displayName("Limit")
				.description("The maximum number of results to return.")
				.type(ParameterType.NUMBER)
				.defaultValue(4)
				.build());

		params.add(NodeParameter.builder()
				.name("minScore")
				.displayName("Minimum Score")
				.description("Minimum similarity score threshold (0.0–1.0). Documents below this score are excluded. Leave at 0 to disable.")
				.type(ParameterType.NUMBER)
				.defaultValue(0)
				.build());

		return params;
	}
}
