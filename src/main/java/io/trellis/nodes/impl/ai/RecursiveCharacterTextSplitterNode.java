package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTextSplitterNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Recursive Character Text Splitter — splits documents by recursively trying a list
 * of separators (double newline, single newline, space, empty) until chunks fit
 * within the specified size. Mirrors n8n's textSplitterRecursiveCharacterTextSplitter node.
 */
@Node(
		type = "textSplitterRecursiveCharacterTextSplitter",
		displayName = "Recursive Character Text Splitter",
		description = "Split text recursively by multiple separators into chunks",
		category = "AI / Text Splitters",
		icon = "scissors"
)
public class RecursiveCharacterTextSplitterNode extends AbstractTextSplitterNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int chunkSize = toInt(context.getParameters().get("chunkSize"), 1000);
		int chunkOverlap = toInt(context.getParameters().get("chunkOverlap"), 200);

		if (chunkOverlap >= chunkSize) {
			chunkOverlap = 0;
		}

		return new RecursiveSplitterConfig(chunkSize, chunkOverlap);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
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
	public record RecursiveSplitterConfig(int chunkSize, int chunkOverlap) {
		public DocumentSplitter toSplitter() {
			return new DocumentByParagraphSplitter(chunkSize, chunkOverlap);
		}
	}
}
