package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTextSplitterNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Character Text Splitter — splits documents into chunks based on a fixed character separator.
 * Mirrors n8n's textSplitterCharacterTextSplitter node.
 */
@Node(
		type = "textSplitterCharacterTextSplitter",
		displayName = "Character Text Splitter",
		description = "Split text by character separator into chunks",
		category = "AI / Text Splitters",
		icon = "scissors"
)
public class CharacterTextSplitterNode extends AbstractTextSplitterNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int chunkSize = toInt(context.getParameters().get("chunkSize"), 1000);
		int chunkOverlap = toInt(context.getParameters().get("chunkOverlap"), 200);
		String separator = context.getParameter("separator", "\n\n");

		if (chunkOverlap >= chunkSize) {
			chunkOverlap = 0;
		}

		return new CharacterSplitterConfig(separator, chunkSize, chunkOverlap);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("separator").displayName("Separator")
						.type(ParameterType.STRING)
						.defaultValue("\n\n")
						.description("Character(s) to split on. Default is double newline.").build(),
				NodeParameter.builder()
						.name("chunkSize").displayName("Chunk Size")
						.type(ParameterType.NUMBER)
						.defaultValue(1000)
						.description("Maximum number of characters per chunk.").build(),
				NodeParameter.builder()
						.name("chunkOverlap").displayName("Chunk Overlap")
						.type(ParameterType.NUMBER)
						.defaultValue(200)
						.description("Number of overlapping characters between chunks.").build()
		);
	}

	/**
	 * Configuration object returned by this node. Consumer nodes can use this
	 * to create a LangChain4j DocumentSplitter via {@link #toSplitter()}.
	 */
	public record CharacterSplitterConfig(String separator, int chunkSize, int chunkOverlap) {
		public DocumentSplitter toSplitter() {
			return new DocumentByCharacterSplitter(chunkSize, chunkOverlap);
		}
	}
}
