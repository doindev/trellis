package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Vero — manage users and track events using the Vero API.
 */
@Node(
		type = "vero",
		displayName = "Vero",
		description = "Manage users and track events in Vero",
		category = "Miscellaneous",
		icon = "vero",
		credentials = {"veroApi"},
		searchOnly = true
)
public class VeroNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.getvero.com/api/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String authToken = (String) credentials.getOrDefault("authToken", "");

		String resource = context.getParameter("resource", "user");
		String operation = context.getParameter("operation", "createUpdate");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "user" -> handleUser(context, authToken, operation);
					case "event" -> handleEvent(context, authToken);
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

	private Map<String, Object> handleUser(NodeExecutionContext context, String authToken, String operation) throws Exception {
		Map<String, String> headers = Map.of("Content-Type", "application/json", "Accept", "application/json");
		return switch (operation) {
			case "createUpdate" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("auth_token", authToken);
				body.put("id", context.getParameter("userId", ""));
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String data = context.getParameter("data", "");
				if (!data.isEmpty()) body.put("data", parseJson(data));
				HttpResponse<String> response = post(BASE_URL + "/users/track", body, headers);
				yield parseResponse(response);
			}
			case "alias" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("auth_token", authToken);
				body.put("id", context.getParameter("userId", ""));
				body.put("new_id", context.getParameter("newId", ""));
				HttpResponse<String> response = put(BASE_URL + "/users/reidentify", body, headers);
				yield parseResponse(response);
			}
			case "unsubscribe" -> {
				Map<String, Object> body = Map.of("auth_token", authToken, "id", context.getParameter("userId", ""));
				HttpResponse<String> response = post(BASE_URL + "/users/unsubscribe", body, headers);
				yield parseResponse(response);
			}
			case "resubscribe" -> {
				Map<String, Object> body = Map.of("auth_token", authToken, "id", context.getParameter("userId", ""));
				HttpResponse<String> response = post(BASE_URL + "/users/resubscribe", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				Map<String, Object> body = Map.of("auth_token", authToken, "id", context.getParameter("userId", ""));
				HttpResponse<String> response = post(BASE_URL + "/users/delete", body, headers);
				yield parseResponse(response);
			}
			case "addTags" -> {
				String tags = context.getParameter("tags", "");
				List<String> tagList = Arrays.stream(tags.split(",")).map(String::trim).toList();
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("auth_token", authToken);
				body.put("id", context.getParameter("userId", ""));
				body.put("add", tagList);
				HttpResponse<String> response = put(BASE_URL + "/users/tags/edit", body, headers);
				yield parseResponse(response);
			}
			case "removeTags" -> {
				String tags = context.getParameter("tags", "");
				List<String> tagList = Arrays.stream(tags.split(",")).map(String::trim).toList();
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("auth_token", authToken);
				body.put("id", context.getParameter("userId", ""));
				body.put("remove", tagList);
				HttpResponse<String> response = put(BASE_URL + "/users/tags/edit", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private Map<String, Object> handleEvent(NodeExecutionContext context, String authToken) throws Exception {
		Map<String, String> headers = Map.of("Content-Type", "application/json", "Accept", "application/json");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("auth_token", authToken);
		body.put("identity", Map.of("id", context.getParameter("userId", "")));
		body.put("event_name", context.getParameter("eventName", ""));
		String email = context.getParameter("email", "");
		if (!email.isEmpty()) body.put("email", email);
		String data = context.getParameter("data", "");
		if (!data.isEmpty()) body.put("data", parseJson(data));
		HttpResponse<String> response = post(BASE_URL + "/events/track", body, headers);
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("user")
						.options(List.of(
								ParameterOption.builder().name("User").value("user").build(),
								ParameterOption.builder().name("Event").value("event").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("createUpdate")
						.options(List.of(
								ParameterOption.builder().name("Create/Update").value("createUpdate").build(),
								ParameterOption.builder().name("Alias").value("alias").build(),
								ParameterOption.builder().name("Unsubscribe").value("unsubscribe").build(),
								ParameterOption.builder().name("Resubscribe").value("resubscribe").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Add Tags").value("addTags").build(),
								ParameterOption.builder().name("Remove Tags").value("removeTags").build(),
								ParameterOption.builder().name("Track Event").value("track").build()
						)).build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The user's unique identifier.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("newId").displayName("New ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("New identifier for the alias operation.").build(),
				NodeParameter.builder()
						.name("eventName").displayName("Event Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the event to track.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated tags to add or remove.").build(),
				NodeParameter.builder()
						.name("data").displayName("Data (JSON)")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON object of user/event attributes.").build()
		);
	}
}
