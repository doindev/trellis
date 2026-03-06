package io.cwc.nodes.impl.ai;

import java.util.List;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractVectorStoreNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@Node(
		type = "vectorStorePGVector",
		displayName = "Postgres PGVector Store",
		description = "Store and retrieve embeddings using PostgreSQL with pgvector extension",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"postgresApi"}
)
public class PgVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 5432);
		String database = context.getCredentialString("database");
		String username = context.getCredentialString("username");
		String password = context.getCredentialString("password");

		String tableName = context.getParameter("tableName", "n8n_vectors");
		int dimension = toInt(context.getParameters().get("dimension"), 1536);

		return PgVectorEmbeddingStore.builder()
				.host(host)
				.port(port)
				.database(database)
				.user(username)
				.password(password)
				.table(tableName)
				.dimension(dimension)
				.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING)
						.defaultValue("n8n_vectors")
						.description("PostgreSQL table name for embeddings").build(),
				NodeParameter.builder()
						.name("dimension").displayName("Embedding Dimension")
						.type(ParameterType.NUMBER)
						.defaultValue(1536)
						.description("Dimension of embedding vectors").build()
		);
	}
}
