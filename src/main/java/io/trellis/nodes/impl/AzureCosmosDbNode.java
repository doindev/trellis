package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Azure Cosmos DB Node -- manage databases, containers, and items
 * via the Azure Cosmos DB REST API with HMAC-SHA256 authentication.
 */
@Slf4j
@Node(
	type = "azureCosmosDb",
	displayName = "Azure Cosmos DB",
	description = "Manage databases, containers, and items in Azure Cosmos DB",
	category = "Data & Storage / Databases",
	icon = "azureCosmosDb",
	credentials = {"azureCosmosDbApi"}
)
public class AzureCosmosDbNode extends AbstractApiNode {

	private static final DateTimeFormatter RFC_1123_FORMATTER = DateTimeFormatter.ofPattern(
			"EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

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

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("item")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Container").value("container").description("Manage containers").build(),
				ParameterOption.builder().name("Database").value("database").description("Manage databases").build(),
				ParameterOption.builder().name("Item").value("item").description("Manage items (documents)").build()
			)).build());

		// Container operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all containers in a database").build()
			)).build());

		// Database operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("database"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all databases").build()
			)).build());

		// Item operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("item"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an item").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an item").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an item").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many items using SQL query").build(),
				ParameterOption.builder().name("Update").value("update").description("Replace an item").build(),
				ParameterOption.builder().name("Upsert").value("upsert").description("Create or replace an item").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("databaseId").displayName("Database ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the Cosmos DB database.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container", "item"))))
			.build());

		params.add(NodeParameter.builder()
			.name("containerId").displayName("Container ID")
			.type(ParameterType.STRING)
			.description("The ID of the container (collection).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("item"))))
			.build());

		params.add(NodeParameter.builder()
			.name("itemId").displayName("Item ID")
			.type(ParameterType.STRING)
			.description("The ID of the item (document).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("item"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("partitionKey").displayName("Partition Key Value")
			.type(ParameterType.STRING).required(true)
			.description("The partition key value for the item.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("item"))))
			.build());

		params.add(NodeParameter.builder()
			.name("itemBody").displayName("Item Body (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("JSON document body for the item.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("item"), "operation", List.of("create", "update", "upsert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sqlQuery").displayName("SQL Query")
			.type(ParameterType.STRING)
			.description("Cosmos DB SQL query (e.g. SELECT * FROM c WHERE c.status = 'active').")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("item"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("maxItemCount").displayName("Max Item Count")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Maximum number of items to return per page.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "item");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String endpoint = String.valueOf(credentials.getOrDefault("endpoint",
					credentials.getOrDefault("accountEndpoint", "")));
			if (endpoint.endsWith("/")) {
				endpoint = endpoint.substring(0, endpoint.length() - 1);
			}
			String masterKey = String.valueOf(credentials.getOrDefault("accountKey",
					credentials.getOrDefault("key", "")));

			return switch (resource) {
				case "container" -> executeContainer(context, operation, endpoint, masterKey);
				case "database" -> executeDatabase(context, operation, endpoint, masterKey);
				case "item" -> executeItem(context, operation, endpoint, masterKey);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Azure Cosmos DB API error: " + e.getMessage(), e);
		}
	}

	// ========================= Container Operations =========================

	private NodeExecutionResult executeContainer(NodeExecutionContext context, String operation,
			String endpoint, String masterKey) throws Exception {
		if ("getAll".equals(operation)) {
			String databaseId = context.getParameter("databaseId", "");
			String resourceLink = "dbs/" + databaseId;
			String resourcePath = resourceLink + "/colls";
			Map<String, String> headers = cosmosHeaders("GET", "colls", resourceLink, masterKey);
			HttpResponse<String> response = get(endpoint + "/" + resourcePath, headers);
			return toArrayResult(response, "DocumentCollections");
		}
		return NodeExecutionResult.error("Unknown container operation: " + operation);
	}

	// ========================= Database Operations =========================

	private NodeExecutionResult executeDatabase(NodeExecutionContext context, String operation,
			String endpoint, String masterKey) throws Exception {
		if ("getAll".equals(operation)) {
			Map<String, String> headers = cosmosHeaders("GET", "dbs", "", masterKey);
			HttpResponse<String> response = get(endpoint + "/dbs", headers);
			return toArrayResult(response, "Databases");
		}
		return NodeExecutionResult.error("Unknown database operation: " + operation);
	}

	// ========================= Item Operations =========================

	private NodeExecutionResult executeItem(NodeExecutionContext context, String operation,
			String endpoint, String masterKey) throws Exception {
		String databaseId = context.getParameter("databaseId", "");
		String containerId = context.getParameter("containerId", "");
		String partitionKey = context.getParameter("partitionKey", "");
		String collLink = "dbs/" + databaseId + "/colls/" + containerId;

		switch (operation) {
			case "create": {
				String itemBodyJson = context.getParameter("itemBody", "{}");
				Map<String, Object> body = parseJson(itemBodyJson);
				Map<String, String> headers = cosmosHeaders("POST", "docs", collLink, masterKey);
				headers.put("x-ms-documentdb-partitionkey", "[\"" + partitionKey + "\"]");
				HttpResponse<String> response = post(endpoint + "/" + collLink + "/docs", body, headers);
				return toResult(response);
			}
			case "delete": {
				String itemId = context.getParameter("itemId", "");
				String docLink = collLink + "/docs/" + itemId;
				Map<String, String> headers = cosmosHeaders("DELETE", "docs", docLink, masterKey);
				headers.put("x-ms-documentdb-partitionkey", "[\"" + partitionKey + "\"]");
				HttpResponse<String> response = delete(endpoint + "/" + docLink, headers);
				return toDeleteResult(response, itemId);
			}
			case "get": {
				String itemId = context.getParameter("itemId", "");
				String docLink = collLink + "/docs/" + itemId;
				Map<String, String> headers = cosmosHeaders("GET", "docs", docLink, masterKey);
				headers.put("x-ms-documentdb-partitionkey", "[\"" + partitionKey + "\"]");
				HttpResponse<String> response = get(endpoint + "/" + docLink, headers);
				return toResult(response);
			}
			case "getAll": {
				String sqlQuery = context.getParameter("sqlQuery", "SELECT * FROM c");
				int maxItemCount = toInt(context.getParameter("maxItemCount", 100), 100);
				Map<String, String> headers = cosmosHeaders("POST", "docs", collLink, masterKey);
				headers.put("x-ms-documentdb-partitionkey", "[\"" + partitionKey + "\"]");
				headers.put("x-ms-documentdb-isquery", "true");
				headers.put("x-ms-max-item-count", String.valueOf(maxItemCount));
				headers.put("Content-Type", "application/query+json");
				Map<String, Object> queryBody = Map.of("query", sqlQuery);
				HttpResponse<String> response = post(endpoint + "/" + collLink + "/docs", queryBody, headers);
				return toArrayResult(response, "Documents");
			}
			case "update": {
				String itemId = context.getParameter("itemId", "");
				String itemBodyJson = context.getParameter("itemBody", "{}");
				Map<String, Object> body = parseJson(itemBodyJson);
				String docLink = collLink + "/docs/" + itemId;
				Map<String, String> headers = cosmosHeaders("PUT", "docs", docLink, masterKey);
				headers.put("x-ms-documentdb-partitionkey", "[\"" + partitionKey + "\"]");
				HttpResponse<String> response = put(endpoint + "/" + docLink, body, headers);
				return toResult(response);
			}
			case "upsert": {
				String itemBodyJson = context.getParameter("itemBody", "{}");
				Map<String, Object> body = parseJson(itemBodyJson);
				Map<String, String> headers = cosmosHeaders("POST", "docs", collLink, masterKey);
				headers.put("x-ms-documentdb-partitionkey", "[\"" + partitionKey + "\"]");
				headers.put("x-ms-documentdb-is-upsert", "true");
				HttpResponse<String> response = post(endpoint + "/" + collLink + "/docs", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown item operation: " + operation);
		}
	}

	// ========================= Cosmos DB Auth =========================

	private Map<String, String> cosmosHeaders(String verb, String resourceType,
			String resourceLink, String masterKey) throws Exception {
		String date = RFC_1123_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
		String authToken = generateAuthToken(verb, resourceType, resourceLink, date, masterKey);

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", authToken);
		headers.put("x-ms-date", date);
		headers.put("x-ms-version", "2018-12-31");
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	/**
	 * Generates the Cosmos DB authorization token using HMAC-SHA256.
	 * Format: type={type}&ver={ver}&sig={sig}
	 * where sig = Base64(HMAC-SHA256(key, verb\nresourceType\nresourceLink\ndate\n\n))
	 */
	private String generateAuthToken(String verb, String resourceType, String resourceLink,
			String date, String masterKey) throws NoSuchAlgorithmException, InvalidKeyException {

		String payload = verb.toLowerCase() + "\n"
			+ resourceType.toLowerCase() + "\n"
			+ resourceLink + "\n"
			+ date.toLowerCase() + "\n"
			+ "" + "\n";

		byte[] keyBytes = Base64.getDecoder().decode(masterKey);
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
		byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		String signature = Base64.getEncoder().encodeToString(hash);

		return encode("type=master&ver=1.0&sig=" + signature);
	}

	// ========================= Helpers =========================

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return cosmosError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toArrayResult(HttpResponse<String> response, String key) throws Exception {
		if (response.statusCode() >= 400) {
			return cosmosError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get(key);
		if (data instanceof List) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					results.add(wrapInJson(item));
				}
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return cosmosError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult cosmosError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Azure Cosmos DB API error (HTTP " + response.statusCode() + "): " + body);
	}
}
