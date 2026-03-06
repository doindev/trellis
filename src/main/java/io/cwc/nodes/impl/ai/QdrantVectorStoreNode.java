package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractVectorStoreNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.net.URI;
import java.util.List;

@Node(
		type = "vectorStoreQdrant",
		displayName = "Qdrant Vector Store",
		description = "Store and retrieve embeddings using Qdrant",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"qdrantApi"}
)
public class QdrantVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String url = context.getCredentialString("url", "http://localhost:6333");
		String apiKey = context.getCredentialString("apiKey", "");
		String collectionName = context.getParameter("collectionName", "");

		URI uri = URI.create(url);
		String host = uri.getHost();
		int port = uri.getPort() > 0 ? uri.getPort() : 6334; // gRPC port

		var builder = QdrantEmbeddingStore.builder()
				.host(host)
				.port(port)
				.collectionName(collectionName);

		if (apiKey != null && !apiKey.isBlank()) {
			builder.apiKey(apiKey);
		}

		return builder.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("collectionName").displayName("Collection Name")
						.type(ParameterType.STRING).required(true)
						.description("Name of the Qdrant collection").build()
		);
	}
}
