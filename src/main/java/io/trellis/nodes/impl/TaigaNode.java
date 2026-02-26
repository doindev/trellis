package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Taiga — manage epics, issues, tasks, and user stories using the Taiga API.
 */
@Node(
		type = "taiga",
		displayName = "Taiga",
		description = "Manage epics, issues, tasks, and user stories with Taiga",
		category = "Project Management",
		icon = "taiga",
		credentials = {"taigaApi"}
)
public class TaigaNode extends AbstractApiNode {

	private static final String DEFAULT_BASE = "https://api.taiga.io";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", DEFAULT_BASE);
		if (baseUrl.isEmpty()) baseUrl = DEFAULT_BASE;
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String apiUrl = baseUrl + "/api/v1";

		String username = context.getCredentialString("username", "");
		String password = context.getCredentialString("password", "");

		String resource = context.getParameter("resource", "issue");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// Authenticate to get auth token
				Map<String, Object> authBody = Map.of("type", "normal", "username", username, "password", password);
				HttpResponse<String> authResponse = post(apiUrl + "/auth", authBody, headers);
				Map<String, Object> authResult = parseResponse(authResponse);
				String authToken = String.valueOf(authResult.getOrDefault("auth_token", ""));
				headers.put("Authorization", "Bearer " + authToken);

				Map<String, Object> result = switch (resource) {
					case "epic" -> handleResource(context, apiUrl, headers, operation, "epics");
					case "issue" -> handleResource(context, apiUrl, headers, operation, "issues");
					case "task" -> handleResource(context, apiUrl, headers, operation, "tasks");
					case "userStory" -> handleResource(context, apiUrl, headers, operation, "userstories");
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

	private Map<String, Object> handleResource(NodeExecutionContext context, String apiUrl, Map<String, String> headers, String operation, String resourcePath) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("project", toInt(context.getParameters().get("projectId"), 0));
				body.put("subject", context.getParameter("subject", ""));
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String status = context.getParameter("status", "");
				if (!status.isEmpty()) body.put("status", toInt(status, 0));
				String assignedTo = context.getParameter("assignedTo", "");
				if (!assignedTo.isEmpty()) body.put("assigned_to", toInt(assignedTo, 0));
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) body.put("tags", List.of(tags.split(",")));
				HttpResponse<String> response = post(apiUrl + "/" + resourcePath, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String resourceId = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(apiUrl + "/" + resourcePath + "/" + encode(resourceId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String resourceId = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(apiUrl + "/" + resourcePath + "/" + encode(resourceId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String projectId = context.getParameter("projectId", "");
				StringBuilder url = new StringBuilder(apiUrl + "/" + resourcePath);
				if (!projectId.isEmpty()) url.append("?project=").append(encode(projectId));
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String resourceId = context.getParameter("resourceId", "");
				// Fetch current version for optimistic locking
				HttpResponse<String> current = get(apiUrl + "/" + resourcePath + "/" + encode(resourceId), headers);
				Map<String, Object> currentData = parseResponse(current);
				int version = toInt(currentData.get("version"), 1);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("version", version);
				String subject = context.getParameter("subject", "");
				if (!subject.isEmpty()) body.put("subject", subject);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String status = context.getParameter("status", "");
				if (!status.isEmpty()) body.put("status", toInt(status, 0));
				String assignedTo = context.getParameter("assignedTo", "");
				if (!assignedTo.isEmpty()) body.put("assigned_to", toInt(assignedTo, 0));
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) body.put("tags", List.of(tags.split(",")));
				HttpResponse<String> response = patch(apiUrl + "/" + resourcePath + "/" + encode(resourceId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("issue")
						.options(List.of(
								ParameterOption.builder().name("Epic").value("epic").build(),
								ParameterOption.builder().name("Issue").value("issue").build(),
								ParameterOption.builder().name("Task").value("task").build(),
								ParameterOption.builder().name("User Story").value("userStory").build()
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
						.name("projectId").displayName("Project ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The Taiga project ID.").build(),
				NodeParameter.builder()
						.name("resourceId").displayName("Resource ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the epic, issue, task, or user story.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("status").displayName("Status ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Status ID (numeric).").build(),
				NodeParameter.builder()
						.name("assignedTo").displayName("Assigned To")
						.type(ParameterType.STRING).defaultValue("")
						.description("User ID to assign to (numeric).").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of tags.").build()
		);
	}
}
