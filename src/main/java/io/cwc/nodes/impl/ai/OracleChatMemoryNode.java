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
		type = "memoryOracleChat",
		displayName = "Oracle Chat Memory",
		description = "Stores chat history in an Oracle database table",
		category = "AI / Memory",
		icon = "database",
		credentials = "oracleDBApi",
		searchOnly = true
)
public class OracleChatMemoryNode extends AbstractAiMemoryNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		String tableName = context.getParameter("tableName", "chat_histories");

		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 1521);
		String database = context.getCredentialString("database");
		String username = context.getCredentialString("username");
		String password = context.getCredentialString("password");
		String connectAs = context.getCredentialString("connectAs", "serviceName");

		String jdbcUrl;
		if ("sid".equals(connectAs)) {
			jdbcUrl = String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, database);
		} else {
			jdbcUrl = String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, database);
		}

		return new OracleChatMemoryStore(jdbcUrl, username, password, tableName);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING)
						.defaultValue("chat_histories")
						.description("Oracle table name (auto-created if missing)")
						.build()
		);
	}

	static class OracleChatMemoryStore implements ChatMemoryStore {
		private final String jdbcUrl;
		private final String username;
		private final String password;
		private final String tableName;
		private boolean tableCreated = false;

		OracleChatMemoryStore(String jdbcUrl, String username, String password, String tableName) {
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
				try (ResultSet rs = stmt.executeQuery(
						"SELECT COUNT(*) FROM user_tables WHERE table_name = '" + tableName.toUpperCase() + "'")) {
					rs.next();
					if (rs.getInt(1) == 0) {
						stmt.execute("CREATE TABLE " + tableName + " (" +
								"session_id VARCHAR2(255) PRIMARY KEY, " +
								"messages CLOB, " +
								"updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
					}
				}
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
				throw new RuntimeException("Failed to read messages from Oracle", e);
			}
			return List.of();
		}

		@Override
		public void updateMessages(Object memoryId, List<ChatMessage> messages) {
			String json = ChatMessageSerialization.serialize(messages);
			try (Connection conn = getConnection()) {
				ensureTable(conn);
				try (PreparedStatement ps = conn.prepareStatement(
						"MERGE INTO " + tableName + " t " +
								"USING (SELECT ? AS session_id, ? AS messages FROM dual) s " +
								"ON (t.session_id = s.session_id) " +
								"WHEN MATCHED THEN UPDATE SET t.messages = s.messages, t.updated_at = CURRENT_TIMESTAMP " +
								"WHEN NOT MATCHED THEN INSERT (session_id, messages) VALUES (s.session_id, s.messages)")) {
					ps.setString(1, memoryId.toString());
					ps.setString(2, json);
					ps.executeUpdate();
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to update messages in Oracle", e);
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
				throw new RuntimeException("Failed to delete messages from Oracle", e);
			}
		}

		private static String sanitizeIdentifier(String name) {
			return name.replaceAll("[^a-zA-Z0-9_]", "_");
		}
	}
}
