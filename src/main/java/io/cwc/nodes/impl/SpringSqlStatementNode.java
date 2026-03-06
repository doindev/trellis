package io.cwc.nodes.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

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
	type = "springSqlStatement",
	displayName = "Spring SQL Statement",
	description = "Execute a parameterized SQL write statement using Spring NamedParameterJdbcTemplate.",
	category = "Spring Boot",
	icon = "leaf",
	credentials = {"postgresApi", "mysqlApi", "oracleDBApi", "microsoftSql", "crateDb", "questDb", "timescaleDb"}
)
public class SpringSqlStatementNode extends AbstractSpringDbNode {

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
				.placeHolder("INSERT INTO users (name, email) VALUES (:name, :email)")
				.description("Use :paramName syntax for named parameters.")
				.build(),
			NodeParameter.builder()
				.name("parameters").displayName("Parameters")
				.type(ParameterType.FIXED_COLLECTION)
				.description("Bind named parameters to values. Name must match :paramName in the SQL.")
				.nestedParameters(List.of(
					NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
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
				.name("returnGeneratedKeys").displayName("Return Generated Keys")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.description("When true, returns auto-generated keys (e.g. auto-increment IDs) from INSERT statements.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String sql = context.getParameter("sql", "");
			List<Map<String, Object>> paramDefs = context.getParameter("parameters", List.of());
			boolean returnKeys = context.getParameter("returnGeneratedKeys", false);
			MapSqlParameterSource params = buildParams(paramDefs);

			NamedParameterJdbcTemplate template = getTemplate(context);

			if (returnKeys) {
				KeyHolder keyHolder = new GeneratedKeyHolder();
				int affected = template.update(sql, params, keyHolder);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("affectedRows", affected);
				Map<String, Object> keys = keyHolder.getKeys();
				if (keys != null) {
					result.put("generatedKeys", keys);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			} else {
				int affected = template.update(sql, params);
				return NodeExecutionResult.success(List.of(wrapInJson(Map.of("affectedRows", affected))));
			}
		} catch (Exception e) {
			return handleError(context, "Spring SQL Statement error: " + e.getMessage(), e);
		}
	}
}
