package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractVectorStoreNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStoreSupabase",
		displayName = "Supabase Vector Store",
		description = "Store and retrieve embeddings using Supabase (pgvector)",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"supabaseApi"}
)
public class SupabaseVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected boolean supportsUpdate() {
		return true;
	}

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String host = context.getParameter("supabaseHost", "");
		String tableName = context.getParameter("tableName", "documents");
		int dimension = toInt(context.getParameters().get("dimension"), 1536);

		// Supabase connection: db.<project-ref>.supabase.co, port 5432, database "postgres"
		// password is the API service_role key or database password
		return PgVectorEmbeddingStore.builder()
				.host(host)
				.port(5432)
				.database("postgres")
				.user("postgres")
				.password(apiKey)
				.table(tableName)
				.dimension(dimension)
				.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("supabaseHost").displayName("Supabase Database Host")
						.type(ParameterType.STRING).required(true)
						.description("Database host (e.g. db.xxxx.supabase.co)").build(),
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING)
						.defaultValue("documents")
						.description("Name of the pgvector table").build(),
				NodeParameter.builder()
						.name("dimension").displayName("Embedding Dimension")
						.type(ParameterType.NUMBER)
						.defaultValue(1536)
						.description("Dimension of embedding vectors").build()
		);
	}
}
