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
 * Notion Node -- manage blocks, databases, database pages, pages, and users in Notion.
 */
@Slf4j
@Node(
	type = "notion",
	displayName = "Notion",
	description = "Manage blocks, databases, pages, and users in Notion",
	category = "Project Management",
	icon = "notion",
	credentials = {"notionApi"}
)
public class NotionNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.notion.com/v1";
	private static final String NOTION_VERSION = "2022-06-28";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("databasePage")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Block").value("block").description("Manage blocks").build(),
				ParameterOption.builder().name("Database").value("database").description("Manage databases").build(),
				ParameterOption.builder().name("Database Page").value("databasePage").description("Manage pages in a database").build(),
				ParameterOption.builder().name("Page").value("page").description("Manage pages").build(),
				ParameterOption.builder().name("User").value("user").description("Manage users").build()
			)).build());

		// Block operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("append")
			.displayOptions(Map.of("show", Map.of("resource", List.of("block"))))
			.options(List.of(
				ParameterOption.builder().name("Append").value("append").description("Append child blocks").build()
			)).build());

		// Database operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("database"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a database").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many databases").build(),
				ParameterOption.builder().name("Search").value("search").description("Search databases").build()
			)).build());

		// Database Page operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("databasePage"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a page in a database").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a database page").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Query a database for pages").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a database page").build()
			)).build());

		// Page operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"))))
			.options(List.of(
				ParameterOption.builder().name("Archive").value("archive").description("Archive a page").build(),
				ParameterOption.builder().name("Create").value("create").description("Create a page").build(),
				ParameterOption.builder().name("Search").value("search").description("Search pages").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a user").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many users").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("blockId").displayName("Block ID")
			.type(ParameterType.STRING)
			.description("The ID of the block (also accepts page ID).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("block"))))
			.build());

		params.add(NodeParameter.builder()
			.name("databaseId").displayName("Database ID")
			.type(ParameterType.STRING)
			.description("The ID of the database.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("database", "databasePage"))))
			.build());

		params.add(NodeParameter.builder()
			.name("pageId").displayName("Page ID")
			.type(ParameterType.STRING)
			.description("The ID of the page.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("page", "databasePage"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID")
			.type(ParameterType.STRING)
			.description("The ID of the user.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("title").displayName("Title")
			.type(ParameterType.STRING)
			.description("Title of the page.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("page", "databasePage"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("parentPageId").displayName("Parent Page ID")
			.type(ParameterType.STRING)
			.description("The ID of the parent page.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query")
			.type(ParameterType.STRING)
			.description("Text to search for.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("filterJson").displayName("Filter (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Database query filter as JSON.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("databasePage"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("propertiesJson").displayName("Properties (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Page properties as JSON.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("databasePage", "page"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("childrenJson").displayName("Children Blocks (JSON)")
			.type(ParameterType.JSON).defaultValue("[]")
			.description("Array of block objects as JSON.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("block"), "operation", List.of("append"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "databasePage");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String token = String.valueOf(credentials.getOrDefault("apiKey",
				credentials.getOrDefault("accessToken", "")));

		try {
			Map<String, String> headers = authHeaders(token);
			return switch (resource) {
				case "block" -> executeBlock(context, operation, headers);
				case "database" -> executeDatabase(context, operation, headers);
				case "databasePage" -> executeDatabasePage(context, operation, headers);
				case "page" -> executePage(context, operation, headers);
				case "user" -> executeUser(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Notion API error: " + e.getMessage(), e);
		}
	}

	// ========================= Block Operations =========================

	private NodeExecutionResult executeBlock(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		if ("append".equals(operation)) {
			String blockId = context.getParameter("blockId", "");
			String childrenJson = context.getParameter("childrenJson", "[]");
			List<Map<String, Object>> children = parseJsonArray(childrenJson);
			Map<String, Object> body = Map.of("children", children);
			HttpResponse<String> response = patch(BASE_URL + "/blocks/" + encode(blockId) + "/children", body, headers);
			return toResult(response);
		}
		return NodeExecutionResult.error("Unknown block operation: " + operation);
	}

	// ========================= Database Operations =========================

	private NodeExecutionResult executeDatabase(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String databaseId = context.getParameter("databaseId", "");
				HttpResponse<String> response = get(BASE_URL + "/databases/" + encode(databaseId), headers);
				return toResult(response);
			}
			case "getAll": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("filter", Map.of("value", "database", "property", "object"));
				HttpResponse<String> response = post(BASE_URL + "/search", body, headers);
				if (response.statusCode() >= 400) {
					return notionError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object results = parsed.get("results");
				return toArrayFromList(results);
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!query.isEmpty()) body.put("query", query);
				body.put("filter", Map.of("value", "database", "property", "object"));
				HttpResponse<String> response = post(BASE_URL + "/search", body, headers);
				if (response.statusCode() >= 400) {
					return notionError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object results = parsed.get("results");
				return toArrayFromList(results);
			}
			default:
				return NodeExecutionResult.error("Unknown database operation: " + operation);
		}
	}

	// ========================= Database Page Operations =========================

	private NodeExecutionResult executeDatabasePage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String databaseId = context.getParameter("databaseId", "");
				String propertiesJson = context.getParameter("propertiesJson", "{}");
				String title = context.getParameter("title", "");
				Map<String, Object> properties = new LinkedHashMap<>(parseJson(propertiesJson));
				if (!title.isEmpty() && !properties.containsKey("title") && !properties.containsKey("Name")) {
					properties.put("Name", Map.of("title", List.of(Map.of("text", Map.of("content", title)))));
				}
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("parent", Map.of("database_id", databaseId));
				body.put("properties", properties);
				HttpResponse<String> response = post(BASE_URL + "/pages", body, headers);
				return toResult(response);
			}
			case "get": {
				String pageId = context.getParameter("pageId", "");
				HttpResponse<String> response = get(BASE_URL + "/pages/" + encode(pageId), headers);
				return toResult(response);
			}
			case "getAll": {
				String databaseId = context.getParameter("databaseId", "");
				String filterJson = context.getParameter("filterJson", "{}");
				Map<String, Object> body = new LinkedHashMap<>();
				Map<String, Object> filter = parseJson(filterJson);
				if (!filter.isEmpty()) body.put("filter", filter);
				HttpResponse<String> response = post(BASE_URL + "/databases/" + encode(databaseId) + "/query", body, headers);
				if (response.statusCode() >= 400) {
					return notionError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object results = parsed.get("results");
				return toArrayFromList(results);
			}
			case "update": {
				String pageId = context.getParameter("pageId", "");
				String propertiesJson = context.getParameter("propertiesJson", "{}");
				Map<String, Object> properties = parseJson(propertiesJson);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = patch(BASE_URL + "/pages/" + encode(pageId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown databasePage operation: " + operation);
		}
	}

	// ========================= Page Operations =========================

	private NodeExecutionResult executePage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "archive": {
				String pageId = context.getParameter("pageId", "");
				Map<String, Object> body = Map.of("archived", true);
				HttpResponse<String> response = patch(BASE_URL + "/pages/" + encode(pageId), body, headers);
				return toResult(response);
			}
			case "create": {
				String parentPageId = context.getParameter("parentPageId", "");
				String title = context.getParameter("title", "");
				String propertiesJson = context.getParameter("propertiesJson", "{}");
				Map<String, Object> properties = new LinkedHashMap<>(parseJson(propertiesJson));
				if (!title.isEmpty()) {
					properties.put("title", List.of(Map.of("text", Map.of("content", title))));
				}
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("parent", Map.of("page_id", parentPageId));
				body.put("properties", properties);
				HttpResponse<String> response = post(BASE_URL + "/pages", body, headers);
				return toResult(response);
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!query.isEmpty()) body.put("query", query);
				body.put("filter", Map.of("value", "page", "property", "object"));
				HttpResponse<String> response = post(BASE_URL + "/search", body, headers);
				if (response.statusCode() >= 400) {
					return notionError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object results = parsed.get("results");
				return toArrayFromList(results);
			}
			default:
				return NodeExecutionResult.error("Unknown page operation: " + operation);
		}
	}

	// ========================= User Operations =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/users", headers);
				if (response.statusCode() >= 400) {
					return notionError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object results = parsed.get("results");
				return toArrayFromList(results);
			}
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(String token) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Notion-Version", NOTION_VERSION);
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return notionError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toArrayFromList(Object listObj) {
		if (listObj instanceof List) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (Object item : (List<?>) listObj) {
				if (item instanceof Map) {
					results.add(wrapInJson(item));
				}
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}
		return NodeExecutionResult.empty();
	}

	private NodeExecutionResult notionError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Notion API error (HTTP " + response.statusCode() + "): " + body);
	}
}
