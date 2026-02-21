package io.trellis.nodes.base;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeInput.InputType;
import io.trellis.nodes.core.NodeOutput.OutputType;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for vector store nodes. These are hybrid nodes that can either
 * execute normally (insert/load/update modes) or supply data to AI chains
 * (retrieve/retrieve-as-tool modes).
 */
@Slf4j
public abstract class AbstractVectorStoreNode extends AbstractNode implements AiSubNodeInterface {

	protected abstract EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context);

	protected abstract List<NodeParameter> getStoreParameters();

	protected boolean supportsUpdate() {
		return false;
	}

	// --- AiSubNodeInterface ---

	@Override
	public boolean shouldSupplyData(NodeExecutionContext context) {
		String mode = context.getParameter("mode", "retrieve");
		return "retrieve".equals(mode) || "retrieve-as-tool".equals(mode);
	}

	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		String mode = context.getParameter("mode", "retrieve");
		EmbeddingModel embeddingModel = context.getAiInput("ai_embedding", EmbeddingModel.class);
		if (embeddingModel == null) {
			throw new IllegalStateException("No embedding model connected");
		}
		EmbeddingStore<TextSegment> store = createEmbeddingStore(context);

		if ("retrieve-as-tool".equals(mode)) {
			return createSearchTool(context, embeddingModel, store);
		}
		// retrieve mode — return the store itself
		return store;
	}

	// --- NodeInterface execute ---

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String mode = context.getParameter("mode", "retrieve");
		EmbeddingModel embeddingModel = context.getAiInput("ai_embedding", EmbeddingModel.class);
		if (embeddingModel == null) {
			return NodeExecutionResult.error("No embedding model connected");
		}

		try {
			EmbeddingStore<TextSegment> store = createEmbeddingStore(context);
			return switch (mode) {
				case "insert" -> executeInsert(context, embeddingModel, store);
				case "load" -> executeLoad(context, embeddingModel, store);
				case "update" -> executeUpdate(context, embeddingModel, store);
				default -> NodeExecutionResult.error("Unknown mode: " + mode);
			};
		} catch (Exception e) {
			return handleError(context, "Vector store operation failed: " + e.getMessage(), e);
		}
	}

	// --- Insert ---

	private NodeExecutionResult executeInsert(NodeExecutionContext context,
			EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
		String textField = context.getParameter("textField", "text");
		String metadataFieldsStr = context.getParameter("metadataFields", "");
		List<String> metadataFields = metadataFieldsStr.isBlank()
				? List.of()
				: Arrays.stream(metadataFieldsStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

		List<Map<String, Object>> inputData = context.getInputData();
		List<TextSegment> segments = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			Object textValue = getNestedValue(item, textField);
			if (textValue == null) {
				textValue = getNestedValue(item, "json." + textField);
			}
			String text = textValue != null ? String.valueOf(textValue) : "";
			if (text.isBlank()) continue;

			Metadata metadata = new Metadata();
			for (String field : metadataFields) {
				Object val = getNestedValue(item, field);
				if (val == null) val = getNestedValue(item, "json." + field);
				if (val != null) {
					metadata.put(field, String.valueOf(val));
				}
			}
			segments.add(TextSegment.from(text, metadata));
		}

		if (segments.isEmpty()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
					"inserted", 0, "message", "No text found in input items"))));
		}

		List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
		List<String> ids = store.addAll(embeddings, segments);

		List<Map<String, Object>> results = new ArrayList<>();
		results.add(wrapInJson(Map.of(
				"inserted", ids.size(),
				"ids", ids
		)));
		return NodeExecutionResult.success(results);
	}

	// --- Load / Get Many ---

	private NodeExecutionResult executeLoad(NodeExecutionContext context,
			EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
		int topK = toInt(context.getParameters().get("topK"), 4);
		String prompt = context.getParameter("prompt", "");

		// If no explicit prompt, try to get from input data
		if (prompt.isBlank()) {
			List<Map<String, Object>> inputData = context.getInputData();
			if (inputData != null && !inputData.isEmpty()) {
				Map<String, Object> firstItem = unwrapJson(inputData.get(0));
				Object chatInput = firstItem.get("chatInput");
				if (chatInput == null) chatInput = firstItem.get("input");
				if (chatInput == null) chatInput = firstItem.get("query");
				if (chatInput == null) chatInput = firstItem.get("text");
				if (chatInput != null) prompt = String.valueOf(chatInput);
			}
		}

		if (prompt.isBlank()) {
			return NodeExecutionResult.error("No search prompt provided");
		}

		Embedding queryEmbedding = embeddingModel.embed(prompt).content();
		EmbeddingSearchResult<TextSegment> searchResult = store.search(
				EmbeddingSearchRequest.builder()
						.queryEmbedding(queryEmbedding)
						.maxResults(topK)
						.build());

		List<Map<String, Object>> results = new ArrayList<>();
		for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
			Map<String, Object> doc = new LinkedHashMap<>();
			doc.put("pageContent", match.embedded() != null ? match.embedded().text() : "");
			doc.put("metadata", match.embedded() != null ? match.embedded().metadata().toMap() : Map.of());
			results.add(wrapInJson(Map.of(
					"document", doc,
					"score", match.score(),
					"id", match.embeddingId() != null ? match.embeddingId() : ""
			)));
		}

		return NodeExecutionResult.success(results);
	}

	// --- Update ---

	private NodeExecutionResult executeUpdate(NodeExecutionContext context,
			EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
		if (!supportsUpdate()) {
			return NodeExecutionResult.error("This vector store does not support update operations");
		}

		String textField = context.getParameter("textField", "text");
		String metadataFieldsStr = context.getParameter("metadataFields", "");
		List<String> metadataFields = metadataFieldsStr.isBlank()
				? List.of()
				: Arrays.stream(metadataFieldsStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

		List<Map<String, Object>> inputData = context.getInputData();
		int updated = 0;

		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			String docId = toString(json.get("id"));
			if (docId == null || docId.isBlank()) {
				docId = context.getParameter("documentId", "");
			}
			if (docId.isBlank()) continue;

			Object textValue = getNestedValue(item, textField);
			if (textValue == null) textValue = getNestedValue(item, "json." + textField);
			String text = textValue != null ? String.valueOf(textValue) : "";
			if (text.isBlank()) continue;

			Metadata metadata = new Metadata();
			for (String field : metadataFields) {
				Object val = getNestedValue(item, field);
				if (val == null) val = getNestedValue(item, "json." + field);
				if (val != null) {
					metadata.put(field, String.valueOf(val));
				}
			}

			TextSegment segment = TextSegment.from(text, metadata);
			Embedding embedding = embeddingModel.embed(segment).content();
			store.remove(docId);
			store.addAll(List.of(docId), List.of(embedding), List.of(segment));
			updated++;
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("updated", updated))));
	}

	// --- Retrieve as Tool ---

	private DynamicTool createSearchTool(NodeExecutionContext context,
			EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> store) {
		int topK = toInt(context.getParameters().get("topK"), 4);
		String toolDescription = context.getParameter("toolDescription",
				"Search documents for relevant information");

		ToolSpecification spec = ToolSpecification.builder()
				.name("vector_store_search")
				.description(toolDescription)
				.parameters(JsonObjectSchema.builder()
						.addStringProperty("query", "The search query")
						.required("query")
						.build())
				.build();

		ToolExecutor executor = (ToolExecutionRequest request, Object memoryId) -> {
			String query = extractJsonString(request.arguments(), "query");
			if (query == null || query.isBlank()) query = request.arguments();
			Embedding queryEmb = embeddingModel.embed(query).content();
			EmbeddingSearchResult<TextSegment> result = store.search(
					EmbeddingSearchRequest.builder()
							.queryEmbedding(queryEmb)
							.maxResults(topK)
							.build());
			return result.matches().stream()
					.map(m -> m.embedded() != null ? m.embedded().text() : "")
					.collect(Collectors.joining("\n\n"));
		};

		return new DynamicTool(spec, executor);
	}

	// --- Inputs / Outputs ---

	@Override
	public List<NodeInput> getInputs() {
		return List.of(
				NodeInput.builder()
						.name("main").displayName("")
						.type(InputType.MAIN).build(),
				NodeInput.builder()
						.name("ai_embedding").displayName("Embedding")
						.type(InputType.AI_EMBEDDING).required(true).build()
		);
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("main").displayName("")
						.type(OutputType.MAIN).build(),
				NodeOutput.builder()
						.name("ai_vectorStore").displayName("Vector Store")
						.type(OutputType.AI_VECTOR_STORE).build(),
				NodeOutput.builder()
						.name("ai_tool").displayName("Tool")
						.type(OutputType.AI_TOOL).build()
		);
	}

	// --- Parameters ---

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Mode selector
		List<ParameterOption> modeOptions = new ArrayList<>(List.of(
				ParameterOption.builder().name("Get Many").value("load")
						.description("Retrieve documents by similarity search").build(),
				ParameterOption.builder().name("Insert Documents").value("insert")
						.description("Embed and store documents").build(),
				ParameterOption.builder().name("Retrieve (As Vector Store)").value("retrieve")
						.description("Supply vector store to AI chain").build(),
				ParameterOption.builder().name("Retrieve (As Tool)").value("retrieve-as-tool")
						.description("Supply search tool to AI agent").build()
		));
		if (supportsUpdate()) {
			modeOptions.add(ParameterOption.builder().name("Update Documents").value("update")
					.description("Update existing documents by ID").build());
		}

		params.add(NodeParameter.builder()
				.name("mode").displayName("Operation Mode")
				.type(ParameterType.OPTIONS)
				.defaultValue("retrieve")
				.options(modeOptions)
				.build());

		// Load/search params
		params.add(NodeParameter.builder()
				.name("prompt").displayName("Prompt")
				.type(ParameterType.STRING)
				.description("Search query text (or use input data)")
				.displayOptions(Map.of("show", Map.of("mode", List.of("load"))))
				.build());

		params.add(NodeParameter.builder()
				.name("topK").displayName("Limit")
				.type(ParameterType.NUMBER)
				.defaultValue(4)
				.description("Number of top results to return")
				.displayOptions(Map.of("show", Map.of("mode", List.of("load", "retrieve-as-tool"))))
				.build());

		// Insert params
		params.add(NodeParameter.builder()
				.name("textField").displayName("Text Field")
				.type(ParameterType.STRING)
				.defaultValue("text")
				.description("Input field containing the text to embed")
				.displayOptions(Map.of("show", Map.of("mode", List.of("insert", "update"))))
				.build());

		params.add(NodeParameter.builder()
				.name("metadataFields").displayName("Metadata Fields")
				.type(ParameterType.STRING)
				.description("Comma-separated field names to store as metadata")
				.displayOptions(Map.of("show", Map.of("mode", List.of("insert", "update"))))
				.build());

		// Update params
		if (supportsUpdate()) {
			params.add(NodeParameter.builder()
					.name("documentId").displayName("Document ID")
					.type(ParameterType.STRING)
					.description("ID of the document to update (or use 'id' field from input)")
					.displayOptions(Map.of("show", Map.of("mode", List.of("update"))))
					.build());
		}

		// Retrieve-as-tool params
		params.add(NodeParameter.builder()
				.name("toolDescription").displayName("Tool Description")
				.type(ParameterType.STRING)
				.defaultValue("Search documents for relevant information")
				.description("Description the AI agent sees for this tool")
				.displayOptions(Map.of("show", Map.of("mode", List.of("retrieve-as-tool"))))
				.build());

		// Store-specific params from subclass
		params.addAll(getStoreParameters());

		return params;
	}

	// --- Utility ---

	private static String extractJsonString(String json, String key) {
		if (json == null) return null;
		String search = "\"" + key + "\"";
		int idx = json.indexOf(search);
		if (idx < 0) return null;
		idx = json.indexOf(':', idx + search.length());
		if (idx < 0) return null;
		idx++;
		while (idx < json.length() && json.charAt(idx) == ' ') idx++;
		if (idx >= json.length() || json.charAt(idx) != '"') return null;
		int start = idx + 1;
		int end = json.indexOf('"', start);
		return end > start ? json.substring(start, end) : null;
	}
}
