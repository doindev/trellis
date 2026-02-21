package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractVectorStoreNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStoreRedis",
		displayName = "Redis Vector Store",
		description = "Store and retrieve embeddings using Redis with RediSearch",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"redis"}
)
public class RedisVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected boolean supportsUpdate() {
		return true;
	}

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 6379);
		String password = context.getCredentialString("password", "");

		String indexName = context.getParameter("indexName", "");
		String prefix = context.getParameter("prefix", "doc:");
		int dimension = toInt(context.getParameters().get("dimension"), 1536);

		var builder = RedisEmbeddingStore.builder()
				.host(host)
				.port(port)
				.indexName(indexName)
				.prefix(prefix)
				.dimension(dimension);

		if (password != null && !password.isBlank()) {
			builder.password(password);
		}

		return builder.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("indexName").displayName("Index Name")
						.type(ParameterType.STRING).required(true)
						.description("Name of the RediSearch index").build(),
				NodeParameter.builder()
						.name("prefix").displayName("Key Prefix")
						.type(ParameterType.STRING)
						.defaultValue("doc:")
						.description("Prefix for Redis keys").build(),
				NodeParameter.builder()
						.name("dimension").displayName("Embedding Dimension")
						.type(ParameterType.NUMBER)
						.defaultValue(1536)
						.description("Dimension of embedding vectors").build()
		);
	}
}
