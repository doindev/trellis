package io.cwc.nodes.impl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractDatabaseNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * CrateDB — execute queries against a CrateDB database using the PostgreSQL wire protocol.
 */
@Node(
		type = "crateDb",
		displayName = "CrateDB",
		description = "Execute queries against a CrateDB database",
		category = "Data & Storage / Databases",
		icon = "cratedb",
		credentials = {"crateDbApi"}
)
public class CrateDbNode extends AbstractDatabaseNode {

	@Override
	protected String buildJdbcUrl(String host, int port, String database, Map<String, Object> credentials) {
		return "jdbc:crate://" + host + ":" + port + "/";
	}

	@Override
	protected int getDefaultPort() {
		return 5432;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "executeQuery");

		try (Connection conn = getPooledConnection(credentials, "cratedb")) {
			return switch (operation) {
				case "executeQuery" -> {
					String query = context.getParameter("query", "");
					List<Map<String, Object>> results = executeQuery(conn, query, null);
					yield results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
				}
				case "insert" -> {
					String table = context.getParameter("table", "");
					List<Map<String, Object>> columnDefs = context.getParameter("columns", List.of());
					Map<String, Object> values = new LinkedHashMap<>();
					for (Map<String, Object> col : columnDefs) {
						values.put((String) col.get("column"), col.get("value"));
					}
					if (values.isEmpty()) yield NodeExecutionResult.error("No columns specified for insert");
					String sql = buildInsertSql(table, values);
					List<Object> params = new ArrayList<>(values.values());
					int affected = executeUpdate(conn, sql, params);
					yield NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
				}
				case "update" -> {
					String table = context.getParameter("table", "");
					String where = context.getParameter("where", "");
					List<Map<String, Object>> columnDefs = context.getParameter("columns", List.of());
					Map<String, Object> values = new LinkedHashMap<>();
					for (Map<String, Object> col : columnDefs) {
						values.put((String) col.get("column"), col.get("value"));
					}
					if (values.isEmpty()) yield NodeExecutionResult.error("No columns specified for update");
					String sql = buildUpdateSql(table, values, where);
					List<Object> params = new ArrayList<>(values.values());
					int affected = executeUpdate(conn, sql, params);
					yield NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
				}
				case "executeSql" -> {
					String query = context.getParameter("query", "");
					List<Map<String, Object>> results = executeQuery(conn, query, null);
					yield results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
				}
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "CrateDB error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).required(true)
						.defaultValue("executeQuery")
						.options(List.of(
								ParameterOption.builder().name("Execute Query").value("executeQuery").build(),
								ParameterOption.builder().name("Insert").value("insert").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Execute SQL").value("executeSql").build()
						)).build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("SQL query to execute.").build(),
				NodeParameter.builder()
						.name("table").displayName("Table")
						.type(ParameterType.STRING).defaultValue("")
						.description("Table name for insert/update.").build(),
				NodeParameter.builder()
						.name("columns").displayName("Columns")
						.type(ParameterType.FIXED_COLLECTION)
						.nestedParameters(List.of(
								NodeParameter.builder().name("column").displayName("Column").type(ParameterType.STRING).build(),
								NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).build()
						)).build(),
				NodeParameter.builder()
						.name("where").displayName("WHERE Clause")
						.type(ParameterType.STRING).defaultValue("")
						.description("WHERE clause for update operations.").build()
		);
	}
}
