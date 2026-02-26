package io.trellis.nodes.impl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractDatabaseNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Microsoft SQL Server Node -- execute queries against a Microsoft SQL Server database.
 */
@Slf4j
@Node(
	type = "microsoftSql",
	displayName = "Microsoft SQL Server",
	description = "Execute queries against a Microsoft SQL Server database.",
	category = "Data & Storage / Databases",
	icon = "microsoftSql",
	credentials = {"microsoftSqlApi"}
)
public class MicrosoftSqlNode extends AbstractDatabaseNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true)
				.defaultValue("executeQuery")
				.options(List.of(
					ParameterOption.builder().name("Execute Query").value("executeQuery").description("Run a SELECT query").build(),
					ParameterOption.builder().name("Insert").value("insert").description("Insert rows into a table").build(),
					ParameterOption.builder().name("Update").value("update").description("Update rows in a table").build(),
					ParameterOption.builder().name("Delete").value("deleteTable").description("Delete rows from a table").build(),
					ParameterOption.builder().name("Execute SQL").value("executeSql").description("Execute a raw SQL statement").build()
				)).build(),
			NodeParameter.builder()
				.name("query").displayName("Query")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 5))
				.placeHolder("SELECT * FROM users WHERE id = @p1")
				.displayOptions(Map.of("show", Map.of("operation", List.of("executeQuery", "executeSql"))))
				.build(),
			NodeParameter.builder()
				.name("table").displayName("Table")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("insert", "update", "deleteTable"))))
				.build(),
			NodeParameter.builder()
				.name("columns").displayName("Columns")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("operation", List.of("insert", "update"))))
				.nestedParameters(List.of(
					NodeParameter.builder().name("column").displayName("Column").type(ParameterType.STRING).build(),
					NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).build()
				)).build(),
			NodeParameter.builder()
				.name("where").displayName("WHERE Clause")
				.type(ParameterType.STRING)
				.placeHolder("id = 1")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update", "deleteTable"))))
				.build(),
			NodeParameter.builder()
				.name("options").displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder().name("queryBatching").displayName("Query Batching")
						.type(ParameterType.OPTIONS).defaultValue("single")
						.options(List.of(
							ParameterOption.builder().name("Single Query for All Items").value("single").build(),
							ParameterOption.builder().name("One Query Per Item").value("perItem").build()
						)).build()
				)).build()
		);
	}

	@Override
	protected String buildJdbcUrl(String host, int port, String database, Map<String, Object> credentials) {
		StringBuilder url = new StringBuilder("jdbc:sqlserver://");
		url.append(host).append(":").append(port);
		if (database != null && !database.isEmpty()) {
			url.append(";databaseName=").append(database);
		}
		url.append(";encrypt=true;trustServerCertificate=true");
		return url.toString();
	}

	@Override
	protected int getDefaultPort() {
		return 1433;
	}

	@Override
	protected String quoteIdentifier(String identifier) {
		return "[" + identifier.replace("]", "]]") + "]";
	}

	@Override
	protected void addConnectionProperties(java.util.Properties props, Map<String, Object> credentials) {
		if (Boolean.TRUE.equals(credentials.get("ssl")) || "true".equalsIgnoreCase(String.valueOf(credentials.get("ssl")))) {
			props.setProperty("encrypt", "true");
		}
		String domain = String.valueOf(credentials.getOrDefault("domain", ""));
		if (!domain.isEmpty()) {
			props.setProperty("domain", domain);
		}
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "executeQuery");

		try (Connection conn = getPooledConnection(credentials, "mssql")) {
			return switch (operation) {
				case "executeQuery" -> executeSelectQuery(conn, context);
				case "insert" -> executeInsert(conn, context);
				case "update" -> executeUpdateOp(conn, context);
				case "deleteTable" -> executeDelete(conn, context);
				case "executeSql" -> executeRawSql(conn, context);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Microsoft SQL Server error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeSelectQuery(Connection conn, NodeExecutionContext context) throws Exception {
		String query = context.getParameter("query", "");
		List<Map<String, Object>> results = executeQuery(conn, query, null);
		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	private NodeExecutionResult executeInsert(Connection conn, NodeExecutionContext context) throws Exception {
		String table = context.getParameter("table", "");
		List<Map<String, Object>> columnDefs = context.getParameter("columns", List.of());
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map<String, Object> col : columnDefs) {
			values.put((String) col.get("column"), col.get("value"));
		}
		if (values.isEmpty()) {
			return NodeExecutionResult.error("No columns specified for insert");
		}
		String sql = buildInsertSql(table, values);
		List<Object> params = new ArrayList<>(values.values());
		int affected = executeUpdate(conn, sql, params);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
	}

	private NodeExecutionResult executeUpdateOp(Connection conn, NodeExecutionContext context) throws Exception {
		String table = context.getParameter("table", "");
		String where = context.getParameter("where", "");
		List<Map<String, Object>> columnDefs = context.getParameter("columns", List.of());
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map<String, Object> col : columnDefs) {
			values.put((String) col.get("column"), col.get("value"));
		}
		if (values.isEmpty()) {
			return NodeExecutionResult.error("No columns specified for update");
		}
		String sql = buildUpdateSql(table, values, where);
		List<Object> params = new ArrayList<>(values.values());
		int affected = executeUpdate(conn, sql, params);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
	}

	private NodeExecutionResult executeDelete(Connection conn, NodeExecutionContext context) throws Exception {
		String table = context.getParameter("table", "");
		String where = context.getParameter("where", "");
		String sql = "DELETE FROM " + quoteIdentifier(table);
		if (where != null && !where.isEmpty()) {
			sql += " WHERE " + where;
		}
		int affected = executeUpdate(conn, sql, null);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
	}

	private NodeExecutionResult executeRawSql(Connection conn, NodeExecutionContext context) throws Exception {
		String query = context.getParameter("query", "");
		String trimmed = query.trim().toUpperCase();
		if (trimmed.startsWith("SELECT") || trimmed.startsWith("WITH") || trimmed.startsWith("EXEC")
				|| trimmed.startsWith("SP_") || trimmed.startsWith("EXPLAIN")) {
			List<Map<String, Object>> results = executeQuery(conn, query, null);
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		} else {
			int affected = executeUpdate(conn, query, null);
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
		}
	}
}
