package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractSpringDbNode;
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
	type = "springBatchSql",
	displayName = "Spring Batch SQL",
	description = "Execute a parameterized SQL statement once per input item using Spring NamedParameterJdbcTemplate.batchUpdate().",
	category = "Spring Boot",
	icon = "layers",
	credentials = {"postgresApi", "mysqlApi", "oracleDBApi", "microsoftSql", "crateDb", "questDb", "timescaleDb"}
)
public class SpringBatchSqlNode extends AbstractSpringDbNode {

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
				.name("sql").displayName("SQL Statement")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 5))
				.placeHolder("INSERT INTO logs (user_id, action) VALUES (:userId, :action)")
				.description("Use :paramName syntax. Parameters are mapped from each input item via the mappings below.")
				.build(),
			NodeParameter.builder()
				.name("parameterMappings").displayName("Parameter Mappings")
				.type(ParameterType.FIXED_COLLECTION)
				.description("Map SQL parameter names to JSON paths in the input data.")
				.nestedParameters(List.of(
					NodeParameter.builder().name("name").displayName("Parameter Name")
						.type(ParameterType.STRING)
						.placeHolder("userId")
						.description("The :paramName used in the SQL statement.")
						.build(),
					NodeParameter.builder().name("jsonPath").displayName("JSON Path")
						.type(ParameterType.STRING)
						.placeHolder("id")
						.description("Path to the value in the input item (e.g. 'id' or 'metadata.action').")
						.build(),
					NodeParameter.builder().name("type").displayName("Type").type(ParameterType.OPTIONS)
						.defaultValue("string")
						.options(List.of(
							ParameterOption.builder().name("String").value("string").build(),
							ParameterOption.builder().name("Number").value("number").build(),
							ParameterOption.builder().name("Boolean").value("boolean").build()
						)).build()
				)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String sql = context.getParameter("sql", "");
			List<Map<String, Object>> mappings = context.getParameter("parameterMappings", List.of());
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData == null || inputData.isEmpty()) {
				return NodeExecutionResult.empty();
			}

			List<SqlParameterSource> batchParams = new ArrayList<>();
			for (Map<String, Object> item : inputData) {
				Map<String, Object> unwrapped = unwrapJson(item);
				MapSqlParameterSource params = new MapSqlParameterSource();
				for (Map<String, Object> mapping : mappings) {
					String name = (String) mapping.get("name");
					String jsonPath = (String) mapping.get("jsonPath");
					String type = (String) mapping.getOrDefault("type", "string");
					Object value = getNestedValue(unwrapped, jsonPath);
					params.addValue(name, coerceBatchType(value, type));
				}
				batchParams.add(params);
			}

			NamedParameterJdbcTemplate template = getTemplate(context);
			int[] results = template.batchUpdate(sql, batchParams.toArray(new SqlParameterSource[0]));

			List<Map<String, Object>> output = new ArrayList<>();
			int totalAffected = 0;
			for (int i = 0; i < results.length; i++) {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("index", i);
				row.put("affectedRows", results[i]);
				output.add(wrapInJson(row));
				totalAffected += Math.max(results[i], 0);
			}

			log.debug("Batch SQL executed: {} items, {} total rows affected", results.length, totalAffected);
			return NodeExecutionResult.success(output);
		} catch (Exception e) {
			return handleError(context, "Spring Batch SQL error: " + e.getMessage(), e);
		}
	}

	private Object coerceBatchType(Object value, String type) {
		if (value == null) return null;
		String str = String.valueOf(value);
		return switch (type) {
			case "number" -> str.contains(".") ? Double.parseDouble(str) : Long.parseLong(str);
			case "boolean" -> Boolean.parseBoolean(str);
			default -> str;
		};
	}
}
