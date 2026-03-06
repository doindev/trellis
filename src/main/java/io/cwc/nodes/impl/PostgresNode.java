package io.cwc.nodes.impl;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractDatabaseNode;
import io.cwc.nodes.core.CacheableNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "postgres",
	displayName = "PostgreSQL",
	description = "Execute queries against a PostgreSQL database.",
	category = "Data & Storage / Databases",
	icon = "postgres",
	credentials = {"postgresApi"}
)
public class PostgresNode extends AbstractDatabaseNode implements CacheableNode {

	@Override
	public Map<String, List<Object>> cacheDisplayOptions() {
		return Map.of("operation", List.of("executeQuery"));
	}

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
					ParameterOption.builder().name("Delete").value("delete").description("Delete rows from a table").build(),
					ParameterOption.builder().name("Execute SQL").value("executeSql").description("Execute a raw SQL statement").build()
				)).build(),
			NodeParameter.builder()
				.name("query").displayName("Query")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 5))
				.placeHolder("SELECT * FROM users WHERE id = $1")
				.displayOptions(Map.of("show", Map.of("operation", List.of("executeQuery", "executeSql"))))
				.build(),
			NodeParameter.builder()
				.name("table").displayName("Table")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("insert", "update", "delete"))))
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
				.description("Raw WHERE clause. Prefer 'WHERE Conditions' for parameterized queries.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update", "delete"))))
				.build(),
			NodeParameter.builder()
				.name("whereConditions").displayName("WHERE Conditions")
				.type(ParameterType.FIXED_COLLECTION)
				.description("Parameterized WHERE conditions (safe from SQL injection). Overrides raw WHERE clause if provided.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update", "delete"))))
				.nestedParameters(List.of(
					NodeParameter.builder().name("column").displayName("Column").type(ParameterType.STRING).build(),
					NodeParameter.builder().name("operator").displayName("Operator").type(ParameterType.OPTIONS)
						.defaultValue("=")
						.options(List.of(
							ParameterOption.builder().name("Equals").value("=").build(),
							ParameterOption.builder().name("Not Equals").value("!=").build(),
							ParameterOption.builder().name("Greater Than").value(">").build(),
							ParameterOption.builder().name("Less Than").value("<").build(),
							ParameterOption.builder().name(">=").value(">=").build(),
							ParameterOption.builder().name("<=").value("<=").build(),
							ParameterOption.builder().name("LIKE").value("LIKE").build(),
							ParameterOption.builder().name("IS NULL").value("IS NULL").build(),
							ParameterOption.builder().name("IS NOT NULL").value("IS NOT NULL").build()
						)).build(),
					NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).build()
				)).build(),
			NodeParameter.builder()
				.name("queryParameters").displayName("Query Parameters")
				.type(ParameterType.FIXED_COLLECTION)
				.description("Bind values to ? placeholders in your query (safe from SQL injection).")
				.displayOptions(Map.of("show", Map.of("operation", List.of("executeQuery", "executeSql"))))
				.nestedParameters(List.of(
					NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).build(),
					NodeParameter.builder().name("type").displayName("Type").type(ParameterType.OPTIONS)
						.defaultValue("string")
						.options(List.of(
							ParameterOption.builder().name("String").value("string").build(),
							ParameterOption.builder().name("Number").value("number").build(),
							ParameterOption.builder().name("Boolean").value("boolean").build()
						)).build()
				)).build(),
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
		return "jdbc:postgresql://" + host + ":" + port + "/" + database;
	}

	@Override
	protected int getDefaultPort() {
		return 5432;
	}

	@Override
	protected void addConnectionProperties(java.util.Properties props, Map<String, Object> credentials) {
		if (Boolean.TRUE.equals(credentials.get("ssl"))) {
			props.setProperty("ssl", "true");
			props.setProperty("sslmode", "require");
		}
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "executeQuery");

		try (Connection conn = getPooledConnection(credentials, "postgres")) {
			return switch (operation) {
				case "executeQuery" -> executeSelectQuery(conn, context);
				case "insert" -> executeInsert(conn, context);
				case "update" -> executeUpdateOp(conn, context);
				case "delete" -> executeDelete(conn, context);
				case "executeSql" -> executeRawSql(conn, context);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "PostgreSQL error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeSelectQuery(Connection conn, NodeExecutionContext context) throws Exception {
		String query = context.getParameter("query", "");
		List<Object> params = extractQueryParams(context);
		List<Map<String, Object>> results = executeQuery(conn, query, params.isEmpty() ? null : params);
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
		List<Map<String, Object>> columnDefs = context.getParameter("columns", List.of());
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map<String, Object> col : columnDefs) {
			values.put((String) col.get("column"), col.get("value"));
		}
		if (values.isEmpty()) {
			return NodeExecutionResult.error("No columns specified for update");
		}

		List<Map<String, Object>> whereConditions = context.getParameter("whereConditions", List.of());
		List<Object> params = new ArrayList<>(values.values());
		String whereClause;
		if (!whereConditions.isEmpty()) {
			WhereResult wr = buildWhereClause(whereConditions);
			whereClause = wr.sql();
			params.addAll(wr.params());
		} else {
			whereClause = context.getParameter("where", "");
		}

		String sql = buildUpdateSql(table, values, whereClause);
		int affected = executeUpdate(conn, sql, params);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
	}

	private NodeExecutionResult executeDelete(Connection conn, NodeExecutionContext context) throws Exception {
		String table = context.getParameter("table", "");
		String sql = "DELETE FROM " + quoteIdentifier(table);

		List<Map<String, Object>> whereConditions = context.getParameter("whereConditions", List.of());
		List<Object> params;
		if (!whereConditions.isEmpty()) {
			WhereResult wr = buildWhereClause(whereConditions);
			sql += " WHERE " + wr.sql();
			params = wr.params();
		} else {
			String where = context.getParameter("where", "");
			if (where != null && !where.isEmpty()) {
				sql += " WHERE " + where;
			}
			params = null;
		}

		int affected = executeUpdate(conn, sql, params);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
	}

	private NodeExecutionResult executeRawSql(Connection conn, NodeExecutionContext context) throws Exception {
		String query = context.getParameter("query", "");
		List<Object> params = extractQueryParams(context);
		List<Object> effectiveParams = params.isEmpty() ? null : params;
		String trimmed = query.trim().toUpperCase();
		if (trimmed.startsWith("SELECT") || trimmed.startsWith("WITH") || trimmed.startsWith("SHOW") || trimmed.startsWith("EXPLAIN")) {
			List<Map<String, Object>> results = executeQuery(conn, query, effectiveParams);
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		} else {
			int affected = executeUpdate(conn, query, effectiveParams);
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
		}
	}
}
