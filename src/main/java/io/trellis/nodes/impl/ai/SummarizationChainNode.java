package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Node(
		type = "summarizationChain",
		displayName = "Summarization Chain",
		description = "Transforms text into a concise summary",
		category = "AI / Chains",
		icon = "file-text"
)
public class SummarizationChainNode extends AbstractNode {

	private static final String DEFAULT_STUFF_PROMPT =
			"Write a concise summary of the following:\n\n\"{text}\"\n\nCONCISE SUMMARY:";

	private static final String DEFAULT_MAP_PROMPT =
			"Write a concise summary of the following:\n\n\"{text}\"\n\nCONCISE SUMMARY:";

	private static final String DEFAULT_COMBINE_PROMPT =
			"Write a concise summary of the following:\n\n\"{text}\"\n\nCONCISE SUMMARY:";

	private static final String DEFAULT_REFINE_QUESTION_PROMPT =
			"Write a concise summary of the following:\n\n\"{text}\"\n\nCONCISE SUMMARY:";

	private static final String DEFAULT_REFINE_PROMPT =
			"Your job is to produce a final summary.\n\n" +
			"We have provided an existing summary up to a certain point:\n{existing_answer}\n\n" +
			"We have the opportunity to refine the existing summary (only if needed) with some more context below.\n\n" +
			"Given the new context, refine the original summary.\n" +
			"If the context isn't useful, return the original summary.\n\n\"{text}\"";

	private static final int DEFAULT_CHUNK_SIZE = 1000;
	private static final int DEFAULT_CHUNK_OVERLAP = 200;

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		ChatModel model = context.getAiInput("ai_languageModel", ChatModel.class);
		if (model == null) {
			return NodeExecutionResult.error("No language model connected");
		}

		String summarizationMethod = context.getParameter("summarizationMethod", "map_reduce");
		String textField = context.getParameter("textField", "text");

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : context.getInputData()) {
			try {
				Map<String, Object> json = unwrapJson(item);
				Object textObj = getNestedValue(json, textField);
				String text = textObj != null ? String.valueOf(textObj) : "";

				if (text.isBlank()) {
					results.add(wrapInJson(Map.of("summary", "")));
					continue;
				}

				String summary = switch (summarizationMethod) {
					case "stuff" -> summarizeStuff(context, model, text);
					case "refine" -> summarizeRefine(context, model, text);
					default -> summarizeMapReduce(context, model, text);
				};

				results.add(wrapInJson(Map.of("summary", summary)));
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

	/**
	 * Stuff method: concatenate all text into a single prompt.
	 * Best for short documents that fit within the model's context window.
	 */
	private String summarizeStuff(NodeExecutionContext context, ChatModel model, String text) {
		String promptTemplate = context.getParameter("stuffPrompt", DEFAULT_STUFF_PROMPT);
		String prompt = promptTemplate.replace("{text}", text);

		ChatResponse response = model.chat(List.of(
				SystemMessage.from("You are a summarization assistant."),
				UserMessage.from(prompt)
		));
		return response.aiMessage().text();
	}

	/**
	 * Map-Reduce method: split text into chunks, summarize each, then combine summaries.
	 * Best for long documents that exceed the model's context window.
	 */
	private String summarizeMapReduce(NodeExecutionContext context, ChatModel model, String text) {
		int chunkSize = toInt(context.getParameter("chunkSize", DEFAULT_CHUNK_SIZE), DEFAULT_CHUNK_SIZE);
		int chunkOverlap = toInt(context.getParameter("chunkOverlap", DEFAULT_CHUNK_OVERLAP), DEFAULT_CHUNK_OVERLAP);
		String mapPrompt = context.getParameter("combineMapPrompt", DEFAULT_MAP_PROMPT);
		String combinePrompt = context.getParameter("combinePrompt", DEFAULT_COMBINE_PROMPT);

		// Split text into chunks
		List<String> chunks = splitTextIntoChunks(text, chunkSize, chunkOverlap);

		if (chunks.size() <= 1) {
			// No need to map-reduce for a single chunk
			return summarizeChunk(model, mapPrompt, text);
		}

		// Map phase: summarize each chunk individually
		List<String> chunkSummaries = new ArrayList<>();
		for (String chunk : chunks) {
			chunkSummaries.add(summarizeChunk(model, mapPrompt, chunk));
		}

		// Reduce phase: combine all summaries
		String combinedSummaries = String.join("\n\n", chunkSummaries);

		// If combined summaries are still too long, recursively reduce
		if (combinedSummaries.length() > chunkSize * 3) {
			return summarizeMapReduce(context, model, combinedSummaries);
		}

		return summarizeChunk(model, combinePrompt, combinedSummaries);
	}

	/**
	 * Refine method: summarize first chunk, then iteratively refine with each subsequent chunk.
	 * Produces higher quality summaries at the cost of more LLM calls.
	 */
	private String summarizeRefine(NodeExecutionContext context, ChatModel model, String text) {
		int chunkSize = toInt(context.getParameter("chunkSize", DEFAULT_CHUNK_SIZE), DEFAULT_CHUNK_SIZE);
		int chunkOverlap = toInt(context.getParameter("chunkOverlap", DEFAULT_CHUNK_OVERLAP), DEFAULT_CHUNK_OVERLAP);
		String questionPrompt = context.getParameter("refineQuestionPrompt", DEFAULT_REFINE_QUESTION_PROMPT);
		String refinePrompt = context.getParameter("refinePrompt", DEFAULT_REFINE_PROMPT);

		List<String> chunks = splitTextIntoChunks(text, chunkSize, chunkOverlap);

		// Initial summary from first chunk
		String currentSummary = summarizeChunk(model, questionPrompt, chunks.get(0));

		// Refine with subsequent chunks
		for (int i = 1; i < chunks.size(); i++) {
			String prompt = refinePrompt
					.replace("{existing_answer}", currentSummary)
					.replace("{text}", chunks.get(i));

			ChatResponse response = model.chat(List.of(
					SystemMessage.from("You are a summarization assistant."),
					UserMessage.from(prompt)
			));
			currentSummary = response.aiMessage().text();
		}

		return currentSummary;
	}

	private String summarizeChunk(ChatModel model, String promptTemplate, String text) {
		String prompt = promptTemplate.replace("{text}", text);
		ChatResponse response = model.chat(List.of(
				SystemMessage.from("You are a summarization assistant."),
				UserMessage.from(prompt)
		));
		return response.aiMessage().text();
	}

	/**
	 * Split text into chunks with overlap using character boundaries.
	 * Tries to split at sentence boundaries when possible.
	 */
	private List<String> splitTextIntoChunks(String text, int chunkSize, int chunkOverlap) {
		if (text.length() <= chunkSize) {
			return List.of(text);
		}

		List<String> chunks = new ArrayList<>();
		int start = 0;

		while (start < text.length()) {
			int end = Math.min(start + chunkSize, text.length());

			// Try to find a sentence boundary near the end
			if (end < text.length()) {
				int sentenceEnd = findSentenceBoundary(text, start + (chunkSize / 2), end);
				if (sentenceEnd > start) {
					end = sentenceEnd;
				}
			}

			chunks.add(text.substring(start, end).trim());
			start = end - chunkOverlap;
			if (start <= 0 && !chunks.isEmpty()) break;
		}

		return chunks;
	}

	private int findSentenceBoundary(String text, int searchFrom, int searchTo) {
		// Look backwards from searchTo for sentence-ending punctuation
		for (int i = searchTo; i >= searchFrom; i--) {
			char c = text.charAt(i);
			if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
				return i + 1;
			}
		}
		// Fallback: look for any whitespace near the end
		for (int i = searchTo; i >= searchFrom; i--) {
			if (Character.isWhitespace(text.charAt(i))) {
				return i;
			}
		}
		return searchTo;
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
		List<NodeParameter> params = new ArrayList<>();

		// Text field
		params.add(NodeParameter.builder()
				.name("textField").displayName("Text Field")
				.type(ParameterType.STRING)
				.defaultValue("text")
				.description("The field name containing the text to summarize")
				.build());

		// Summarization method
		params.add(NodeParameter.builder()
				.name("summarizationMethod").displayName("Summarization Method")
				.type(ParameterType.OPTIONS)
				.defaultValue("map_reduce")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Map Reduce (Recommended)").value("map_reduce")
								.description("Summarize each chunk individually, then combine summaries. Best for long documents.").build(),
						ParameterOption.builder().name("Stuff").value("stuff")
								.description("Concatenate all text into a single prompt. Best for short documents.").build(),
						ParameterOption.builder().name("Refine").value("refine")
								.description("Summarize first chunk, then iteratively refine. Higher quality, more LLM calls.").build()
				))
				.build());

		// Chunk size (shown for map_reduce and refine)
		params.add(NodeParameter.builder()
				.name("chunkSize").displayName("Characters Per Chunk")
				.type(ParameterType.NUMBER)
				.defaultValue(DEFAULT_CHUNK_SIZE)
				.description("Maximum number of characters per text chunk")
				.displayOptions(Map.of("show", Map.of("summarizationMethod", List.of("map_reduce", "refine"))))
				.build());

		// Chunk overlap
		params.add(NodeParameter.builder()
				.name("chunkOverlap").displayName("Chunk Overlap")
				.type(ParameterType.NUMBER)
				.defaultValue(DEFAULT_CHUNK_OVERLAP)
				.description("Number of overlapping characters between chunks to maintain context")
				.displayOptions(Map.of("show", Map.of("summarizationMethod", List.of("map_reduce", "refine"))))
				.build());

		// Stuff prompt
		params.add(NodeParameter.builder()
				.name("stuffPrompt").displayName("Prompt")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 9))
				.defaultValue(DEFAULT_STUFF_PROMPT)
				.description("Prompt template. Use {text} for the document content.")
				.displayOptions(Map.of("show", Map.of("summarizationMethod", List.of("stuff"))))
				.build());

		// Map-Reduce prompts
		params.add(NodeParameter.builder()
				.name("combineMapPrompt").displayName("Individual Summary Prompt")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 9))
				.defaultValue(DEFAULT_MAP_PROMPT)
				.description("Prompt for summarizing each chunk individually. Use {text} for the chunk content.")
				.displayOptions(Map.of("show", Map.of("summarizationMethod", List.of("map_reduce"))))
				.build());

		params.add(NodeParameter.builder()
				.name("combinePrompt").displayName("Final Combine Prompt")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 9))
				.defaultValue(DEFAULT_COMBINE_PROMPT)
				.description("Prompt for combining all chunk summaries. Use {text} for the combined summaries.")
				.displayOptions(Map.of("show", Map.of("summarizationMethod", List.of("map_reduce"))))
				.build());

		// Refine prompts
		params.add(NodeParameter.builder()
				.name("refineQuestionPrompt").displayName("Initial Summary Prompt")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 9))
				.defaultValue(DEFAULT_REFINE_QUESTION_PROMPT)
				.description("Prompt for the initial summary of the first chunk. Use {text} for the chunk content.")
				.displayOptions(Map.of("show", Map.of("summarizationMethod", List.of("refine"))))
				.build());

		params.add(NodeParameter.builder()
				.name("refinePrompt").displayName("Refine Prompt")
				.type(ParameterType.STRING)
				.typeOptions(Map.of("rows", 9))
				.defaultValue(DEFAULT_REFINE_PROMPT)
				.description("Prompt for refining the summary with each subsequent chunk. Use {existing_answer} and {text}.")
				.displayOptions(Map.of("show", Map.of("summarizationMethod", List.of("refine"))))
				.build());

		return params;
	}
}
