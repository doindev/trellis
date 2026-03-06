package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractVectorStoreNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStoreChromaDB",
		displayName = "Chroma Vector Store",
		description = "Store and retrieve embeddings using ChromaDB",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"chromaApi"}
)
public class ChromaVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String url = context.getCredentialString("url", "http://localhost:8000");
		String collectionName = context.getParameter("collectionName", "");

		return ChromaEmbeddingStore.builder()
				.baseUrl(url)
				.collectionName(collectionName)
				.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("collectionName").displayName("Collection Name")
						.type(ParameterType.STRING).required(true)
						.description("Name of the Chroma collection").build()
		);
	}
}
