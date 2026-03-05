package io.trellis.nodes.impl;

import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * Postgres Trigger Node -- polling-based trigger that detects new or updated
 * rows in a PostgreSQL table by tracking a timestamp or auto-increment column.
 */
@Slf4j
@Node(
	type = "postgresTrigger",
	displayName = "Postgres Trigger",
	description = "Starts the workflow when new or updated rows are detected in a PostgreSQL table",
	category = "Data & Storage / Databases",
	icon = "postgres",
	trigger = true,
	polling = true,
	credentials = {"postgresApi"}
)
public class PostgresTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("newRows")
				.options(List.of(
					ParameterOption.builder().name("New Rows").value("newRows")
						.description("Trigger when new rows are inserted").build(),
					ParameterOption.builder().name("Updated Rows").value("updatedRows")
						.description("Trigger when rows are updated").build()
				)).build(),

			NodeParameter.builder()
				.name("schema").displayName("Schema")
				.type(ParameterType.STRING).defaultValue("public")
				.description("The database schema.")
				.build(),

			NodeParameter.builder()
				.name("table").displayName("Table")
				.type(ParameterType.STRING).required(true)
				.description("The name of the table to watch.")
				.build(),

			NodeParameter.builder()
				.name("triggerColumn").displayName("Trigger Column")
				.type(ParameterType.STRING).required(true)
				.description("The column used to detect new/updated rows (e.g., 'id' for new rows, 'updated_at' for updates).")
				.build(),

			NodeParameter.builder()
				.name("orderDirection").displayName("Order Direction")
				.type(ParameterType.OPTIONS).defaultValue("ASC")
				.options(List.of(
					ParameterOption.builder().name("Ascending").value("ASC").build(),
					ParameterOption.builder().name("Descending").value("DESC").build()
				)).build(),

			NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Max number of rows to return per poll.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String event = context.getParameter("event", "newRows");
		String schema = context.getParameter("schema", "public");
		String table = context.getParameter("table", "");
		String triggerColumn = context.getParameter("triggerColumn", "");
		String orderDirection = context.getParameter("orderDirection", "ASC");
		int limit = toInt(context.getParameters().get("limit"), 100);

		try {
			String jdbcUrl = buildJdbcUrl(credentials);
			String username = String.valueOf(credentials.getOrDefault("user",
				credentials.getOrDefault("username", "")));
			String password = String.valueOf(credentials.getOrDefault("password", ""));

			Object lastValue = staticData.get("lastValue");

			String fullTable = schema + "." + table;
			StringBuilder sql = new StringBuilder();
			sql.append("SELECT * FROM ").append(fullTable);

			if (lastValue != null) {
				sql.append(" WHERE ").append(triggerColumn).append(" > ?");
			}

			sql.append(" ORDER BY ").append(triggerColumn).append(" ").append(orderDirection);
			sql.append(" LIMIT ").append(limit);

			List<Map<String, Object>> items = new ArrayList<>();
			Object newLastValue = lastValue;

			try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
				 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

				if (lastValue != null) {
					stmt.setObject(1, lastValue);
				}

				try (ResultSet rs = stmt.executeQuery()) {
					ResultSetMetaData metaData = rs.getMetaData();
					int columnCount = metaData.getColumnCount();

					while (rs.next()) {
						Map<String, Object> row = new LinkedHashMap<>();
						for (int i = 1; i <= columnCount; i++) {
							row.put(metaData.getColumnName(i), rs.getObject(i));
						}

						Object triggerValue = row.get(triggerColumn);
						if (triggerValue != null) {
							newLastValue = triggerValue;
						}

						row.put("_triggerEvent", event);
						row.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(row));
					}
				}
			}

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			if (newLastValue != null) {
				newStaticData.put("lastValue", newLastValue);
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Postgres trigger: found {} new rows in {}.{}", items.size(), schema, table);
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Postgres Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private String buildJdbcUrl(Map<String, Object> credentials) {
		String host = String.valueOf(credentials.getOrDefault("host", "localhost"));
		int port = toInt(credentials.getOrDefault("port", 5432), 5432);
		String database = String.valueOf(credentials.getOrDefault("database", "postgres"));
		String ssl = String.valueOf(credentials.getOrDefault("ssl", "false"));

		StringBuilder url = new StringBuilder("jdbc:postgresql://");
		url.append(host).append(":").append(port).append("/").append(database);
		if ("true".equalsIgnoreCase(ssl)) {
			url.append("?ssl=true&sslmode=require");
		}
		return url.toString();
	}

}
