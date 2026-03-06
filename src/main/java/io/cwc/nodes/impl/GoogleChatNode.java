package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Google Chat — manage messages and spaces via the Google Chat API.
 */
@Node(
		type = "googleChat",
		displayName = "Google Chat",
		description = "Manage Google Chat messages and spaces",
		category = "Google",
		icon = "googleChat",
		credentials = {"googleChatOAuth2Api"}
)
public class GoogleChatNode extends AbstractApiNode {

	private static final String BASE_URL = "https://chat.googleapis.com/v1";

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

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("message")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Message").value("message")
								.description("Manage chat messages").build(),
						ParameterOption.builder().name("Space").value("space")
								.description("Manage chat spaces").build()
				)).build());

		// Message operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("create")
				.displayOptions(Map.of("show", Map.of("resource", List.of("message"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Send a message").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a message").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a message").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a message").build()
				)).build());

		// Space operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("list")
				.displayOptions(Map.of("show", Map.of("resource", List.of("space"))))
				.options(List.of(
						ParameterOption.builder().name("Get").value("get").description("Get a space").build(),
						ParameterOption.builder().name("List").value("list").description("List all spaces").build()
				)).build());

		// Space name (used by message and space > get)
		params.add(NodeParameter.builder()
				.name("spaceName").displayName("Space Name")
				.type(ParameterType.STRING).required(true)
				.description("The resource name of the space (e.g., spaces/AAAA).")
				.placeHolder("spaces/AAAA")
				.displayOptions(Map.of("show", Map.of("resource", List.of("message"))))
				.build());

		params.add(NodeParameter.builder()
				.name("spaceName").displayName("Space Name")
				.type(ParameterType.STRING).required(true)
				.description("The resource name of the space (e.g., spaces/AAAA).")
				.placeHolder("spaces/AAAA")
				.displayOptions(Map.of("show", Map.of("resource", List.of("space"), "operation", List.of("get"))))
				.build());

		// Message > Create: text
		params.add(NodeParameter.builder()
				.name("messageText").displayName("Message Text")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 4))
				.description("The text body of the message.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
				.build());

		// Message > Create: additional fields
		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("threadKey").displayName("Thread Key")
								.type(ParameterType.STRING)
								.description("Thread key for replying to an existing thread.")
								.build(),
						NodeParameter.builder().name("requestId").displayName("Request ID")
								.type(ParameterType.STRING)
								.description("Unique request ID for idempotent requests.")
								.build()
				)).build());

		// Message > Get / Delete / Update: message name
		params.add(NodeParameter.builder()
				.name("messageName").displayName("Message Name")
				.type(ParameterType.STRING).required(true)
				.description("The resource name of the message (e.g., spaces/AAAA/messages/BBBB).")
				.placeHolder("spaces/AAAA/messages/BBBB")
				.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("get", "delete", "update"))))
				.build());

		// Message > Update: text
		params.add(NodeParameter.builder()
				.name("updateText").displayName("Updated Text")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 4))
				.description("The new text body of the message.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("update"))))
				.build());

		// Space > List: limit
		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Maximum number of spaces to return.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("space"), "operation", List.of("list"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "message");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "message" -> executeMessage(context, credentials);
				case "space" -> executeSpace(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Chat error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeMessage(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "create": {
				String spaceName = context.getParameter("spaceName", "");
				String messageText = context.getParameter("messageText", "");
				Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("text", messageText);

				String url = BASE_URL + "/" + spaceName + "/messages";

				if (additionalFields.get("threadKey") != null) {
					url += "?threadKey=" + encode((String) additionalFields.get("threadKey"));
					body.put("thread", Map.of("threadKey", additionalFields.get("threadKey")));
				}
				if (additionalFields.get("requestId") != null) {
					url += (url.contains("?") ? "&" : "?") + "requestId=" + encode((String) additionalFields.get("requestId"));
				}

				HttpResponse<String> response = post(url, body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String messageName = context.getParameter("messageName", "");
				HttpResponse<String> response = delete(BASE_URL + "/" + messageName, headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", messageName))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String messageName = context.getParameter("messageName", "");
				HttpResponse<String> response = get(BASE_URL + "/" + messageName, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String messageName = context.getParameter("messageName", "");
				String updateText = context.getParameter("updateText", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("text", updateText);

				String url = BASE_URL + "/" + messageName + "?updateMask=text";
				HttpResponse<String> response = patch(url, body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown message operation: " + operation);
		}
	}

	private NodeExecutionResult executeSpace(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "list");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "get": {
				String spaceName = context.getParameter("spaceName", "");
				HttpResponse<String> response = get(BASE_URL + "/" + spaceName, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "list": {
				int limit = toInt(context.getParameter("limit", 100), 100);
				String url = BASE_URL + "/spaces?pageSize=" + limit;

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object spaces = result.get("spaces");
				if (spaces instanceof List) {
					List<Map<String, Object>> spaceItems = new ArrayList<>();
					for (Object space : (List<?>) spaces) {
						if (space instanceof Map) {
							spaceItems.add(wrapInJson(space));
						}
					}
					return spaceItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(spaceItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown space operation: " + operation);
		}
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
