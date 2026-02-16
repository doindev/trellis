package io.trellis.nodes.core;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeOutput {
	private String name;
	private String displayName;

	@Builder.Default
	private OutputType type = OutputType.MAIN;
	
	public enum OutputType {
		MAIN,
		AI_MAIN,
		AI_TOOL,
		AI_LANGUAGE_MODEL,
		AI_MEMORY,
		AI_OUTPUT_PARSER,
		AI_EMBEDDING,
		AI_RETRIEVER,
		AI_TEXT_SPLITTER,
		AI_VECTOR_STORE,
		AI_AGENT
	}
}
