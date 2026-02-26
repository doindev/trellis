package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

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
 * Monday.com Node -- manage boards, groups, items, columns, and updates
 * in Monday.com using the GraphQL API.
 */
@Slf4j
@Node(
	type = "mondayCom",
	displayName = "Monday.com",
	description = "Manage boards, groups, items, columns, and updates in Monday.com",
	category = "Project Management",
	icon = "mondayCom",
	credentials = {"mondayComApi"}
)
public class MondayComNode extends AbstractApiNode {

	private static final String GRAPHQL_URL = "https://api.monday.com/v2";

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
				ParameterOption.builder().name("Board").value("board").description("Manage boards").build(),
				ParameterOption.builder().name("Group").value("group").description("Manage groups within boards").build(),
				ParameterOption.builder().name("Item").value("item").description("Manage items").build(),
				ParameterOption.builder().name("Column").value("column").description("Manage columns").build(),
				ParameterOption.builder().name("Update").value("update").description("Manage updates (comments)").build()
			)).build());

		// Board operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("board"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a board").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a board").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many boards").build(),
				ParameterOption.builder().name("Archive").value("archive").description("Archive a board").build()
			)).build());

		// Group operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a group").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a group").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all groups in a board").build()
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
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many items from a board").build(),
				ParameterOption.builder().name("Move to Group").value("moveToGroup").description("Move an item to a different group").build()
			)).build());

		// Column operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("column"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a column").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all columns in a board").build(),
				ParameterOption.builder().name("Change Value").value("changeValue").description("Change a column value for an item").build()
			)).build());

		// Update operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("update"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an update (comment)").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many updates for an item").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an update").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("boardId").displayName("Board ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the board.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("board", "group", "item", "column"))))
			.build());

		params.add(NodeParameter.builder()
			.name("itemId").displayName("Item ID")
			.type(ParameterType.STRING)
			.description("The ID of the item.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("item", "update", "column"), "operation", List.of("get", "delete", "create", "getAll", "moveToGroup", "changeValue"))))
			.build());

		params.add(NodeParameter.builder()
			.name("groupId").displayName("Group ID")
			.type(ParameterType.STRING)
			.description("The ID of the group.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group", "item"), "operation", List.of("delete", "create", "moveToGroup"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the board, group, item, or column.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("boardKind").displayName("Board Kind")
			.type(ParameterType.OPTIONS).defaultValue("public")
			.displayOptions(Map.of("show", Map.of("resource", List.of("board"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Public").value("public").build(),
				ParameterOption.builder().name("Private").value("private").build(),
				ParameterOption.builder().name("Share").value("share").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("columnId").displayName("Column ID")
			.type(ParameterType.STRING)
			.description("The ID of the column.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("column"), "operation", List.of("changeValue"))))
			.build());

		params.add(NodeParameter.builder()
			.name("columnType").displayName("Column Type")
			.type(ParameterType.OPTIONS).defaultValue("text")
			.displayOptions(Map.of("show", Map.of("resource", List.of("column"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Text").value("text").build(),
				ParameterOption.builder().name("Number").value("numbers").build(),
				ParameterOption.builder().name("Status").value("status").build(),
				ParameterOption.builder().name("Date").value("date").build(),
				ParameterOption.builder().name("Person").value("people").build(),
				ParameterOption.builder().name("Timeline").value("timeline").build(),
				ParameterOption.builder().name("Email").value("email").build(),
				ParameterOption.builder().name("Phone").value("phone").build(),
				ParameterOption.builder().name("Link").value("link").build(),
				ParameterOption.builder().name("Checkbox").value("checkbox").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("value").displayName("Value")
			.type(ParameterType.STRING)
			.description("Value to set (JSON string for column values).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("changeValue"))))
			.build());

		params.add(NodeParameter.builder()
			.name("body").displayName("Body")
			.type(ParameterType.STRING)
			.description("Body text of the update.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("update"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("updateId").displayName("Update ID")
			.type(ParameterType.STRING)
			.description("The ID of the update to delete.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("update"), "operation", List.of("delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("columnValues").displayName("Column Values (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Column values as JSON object for item creation.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("item"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Max number of results to return.")
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
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "board" -> executeBoard(context, operation, headers);
				case "group" -> executeGroup(context, operation, headers);
				case "item" -> executeItem(context, operation, headers);
				case "column" -> executeColumn(context, operation, headers);
				case "update" -> executeUpdate(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Monday.com API error: " + e.getMessage(), e);
		}
	}

	// ========================= Board Operations =========================

	private NodeExecutionResult executeBoard(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				String kind = context.getParameter("boardKind", "public");
				String query = "mutation { create_board(board_name: \"" + escapeGraphQL(name) + "\", board_kind: " + kind + ") { id name } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("create_board", result))));
			}
			case "get": {
				String boardId = context.getParameter("boardId", "");
				String query = "query { boards(ids: [" + boardId + "]) { id name description state board_kind columns { id title type } groups { id title } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return extractBoardItems(result, "boards");
			}
			case "getAll": {
				int limit = toInt(context.getParameters().get("limit"), 50);
				String query = "query { boards(limit: " + limit + ") { id name description state board_kind } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return extractBoardItems(result, "boards");
			}
			case "archive": {
				String boardId = context.getParameter("boardId", "");
				String query = "mutation { archive_board(board_id: " + boardId + ") { id } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("archive_board", result))));
			}
			default:
				return NodeExecutionResult.error("Unknown board operation: " + operation);
		}
	}

	// ========================= Group Operations =========================

	private NodeExecutionResult executeGroup(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String boardId = context.getParameter("boardId", "");

		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				String query = "mutation { create_group(board_id: " + boardId + ", group_name: \"" + escapeGraphQL(name) + "\") { id title } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("create_group", result))));
			}
			case "delete": {
				String groupId = context.getParameter("groupId", "");
				String query = "mutation { delete_group(board_id: " + boardId + ", group_id: \"" + escapeGraphQL(groupId) + "\") { id deleted } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("delete_group", result))));
			}
			case "getAll": {
				String query = "query { boards(ids: [" + boardId + "]) { groups { id title color archived } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return extractNestedItems(result, "boards", "groups");
			}
			default:
				return NodeExecutionResult.error("Unknown group operation: " + operation);
		}
	}

	// ========================= Item Operations =========================

	private NodeExecutionResult executeItem(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String boardId = context.getParameter("boardId", "");
				String name = context.getParameter("name", "");
				String groupId = context.getParameter("groupId", "");
				String columnValues = context.getParameter("columnValues", "{}");

				StringBuilder mutation = new StringBuilder();
				mutation.append("mutation { create_item(board_id: ").append(boardId);
				mutation.append(", item_name: \"").append(escapeGraphQL(name)).append("\"");
				if (!groupId.isEmpty()) {
					mutation.append(", group_id: \"").append(escapeGraphQL(groupId)).append("\"");
				}
				if (!"{}".equals(columnValues) && !columnValues.isEmpty()) {
					mutation.append(", column_values: \"").append(escapeGraphQL(columnValues)).append("\"");
				}
				mutation.append(") { id name } }");

				Map<String, Object> result = executeGraphQL(mutation.toString(), headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("create_item", result))));
			}
			case "delete": {
				String itemId = context.getParameter("itemId", "");
				String query = "mutation { delete_item(item_id: " + itemId + ") { id } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("delete_item", result))));
			}
			case "get": {
				String itemId = context.getParameter("itemId", "");
				String query = "query { items(ids: [" + itemId + "]) { id name state group { id title } column_values { id title text value type } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return extractBoardItems(result, "items");
			}
			case "getAll": {
				String boardId = context.getParameter("boardId", "");
				int limit = toInt(context.getParameters().get("limit"), 50);
				String query = "query { boards(ids: [" + boardId + "]) { items_page(limit: " + limit + ") { items { id name state group { id title } column_values { id title text value type } } } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return extractItemsPage(result);
			}
			case "moveToGroup": {
				String itemId = context.getParameter("itemId", "");
				String groupId = context.getParameter("groupId", "");
				String query = "mutation { move_item_to_group(item_id: " + itemId + ", group_id: \"" + escapeGraphQL(groupId) + "\") { id } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("move_item_to_group", result))));
			}
			default:
				return NodeExecutionResult.error("Unknown item operation: " + operation);
		}
	}

	// ========================= Column Operations =========================

	private NodeExecutionResult executeColumn(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String boardId = context.getParameter("boardId", "");

		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				String columnType = context.getParameter("columnType", "text");
				String query = "mutation { create_column(board_id: " + boardId + ", title: \"" + escapeGraphQL(name) + "\", column_type: " + columnType + ") { id title type } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("create_column", result))));
			}
			case "getAll": {
				String query = "query { boards(ids: [" + boardId + "]) { columns { id title type settings_str } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return extractNestedItems(result, "boards", "columns");
			}
			case "changeValue": {
				String itemId = context.getParameter("itemId", "");
				String columnId = context.getParameter("columnId", "");
				String value = context.getParameter("value", "");
				String query = "mutation { change_simple_column_value(board_id: " + boardId + ", item_id: " + itemId + ", column_id: \"" + escapeGraphQL(columnId) + "\", value: \"" + escapeGraphQL(value) + "\") { id } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("change_simple_column_value", result))));
			}
			default:
				return NodeExecutionResult.error("Unknown column operation: " + operation);
		}
	}

	// ========================= Update Operations =========================

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String itemId = context.getParameter("itemId", "");
				String body = context.getParameter("body", "");
				String query = "mutation { create_update(item_id: " + itemId + ", body: \"" + escapeGraphQL(body) + "\") { id body text_body created_at } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("create_update", result))));
			}
			case "getAll": {
				String itemId = context.getParameter("itemId", "");
				int limit = toInt(context.getParameters().get("limit"), 50);
				String query = "query { items(ids: [" + itemId + "]) { updates(limit: " + limit + ") { id body text_body created_at creator { id name } } } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return extractNestedItems(result, "items", "updates");
			}
			case "delete": {
				String updateId = context.getParameter("updateId", "");
				String query = "mutation { delete_update(id: " + updateId + ") { id } }";
				Map<String, Object> result = executeGraphQL(query, headers);
				return NodeExecutionResult.success(List.of(wrapInJson(result.getOrDefault("delete_update", result))));
			}
			default:
				return NodeExecutionResult.error("Unknown update operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@SuppressWarnings("unchecked")
	private Map<String, Object> executeGraphQL(String query, Map<String, String> headers) throws Exception {
		Map<String, Object> body = Map.of("query", query);
		HttpResponse<String> response = post(GRAPHQL_URL, body, headers);

		if (response.statusCode() >= 400) {
			String responseBody = response.body() != null ? response.body() : "";
			throw new RuntimeException("Monday.com GraphQL error (HTTP " + response.statusCode() + "): " + responseBody);
		}

		Map<String, Object> parsed = parseResponse(response);

		if (parsed.get("errors") != null) {
			List<Map<String, Object>> errors = (List<Map<String, Object>>) parsed.get("errors");
			if (!errors.isEmpty()) {
				String message = String.valueOf(errors.get(0).getOrDefault("message", "Unknown GraphQL error"));
				throw new RuntimeException("Monday.com GraphQL error: " + message);
			}
		}

		Object data = parsed.get("data");
		if (data instanceof Map) {
			return (Map<String, Object>) data;
		}
		return parsed;
	}

	private NodeExecutionResult extractBoardItems(Map<String, Object> result, String key) {
		Object obj = result.get(key);
		if (obj instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) obj) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult extractNestedItems(Map<String, Object> result, String parentKey, String childKey) {
		Object parentObj = result.get(parentKey);
		if (parentObj instanceof List) {
			List<?> parentList = (List<?>) parentObj;
			if (!parentList.isEmpty() && parentList.get(0) instanceof Map) {
				Map<String, Object> firstParent = (Map<String, Object>) parentList.get(0);
				Object childObj = firstParent.get(childKey);
				if (childObj instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object item : (List<?>) childObj) {
						if (item instanceof Map) {
							items.add(wrapInJson(item));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
			}
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult extractItemsPage(Map<String, Object> result) {
		Object boardsObj = result.get("boards");
		if (boardsObj instanceof List) {
			List<?> boards = (List<?>) boardsObj;
			if (!boards.isEmpty() && boards.get(0) instanceof Map) {
				Map<String, Object> board = (Map<String, Object>) boards.get(0);
				Object itemsPage = board.get("items_page");
				if (itemsPage instanceof Map) {
					Object items = ((Map<String, Object>) itemsPage).get("items");
					if (items instanceof List) {
						List<Map<String, Object>> resultItems = new ArrayList<>();
						for (Object item : (List<?>) items) {
							if (item instanceof Map) {
								resultItems.add(wrapInJson(item));
							}
						}
						return resultItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(resultItems);
					}
				}
			}
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", credentials.getOrDefault("apiToken", "")));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("API-Version", "2024-01");
		return headers;
	}

	private String escapeGraphQL(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
