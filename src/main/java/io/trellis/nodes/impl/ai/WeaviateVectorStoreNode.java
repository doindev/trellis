package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractVectorStoreNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStoreWeaviate",
		displayName = "Weaviate Vector Store",
		description = "Store and retrieve embeddings using Weaviate",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"weaviateApi"}
)
public class WeaviateVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String host = context.getCredentialString("host", "localhost:8080");
		String scheme = context.getCredentialString("scheme", "http");
		String apiKey = context.getCredentialString("apiKey", "");
		String collectionName = context.getParameter("collectionName", "");

		var builder = WeaviateEmbeddingStore.builder()
				.scheme(scheme)
				.host(host)
				.objectClass(collectionName);

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
						.description("Name of the Weaviate class/collection").build()
		);
	}
}
