package io.trellis.nodes.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

/**
 * AWS DynamoDB — manage items in AWS DynamoDB tables.
 */
@Node(
		type = "awsDynamoDb",
		displayName = "AWS DynamoDB",
		description = "Manage items in AWS DynamoDB tables",
		category = "AWS Services",
		icon = "awsDynamoDb",
		credentials = {"awsApi"}
)
public class AwsDynamoDbNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String operation = context.getParameter("operation", "get");

		DynamoDbClient client = DynamoDbClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "upsert" -> handleUpsert(context, client);
					case "delete" -> handleDelete(context, client);
					case "get" -> handleGet(context, client);
					case "getAll" -> handleGetAll(context, client);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		client.close();
		return NodeExecutionResult.success(results);
	}

	// ========================= Item Operations =========================

	private Map<String, Object> handleUpsert(NodeExecutionContext context, DynamoDbClient client) {
		String tableName = context.getParameter("tableName", "");
		String itemData = context.getParameter("itemData", "{}");
		String conditionExpression = context.getParameter("conditionExpression", "");
		String expressionAttributeValues = context.getParameter("expressionAttributeValues", "");

		Map<String, AttributeValue> item = jsonToAttributeValueMap(itemData);

		PutItemRequest.Builder builder = PutItemRequest.builder()
				.tableName(tableName)
				.item(item);

		if (!conditionExpression.isBlank()) {
			builder.conditionExpression(conditionExpression);
		}

		if (!expressionAttributeValues.isBlank()) {
			builder.expressionAttributeValues(jsonToAttributeValueMap(expressionAttributeValues));
		}

		client.putItem(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("tableName", tableName);
		return result;
	}

	private Map<String, Object> handleDelete(NodeExecutionContext context, DynamoDbClient client) {
		String tableName = context.getParameter("tableName", "");
		String keyData = context.getParameter("keyData", "{}");
		String conditionExpression = context.getParameter("conditionExpression", "");
		String expressionAttributeValues = context.getParameter("expressionAttributeValues", "");

		Map<String, AttributeValue> key = jsonToAttributeValueMap(keyData);

		DeleteItemRequest.Builder builder = DeleteItemRequest.builder()
				.tableName(tableName)
				.key(key);

		if (!conditionExpression.isBlank()) {
			builder.conditionExpression(conditionExpression);
		}

		if (!expressionAttributeValues.isBlank()) {
			builder.expressionAttributeValues(jsonToAttributeValueMap(expressionAttributeValues));
		}

		builder.returnValues(ReturnValue.ALL_OLD);
		DeleteItemResponse response = client.deleteItem(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("tableName", tableName);
		if (response.attributes() != null && !response.attributes().isEmpty()) {
			result.put("deletedItem", attributeValueMapToJson(response.attributes()));
		}
		return result;
	}

	private Map<String, Object> handleGet(NodeExecutionContext context, DynamoDbClient client) {
		String tableName = context.getParameter("tableName", "");
		String keyData = context.getParameter("keyData", "{}");
		String projectionExpression = context.getParameter("projectionExpression", "");
		boolean consistentRead = toBoolean(context.getParameters().get("consistentRead"), false);

		Map<String, AttributeValue> key = jsonToAttributeValueMap(keyData);

		GetItemRequest.Builder builder = GetItemRequest.builder()
				.tableName(tableName)
				.key(key)
				.consistentRead(consistentRead);

		if (!projectionExpression.isBlank()) {
			builder.projectionExpression(projectionExpression);
		}

		GetItemResponse response = client.getItem(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		if (response.item() != null && !response.item().isEmpty()) {
			result.putAll(attributeValueMapToJson(response.item()));
		} else {
			result.put("_empty", true);
		}
		return result;
	}

	private Map<String, Object> handleGetAll(NodeExecutionContext context, DynamoDbClient client) {
		String tableName = context.getParameter("tableName", "");
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		String filterExpression = context.getParameter("filterExpression", "");
		String projectionExpression = context.getParameter("projectionExpression", "");
		String expressionAttributeValues = context.getParameter("expressionAttributeValues", "");
		String expressionAttributeNames = context.getParameter("expressionAttributeNames", "");

		ScanRequest.Builder builder = ScanRequest.builder()
				.tableName(tableName);

		if (!returnAll) {
			builder.limit(limit);
		}

		if (!filterExpression.isBlank()) {
			builder.filterExpression(filterExpression);
		}

		if (!projectionExpression.isBlank()) {
			builder.projectionExpression(projectionExpression);
		}

		if (!expressionAttributeValues.isBlank()) {
			builder.expressionAttributeValues(jsonToAttributeValueMap(expressionAttributeValues));
		}

		if (!expressionAttributeNames.isBlank()) {
			builder.expressionAttributeNames(parseExpressionAttributeNames(expressionAttributeNames));
		}

		List<Map<String, Object>> items = new ArrayList<>();
		Map<String, AttributeValue> lastEvaluatedKey = null;
		do {
			if (lastEvaluatedKey != null) {
				builder.exclusiveStartKey(lastEvaluatedKey);
			}
			ScanResponse response = client.scan(builder.build());
			for (Map<String, AttributeValue> dynamoItem : response.items()) {
				items.add(attributeValueMapToJson(dynamoItem));
				if (!returnAll && items.size() >= limit) break;
			}
			lastEvaluatedKey = response.lastEvaluatedKey();
			if (lastEvaluatedKey != null && lastEvaluatedKey.isEmpty()) {
				lastEvaluatedKey = null;
			}
		} while (returnAll && lastEvaluatedKey != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("items", items);
		result.put("count", items.size());
		return result;
	}

	// ========================= AttributeValue Helpers =========================

	@SuppressWarnings("unchecked")
	private Map<String, AttributeValue> jsonToAttributeValueMap(String json) {
		try {
			Map<String, Object> map = MAPPER.readValue(json, Map.class);
			return convertToAttributeValueMap(map);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, AttributeValue> convertToAttributeValueMap(Map<String, Object> map) {
		Map<String, AttributeValue> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			result.put(entry.getKey(), toAttributeValue(entry.getValue()));
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private AttributeValue toAttributeValue(Object value) {
		if (value == null) {
			return AttributeValue.builder().nul(true).build();
		}
		if (value instanceof String s) {
			return AttributeValue.builder().s(s).build();
		}
		if (value instanceof Number n) {
			return AttributeValue.builder().n(n.toString()).build();
		}
		if (value instanceof Boolean b) {
			return AttributeValue.builder().bool(b).build();
		}
		if (value instanceof List<?> list) {
			List<AttributeValue> attributeValues = new ArrayList<>();
			for (Object item : list) {
				attributeValues.add(toAttributeValue(item));
			}
			return AttributeValue.builder().l(attributeValues).build();
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, AttributeValue> attributeMap = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
				attributeMap.put(entry.getKey(), toAttributeValue(entry.getValue()));
			}
			return AttributeValue.builder().m(attributeMap).build();
		}
		return AttributeValue.builder().s(String.valueOf(value)).build();
	}

	private Map<String, Object> attributeValueMapToJson(Map<String, AttributeValue> attributeMap) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, AttributeValue> entry : attributeMap.entrySet()) {
			result.put(entry.getKey(), fromAttributeValue(entry.getValue()));
		}
		return result;
	}

	private Object fromAttributeValue(AttributeValue value) {
		if (value.s() != null) {
			return value.s();
		}
		if (value.n() != null) {
			String num = value.n();
			if (num.contains(".")) {
				return Double.parseDouble(num);
			}
			return Long.parseLong(num);
		}
		if (value.bool() != null) {
			return value.bool();
		}
		if (Boolean.TRUE.equals(value.nul())) {
			return null;
		}
		if (value.hasL()) {
			List<Object> list = new ArrayList<>();
			for (AttributeValue item : value.l()) {
				list.add(fromAttributeValue(item));
			}
			return list;
		}
		if (value.hasM()) {
			return attributeValueMapToJson(value.m());
		}
		if (value.hasSs()) {
			return value.ss();
		}
		if (value.hasNs()) {
			return value.ns();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> parseExpressionAttributeNames(String json) {
		try {
			return MAPPER.readValue(json, Map.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid expression attribute names JSON: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Upsert").value("upsert")
										.description("Create or replace an item").build(),
								ParameterOption.builder().name("Delete").value("delete")
										.description("Delete an item by key").build(),
								ParameterOption.builder().name("Get").value("get")
										.description("Get an item by key").build(),
								ParameterOption.builder().name("Get All").value("getAll")
										.description("Scan and return all items").build()
						)).build(),
				NodeParameter.builder()
						.name("tableName").displayName("Table Name")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("The name of the DynamoDB table.").build(),
				NodeParameter.builder()
						.name("keyData").displayName("Key (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("The primary key of the item as JSON (e.g. {\"id\": \"123\"}).").build(),
				NodeParameter.builder()
						.name("itemData").displayName("Item Data (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("The full item data as JSON for upsert (must include the key).").build(),
				NodeParameter.builder()
						.name("conditionExpression").displayName("Condition Expression")
						.type(ParameterType.STRING).defaultValue("")
						.description("A condition that must be satisfied for the operation to succeed.").build(),
				NodeParameter.builder()
						.name("filterExpression").displayName("Filter Expression")
						.type(ParameterType.STRING).defaultValue("")
						.description("A filter expression for the scan operation.").build(),
				NodeParameter.builder()
						.name("projectionExpression").displayName("Projection Expression")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of attributes to retrieve.").build(),
				NodeParameter.builder()
						.name("expressionAttributeValues").displayName("Expression Attribute Values (JSON)")
						.type(ParameterType.JSON).defaultValue("")
						.description("Substitution values for expression placeholders as JSON (e.g. {\":val\": \"foo\"}).").build(),
				NodeParameter.builder()
						.name("expressionAttributeNames").displayName("Expression Attribute Names (JSON)")
						.type(ParameterType.JSON).defaultValue("")
						.description("Substitution names for reserved words as JSON (e.g. {\"#s\": \"status\"}).").build(),
				NodeParameter.builder()
						.name("consistentRead").displayName("Consistent Read")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to use strongly consistent reads.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results (paginate automatically).").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of items to return.").build()
		);
	}
}
