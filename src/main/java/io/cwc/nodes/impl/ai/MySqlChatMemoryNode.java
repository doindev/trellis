package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiMemoryNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import io.cwc.nodes.impl.ai.memory.ChatMessageSerialization;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

@Slf4j
@Node(
		type = "memoryMysqlChat",
		displayName = "MySQL Chat Memory",
		description = "Stores chat history in a MySQL table",
		category = "AI / Memory",
		icon = "database",
		credentials = "mysqlApi",
		searchOnly = true
)
public class MySqlChatMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String tableName = context.getParameter("tableName", "chat_histories");

		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 3306);
		String database = context.getCredentialString("database");
		String username = context.getCredentialString("username");
		String password = context.getCredentialString("password");

		String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, database);

		return new MySqlChatMemoryStore(jdbcUrl, username, password, tableName);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING)
						.defaultValue("chat_histories")
						.description("MySQL table name (auto-created if missing)")
						.build()
		);
	}

	static class MySqlChatMemoryStore implements ChatMemoryStore {
		private final String jdbcUrl;
		private final String username;
		private final String password;
		private final String tableName;
		private boolean tableCreated = false;

		MySqlChatMemoryStore(String jdbcUrl, String username, String password, String tableName) {
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
						"messages LONGTEXT, " +
						"updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
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
				throw new RuntimeException("Failed to read messages from MySQL", e);
			}
			return List.of();
		}

		@Override
		public void updateMessages(Object memoryId, List<ChatMessage> messages) {
			String json = ChatMessageSerialization.serialize(messages);
			try (Connection conn = getConnection()) {
				ensureTable(conn);
				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO " + tableName + " (session_id, messages) VALUES (?, ?) " +
								"ON DUPLICATE KEY UPDATE messages = VALUES(messages), updated_at = CURRENT_TIMESTAMP")) {
					ps.setString(1, memoryId.toString());
					ps.setString(2, json);
					ps.executeUpdate();
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to update messages in MySQL", e);
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
				throw new RuntimeException("Failed to delete messages from MySQL", e);
			}
		}

		private static String sanitizeIdentifier(String name) {
			return name.replaceAll("[^a-zA-Z0-9_]", "_");
		}
	}
}
