package io.trellis.nodes.impl.ai;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiMemoryNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.impl.ai.memory.ChatMessageSerialization;
import org.bson.Document;

import java.util.List;

@Node(
		type = "memoryMongoDbChat",
		displayName = "MongoDB Chat Memory",
		description = "Stores chat history in a MongoDB collection",
		category = "AI / Memory",
		icon = "database",
		credentials = "mongoDb",
		searchOnly = true
)
public class MongoDbChatMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int windowSize = toInt(context.getParameters().get("contextWindowLength"), 10);
		String sessionId = context.getParameter("sessionId", "default");
		String collectionName = context.getParameter("collectionName", "chat_histories");
		String databaseName = context.getParameter("databaseName", "");

		// Build MongoDB connection string from credentials
		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 27017);
		String username = context.getCredentialString("username");
		String password = context.getCredentialString("password");
		String database = context.getCredentialString("database");
		boolean tls = Boolean.parseBoolean(context.getCredentialString("tls", "false"));

		// Use parameter database name, fall back to credential database
		String dbName = (databaseName != null && !databaseName.isBlank()) ? databaseName : database;
		if (dbName == null || dbName.isBlank()) {
			throw new IllegalArgumentException("Database name must be provided either as a parameter or in the credentials.");
		}

		String connectionString;
		if (username != null && !username.isBlank()) {
			connectionString = String.format("mongodb://%s:%s@%s:%d/?appname=trellis%s",
					username, password, host, port, tls ? "&ssl=true" : "");
		} else {
			connectionString = String.format("mongodb://%s:%d/?appname=trellis%s",
					host, port, tls ? "&ssl=true" : "");
		}

		MongoClient client = MongoClients.create(connectionString);
		MongoCollection<Document> collection = client.getDatabase(dbName).getCollection(collectionName);

		ChatMemoryStore store = new MongoDbChatMemoryStore(collection);

		return MessageWindowChatMemory.builder()
				.id(sessionId)
				.maxMessages(windowSize)
				.chatMemoryStore(store)
				.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("sessionId").displayName("Session ID")
						.type(ParameterType.STRING)
						.defaultValue("default")
						.description("A unique key to isolate this conversation")
						.build(),
				NodeParameter.builder()
						.name("collectionName").displayName("Collection Name")
						.type(ParameterType.STRING)
						.defaultValue("chat_histories")
						.description("MongoDB collection to store chat history")
						.build(),
				NodeParameter.builder()
						.name("databaseName").displayName("Database Name")
						.type(ParameterType.STRING)
						.description("Database name (overrides credential database if set)")
						.build(),
				NodeParameter.builder()
						.name("contextWindowLength").displayName("Context Window Length")
						.type(ParameterType.NUMBER)
						.defaultValue(10)
						.description("Number of recent messages to keep in memory")
						.build()
		);
	}

	/**
	 * ChatMemoryStore backed by a MongoDB collection.
	 * Each session is stored as a document: { sessionId: "...", messages: "[JSON]" }
	 */
	static class MongoDbChatMemoryStore implements ChatMemoryStore {
		private final MongoCollection<Document> collection;

		MongoDbChatMemoryStore(MongoCollection<Document> collection) {
			this.collection = collection;
		}

		@Override
		public List<ChatMessage> getMessages(Object memoryId) {
			Document doc = collection.find(Filters.eq("sessionId", memoryId.toString())).first();
			if (doc == null) return List.of();
			String json = doc.getString("messages");
			return ChatMessageSerialization.deserialize(json);
		}

		@Override
		public void updateMessages(Object memoryId, List<ChatMessage> messages) {
			String json = ChatMessageSerialization.serialize(messages);
			Document doc = new Document("sessionId", memoryId.toString())
					.append("messages", json);
			collection.replaceOne(
					Filters.eq("sessionId", memoryId.toString()),
					doc,
					new ReplaceOptions().upsert(true)
			);
		}

		@Override
		public void deleteMessages(Object memoryId) {
			collection.deleteOne(Filters.eq("sessionId", memoryId.toString()));
		}
	}
}
