package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Elastic Security — manage cases, comments, tags, and connectors via the Elastic Security API.
 */
@Slf4j
@Node(
		type = "elasticSecurity",
		displayName = "Elastic Security",
		description = "Manage Elastic Security cases and connectors",
		category = "Miscellaneous",
		icon = "elasticSecurity",
		credentials = {"elasticSecurityApi"}
)
public class ElasticSecurityNode extends AbstractApiNode {

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
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).required(true).defaultValue("case")
						.options(List.of(
								ParameterOption.builder().name("Case").value("case").build(),
								ParameterOption.builder().name("Case Comment").value("caseComment").build(),
								ParameterOption.builder().name("Case Tag").value("caseTag").build(),
								ParameterOption.builder().name("Connector").value("connector").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).required(true).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get Status").value("getStatus").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Remove").value("remove").build()
						)).build(),
				NodeParameter.builder()
						.name("caseId").displayName("Case ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the case.").build(),
				NodeParameter.builder()
						.name("commentId").displayName("Comment ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the comment.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("The title of the case.").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("The description of the case.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated tags.").build(),
				NodeParameter.builder()
						.name("tag").displayName("Tag")
						.type(ParameterType.STRING).defaultValue("")
						.description("The tag to add or remove.").build(),
				NodeParameter.builder()
						.name("comment").displayName("Comment")
						.type(ParameterType.STRING).defaultValue("")
						.description("The comment text.").build(),
				NodeParameter.builder()
						.name("connectorName").displayName("Connector Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the connector.").build(),
				NodeParameter.builder()
						.name("connectorType").displayName("Connector Type")
						.type(ParameterType.OPTIONS).defaultValue(".none")
						.options(List.of(
								ParameterOption.builder().name("None").value(".none").build(),
								ParameterOption.builder().name("Jira").value(".jira").build(),
								ParameterOption.builder().name("IBM Resilient").value(".resilient").build(),
								ParameterOption.builder().name("ServiceNow ITSM").value(".servicenow").build(),
								ParameterOption.builder().name("Swimlane").value(".swimlane").build()
						)).build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.OPTIONS).defaultValue("open")
						.options(List.of(
								ParameterOption.builder().name("Open").value("open").build(),
								ParameterOption.builder().name("In Progress").value("in-progress").build(),
								ParameterOption.builder().name("Closed").value("closed").build()
						)).build(),
				NodeParameter.builder()
						.name("severity").displayName("Severity")
						.type(ParameterType.OPTIONS).defaultValue("low")
						.options(List.of(
								ParameterOption.builder().name("Low").value("low").build(),
								ParameterOption.builder().name("Medium").value("medium").build(),
								ParameterOption.builder().name("High").value("high").build(),
								ParameterOption.builder().name("Critical").value("critical").build()
						)).build(),
				NodeParameter.builder()
						.name("additionalFields").displayName("Additional Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Additional fields as JSON.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max number of results to return.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "case");
		String operation = context.getParameter("operation", "get");
		Map<String, Object> credentials = context.getCredentials();

		String baseUrl = String.valueOf(credentials.getOrDefault("baseUrl", ""));
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		baseUrl += "/api";

		Map<String, String> headers = getAuthHeaders(credentials);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "case" -> handleCase(context, baseUrl, headers, operation);
					case "caseComment" -> handleCaseComment(context, baseUrl, headers, operation);
					case "caseTag" -> handleCaseTag(context, baseUrl, headers, operation);
					case "connector" -> handleConnector(context, baseUrl, headers, operation);
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

	private Map<String, Object> handleCase(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String caseId = context.getParameter("caseId", "");

		return switch (operation) {
			case "create" -> {
				String title = context.getParameter("title", "");
				String description = context.getParameter("description", "");
				String tags = context.getParameter("tags", "");
				String severity = context.getParameter("severity", "low");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", title);
				body.put("description", description);
				body.put("severity", severity);
				if (!tags.isEmpty()) {
					body.put("tags", Arrays.asList(tags.split(",")));
				} else {
					body.put("tags", List.of());
				}
				body.put("connector", Map.of("id", "none", "name", "none", "type", ".none", "fields", Map.of()));
				body.put("settings", Map.of("syncAlerts", true));
				body.put("owner", "securitySolution");

				HttpResponse<String> response = post(baseUrl + "/cases", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				HttpResponse<String> response = delete(baseUrl + "/cases?ids=%5B%22" + encode(caseId) + "%22%5D", headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("caseId", caseId);
				yield result;
			}
			case "get" -> {
				HttpResponse<String> response = get(baseUrl + "/cases/" + encode(caseId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/cases/_find?perPage=" + limit, headers);
				yield parseResponse(response);
			}
			case "getStatus" -> {
				HttpResponse<String> response = get(baseUrl + "/cases/status", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String title = context.getParameter("title", "");
				String description = context.getParameter("description", "");
				String status = context.getParameter("status", "");
				String severity = context.getParameter("severity", "");

				Map<String, Object> caseData = new LinkedHashMap<>();
				caseData.put("id", caseId);
				// Get current version
				HttpResponse<String> getResp = get(baseUrl + "/cases/" + encode(caseId), headers);
				Map<String, Object> current = parseResponse(getResp);
				caseData.put("version", current.get("version"));

				if (!title.isEmpty()) caseData.put("title", title);
				if (!description.isEmpty()) caseData.put("description", description);
				if (!status.isEmpty()) caseData.put("status", status);
				if (!severity.isEmpty()) caseData.put("severity", severity);

				Map<String, Object> body = Map.of("cases", List.of(caseData));
				HttpResponse<String> response = patch(baseUrl + "/cases", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown case operation: " + operation);
		};
	}

	private Map<String, Object> handleCaseComment(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String caseId = context.getParameter("caseId", "");

		return switch (operation) {
			case "add" -> {
				String comment = context.getParameter("comment", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("comment", comment);
				body.put("type", "user");
				body.put("owner", "securitySolution");

				HttpResponse<String> response = post(baseUrl + "/cases/" + encode(caseId) + "/comments", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/cases/" + encode(caseId) + "/comments", headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				String commentId = context.getParameter("commentId", "");
				HttpResponse<String> response = delete(baseUrl + "/cases/" + encode(caseId) + "/comments/" + encode(commentId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				yield result;
			}
			case "update" -> {
				String commentId = context.getParameter("commentId", "");
				String comment = context.getParameter("comment", "");

				// Get current version
				HttpResponse<String> getResp = get(baseUrl + "/cases/" + encode(caseId) + "/comments/" + encode(commentId), headers);
				Map<String, Object> current = parseResponse(getResp);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", commentId);
				body.put("version", current.get("version"));
				body.put("comment", comment);
				body.put("type", "user");
				body.put("owner", "securitySolution");

				HttpResponse<String> response = patch(baseUrl + "/cases/" + encode(caseId) + "/comments", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown case comment operation: " + operation);
		};
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> handleCaseTag(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String caseId = context.getParameter("caseId", "");
		String tag = context.getParameter("tag", "");

		// Get current case to get tags and version
		HttpResponse<String> getResp = get(baseUrl + "/cases/" + encode(caseId), headers);
		Map<String, Object> current = parseResponse(getResp);
		List<String> currentTags = new ArrayList<>((List<String>) current.getOrDefault("tags", List.of()));

		if ("add".equals(operation)) {
			if (!currentTags.contains(tag)) {
				currentTags.add(tag);
			}
		} else if ("remove".equals(operation)) {
			currentTags.remove(tag);
		}

		Map<String, Object> caseData = new LinkedHashMap<>();
		caseData.put("id", caseId);
		caseData.put("version", current.get("version"));
		caseData.put("tags", currentTags);

		Map<String, Object> body = Map.of("cases", List.of(caseData));
		HttpResponse<String> response = patch(baseUrl + "/cases", body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleConnector(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String connectorName = context.getParameter("connectorName", "");
				String connectorType = context.getParameter("connectorType", ".none");
				String additionalFields = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", connectorName);
				body.put("connector_type_id", connectorType);
				body.put("config", parseJson(additionalFields));

				HttpResponse<String> response = post(baseUrl + "/actions/connector", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/actions/connectors", headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("connectors", parseJsonArray(response.body()));
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown connector operation: " + operation);
		};
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("kbn-xsrf", "true");

		String username = String.valueOf(credentials.getOrDefault("username", ""));
		String password = String.valueOf(credentials.getOrDefault("password", ""));
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));

		if (!apiKey.isEmpty()) {
			headers.put("Authorization", "ApiKey " + apiKey);
		} else if (!username.isEmpty() && !password.isEmpty()) {
			String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
			headers.put("Authorization", "Basic " + auth);
		}

		return headers;
	}
}
