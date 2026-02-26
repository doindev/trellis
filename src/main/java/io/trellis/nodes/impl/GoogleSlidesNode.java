package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Slides — create, get, and modify Google Slides presentations.
 */
@Node(
		type = "googleSlides",
		displayName = "Google Slides",
		description = "Create, get, and modify Google Slides presentations",
		category = "Google",
		icon = "googleSlides",
		credentials = {"googleSlidesOAuth2Api"}
)
public class GoogleSlidesNode extends AbstractApiNode {

	private static final String BASE_URL = "https://slides.googleapis.com/v1";

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
								.description("Create a new presentation").build(),
						ParameterOption.builder().name("Get").value("get")
								.description("Get a presentation").build(),
						ParameterOption.builder().name("Get Slide").value("getSlide")
								.description("Get a specific slide from a presentation").build(),
						ParameterOption.builder().name("Replace Text").value("replaceText")
								.description("Replace text in a presentation").build()
				)).build());

		// Create: title
		params.add(NodeParameter.builder()
				.name("title").displayName("Title")
				.type(ParameterType.STRING).required(true)
				.description("The title of the new presentation.")
				.placeHolder("My Presentation")
				.displayOptions(Map.of("show", Map.of("operation", List.of("create"))))
				.build());

		// Get / GetSlide / ReplaceText: presentationId
		params.add(NodeParameter.builder()
				.name("presentationId").displayName("Presentation ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the presentation.")
				.placeHolder("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms")
				.displayOptions(Map.of("show", Map.of("operation", List.of("get", "getSlide", "replaceText"))))
				.build());

		// GetSlide: slideIndex
		params.add(NodeParameter.builder()
				.name("slideIndex").displayName("Slide Index")
				.type(ParameterType.NUMBER).required(true).defaultValue(0)
				.description("The 0-based index of the slide to get.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("getSlide"))))
				.build());

		// ReplaceText: search text
		params.add(NodeParameter.builder()
				.name("searchText").displayName("Search Text")
				.type(ParameterType.STRING).required(true)
				.description("The text to search for in the presentation.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("replaceText"))))
				.build());

		// ReplaceText: replace text
		params.add(NodeParameter.builder()
				.name("replaceText").displayName("Replace With")
				.type(ParameterType.STRING).required(true)
				.description("The text to replace with.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("replaceText"))))
				.build());

		// ReplaceText: match case
		params.add(NodeParameter.builder()
				.name("matchCase").displayName("Match Case")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("replaceText"))))
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
				case "getSlide" -> executeGetSlide(context, credentials);
				case "replaceText" -> executeReplaceText(context, credentials);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Google Slides error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeCreate(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String title = context.getParameter("title", "");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", title);

		HttpResponse<String> response = post(BASE_URL + "/presentations", body, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String presentationId = context.getParameter("presentationId", "");
		HttpResponse<String> response = get(BASE_URL + "/presentations/" + presentationId, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeGetSlide(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String presentationId = context.getParameter("presentationId", "");
		int slideIndex = toInt(context.getParameter("slideIndex", 0), 0);

		// Fetch the full presentation to get the slide at the given index
		HttpResponse<String> response = get(BASE_URL + "/presentations/" + presentationId, headers);
		Map<String, Object> result = parseResponse(response);

		Object slides = result.get("slides");
		if (slides instanceof List) {
			List<?> slidesList = (List<?>) slides;
			if (slideIndex >= 0 && slideIndex < slidesList.size()) {
				Object slide = slidesList.get(slideIndex);
				return NodeExecutionResult.success(List.of(wrapInJson(slide)));
			} else {
				return NodeExecutionResult.error("Slide index " + slideIndex + " is out of range. Presentation has " + slidesList.size() + " slides.");
			}
		}
		return NodeExecutionResult.error("No slides found in presentation.");
	}

	private NodeExecutionResult executeReplaceText(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String presentationId = context.getParameter("presentationId", "");
		String searchText = context.getParameter("searchText", "");
		String replaceText = context.getParameter("replaceText", "");
		boolean matchCase = toBoolean(context.getParameter("matchCase", true), true);

		Map<String, Object> replaceRequest = Map.of(
				"replaceAllText", Map.of(
						"containsText", Map.of(
								"text", searchText,
								"matchCase", matchCase
						),
						"replaceText", replaceText
				)
		);

		Map<String, Object> body = Map.of("requests", List.of(replaceRequest));
		HttpResponse<String> response = post(BASE_URL + "/presentations/" + presentationId + ":batchUpdate", body, headers);
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
