package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.CacheableNode;
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
@Node(
	type = "mongoDb",
	displayName = "MongoDB",
	description = "Interact with a MongoDB database.",
	category = "Databases",
	icon = "mongo",
	credentials = {"mongoDb"}
)
public class MongoDbNode extends AbstractNode implements CacheableNode {

	@Autowired
	private DatabaseConnectionPoolService poolService;

	@Override
	public Map<String, List<Object>> cacheDisplayOptions() {
		return Map.of("operation", List.of("find", "aggregate", "count"));
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
				.defaultValue("find")
				.options(List.of(
					ParameterOption.builder().name("Find").value("find").description("Find documents matching a filter").build(),
					ParameterOption.builder().name("Insert").value("insert").description("Insert a document").build(),
					ParameterOption.builder().name("Update").value("update").description("Update documents").build(),
					ParameterOption.builder().name("Delete").value("delete").description("Delete documents").build(),
					ParameterOption.builder().name("Aggregate").value("aggregate").description("Run an aggregation pipeline").build(),
					ParameterOption.builder().name("Count").value("count").description("Count documents matching a filter").build()
				)).build(),
			NodeParameter.builder()
				.name("collection").displayName("Collection")
				.type(ParameterType.STRING).required(true)
				.build(),
			NodeParameter.builder()
				.name("filter").displayName("Filter (JSON)")
				.type(ParameterType.JSON)
				.defaultValue("{}")
				.displayOptions(Map.of("show", Map.of("operation", List.of("find", "update", "delete", "count"))))
				.build(),
			NodeParameter.builder()
				.name("projection").displayName("Projection (JSON)")
				.type(ParameterType.JSON)
				.displayOptions(Map.of("show", Map.of("operation", List.of("find"))))
				.build(),
			NodeParameter.builder()
				.name("sort").displayName("Sort (JSON)")
				.type(ParameterType.JSON)
				.displayOptions(Map.of("show", Map.of("operation", List.of("find"))))
				.build(),
			NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(0)
				.displayOptions(Map.of("show", Map.of("operation", List.of("find"))))
				.build(),
			NodeParameter.builder()
				.name("document").displayName("Document (JSON)")
				.type(ParameterType.JSON).required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("insert", "update"))))
				.build(),
			NodeParameter.builder()
				.name("pipeline").displayName("Pipeline (JSON Array)")
				.type(ParameterType.JSON).required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("aggregate"))))
				.build(),
			NodeParameter.builder()
				.name("options").displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder().name("updateAll").displayName("Update All Matching")
						.type(ParameterType.BOOLEAN).defaultValue(false).build()
				)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "find");
		String collectionName = context.getParameter("collection", "");

		try {
			MongoClient client = poolService.getMongoClient(credentials);
			String dbName = (String) credentials.getOrDefault("database", "test");
			MongoDatabase db = client.getDatabase(dbName);
			MongoCollection<Document> collection = db.getCollection(collectionName);

			return switch (operation) {
				case "find" -> doFind(collection, context);
				case "insert" -> doInsert(collection, context);
				case "update" -> doUpdate(collection, context);
				case "delete" -> doDelete(collection, context);
				case "aggregate" -> doAggregate(collection, context);
				case "count" -> doCount(collection, context);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "MongoDB error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult doFind(MongoCollection<Document> collection, NodeExecutionContext context) {
		Document filter = parseDocument(context.getParameter("filter", "{}"));
		Document projection = parseDocument(context.getParameter("projection", null));
		Document sort = parseDocument(context.getParameter("sort", null));
		int limit = toInt(context.getParameter("limit", 0), 0);

		var cursor = collection.find(filter);
		if (projection != null) cursor = cursor.projection(projection);
		if (sort != null) cursor = cursor.sort(sort);
		if (limit > 0) cursor = cursor.limit(limit);

		List<Map<String, Object>> results = new ArrayList<>();
		for (Document doc : cursor) {
			results.add(wrapInJson(documentToMap(doc)));
		}
		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	private NodeExecutionResult doInsert(MongoCollection<Document> collection, NodeExecutionContext context) {
		Document doc = parseDocument(context.getParameter("document", "{}"));
		collection.insertOne(doc);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("insertedId", doc.getObjectId("_id") != null ? doc.getObjectId("_id").toHexString() : null);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult doUpdate(MongoCollection<Document> collection, NodeExecutionContext context) {
		Document filter = parseDocument(context.getParameter("filter", "{}"));
		Document update = parseDocument(context.getParameter("document", "{}"));

		// Wrap in $set if not already using update operators
		if (update.keySet().stream().noneMatch(k -> k.startsWith("$"))) {
			update = new Document("$set", update);
		}

		Map<String, Object> opts = context.getParameter("options", Map.of());
		boolean updateAll = Boolean.TRUE.equals(opts.get("updateAll"));

		UpdateResult result;
		if (updateAll) {
			result = collection.updateMany(filter, update);
		} else {
			result = collection.updateOne(filter, update);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
			"matchedCount", result.getMatchedCount(),
			"modifiedCount", result.getModifiedCount()
		))));
	}

	private NodeExecutionResult doDelete(MongoCollection<Document> collection, NodeExecutionContext context) {
		Document filter = parseDocument(context.getParameter("filter", "{}"));
		DeleteResult result = collection.deleteMany(filter);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("deletedCount", result.getDeletedCount()))));
	}

	private NodeExecutionResult doAggregate(MongoCollection<Document> collection, NodeExecutionContext context) {
		String pipelineJson = context.getParameter("pipeline", "[]");
		List<Document> pipeline = Document.parse("{\"p\":" + pipelineJson + "}").getList("p", Document.class);

		List<Map<String, Object>> results = new ArrayList<>();
		for (Document doc : collection.aggregate(pipeline)) {
			results.add(wrapInJson(documentToMap(doc)));
		}
		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	private NodeExecutionResult doCount(MongoCollection<Document> collection, NodeExecutionContext context) {
		Document filter = parseDocument(context.getParameter("filter", "{}"));
		long count = collection.countDocuments(filter);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("count", count))));
	}

	private Document parseDocument(String json) {
		if (json == null || json.isBlank()) return null;
		return Document.parse(json);
	}

	private Map<String, Object> documentToMap(Document doc) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : doc.entrySet()) {
			Object val = entry.getValue();
			if (val instanceof Document) {
				map.put(entry.getKey(), documentToMap((Document) val));
			} else if (val instanceof org.bson.types.ObjectId) {
				map.put(entry.getKey(), val.toString());
			} else {
				map.put(entry.getKey(), val);
			}
		}
		return map;
	}
}
