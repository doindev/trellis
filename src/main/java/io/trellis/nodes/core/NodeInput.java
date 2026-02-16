package io.trellis.nodes.core;

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
		MAIN,
		AI_MAIN,
		AI_TOOL,
		AI_LANGUAGE_MODEL,
		AI_MEMORY,
		AI_OUTPUT_PARSER,
		AI_EMBEDDING,
		AI_RETRIEVER,
		AT_TEXT_SPLITTER,
		AI_VECTOR_STORE,
		AI_DOCUMENT,
		AI_AGENT
	}
}
