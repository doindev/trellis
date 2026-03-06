package io.cwc.nodes.base;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.service.DatabaseConnectionPoolService;

public abstract class AbstractSpringDbNode extends AbstractNode {

	@Autowired
	protected DatabaseConnectionPoolService poolService;

	protected NamedParameterJdbcTemplate getTemplate(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		if (credentials == null || credentials.isEmpty()) {
			throw new IllegalStateException(
				"No database credentials configured. Please select a credential in the node settings.");
		}
		String dbType = resolveDbType(context.getCredentialType());
		DataSource ds = poolService.getJdbcPool(credentials, dbType);
		return new NamedParameterJdbcTemplate(ds);
	}

	protected String resolveDbType(String credentialType) {
		if (credentialType == null) return "postgres";
		return switch (credentialType) {
			case "postgresApi" -> "postgres";
			case "mysqlApi" -> "mysql";
			case "oracleDBApi" -> "oracle";
			case "microsoftSql" -> "mssql";
			case "crateDb" -> "cratedb";
			case "questDb" -> "questdb";
			case "timescaleDb" -> "timescaledb";
			default -> "postgres";
		};
	}

	protected MapSqlParameterSource buildParams(List<Map<String, Object>> paramDefs) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		for (Map<String, Object> p : paramDefs) {
			String name = (String) p.get("name");
			Object value = p.get("value");
			String type = (String) p.getOrDefault("type", "string");
			params.addValue(name, coerceType(value, type));
		}
		return params;
	}

	private Object coerceType(Object value, String type) {
		if (value == null) return null;
		String str = String.valueOf(value);
		return switch (type) {
			case "number" -> str.contains(".") ? Double.parseDouble(str) : Long.parseLong(str);
			case "boolean" -> Boolean.parseBoolean(str);
			default -> str;
		};
	}
}
