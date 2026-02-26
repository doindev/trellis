package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Docs — create, get, and update Google Docs via the Google Docs API.
 */
@Node(
		type = "googleDocs",
		displayName = "Google Docs",
		description = "Create, get, and update Google Docs documents",
		category = "Google",
		icon = "googleDocs",
		credentials = {"googleDocsOAuth2Api"}
)
public class GoogleDocsNode extends AbstractApiNode {

	private static final String BASE_URL = "https://docs.googleapis.com/v1";

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
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("get")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Create").value("create")
								.description("Create a new document").build(),
						ParameterOption.builder().name("Get").value("get")
								.description("Get a document").build(),
						ParameterOption.builder().name("Update").value("update")
								.description("Update a document via batchUpdate").build()
				)).build());

		// Create: title
		params.add(NodeParameter.builder()
				.name("title").displayName("Title")
				.type(ParameterType.STRING).required(true)
				.description("The title of the new document.")
				.placeHolder("My Document")
				.displayOptions(Map.of("show", Map.of("operation", List.of("create"))))
				.build());

		// Create: initial content
		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("initialContent").displayName("Initial Content")
								.type(ParameterType.STRING)
								.typeOptions(Map.of("rows", 6))
								.description("Text content to insert into the document after creation.")
								.build()
				)).build());

		// Get / Update: documentId
		params.add(NodeParameter.builder()
				.name("documentId").displayName("Document ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the document.")
				.placeHolder("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms")
				.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update"))))
				.build());

		// Update: action type
		params.add(NodeParameter.builder()
				.name("updateAction").displayName("Action")
				.type(ParameterType.OPTIONS).required(true).defaultValue("insertText")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"))))
				.options(List.of(
						ParameterOption.builder().name("Insert Text").value("insertText")
								.description("Insert text at a specified location").build(),
						ParameterOption.builder().name("Replace All Text").value("replaceAllText")
								.description("Replace all instances of text").build(),
						ParameterOption.builder().name("Delete Content Range").value("deleteContentRange")
								.description("Delete a range of content").build()
				)).build());

		// Update > Insert Text
		params.add(NodeParameter.builder()
				.name("insertText").displayName("Text to Insert")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 4))
				.description("The text to insert into the document.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"), "updateAction", List.of("insertText"))))
				.build());

		params.add(NodeParameter.builder()
				.name("insertIndex").displayName("Index")
				.type(ParameterType.NUMBER).required(true).defaultValue(1)
				.description("The index in the document to insert at (1-based).")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"), "updateAction", List.of("insertText"))))
				.build());

		// Update > Replace All Text
		params.add(NodeParameter.builder()
				.name("searchText").displayName("Search Text")
				.type(ParameterType.STRING).required(true)
				.description("The text to search for.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"), "updateAction", List.of("replaceAllText"))))
				.build());

		params.add(NodeParameter.builder()
				.name("replaceText").displayName("Replace With")
				.type(ParameterType.STRING).required(true)
				.description("The text to replace with.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"), "updateAction", List.of("replaceAllText"))))
				.build());

		params.add(NodeParameter.builder()
				.name("matchCase").displayName("Match Case")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"), "updateAction", List.of("replaceAllText"))))
				.build());

		// Update > Delete Content Range
		params.add(NodeParameter.builder()
				.name("deleteStartIndex").displayName("Start Index")
				.type(ParameterType.NUMBER).required(true)
				.description("The start index of the range to delete.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"), "updateAction", List.of("deleteContentRange"))))
				.build());

		params.add(NodeParameter.builder()
				.name("deleteEndIndex").displayName("End Index")
				.type(ParameterType.NUMBER).required(true)
				.description("The end index of the range to delete.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("update"), "updateAction", List.of("deleteContentRange"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "get");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (operation) {
				case "create" -> executeCreate(context, credentials);
				case "get" -> executeGet(context, credentials);
				case "update" -> executeUpdate(context, credentials);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Google Docs error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeCreate(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String title = context.getParameter("title", "");
		Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);

		HttpResponse<String> response = post(BASE_URL + "/documents", body, headers);
		Map<String, Object> result = parseResponse(response);

		// If initial content is provided, insert it via batchUpdate
		if (additionalFields.get("initialContent") != null && result.get("documentId") != null) {
			String documentId = (String) result.get("documentId");
			String initialContent = (String) additionalFields.get("initialContent");

			Map<String, Object> insertRequest = Map.of(
					"insertText", Map.of(
							"text", initialContent,
							"location", Map.of("index", 1)
					)
			);

			Map<String, Object> batchBody = Map.of("requests", List.of(insertRequest));
			post(BASE_URL + "/documents/" + documentId + ":batchUpdate", batchBody, headers);

			// Re-fetch the document to get updated state
			HttpResponse<String> getResponse = get(BASE_URL + "/documents/" + documentId, headers);
			result = parseResponse(getResponse);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String documentId = context.getParameter("documentId", "");
		HttpResponse<String> response = get(BASE_URL + "/documents/" + documentId, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String documentId = context.getParameter("documentId", "");
		String updateAction = context.getParameter("updateAction", "insertText");

		Map<String, Object> request;

		switch (updateAction) {
			case "insertText": {
				String text = context.getParameter("insertText", "");
				int index = toInt(context.getParameter("insertIndex", 1), 1);

				request = Map.of(
						"insertText", Map.of(
								"text", text,
								"location", Map.of("index", index)
						)
				);
				break;
			}
			case "replaceAllText": {
				String searchText = context.getParameter("searchText", "");
				String replaceText = context.getParameter("replaceText", "");
				boolean matchCase = toBoolean(context.getParameter("matchCase", true), true);

				request = Map.of(
						"replaceAllText", Map.of(
								"containsText", Map.of(
										"text", searchText,
										"matchCase", matchCase
								),
								"replaceText", replaceText
						)
				);
				break;
			}
			case "deleteContentRange": {
				int startIndex = toInt(context.getParameter("deleteStartIndex", 0), 0);
				int endIndex = toInt(context.getParameter("deleteEndIndex", 0), 0);

				request = Map.of(
						"deleteContentRange", Map.of(
								"range", Map.of(
										"startIndex", startIndex,
										"endIndex", endIndex
								)
						)
				);
				break;
			}
			default:
				return NodeExecutionResult.error("Unknown update action: " + updateAction);
		}

		Map<String, Object> body = Map.of("requests", List.of(request));
		HttpResponse<String> response = post(BASE_URL + "/documents/" + documentId + ":batchUpdate", body, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
