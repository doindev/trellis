package io.cwc.nodes.impl.ai;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractVectorStoreNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "vectorStoreMongoDBAtlas",
		displayName = "MongoDB Atlas Vector Store",
		description = "Store and retrieve embeddings using MongoDB Atlas",
		category = "AI / Vector Stores",
		icon = "database",
		credentials = {"mongoDb"}
)
public class MongoDbAtlasVectorStoreNode extends AbstractVectorStoreNode {

	@Override
	protected boolean supportsUpdate() {
		return true;
	}

	@Override
	protected EmbeddingStore<TextSegment> createEmbeddingStore(NodeExecutionContext context) {
		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 27017);
		String database = context.getCredentialString("database", "");
		String username = context.getCredentialString("username", "");
		String password = context.getCredentialString("password", "");

		String collectionName = context.getParameter("collectionName", "");
		String databaseName = context.getParameter("databaseName", database);
		String indexName = context.getParameter("indexName", "");

		String connectionString;
		if (username != null && !username.isBlank()) {
			connectionString = String.format("mongodb+srv://%s:%s@%s/%s",
					username, password, host, databaseName);
		} else {
			connectionString = String.format("mongodb://%s:%d/%s", host, port, databaseName);
		}

		MongoClient mongoClient = MongoClients.create(connectionString);

		return MongoDbEmbeddingStore.builder()
				.fromClient(mongoClient)
				.databaseName(databaseName)
				.collectionName(collectionName)
				.indexName(indexName)
				.build();
	}

	@Override
	protected List<NodeParameter> getStoreParameters() {
		return List.of(
				NodeParameter.builder()
						.name("databaseName").displayName("Database Name")
						.type(ParameterType.STRING)
						.description("MongoDB database name (defaults to credential database)").build(),
				NodeParameter.builder()
						.name("collectionName").displayName("Collection Name")
						.type(ParameterType.STRING).required(true)
						.description("Name of the MongoDB collection").build(),
				NodeParameter.builder()
						.name("indexName").displayName("Index Name")
						.type(ParameterType.STRING).required(true)
						.description("Name of the Atlas Vector Search index").build()
		);
	}
}
