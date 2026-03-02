package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractSpringDbNode;
import io.trellis.nodes.core.CacheableNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "springSqlQuery",
	displayName = "Spring SQL Query",
	description = "Execute a parameterized read-only SQL query using Spring NamedParameterJdbcTemplate.",
	category = "Spring Boot",
	icon = "leaf",
	credentials = {"postgresApi", "mysqlApi", "oracleDBApi", "microsoftSql", "crateDb", "questDb", "timescaleDb"}
)
public class SpringSqlQueryNode extends AbstractSpringDbNode implements CacheableNode {

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
				.name("sql").displayName("SQL Query")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 5))
				.placeHolder("SELECT * FROM users WHERE name = :name AND age > :minAge")
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
				.name("readOnly").displayName("Read Only")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.description("Hint to the driver that this is a read-only query.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String sql = context.getParameter("sql", "");
			List<Map<String, Object>> paramDefs = context.getParameter("parameters", List.of());
			MapSqlParameterSource params = buildParams(paramDefs);

			NamedParameterJdbcTemplate template = getTemplate(context);
			List<Map<String, Object>> rows = template.queryForList(sql, params);

			List<Map<String, Object>> results = new ArrayList<>();
			for (Map<String, Object> row : rows) {
				results.add(wrapInJson(row));
			}

			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "Spring SQL Query error: " + e.getMessage(), e);
		}
	}
}
