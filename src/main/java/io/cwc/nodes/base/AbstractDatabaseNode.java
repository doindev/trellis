package io.cwc.nodes.base;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;

import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.service.DatabaseConnectionPoolService;

public abstract class AbstractDatabaseNode extends AbstractNode {

	@Autowired(required = false)
	private DatabaseConnectionPoolService poolService;

	// gets a pooled connection if pool service is available, otherwise creates a direct one
	protected Connection getPooledConnection(Map<String, Object> credentials, String dbType) throws SQLException {
		if (poolService != null) {
			DataSource ds = poolService.getJdbcPool(credentials, dbType);
			return ds.getConnection();
		}
		return createConnection(credentials);
	}

	// create a database connection from credentials
	protected Connection createConnection(Map<String, Object> credentials) throws SQLException {
		String host = (String) credentials.getOrDefault("host",  "localhost");
		int port = toInt(credentials.get("port"), getDefaultPort());
		String database = (String) credentials.get("database");
		String user = (String) credentials.getOrDefault("username", credentials.get("user"));
		String password = (String) credentials.get("password");
		String jdbcUrl = buildJdbcUrl(host, port, database, credentials);

		Properties props = new Properties();
		if (user != null) props.setProperty("user",  user);
		if (password != null) props.setProperty("password",  password);

		addConnectionProperties(props, credentials);

		return DriverManager.getConnection(jdbcUrl, props);
	}
	
	// builds the JDBC url for the specific database
	protected abstract String buildJdbcUrl(String host, int port, String database, Map<String, Object> credentials);
	
	
	// return the default port for the specific database
	protected abstract int getDefaultPort();
	
	// adds database-specifc connection properties
	protected void addConnectionProperties(Properties props, Map<String, Object> credentials) {
		// override in subclases if needed
	}
	
	// executes a query and returns results as items
	protected List<Map<String, Object>> executeQuery(Connection conn, String sql, List<Object> params) throws SQLException {
		List<Map<String, Object>> items = new ArrayList<>();
		
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			setParameters(stmt, params);
			
			try (ResultSet rs = stmt.executeQuery()) {
				ResultSetMetaData meta = rs.getMetaData();
				int columnCount = meta.getColumnCount();
				
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int i = 1; i <= columnCount; i++) {
						String columnName = meta.getColumnLabel(i);
						Object value = rs.getObject(i);
						row.put(columnName,  convertSqlValue(value));
					}
					items.add(wrapInJson(row));
				}
			}
		}
			
		return items;
	}
	
	// executes an update/insert/delete statement
	protected int executeUpdate(Connection conn, String sql, List<Object> params) throws Exception {
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			setParameters(stmt, params);
			return stmt.executeUpdate();
		}
	}
	
	// executes an insert and returns generated keys
	protected List<Map<String, Object>> executeInsertReturning(Connection conn, String sql, List<Object> params) throws SQLException {
		List<Map<String, Object>> items = new ArrayList<>();
		
		try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			setParameters(stmt, params);
			
			try (ResultSet rs = stmt.getGeneratedKeys()) {
				ResultSetMetaData meta = rs.getMetaData();
				int columnCount = meta.getColumnCount();
				
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int i = 1; i <= columnCount; i++) {
						String columnName = meta.getColumnLabel(i);
						if (columnName == null || columnName.isEmpty()) {
							columnName = "generated_key_" + i;
						}
						row.put(columnName,  rs.getObject(i));
					}
					items.add(wrapInJson(row));
				}
			}
		}
		
		return items;
	}
	
	// sets parameters on a prepared statement
	protected void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
		if (params == null) return;

		for (int i = 0; i < params.size(); i++) {
			Object param = params.get(i);

			if (param == null) {
				stmt.setNull(i + 1, Types.NULL);
			} else if (param instanceof String strVal) {
				Timestamp ts = tryParseTimestamp(strVal);
				if (ts != null) {
					stmt.setTimestamp(i + 1, ts);
				} else {
					stmt.setString(i + 1, strVal);
				}
			} else if (param instanceof Integer) {
				stmt.setInt(i + 1,  (Integer) param);
			} else if (param instanceof Long) {
				stmt.setLong(i + 1,  (Long) param);
			} else if (param instanceof Double) {
				stmt.setDouble(i + 1,  (Double) param);
			} else if (param instanceof Float) {
				stmt.setFloat(i + 1,  (Float) param);
			} else if (param instanceof Boolean) {
				stmt.setBoolean(i + 1,  (Boolean) param);
			} else if (param instanceof Date) {
				stmt.setTimestamp(i + 1,  new Timestamp(((Date) param).getTime()));
			} else if (param instanceof byte[]) {
				stmt.setBytes(i + 1,  (byte[]) param);
			} else {
				stmt.setObject(i + 1,  param);
			}
		}
	}
	
	// Timestamp patterns: "2026-02-24 19:03:58", "2026-02-24 19:03:58.608", "2026-02-24T19:03:58.608Z"
	private static final java.util.regex.Pattern TIMESTAMP_PATTERN = java.util.regex.Pattern.compile(
			"^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?$"
	);

	private Timestamp tryParseTimestamp(String value) {
		if (value == null || value.length() < 19 || !TIMESTAMP_PATTERN.matcher(value).matches()) {
			return null;
		}
		try {
			// Handle ISO 8601 with timezone (e.g. "2026-02-24T19:03:58.608Z")
			if (value.contains("T")) {
				java.time.Instant instant = java.time.Instant.parse(value);
				return Timestamp.from(instant);
			}
			// Handle "YYYY-MM-DD HH:MM:SS" or "YYYY-MM-DD HH:MM:SS.nnn"
			return Timestamp.valueOf(value);
		} catch (Exception e) {
			return null;
		}
	}

	// converts SQL value types to JSON-compatible types
	protected Object convertSqlValue(Object value) {
		if (value == null) return null;
		if (value instanceof java.sql.Timestamp) {
			return ((java.sql.Timestamp) value).toInstant().toString();
		}
		if (value instanceof java.sql.Date) {
			return ((java.sql.Date) value).toLocalDate().toString();
		}
		if (value instanceof java.sql.Time) {
			return ((java.sql.Time) value).toLocalTime().toString();
		}
		if (value instanceof java.sql.Clob) {
			try {
				java.sql.Clob clob = (java.sql.Clob)value;
				return clob.getSubString(1,  (int) clob.length());
			} catch (SQLException e) {
				return value.toString();
			}
		}
		if (value instanceof java.sql.Blob) {
			try {
				java.sql.Blob blob = (java.sql.Blob)value;
				return Base64.getEncoder().encodeToString(blob.getBytes(1,  (int) blob.length()));
			} catch (SQLException e) {
				return null;
			}
		}
		if (value instanceof BigDecimal) {
			return ((BigDecimal) value).doubleValue();
		}
		return value;
	}
	
	// build INSERT statement from column-value pairs
	protected String buildInsertSql(String table, Map<String, Object> values) {
		StringBuilder sql = new StringBuilder("INSERT INTO ");
		sql
			.append(quoteIdentifier(table))
			.append(" (");
		
		StringBuilder valuesPart = new StringBuilder(" VALUES (");
		boolean first = true;
		
		for (String column : values.keySet()) {
			if (!first) {
				sql.append(", ");
				 valuesPart.append(", ");
			}
			sql.append(quoteIdentifier(column));
			valuesPart.append("?");
			first = false;
		}
		
		sql
			.append(")")
			.append(valuesPart)
			.append(")");
		
		return sql.toString();
	}
	
	// builds UPDATE statement from column-value pairs
	protected String buildUpdateSql(String table, Map<String, Object> values, String whereClause) {
		StringBuilder sql = new StringBuilder("UPDATE ");
		sql
			.append(quoteIdentifier(table))
			.append(" SET ");
	
		boolean first = true;
		
		for (String column : values.keySet()) {
			if (!first) sql.append(", ");
			sql
				.append(quoteIdentifier(column))
				.append(" = ?");
			first = false;
		}
		
		if (whereClause != null && !whereClause.isEmpty()) {
			sql
				.append(" WHERE ")
				.append(whereClause);
		}
		
		return sql.toString();
	}
	
	// quotes an identifier (table/column) for the specific database
	protected String quoteIdentifier(String identifier) {
		return "\"" + identifier.replace("\"", "\"\"") + "\"";
	}

	// extracts query parameters from a FIXED_COLLECTION "queryParameters" param
	protected List<Object> extractQueryParams(NodeExecutionContext context) {
		List<Map<String, Object>> paramDefs = context.getParameter("queryParameters", List.of());
		List<Object> params = new ArrayList<>();
		for (Map<String, Object> p : paramDefs) {
			String type = (String) p.getOrDefault("type", "string");
			Object value = p.get("value");
			params.add(coerceParamType(value, type));
		}
		return params;
	}

	private Object coerceParamType(Object value, String type) {
		if (value == null) return null;
		String str = String.valueOf(value);
		return switch (type) {
			case "number" -> str.contains(".") ? Double.parseDouble(str) : Long.parseLong(str);
			case "boolean" -> Boolean.parseBoolean(str);
			default -> str;
		};
	}

	// builds a parameterized WHERE clause from structured conditions
	protected record WhereResult(String sql, List<Object> params) {}

	protected WhereResult buildWhereClause(List<Map<String, Object>> conditions) {
		StringBuilder sql = new StringBuilder();
		List<Object> params = new ArrayList<>();
		for (int i = 0; i < conditions.size(); i++) {
			if (i > 0) sql.append(" AND ");
			Map<String, Object> cond = conditions.get(i);
			String column = (String) cond.get("column");
			String operator = (String) cond.getOrDefault("operator", "=");
			sql.append(quoteIdentifier(column)).append(" ").append(operator);
			if (!"IS NULL".equals(operator) && !"IS NOT NULL".equals(operator)) {
				sql.append(" ?");
				params.add(cond.get("value"));
			}
		}
		return new WhereResult(sql.toString(), params);
	}

}
