package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.service.DatabaseConnectionPoolService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@io.trellis.nodes.annotation.Node(
	type = "neo4j",
	displayName = "Neo4j",
	description = "Execute Cypher queries against a Neo4j graph database.",
	category = "Databases",
	icon = "neo4j",
	credentials = {"neo4j"}
)
public class Neo4jNode extends AbstractNode {

	@Autowired
	private DatabaseConnectionPoolService poolService;

	private static final ObjectMapper MAPPER = new ObjectMapper();

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
					ParameterOption.builder().name("Execute Query (Read)").value("executeQuery")
						.description("Execute a read-only Cypher query").build(),
					ParameterOption.builder().name("Execute Statement (Write)").value("executeStatement")
						.description("Execute a write Cypher statement").build()
				)).build(),
			NodeParameter.builder()
				.name("query").displayName("Cypher Query")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 5))
				.placeHolder("MATCH (n:Person) WHERE n.name = $name RETURN n")
				.build(),
			NodeParameter.builder()
				.name("parameters").displayName("Parameters (JSON)")
				.type(ParameterType.JSON)
				.defaultValue("{}")
				.description("Query parameters as a JSON object, e.g. {\"name\": \"Alice\"}")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "executeQuery");
		String query = context.getParameter("query", "");
		String paramsJson = context.getParameter("parameters", "{}");

		try {
			Driver driver = poolService.getNeo4jDriver(credentials);
			String database = (String) credentials.getOrDefault("database", "neo4j");

			SessionConfig sessionConfig = SessionConfig.builder()
				.withDatabase(database)
				.build();

			Map<String, Object> params = parseParameters(paramsJson);

			try (Session session = driver.session(sessionConfig)) {
				if ("executeStatement".equals(operation)) {
					return executeWrite(session, query, params);
				} else {
					return executeRead(session, query, params);
				}
			}
		} catch (Exception e) {
			return handleError(context, "Neo4j error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeRead(Session session, String query, Map<String, Object> params) {
		Result result = session.run(query, params);
		List<Map<String, Object>> items = convertResults(result);
		return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
	}

	private NodeExecutionResult executeWrite(Session session, String query, Map<String, Object> params) {
		Result result = session.run(query, params);
		List<Map<String, Object>> items = convertResults(result);

		var summary = result.consume();
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("nodesCreated", summary.counters().nodesCreated());
		stats.put("nodesDeleted", summary.counters().nodesDeleted());
		stats.put("relationshipsCreated", summary.counters().relationshipsCreated());
		stats.put("relationshipsDeleted", summary.counters().relationshipsDeleted());
		stats.put("propertiesSet", summary.counters().propertiesSet());

		if (items.isEmpty()) {
			return NodeExecutionResult.success(List.of(wrapInJson(stats)));
		}
		// Add stats to last item
		items.add(wrapInJson(Map.of("_stats", stats)));
		return NodeExecutionResult.success(items);
	}

	private List<Map<String, Object>> convertResults(Result result) {
		List<Map<String, Object>> items = new ArrayList<>();
		while (result.hasNext()) {
			Record record = result.next();
			Map<String, Object> row = new LinkedHashMap<>();
			for (String key : record.keys()) {
				row.put(key, convertValue(record.get(key)));
			}
			items.add(wrapInJson(row));
		}
		return items;
	}

	private Object convertValue(Value value) {
		if (value == null || value.isNull()) return null;

		switch (value.type().name()) {
			case "NODE":
				Node node = value.asNode();
				Map<String, Object> nodeMap = new LinkedHashMap<>();
				nodeMap.put("_id", node.elementId());
				nodeMap.put("_labels", node.labels());
				nodeMap.putAll(node.asMap(this::convertValue));
				return nodeMap;
			case "RELATIONSHIP":
				Relationship rel = value.asRelationship();
				Map<String, Object> relMap = new LinkedHashMap<>();
				relMap.put("_id", rel.elementId());
				relMap.put("_type", rel.type());
				relMap.put("_startNodeId", rel.startNodeElementId());
				relMap.put("_endNodeId", rel.endNodeElementId());
				relMap.putAll(rel.asMap(this::convertValue));
				return relMap;
			case "PATH":
				Path path = value.asPath();
				Map<String, Object> pathMap = new LinkedHashMap<>();
				pathMap.put("_length", path.length());
				pathMap.put("_startNodeId", path.start().elementId());
				pathMap.put("_endNodeId", path.end().elementId());
				return pathMap;
			case "LIST":
				return value.asList(this::convertValue);
			case "MAP":
				return value.asMap(this::convertValue);
			default:
				return value.asObject();
		}
	}

	private Map<String, Object> parseParameters(String json) {
		if (json == null || json.isBlank() || "{}".equals(json.trim())) {
			return Map.of();
		}
		try {
			return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
		} catch (Exception e) {
			log.warn("Failed to parse Neo4j parameters JSON: {}", e.getMessage());
			return Map.of();
		}
	}
}
