package io.trellis.nodes.impl.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiMemoryNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.nodes.impl.ai.memory.ChatMessageSerialization;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

@Slf4j
@Node(
		type = "memoryPostgresChat",
		displayName = "Postgres Chat Memory",
		description = "Stores chat history in a PostgreSQL table",
		category = "AI / Memory",
		icon = "database",
		credentials = "postgresApi",
		searchOnly = true
)
public class PostgresChatMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int windowSize = toInt(context.getParameters().get("contextWindowLength"), 10);
		String sessionId = context.getParameter("sessionId", "default");
		String tableName = context.getParameter("tableName", "chat_histories");

		// Build JDBC connection from credentials
		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 5432);
		String database = context.getCredentialString("database");
		String username = context.getCredentialString("username");
		String password = context.getCredentialString("password");
		boolean ssl = Boolean.parseBoolean(context.getCredentialString("ssl", "false"));

		String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s%s",
				host, port, database, ssl ? "?ssl=true" : "");

		ChatMemoryStore store = new PostgresChatMemoryStore(jdbcUrl, username, password, tableName);

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
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING)
						.defaultValue("chat_histories")
						.description("PostgreSQL table name (auto-created if missing)")
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
	 * ChatMemoryStore backed by a PostgreSQL table.
	 * Table schema: (session_id VARCHAR PRIMARY KEY, messages TEXT)
	 * Auto-creates the table on first access.
	 */
	static class PostgresChatMemoryStore implements ChatMemoryStore {
		private final String jdbcUrl;
		private final String username;
		private final String password;
		private final String tableName;
		private boolean tableCreated = false;

		PostgresChatMemoryStore(String jdbcUrl, String username, String password, String tableName) {
			this.jdbcUrl = jdbcUrl;
			this.username = username;
			this.password = password;
			this.tableName = sanitizeIdentifier(tableName);
		}

		private Connection getConnection() throws Exception {
			return DriverManager.getConnection(jdbcUrl, username, password);
		}

		private void ensureTable(Connection conn) throws Exception {
			if (tableCreated) return;
			try (var stmt = conn.createStatement()) {
				stmt.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (" +
						"session_id VARCHAR(255) PRIMARY KEY, " +
						"messages TEXT, " +
						"updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
			}
			tableCreated = true;
		}

		@Override
		public List<ChatMessage> getMessages(Object memoryId) {
			try (Connection conn = getConnection()) {
				ensureTable(conn);
				try (PreparedStatement ps = conn.prepareStatement(
						"SELECT messages FROM " + tableName + " WHERE session_id = ?")) {
					ps.setString(1, memoryId.toString());
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							return ChatMessageSerialization.deserialize(rs.getString("messages"));
						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to read messages from PostgreSQL", e);
			}
			return List.of();
		}

		@Override
		public void updateMessages(Object memoryId, List<ChatMessage> messages) {
			String json = ChatMessageSerialization.serialize(messages);
			try (Connection conn = getConnection()) {
				ensureTable(conn);
				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO " + tableName + " (session_id, messages, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
								"ON CONFLICT (session_id) DO UPDATE SET messages = EXCLUDED.messages, updated_at = CURRENT_TIMESTAMP")) {
					ps.setString(1, memoryId.toString());
					ps.setString(2, json);
					ps.executeUpdate();
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to update messages in PostgreSQL", e);
			}
		}

		@Override
		public void deleteMessages(Object memoryId) {
			try (Connection conn = getConnection()) {
				ensureTable(conn);
				try (PreparedStatement ps = conn.prepareStatement(
						"DELETE FROM " + tableName + " WHERE session_id = ?")) {
					ps.setString(1, memoryId.toString());
					ps.executeUpdate();
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to delete messages from PostgreSQL", e);
			}
		}

		private static String sanitizeIdentifier(String name) {
			return name.replaceAll("[^a-zA-Z0-9_]", "_");
		}
	}
}
