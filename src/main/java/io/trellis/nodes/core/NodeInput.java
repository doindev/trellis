package io.trellis.nodes.core;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeInput {
	private String name;
	private String displayName;

	@Builder.Default
	private InputType type = InputType.MAIN;

	@Builder.Default
	private boolean required = false;

	@Builder.Default
	private int maxConnections = -1; // -1 = unlimited

	public enum InputType {
		MAIN("main"),
		AI_MAIN("ai_main"),
		AI_TOOL("ai_tool"),
		AI_LANGUAGE_MODEL("ai_languageModel"),
		AI_MEMORY("ai_memory"),
		AI_OUTPUT_PARSER("ai_outputParser"),
		AI_EMBEDDING("ai_embedding"),
		AI_RETRIEVER("ai_retriever"),
		AI_TEXT_SPLITTER("ai_textSplitter"),
		AI_VECTOR_STORE("ai_vectorStore"),
		AI_DOCUMENT("ai_document"),
		AI_AGENT("ai_agent"),
		AI_RERANKER("ai_reranker");

		private final String jsonValue;

		InputType(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		@JsonValue
		public String toJson() {
			return jsonValue;
		}
	}
}
