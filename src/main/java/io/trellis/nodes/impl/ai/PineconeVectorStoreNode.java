package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractVectorStoreNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStorePinecone",
		displayName = "Pinecone Vector Store",
		description = "Store and retrieve embeddings using Pinecone",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"pineconeApi"}
)
public class PineconeVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected boolean supportsUpdate() {
		return true;
	}

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String indexName = context.getParameter("indexName", "");
		String namespace = context.getParameter("namespace", "");

		var builder = PineconeEmbeddingStore.builder()
				.apiKey(apiKey)
				.index(indexName);

		if (namespace != null && !namespace.isBlank()) {
			builder.nameSpace(namespace);
		}

		return builder.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("indexName").displayName("Index Name")
						.type(ParameterType.STRING).required(true)
						.description("Name of the Pinecone index").build(),
				NodeParameter.builder()
						.name("namespace").displayName("Namespace")
						.type(ParameterType.STRING)
						.description("Optional namespace within the index").build()
		);
	}
}
