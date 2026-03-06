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
 * Trello Node -- manage boards, cards, lists, checklists, labels,
 * and attachments in Trello.
 */
@Slf4j
@Node(
	type = "trello",
	displayName = "Trello",
	description = "Manage boards, cards, lists, checklists, labels, and attachments in Trello",
	category = "Project Management",
	icon = "trello",
	credentials = {"trelloApi"}
)
public class TrelloNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.trello.com/1";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("card")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Board").value("board").description("Manage boards").build(),
				ParameterOption.builder().name("Card").value("card").description("Manage cards").build(),
				ParameterOption.builder().name("List").value("list").description("Manage lists").build(),
				ParameterOption.builder().name("Checklist").value("checklist").description("Manage checklists").build(),
				ParameterOption.builder().name("Label").value("label").description("Manage labels").build(),
				ParameterOption.builder().name("Attachment").value("attachment").description("Manage attachments").build()
			)).build());

		// Board operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("board"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a board").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a board").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a board").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many boards").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a board").build()
			)).build());

		// Card operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("card"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a card").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a card").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a card").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many cards from a list").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a card").build()
			)).build());

		// List operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("list"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a list").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a list").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all lists in a board").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a list").build(),
				ParameterOption.builder().name("Archive").value("archive").description("Archive a list").build()
			)).build());

		// Checklist operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("checklist"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a checklist on a card").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a checklist").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a checklist").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all checklists on a card").build(),
				ParameterOption.builder().name("Create Check Item").value("createCheckItem").description("Create a check item on a checklist").build()
			)).build());

		// Label operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("label"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a label").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a label").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all labels on a board").build(),
				ParameterOption.builder().name("Add to Card").value("addToCard").description("Add a label to a card").build(),
				ParameterOption.builder().name("Remove from Card").value("removeFromCard").description("Remove a label from a card").build()
			)).build());

		// Attachment operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("attachment"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Add an attachment to a card via URL").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an attachment").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all attachments on a card").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("boardId").displayName("Board ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the board.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("board", "list", "label"))))
			.build());

		params.add(NodeParameter.builder()
			.name("cardId").displayName("Card ID")
			.type(ParameterType.STRING)
			.description("The ID of the card.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("card", "checklist", "label", "attachment"))))
			.build());

		params.add(NodeParameter.builder()
			.name("listId").displayName("List ID")
			.type(ParameterType.STRING)
			.description("The ID of the list.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("list", "card"))))
			.build());

		params.add(NodeParameter.builder()
			.name("checklistId").displayName("Checklist ID")
			.type(ParameterType.STRING)
			.description("The ID of the checklist.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("checklist"), "operation", List.of("get", "delete", "createCheckItem"))))
			.build());

		params.add(NodeParameter.builder()
			.name("labelId").displayName("Label ID")
			.type(ParameterType.STRING)
			.description("The ID of the label.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("label"), "operation", List.of("delete", "addToCard", "removeFromCard"))))
			.build());

		params.add(NodeParameter.builder()
			.name("attachmentId").displayName("Attachment ID")
			.type(ParameterType.STRING)
			.description("The ID of the attachment.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("attachment"), "operation", List.of("delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("description").displayName("Description")
			.type(ParameterType.STRING)
			.description("Description of the resource.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("board", "card"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("color").displayName("Color")
			.type(ParameterType.OPTIONS).defaultValue("green")
			.displayOptions(Map.of("show", Map.of("resource", List.of("label"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Green").value("green").build(),
				ParameterOption.builder().name("Yellow").value("yellow").build(),
				ParameterOption.builder().name("Orange").value("orange").build(),
				ParameterOption.builder().name("Red").value("red").build(),
				ParameterOption.builder().name("Purple").value("purple").build(),
				ParameterOption.builder().name("Blue").value("blue").build(),
				ParameterOption.builder().name("Sky").value("sky").build(),
				ParameterOption.builder().name("Lime").value("lime").build(),
				ParameterOption.builder().name("Pink").value("pink").build(),
				ParameterOption.builder().name("Black").value("black").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("url").displayName("URL")
			.type(ParameterType.STRING)
			.description("URL for the attachment.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("attachment"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "card");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		String apiToken = String.valueOf(credentials.getOrDefault("apiToken", credentials.getOrDefault("accessToken", "")));

		try {
			return switch (resource) {
				case "board" -> executeBoard(context, operation, apiKey, apiToken);
				case "card" -> executeCard(context, operation, apiKey, apiToken);
				case "list" -> executeList(context, operation, apiKey, apiToken);
				case "checklist" -> executeChecklist(context, operation, apiKey, apiToken);
				case "label" -> executeLabel(context, operation, apiKey, apiToken);
				case "attachment" -> executeAttachment(context, operation, apiKey, apiToken);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Trello API error: " + e.getMessage(), e);
		}
	}

	// ========================= Board Operations =========================

	private NodeExecutionResult executeBoard(NodeExecutionContext context, String operation,
			String apiKey, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				String desc = context.getParameter("description", "");
				String url = authUrl("/boards", apiKey, apiToken) + "&name=" + encode(name);
				if (!desc.isEmpty()) url += "&desc=" + encode(desc);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String boardId = context.getParameter("boardId", "");
				HttpResponse<String> response = delete(authUrl("/boards/" + encode(boardId), apiKey, apiToken), jsonHeaders());
				return toDeleteResult(response, boardId);
			}
			case "get": {
				String boardId = context.getParameter("boardId", "");
				HttpResponse<String> response = get(authUrl("/boards/" + encode(boardId), apiKey, apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(authUrl("/members/me/boards", apiKey, apiToken), jsonHeaders());
				return toArrayResult(response);
			}
			case "update": {
				String boardId = context.getParameter("boardId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				putIfNotEmpty(body, "desc", context.getParameter("description", ""));
				HttpResponse<String> response = put(authUrl("/boards/" + encode(boardId), apiKey, apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown board operation: " + operation);
		}
	}

	// ========================= Card Operations =========================

	private NodeExecutionResult executeCard(NodeExecutionContext context, String operation,
			String apiKey, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String listId = context.getParameter("listId", "");
				String name = context.getParameter("name", "");
				String desc = context.getParameter("description", "");
				String url = authUrl("/cards", apiKey, apiToken) + "&idList=" + encode(listId) + "&name=" + encode(name);
				if (!desc.isEmpty()) url += "&desc=" + encode(desc);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = delete(authUrl("/cards/" + encode(cardId), apiKey, apiToken), jsonHeaders());
				return toDeleteResult(response, cardId);
			}
			case "get": {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = get(authUrl("/cards/" + encode(cardId), apiKey, apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(authUrl("/lists/" + encode(listId) + "/cards", apiKey, apiToken), jsonHeaders());
				return toArrayResult(response);
			}
			case "update": {
				String cardId = context.getParameter("cardId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				putIfNotEmpty(body, "desc", context.getParameter("description", ""));
				HttpResponse<String> response = put(authUrl("/cards/" + encode(cardId), apiKey, apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown card operation: " + operation);
		}
	}

	// ========================= List Operations =========================

	private NodeExecutionResult executeList(NodeExecutionContext context, String operation,
			String apiKey, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String boardId = context.getParameter("boardId", "");
				String name = context.getParameter("name", "");
				String url = authUrl("/lists", apiKey, apiToken) + "&name=" + encode(name) + "&idBoard=" + encode(boardId);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			case "get": {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(authUrl("/lists/" + encode(listId), apiKey, apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				String boardId = context.getParameter("boardId", "");
				HttpResponse<String> response = get(authUrl("/boards/" + encode(boardId) + "/lists", apiKey, apiToken), jsonHeaders());
				return toArrayResult(response);
			}
			case "update": {
				String listId = context.getParameter("listId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				HttpResponse<String> response = put(authUrl("/lists/" + encode(listId), apiKey, apiToken), body, jsonHeaders());
				return toResult(response);
			}
			case "archive": {
				String listId = context.getParameter("listId", "");
				Map<String, Object> body = Map.of("value", true);
				HttpResponse<String> response = put(authUrl("/lists/" + encode(listId) + "/closed", apiKey, apiToken), body, jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown list operation: " + operation);
		}
	}

	// ========================= Checklist Operations =========================

	private NodeExecutionResult executeChecklist(NodeExecutionContext context, String operation,
			String apiKey, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String cardId = context.getParameter("cardId", "");
				String name = context.getParameter("name", "");
				String url = authUrl("/cards/" + encode(cardId) + "/checklists", apiKey, apiToken) + "&name=" + encode(name);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String checklistId = context.getParameter("checklistId", "");
				HttpResponse<String> response = delete(authUrl("/checklists/" + encode(checklistId), apiKey, apiToken), jsonHeaders());
				return toDeleteResult(response, checklistId);
			}
			case "get": {
				String checklistId = context.getParameter("checklistId", "");
				HttpResponse<String> response = get(authUrl("/checklists/" + encode(checklistId), apiKey, apiToken), jsonHeaders());
				return toResult(response);
			}
			case "getAll": {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = get(authUrl("/cards/" + encode(cardId) + "/checklists", apiKey, apiToken), jsonHeaders());
				return toArrayResult(response);
			}
			case "createCheckItem": {
				String checklistId = context.getParameter("checklistId", "");
				String name = context.getParameter("name", "");
				String url = authUrl("/checklists/" + encode(checklistId) + "/checkItems", apiKey, apiToken) + "&name=" + encode(name);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown checklist operation: " + operation);
		}
	}

	// ========================= Label Operations =========================

	private NodeExecutionResult executeLabel(NodeExecutionContext context, String operation,
			String apiKey, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String boardId = context.getParameter("boardId", "");
				String name = context.getParameter("name", "");
				String color = context.getParameter("color", "green");
				String url = authUrl("/labels", apiKey, apiToken) + "&name=" + encode(name) + "&color=" + encode(color) + "&idBoard=" + encode(boardId);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String labelId = context.getParameter("labelId", "");
				HttpResponse<String> response = delete(authUrl("/labels/" + encode(labelId), apiKey, apiToken), jsonHeaders());
				return toDeleteResult(response, labelId);
			}
			case "getAll": {
				String boardId = context.getParameter("boardId", "");
				HttpResponse<String> response = get(authUrl("/boards/" + encode(boardId) + "/labels", apiKey, apiToken), jsonHeaders());
				return toArrayResult(response);
			}
			case "addToCard": {
				String cardId = context.getParameter("cardId", "");
				String labelId = context.getParameter("labelId", "");
				String url = authUrl("/cards/" + encode(cardId) + "/idLabels", apiKey, apiToken) + "&value=" + encode(labelId);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			case "removeFromCard": {
				String cardId = context.getParameter("cardId", "");
				String labelId = context.getParameter("labelId", "");
				HttpResponse<String> response = delete(authUrl("/cards/" + encode(cardId) + "/idLabels/" + encode(labelId), apiKey, apiToken), jsonHeaders());
				return toDeleteResult(response, labelId);
			}
			default:
				return NodeExecutionResult.error("Unknown label operation: " + operation);
		}
	}

	// ========================= Attachment Operations =========================

	private NodeExecutionResult executeAttachment(NodeExecutionContext context, String operation,
			String apiKey, String apiToken) throws Exception {
		switch (operation) {
			case "create": {
				String cardId = context.getParameter("cardId", "");
				String attachmentUrl = context.getParameter("url", "");
				String name = context.getParameter("name", "");
				String url = authUrl("/cards/" + encode(cardId) + "/attachments", apiKey, apiToken) + "&url=" + encode(attachmentUrl);
				if (!name.isEmpty()) url += "&name=" + encode(name);
				HttpResponse<String> response = post(url, Map.of(), jsonHeaders());
				return toResult(response);
			}
			case "delete": {
				String cardId = context.getParameter("cardId", "");
				String attachmentId = context.getParameter("attachmentId", "");
				HttpResponse<String> response = delete(authUrl("/cards/" + encode(cardId) + "/attachments/" + encode(attachmentId), apiKey, apiToken), jsonHeaders());
				return toDeleteResult(response, attachmentId);
			}
			case "getAll": {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = get(authUrl("/cards/" + encode(cardId) + "/attachments", apiKey, apiToken), jsonHeaders());
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown attachment operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private String authUrl(String path, String apiKey, String apiToken) {
		return BASE_URL + path + "?key=" + encode(apiKey) + "&token=" + encode(apiToken);
	}

	private Map<String, String> jsonHeaders() {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return trelloError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toArrayResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return trelloError(response);
		}
		List<Map<String, Object>> items = parseArrayResponse(response);
		if (items.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : items) {
			results.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(results);
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return trelloError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult trelloError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Trello API error (HTTP " + response.statusCode() + "): " + body);
	}
}
