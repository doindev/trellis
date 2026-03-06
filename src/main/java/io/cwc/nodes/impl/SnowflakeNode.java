package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Snowflake Node -- execute queries, insert, update, and delete data
 * in Snowflake via the Snowflake SQL REST API.
 */
@Slf4j
@Node(
	type = "snowflake",
	displayName = "Snowflake",
	description = "Execute queries and manage data in Snowflake",
	category = "Data & Storage / Databases",
	icon = "snowflake",
	credentials = {"snowflakeApi"}
)
public class SnowflakeNode extends AbstractApiNode {

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
		List<NodeParameter> params = new ArrayList<>();

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("executeQuery")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Execute Query").value("executeQuery").description("Execute a SQL query").build(),
				ParameterOption.builder().name("Insert").value("insert").description("Insert rows into a table").build(),
				ParameterOption.builder().name("Update").value("update").description("Update rows in a table").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete rows from a table").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("query").displayName("SQL Query")
			.type(ParameterType.STRING).required(true)
			.description("The SQL query to execute.")
			.typeOptions(Map.of("rows", 6))
			.displayOptions(Map.of("show", Map.of("operation", List.of("executeQuery"))))
			.build());

		params.add(NodeParameter.builder()
			.name("table").displayName("Table")
			.type(ParameterType.STRING).required(true)
			.description("The name of the table.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("insert", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("schema").displayName("Schema")
			.type(ParameterType.STRING).defaultValue("PUBLIC")
			.description("The schema name.")
			.build());

		params.add(NodeParameter.builder()
			.name("database").displayName("Database")
			.type(ParameterType.STRING)
			.description("The database name. If not set, uses the one from credentials.")
			.build());

		params.add(NodeParameter.builder()
			.name("warehouse").displayName("Warehouse")
			.type(ParameterType.STRING)
			.description("The warehouse name. If not set, uses the one from credentials.")
			.build());

		// Insert parameters
		params.add(NodeParameter.builder()
			.name("columns").displayName("Columns")
			.type(ParameterType.STRING).required(true)
			.description("Comma-separated list of column names.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("insert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("values").displayName("Values (JSON)")
			.type(ParameterType.JSON).required(true).defaultValue("[]")
			.description("JSON array of row arrays, e.g., [[\"val1\", 42], [\"val2\", 43]].")
			.displayOptions(Map.of("show", Map.of("operation", List.of("insert"))))
			.build());

		// Update parameters
		params.add(NodeParameter.builder()
			.name("updateFields").displayName("Update Fields (JSON)")
			.type(ParameterType.JSON).required(true).defaultValue("{}")
			.description("JSON object of column-value pairs to set, e.g., {\"name\": \"new_name\"}.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("whereClause").displayName("WHERE Clause")
			.type(ParameterType.STRING).required(true)
			.description("The WHERE clause for update/delete (without the WHERE keyword).")
			.placeHolder("id = 123")
			.displayOptions(Map.of("show", Map.of("operation", List.of("update", "delete"))))
			.build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "executeQuery");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getSnowflakeApiUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);
			String schema = context.getParameter("schema", "PUBLIC");
			String database = context.getParameter("database",
				String.valueOf(credentials.getOrDefault("database", "")));
			String warehouse = context.getParameter("warehouse",
				String.valueOf(credentials.getOrDefault("warehouse", "")));

			String sql = switch (operation) {
				case "executeQuery" -> context.getParameter("query", "");
				case "insert" -> buildInsertSql(context, schema);
				case "update" -> buildUpdateSql(context, schema);
				case "delete" -> buildDeleteSql(context, schema);
				default -> throw new IllegalArgumentException("Unknown operation: " + operation);
			};

			// Build request body
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("statement", sql);
			if (!database.isEmpty()) body.put("database", database);
			if (!schema.isEmpty()) body.put("schema", schema);
			if (!warehouse.isEmpty()) body.put("warehouse", warehouse);
			body.put("timeout", 60);

			HttpResponse<String> response = post(baseUrl + "/api/v2/statements", body, headers);

			if (response.statusCode() >= 400) {
				String respBody = response.body() != null ? response.body() : "";
				if (respBody.length() > 300) respBody = respBody.substring(0, 300) + "...";
				return NodeExecutionResult.error("Snowflake API error (HTTP " + response.statusCode() + "): " + respBody);
			}

			Map<String, Object> parsed = parseResponse(response);

			// Extract result data
			Object dataObj = parsed.get("data");
			Object metaObj = parsed.get("resultSetMetaData");

			if (dataObj instanceof List && metaObj instanceof Map) {
				Map<String, Object> meta = (Map<String, Object>) metaObj;
				Object rowType = meta.get("rowType");
				List<String> columnNames = new ArrayList<>();

				if (rowType instanceof List) {
					for (Object col : (List<?>) rowType) {
						if (col instanceof Map) {
							columnNames.add(String.valueOf(((Map<String, Object>) col).getOrDefault("name", "column")));
						}
					}
				}

				List<Map<String, Object>> items = new ArrayList<>();
				for (Object row : (List<?>) dataObj) {
					if (row instanceof List) {
						Map<String, Object> rowMap = new LinkedHashMap<>();
						List<?> rowList = (List<?>) row;
						for (int i = 0; i < rowList.size() && i < columnNames.size(); i++) {
							rowMap.put(columnNames.get(i), rowList.get(i));
						}
						items.add(wrapInJson(rowMap));
					}
				}
				return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
			}

			return NodeExecutionResult.success(List.of(wrapInJson(parsed)));

		} catch (Exception e) {
			return handleError(context, "Snowflake error: " + e.getMessage(), e);
		}
	}

	// ========================= SQL Builders =========================

	private String buildInsertSql(NodeExecutionContext context, String schema) {
		String table = context.getParameter("table", "");
		String columns = context.getParameter("columns", "");
		String valuesJson = context.getParameter("values", "[]");

		String fullTable = !schema.isEmpty() ? schema + "." + table : table;
		StringBuilder sql = new StringBuilder();
		sql.append("INSERT INTO ").append(fullTable);
		sql.append(" (").append(columns).append(") VALUES ");

		List<Object> rows = parseJsonArrayGeneric(valuesJson);
		boolean firstRow = true;
		for (Object row : rows) {
			if (!firstRow) sql.append(", ");
			firstRow = false;
			if (row instanceof List) {
				sql.append("(");
				boolean firstVal = true;
				for (Object val : (List<?>) row) {
					if (!firstVal) sql.append(", ");
					firstVal = false;
					if (val == null) {
						sql.append("NULL");
					} else if (val instanceof Number) {
						sql.append(val);
					} else {
						sql.append("'").append(String.valueOf(val).replace("'", "''")).append("'");
					}
				}
				sql.append(")");
			}
		}

		return sql.toString();
	}

	private String buildUpdateSql(NodeExecutionContext context, String schema) {
		String table = context.getParameter("table", "");
		String updateFieldsJson = context.getParameter("updateFields", "{}");
		String whereClause = context.getParameter("whereClause", "");

		String fullTable = !schema.isEmpty() ? schema + "." + table : table;
		Map<String, Object> updateFields = parseJson(updateFieldsJson);

		StringBuilder sql = new StringBuilder();
		sql.append("UPDATE ").append(fullTable).append(" SET ");

		boolean first = true;
		for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
			if (!first) sql.append(", ");
			first = false;
			sql.append(entry.getKey()).append(" = ");
			Object val = entry.getValue();
			if (val == null) {
				sql.append("NULL");
			} else if (val instanceof Number) {
				sql.append(val);
			} else {
				sql.append("'").append(String.valueOf(val).replace("'", "''")).append("'");
			}
		}

		if (!whereClause.isEmpty()) {
			sql.append(" WHERE ").append(whereClause);
		}

		return sql.toString();
	}

	private String buildDeleteSql(NodeExecutionContext context, String schema) {
		String table = context.getParameter("table", "");
		String whereClause = context.getParameter("whereClause", "");

		String fullTable = !schema.isEmpty() ? schema + "." + table : table;
		StringBuilder sql = new StringBuilder();
		sql.append("DELETE FROM ").append(fullTable);

		if (!whereClause.isEmpty()) {
			sql.append(" WHERE ").append(whereClause);
		}

		return sql.toString();
	}

	// ========================= Helpers =========================

	private String getSnowflakeApiUrl(Map<String, Object> credentials) {
		String account = String.valueOf(credentials.getOrDefault("account", ""));
		// Snowflake SQL REST API URL
		return "https://" + account + ".snowflakecomputing.com";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		headers.put("X-Snowflake-Authorization-Token-Type", "KEYPAIR_JWT");

		String token = String.valueOf(credentials.getOrDefault("token",
			credentials.getOrDefault("accessToken",
				credentials.getOrDefault("jwtToken", ""))));
		if (!token.isEmpty()) {
			headers.put("Authorization", "Bearer " + token);
		}

		return headers;
	}

	private List<Object> parseJsonArrayGeneric(String json) {
		try {
			if (json == null || json.isBlank()) return List.of();
			if (json.trim().startsWith("[")) {
				return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {});
			}
			return List.of();
		} catch (Exception e) {
			return List.of();
		}
	}
}
