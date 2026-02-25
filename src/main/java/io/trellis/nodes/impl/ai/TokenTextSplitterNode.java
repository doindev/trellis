package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTextSplitterNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

/**
 * Token Text Splitter — splits documents into chunks measured by token count
 * rather than character count. Uses OpenAI's cl100k_base tokenizer.
 */
@Node(
		type = "textSplitterTokenSplitter",
		displayName = "Token Splitter",
		description = "Split text into chunks by tokens",
		category = "AI / Text Splitters",
		icon = "scissors"
)
public class TokenTextSplitterNode extends AbstractTextSplitterNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int chunkSize = toInt(context.getParameters().get("chunkSize"), 1000);
		int chunkOverlap = toInt(context.getParameters().get("chunkOverlap"), 0);

		if (chunkOverlap >= chunkSize) {
			chunkOverlap = 0;
		}

		return new TokenSplitterConfig(chunkSize, chunkOverlap);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("chunkSize").displayName("Chunk Size")
						.type(ParameterType.NUMBER)
						.defaultValue(1000)
						.description("Maximum number of tokens per chunk.").build(),
				NodeParameter.builder()
						.name("chunkOverlap").displayName("Chunk Overlap")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Number of tokens shared between consecutive chunks to preserve context.").build()
		);
	}

	/**
	 * Configuration object returned by this node. Consumer nodes can use this
	 * to create a LangChain4j DocumentSplitter via {@link #toSplitter()}.
	 */
	public record TokenSplitterConfig(int chunkSize, int chunkOverlap) {
		public DocumentSplitter toSplitter() {
			return DocumentSplitters.recursive(chunkSize, chunkOverlap,
					new OpenAiTokenCountEstimator("gpt-4o"));
		}
	}
}
