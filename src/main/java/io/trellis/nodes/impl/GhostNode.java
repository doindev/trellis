package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Ghost — manage posts via the Ghost Content and Admin APIs.
 */
@Node(
		type = "ghost",
		displayName = "Ghost",
		description = "Create, read, update and delete posts in Ghost CMS",
		category = "CMS / Website Builders",
		icon = "ghost",
		credentials = {"ghostAdminApi", "ghostContentApi"}
)
public class GhostNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiUrl = context.getCredentialString("url", "");
		String apiKey = context.getCredentialString("apiKey", "");
		String operation = context.getParameter("operation", "getMany");

		if (apiUrl.endsWith("/")) {
			apiUrl = apiUrl.substring(0, apiUrl.length() - 1);
		}

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept-Version", "v5.0");

		// Admin API uses JWT auth header; Content API uses ?key= query param
		boolean isAdminApi = apiKey.contains(":");
		if (isAdminApi) {
			headers.put("Authorization", "Ghost " + apiKey);
		}

		String baseEndpoint = apiUrl + (isAdminApi ? "/ghost/api/admin" : "/ghost/api/content");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> handleCreate(context, baseEndpoint, headers);
					case "delete" -> handleDelete(context, baseEndpoint, headers);
					case "get" -> handleGet(context, baseEndpoint, headers, apiKey, isAdminApi);
					case "getMany" -> handleGetMany(context, baseEndpoint, headers, apiKey, isAdminApi);
					case "update" -> handleUpdate(context, baseEndpoint, headers);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	private Map<String, Object> handleCreate(NodeExecutionContext context, String baseEndpoint,
			Map<String, String> headers) throws Exception {
		String title = context.getParameter("title", "");
		String contentFormat = context.getParameter("contentFormat", "html");
		String content = context.getParameter("content", "");
		String status = context.getParameter("status", "draft");

		Map<String, Object> post = new LinkedHashMap<>();
		post.put("title", title);
		post.put("status", status);
		switch (contentFormat) {
			case "html" -> post.put("html", content);
			case "mobiledoc" -> post.put("mobiledoc", content);
			case "lexical" -> post.put("lexical", content);
		}

		Map<String, Object> body = Map.of("posts", List.of(post));

		HttpResponse<String> response = post(baseEndpoint + "/posts/", body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleDelete(NodeExecutionContext context, String baseEndpoint,
			Map<String, String> headers) throws Exception {
		String postId = context.getParameter("postId", "");
		HttpResponse<String> response = delete(baseEndpoint + "/posts/" + encode(postId) + "/", headers);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("postId", postId);
		return result;
	}

	private Map<String, Object> handleGet(NodeExecutionContext context, String baseEndpoint,
			Map<String, String> headers, String apiKey, boolean isAdminApi) throws Exception {
		String identifier = context.getParameter("identifier", "");
		String by = context.getParameter("by", "id");

		String url;
		if ("slug".equals(by)) {
			url = baseEndpoint + "/posts/slug/" + encode(identifier) + "/";
		} else {
			url = baseEndpoint + "/posts/" + encode(identifier) + "/";
		}

		if (!isAdminApi) {
			url += "?key=" + encode(apiKey);
		}

		HttpResponse<String> response = get(url, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleGetMany(NodeExecutionContext context, String baseEndpoint,
			Map<String, String> headers, String apiKey, boolean isAdminApi) throws Exception {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 15);

		String url = baseEndpoint + "/posts/?limit=" + (returnAll ? "all" : limit);
		if (!isAdminApi) {
			url += "&key=" + encode(apiKey);
		}

		HttpResponse<String> response = get(url, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleUpdate(NodeExecutionContext context, String baseEndpoint,
			Map<String, String> headers) throws Exception {
		String postId = context.getParameter("postId", "");
		String contentFormat = context.getParameter("contentFormat", "html");
		String content = context.getParameter("content", "");
		String title = context.getParameter("title", "");
		String status = context.getParameter("status", "");

		// First get the current post to get the updated_at field
		HttpResponse<String> getResp = get(baseEndpoint + "/posts/" + encode(postId) + "/", headers);
		Map<String, Object> current = parseResponse(getResp);

		Map<String, Object> post = new LinkedHashMap<>();
		if (!title.isBlank()) post.put("title", title);
		if (!status.isBlank()) post.put("status", status);
		if (!content.isBlank()) {
			switch (contentFormat) {
				case "html" -> post.put("html", content);
				case "mobiledoc" -> post.put("mobiledoc", content);
				case "lexical" -> post.put("lexical", content);
			}
		}

		// Extract updated_at from current post
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> posts = (List<Map<String, Object>>) current.get("posts");
		if (posts != null && !posts.isEmpty()) {
			post.put("updated_at", posts.get(0).get("updated_at"));
		}

		Map<String, Object> body = Map.of("posts", List.of(post));
		HttpResponse<String> response = put(baseEndpoint + "/posts/" + encode(postId) + "/", body, headers);
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("getMany")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("postId").displayName("Post ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the post.").build(),
				NodeParameter.builder()
						.name("identifier").displayName("Identifier")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID or slug of the post to retrieve.").build(),
				NodeParameter.builder()
						.name("by").displayName("By")
						.type(ParameterType.OPTIONS)
						.defaultValue("id")
						.options(List.of(
								ParameterOption.builder().name("ID").value("id").build(),
								ParameterOption.builder().name("Slug").value("slug").build()
						)).build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title of the post.").build(),
				NodeParameter.builder()
						.name("contentFormat").displayName("Content Format")
						.type(ParameterType.OPTIONS)
						.defaultValue("html")
						.options(List.of(
								ParameterOption.builder().name("HTML").value("html").build(),
								ParameterOption.builder().name("Mobile Doc").value("mobiledoc").build(),
								ParameterOption.builder().name("Lexical").value("lexical").build()
						)).build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("Post content.").build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.OPTIONS)
						.defaultValue("draft")
						.options(List.of(
								ParameterOption.builder().name("Draft").value("draft").build(),
								ParameterOption.builder().name("Published").value("published").build(),
								ParameterOption.builder().name("Scheduled").value("scheduled").build()
						)).build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(15)
						.description("Max number of results to return.").build()
		);
	}
}
