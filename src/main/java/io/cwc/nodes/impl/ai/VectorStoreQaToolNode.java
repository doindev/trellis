package io.cwc.nodes.impl.ai;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiToolNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeInput.InputType;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Vector Store Question Answer Tool — answers questions by searching a connected
 * vector store for relevant documents and using a language model to synthesize an answer.
 */
@Node(
		type = "toolVectorStore",
		displayName = "Vector Store Question Answer Tool",
		description = "Answer questions with a vector store",
		category = "AI / Tools",
		icon = "database",
		searchOnly = true
)
public class VectorStoreQaToolNode extends AbstractAiToolNode {

	@SuppressWarnings("unchecked")
	@Override
	public Object supplyData(NodeExecutionContext context) {
		EmbeddingStore<TextSegment> vectorStore = context.getAiInput("ai_vectorStore", EmbeddingStore.class);
		ChatModel chatModel = context.getAiInput("ai_languageModel", ChatModel.class);
		EmbeddingModel embeddingModel = context.getAiInput("ai_embedding", EmbeddingModel.class);

		if (vectorStore == null) {
			throw new IllegalStateException("No vector store connected");
		}
		if (chatModel == null) {
			throw new IllegalStateException("No language model connected");
		}

		String description = context.getParameter("description", "Useful for answering questions about the data");
		int topK = toInt(context.getParameters().get("topK"), 4);

		return new VectorStoreQaTool(vectorStore, chatModel, embeddingModel, description, topK);
	}

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder()
						.name("ai_vectorStore").displayName("Vector Store")
						.type(InputType.AI_VECTOR_STORE)
						.required(true).maxConnections(1).build(),
				NodeInput.builder()
						.name("ai_languageModel").displayName("Model")
						.type(InputType.AI_LANGUAGE_MODEL)
						.required(true).maxConnections(1).build(),
				NodeInput.builder()
						.name("ai_embedding").displayName("Embedding Model")
						.type(InputType.AI_EMBEDDING)
						.required(false).maxConnections(1).build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("description").displayName("Description of Data")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Describe the data in the vector store (used in tool description for the AI agent).").build(),
				NodeParameter.builder()
						.name("topK").displayName("Limit")
						.type(ParameterType.NUMBER)
						.defaultValue(4)
						.description("Maximum number of documents to retrieve.").build()
		);
	}

	public static class VectorStoreQaTool {
		private final EmbeddingStore<TextSegment> vectorStore;
		private final ChatModel chatModel;
		private final EmbeddingModel embeddingModel;
		private final String description;
		private final int topK;

		public VectorStoreQaTool(EmbeddingStore<TextSegment> vectorStore, ChatModel chatModel,
								 EmbeddingModel embeddingModel, String description, int topK) {
			this.vectorStore = vectorStore;
			this.chatModel = chatModel;
			this.embeddingModel = embeddingModel;
			this.description = description;
			this.topK = topK;
		}

		@Tool("Answer questions using data from a vector store. Query the vector store and synthesize an answer from the retrieved documents.")
		public String answerQuestion(String question) {
			try {
				if (embeddingModel == null) {
					return "No embedding model connected. Cannot search vector store.";
				}

				Embedding queryEmbedding = embeddingModel.embed(question).content();
				EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
						.queryEmbedding(queryEmbedding)
						.maxResults(topK)
						.build();

				EmbeddingSearchResult<TextSegment> searchResult = vectorStore.search(searchRequest);
				List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

				if (matches.isEmpty()) {
					return "No relevant documents found in the vector store.";
				}

				String context = matches.stream()
						.map(m -> m.embedded() != null ? m.embedded().text() : "")
						.filter(t -> !t.isBlank())
						.collect(Collectors.joining("\n\n---\n\n"));

				String dataContext = (description != null && !description.isBlank())
						? "The documents are about: " + description + "\n\n"
						: "";

				String prompt = dataContext +
						"Use the following documents to answer the question. " +
						"If the answer is not in the documents, say so.\n\n" +
						"Documents:\n" + context + "\n\nQuestion: " + question;

				return chatModel.chat(prompt);
			} catch (Exception e) {
				return "Vector store QA failed: " + e.getMessage();
			}
		}
	}
}
