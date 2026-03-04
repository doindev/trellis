package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Iterable — manage users, events, and user lists via the Iterable API.
 */
@Slf4j
@Node(
	type = "iterable",
	displayName = "Iterable",
	description = "Manage users and events in Iterable",
	category = "Marketing",
	icon = "iterable",
	credentials = {"iterableApi"},
	searchOnly = true
)
public class IterableNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.iterable.com/api";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("user")
			.options(List.of(
				ParameterOption.builder().name("Event").value("event").build(),
				ParameterOption.builder().name("User").value("user").build(),
				ParameterOption.builder().name("User List").value("userList").build()
			)).build());

		// Event operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("track")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.options(List.of(
				ParameterOption.builder().name("Track").value("track").description("Track an event").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("upsert")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Upsert").value("upsert").description("Create or update a user").build()
			)).build());

		// User List operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("userList"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Subscribe").value("subscribe").description("Subscribe users to a list").build(),
				ParameterOption.builder().name("Unsubscribe").value("unsubscribe").description("Unsubscribe users from a list").build()
			)).build());

		// Event fields
		params.add(NodeParameter.builder()
			.name("eventEmail").displayName("Email")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("track"))))
			.build());

		params.add(NodeParameter.builder()
			.name("eventName").displayName("Event Name")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("track"))))
			.build());

		params.add(NodeParameter.builder()
			.name("eventData").displayName("Event Data (JSON)")
			.type(ParameterType.STRING).defaultValue("{}")
			.description("Additional event data as JSON object")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("track"))))
			.build());

		// User fields
		params.add(NodeParameter.builder()
			.name("userEmail").displayName("Email")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("upsert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("upsert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("userData").displayName("Data Fields (JSON)")
			.type(ParameterType.STRING).defaultValue("{}")
			.description("User profile data as JSON object")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("upsert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("mergeNestedObjects").displayName("Merge Nested Objects")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("upsert"))))
			.build());

		// User List fields
		params.add(NodeParameter.builder()
			.name("listName").displayName("List Name")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("userList"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("listId").displayName("List ID")
			.type(ParameterType.NUMBER).required(true).defaultValue(0)
			.displayOptions(Map.of("show", Map.of("resource", List.of("userList"), "operation", List.of("get", "delete", "subscribe", "unsubscribe"))))
			.build());

		params.add(NodeParameter.builder()
			.name("subscribers").displayName("Subscribers (Emails)")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.description("Comma-separated email addresses")
			.displayOptions(Map.of("show", Map.of("resource", List.of("userList"), "operation", List.of("subscribe", "unsubscribe"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey", "");
			String resource = context.getParameter("resource", "user");
			String operation = context.getParameter("operation", "upsert");

			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Api-Key", apiKey);
			headers.put("Content-Type", "application/json");

			return switch (resource) {
				case "event" -> executeEvent(context, headers, operation);
				case "user" -> executeUser(context, headers, operation);
				case "userList" -> executeUserList(context, headers, operation);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Iterable API error: " + e.getMessage(), e);
		}
	}

	// ========================= Event =========================

	private NodeExecutionResult executeEvent(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		if ("track".equals(operation)) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("email", context.getParameter("eventEmail", ""));
			body.put("eventName", context.getParameter("eventName", ""));

			String eventDataJson = context.getParameter("eventData", "{}");
			try {
				Map<String, Object> data = objectMapper.readValue(eventDataJson,
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
				body.put("dataFields", data);
			} catch (Exception ignored) {
				body.put("dataFields", Map.of());
			}

			HttpResponse<String> response = post(BASE_URL + "/events/track", body, headers);
			return toResult(response);
		}
		return NodeExecutionResult.error("Unknown event operation: " + operation);
	}

	// ========================= User =========================

	private NodeExecutionResult executeUser(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		if ("upsert".equals(operation)) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("email", context.getParameter("userEmail", ""));

			String userId = context.getParameter("userId", "");
			if (!userId.isEmpty()) body.put("userId", userId);

			boolean mergeNestedObjects = toBoolean(context.getParameter("mergeNestedObjects", false), false);
			body.put("mergeNestedObjects", mergeNestedObjects);

			String userDataJson = context.getParameter("userData", "{}");
			try {
				Map<String, Object> data = objectMapper.readValue(userDataJson,
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
				body.put("dataFields", data);
			} catch (Exception ignored) {
				body.put("dataFields", Map.of());
			}

			HttpResponse<String> response = post(BASE_URL + "/users/update", body, headers);
			return toResult(response);
		}
		return NodeExecutionResult.error("Unknown user operation: " + operation);
	}

	// ========================= User List =========================

	private NodeExecutionResult executeUserList(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "create": {
				String listName = context.getParameter("listName", "");
				Map<String, Object> body = Map.of("name", listName);
				HttpResponse<String> response = post(BASE_URL + "/lists", body, headers);
				return toResult(response);
			}
			case "delete": {
				int listId = toInt(context.getParameter("listId", 0), 0);
				HttpResponse<String> response = delete(BASE_URL + "/lists/" + listId, headers);
				return toDeleteResult(response);
			}
			case "get": {
				int listId = toInt(context.getParameter("listId", 0), 0);
				HttpResponse<String> response = get(BASE_URL + "/lists/" + listId, headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/lists", headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				List<Map<String, Object>> parsed = parseArrayResponse(response);
				List<Map<String, Object>> results = new ArrayList<>();
				for (Map<String, Object> item : parsed) {
					results.add(wrapInJson(item));
				}
				return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
			}
			case "subscribe": {
				int listId = toInt(context.getParameter("listId", 0), 0);
				String subscribersStr = context.getParameter("subscribers", "");
				List<Map<String, String>> subscribers = Arrays.stream(subscribersStr.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.map(email -> Map.of("email", email))
					.toList();

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("listId", listId);
				body.put("subscribers", subscribers);
				HttpResponse<String> response = post(BASE_URL + "/lists/subscribe", body, headers);
				return toResult(response);
			}
			case "unsubscribe": {
				int listId = toInt(context.getParameter("listId", 0), 0);
				String subscribersStr = context.getParameter("subscribers", "");
				List<Map<String, String>> subscribers = Arrays.stream(subscribersStr.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.map(email -> Map.of("email", email))
					.toList();

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("listId", listId);
				body.put("subscribers", subscribers);
				HttpResponse<String> response = post(BASE_URL + "/lists/unsubscribe", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown userList operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Iterable API error (HTTP " + response.statusCode() + "): " + body);
	}
}
