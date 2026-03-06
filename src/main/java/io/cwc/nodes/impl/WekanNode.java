package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Wekan — manage boards, cards, lists, and checklists using the Wekan API.
 */
@Node(
		type = "wekan",
		displayName = "Wekan",
		description = "Manage boards, cards, and lists with Wekan",
		category = "Project Management",
		icon = "wekan",
		credentials = {"wekanApi"}
)
public class WekanNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", "");
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String apiUrl = baseUrl + "/api";

		String authToken = context.getCredentialString("authToken", "");
		String userId = context.getCredentialString("userId", "");

		String resource = context.getParameter("resource", "card");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + authToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "board" -> handleBoard(context, apiUrl, headers, operation, userId);
					case "card" -> handleCard(context, apiUrl, headers, operation);
					case "cardComment" -> handleCardComment(context, apiUrl, headers, operation);
					case "checklist" -> handleChecklist(context, apiUrl, headers, operation);
					case "list" -> handleList(context, apiUrl, headers, operation);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleBoard(NodeExecutionContext context, String apiUrl, Map<String, String> headers, String operation, String userId) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("owner", userId);
				String permission = context.getParameter("permission", "private");
				if (!permission.isEmpty()) body.put("permission", permission);
				String color = context.getParameter("color", "");
				if (!color.isEmpty()) body.put("color", color);
				HttpResponse<String> response = post(apiUrl + "/boards", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String boardId = context.getParameter("boardId", "");
				HttpResponse<String> response = delete(apiUrl + "/boards/" + encode(boardId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String boardId = context.getParameter("boardId", "");
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(apiUrl + "/users/" + encode(userId) + "/boards", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown board operation: " + operation);
		};
	}

	private Map<String, Object> handleCard(NodeExecutionContext context, String apiUrl, Map<String, String> headers, String operation) throws Exception {
		String boardId = context.getParameter("boardId", "");
		String listId = context.getParameter("listId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("authorId", context.getParameter("authorId", ""));
				String swimlaneId = context.getParameter("swimlaneId", "");
				if (!swimlaneId.isEmpty()) body.put("swimlaneId", swimlaneId);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				HttpResponse<String> response = post(apiUrl + "/boards/" + encode(boardId) + "/lists/" + encode(listId) + "/cards", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = delete(apiUrl + "/boards/" + encode(boardId) + "/lists/" + encode(listId) + "/cards/" + encode(cardId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/lists/" + encode(listId) + "/cards/" + encode(cardId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/lists/" + encode(listId) + "/cards", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String cardId = context.getParameter("cardId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String color = context.getParameter("color", "");
				if (!color.isEmpty()) body.put("color", color);
				HttpResponse<String> response = put(apiUrl + "/boards/" + encode(boardId) + "/lists/" + encode(listId) + "/cards/" + encode(cardId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown card operation: " + operation);
		};
	}

	private Map<String, Object> handleCardComment(NodeExecutionContext context, String apiUrl, Map<String, String> headers, String operation) throws Exception {
		String boardId = context.getParameter("boardId", "");
		String cardId = context.getParameter("cardId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("authorId", context.getParameter("authorId", ""));
				body.put("comment", context.getParameter("comment", ""));
				HttpResponse<String> response = post(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/comments", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String commentId = context.getParameter("commentId", "");
				HttpResponse<String> response = delete(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/comments/" + encode(commentId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String commentId = context.getParameter("commentId", "");
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/comments/" + encode(commentId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/comments", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown card comment operation: " + operation);
		};
	}

	private Map<String, Object> handleChecklist(NodeExecutionContext context, String apiUrl, Map<String, String> headers, String operation) throws Exception {
		String boardId = context.getParameter("boardId", "");
		String cardId = context.getParameter("cardId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				String items = context.getParameter("items", "");
				if (!items.isEmpty()) body.put("items", List.of(items.split(",")));
				HttpResponse<String> response = post(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/checklists", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String checklistId = context.getParameter("checklistId", "");
				HttpResponse<String> response = delete(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/checklists/" + encode(checklistId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String checklistId = context.getParameter("checklistId", "");
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/checklists/" + encode(checklistId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/cards/" + encode(cardId) + "/checklists", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown checklist operation: " + operation);
		};
	}

	private Map<String, Object> handleList(NodeExecutionContext context, String apiUrl, Map<String, String> headers, String operation) throws Exception {
		String boardId = context.getParameter("boardId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = Map.of("title", context.getParameter("title", ""));
				HttpResponse<String> response = post(apiUrl + "/boards/" + encode(boardId) + "/lists", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = delete(apiUrl + "/boards/" + encode(boardId) + "/lists/" + encode(listId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/lists/" + encode(listId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(apiUrl + "/boards/" + encode(boardId) + "/lists", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown list operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("card")
						.options(List.of(
								ParameterOption.builder().name("Board").value("board").build(),
								ParameterOption.builder().name("Card").value("card").build(),
								ParameterOption.builder().name("Card Comment").value("cardComment").build(),
								ParameterOption.builder().name("Checklist").value("checklist").build(),
								ParameterOption.builder().name("List").value("list").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("boardId").displayName("Board ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("cardId").displayName("Card ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("commentId").displayName("Comment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("checklistId").displayName("Checklist ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("authorId").displayName("Author ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("swimlaneId").displayName("Swimlane ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("comment").displayName("Comment")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("items").displayName("Items")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated checklist items.").build(),
				NodeParameter.builder()
						.name("permission").displayName("Permission")
						.type(ParameterType.OPTIONS).defaultValue("private")
						.options(List.of(
								ParameterOption.builder().name("Private").value("private").build(),
								ParameterOption.builder().name("Public").value("public").build()
						)).build(),
				NodeParameter.builder()
						.name("color").displayName("Color")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
