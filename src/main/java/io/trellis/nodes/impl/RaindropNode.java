package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Raindrop — manage bookmarks, collections, tags, and users using the Raindrop.io API.
 */
@Node(
		type = "raindrop",
		displayName = "Raindrop",
		description = "Manage bookmarks and collections in Raindrop.io",
		category = "Miscellaneous",
		icon = "raindrop",
		credentials = {"raindropOAuth2Api"},
		searchOnly = true
)
public class RaindropNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.raindrop.io/rest/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");

		String resource = context.getParameter("resource", "bookmark");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "bookmark" -> handleBookmark(context, headers, operation);
					case "collection" -> handleCollection(context, headers, operation);
					case "tag" -> handleTag(context, headers, operation);
					case "user" -> handleUser(context, headers, operation);
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

	private Map<String, Object> handleBookmark(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String link = context.getParameter("link", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("link", link);
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String collectionId = context.getParameter("collectionId", "");
				if (!collectionId.isEmpty()) body.put("collection", Map.of("$id", toInt(collectionId, 0)));
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) {
					body.put("tags", Arrays.asList(tags.split("\\s*,\\s*")));
				}
				boolean important = toBoolean(context.getParameters().get("important"), false);
				if (important) body.put("important", true);
				boolean pleaseParse = toBoolean(context.getParameters().get("pleaseParse"), true);
				body.put("pleaseParse", pleaseParse);
				int order = toInt(context.getParameters().get("order"), 0);
				if (order != 0) body.put("order", order);
				HttpResponse<String> response = post(BASE_URL + "/raindrop", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String bookmarkId = context.getParameter("bookmarkId", "");
				HttpResponse<String> response = delete(BASE_URL + "/raindrop/" + encode(bookmarkId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String bookmarkId = context.getParameter("bookmarkId", "");
				HttpResponse<String> response = get(BASE_URL + "/raindrop/" + encode(bookmarkId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String collectionId = context.getParameter("collectionId", "0");
				if (collectionId.isEmpty()) collectionId = "0";
				HttpResponse<String> response = get(BASE_URL + "/raindrops/" + encode(collectionId), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String bookmarkId = context.getParameter("bookmarkId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String link = context.getParameter("link", "");
				if (!link.isEmpty()) body.put("link", link);
				String collectionId = context.getParameter("collectionId", "");
				if (!collectionId.isEmpty()) body.put("collection", Map.of("$id", toInt(collectionId, 0)));
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) {
					body.put("tags", Arrays.asList(tags.split("\\s*,\\s*")));
				}
				String importantStr = context.getParameter("important", "");
				if (!importantStr.isEmpty()) body.put("important", toBoolean(importantStr, false));
				HttpResponse<String> response = put(BASE_URL + "/raindrop/" + encode(bookmarkId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown bookmark operation: " + operation);
		};
	}

	private Map<String, Object> handleCollection(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String title = context.getParameter("title", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", title);
				String parentId = context.getParameter("parentId", "");
				if (!parentId.isEmpty()) body.put("parent", Map.of("$id", toInt(parentId, 0)));
				boolean isPublic = toBoolean(context.getParameters().get("public"), false);
				body.put("public", isPublic);
				String cover = context.getParameter("cover", "");
				if (!cover.isEmpty()) body.put("cover", List.of(cover));
				String view = context.getParameter("view", "");
				if (!view.isEmpty()) body.put("view", view);
				int sort = toInt(context.getParameters().get("sort"), 0);
				if (sort != 0) body.put("sort", sort);
				HttpResponse<String> response = post(BASE_URL + "/collection", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String collectionId = context.getParameter("collectionId", "");
				HttpResponse<String> response = delete(BASE_URL + "/collection/" + encode(collectionId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String collectionId = context.getParameter("collectionId", "");
				HttpResponse<String> response = get(BASE_URL + "/collection/" + encode(collectionId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				// Get parent collections and child collections
				HttpResponse<String> parentResponse = get(BASE_URL + "/collections", headers);
				Map<String, Object> parentData = parseResponse(parentResponse);
				HttpResponse<String> childResponse = get(BASE_URL + "/collections/childrens", headers);
				Map<String, Object> childData = parseResponse(childResponse);
				Map<String, Object> combined = new LinkedHashMap<>();
				combined.put("parentCollections", parentData);
				combined.put("childCollections", childData);
				yield combined;
			}
			case "update" -> {
				String collectionId = context.getParameter("collectionId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String parentId = context.getParameter("parentId", "");
				if (!parentId.isEmpty()) body.put("parent", Map.of("$id", toInt(parentId, 0)));
				String isPublicStr = context.getParameter("public", "");
				if (!isPublicStr.isEmpty()) body.put("public", toBoolean(isPublicStr, false));
				String cover = context.getParameter("cover", "");
				if (!cover.isEmpty()) body.put("cover", List.of(cover));
				String view = context.getParameter("view", "");
				if (!view.isEmpty()) body.put("view", view);
				int sort = toInt(context.getParameters().get("sort"), 0);
				if (sort != 0) body.put("sort", sort);
				HttpResponse<String> response = put(BASE_URL + "/collection/" + encode(collectionId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown collection operation: " + operation);
		};
	}

	private Map<String, Object> handleTag(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "delete" -> {
				String tags = context.getParameter("tags", "");
				String collectionId = context.getParameter("collectionId", "");
				StringBuilder url = new StringBuilder(BASE_URL + "/tags");
				if (!collectionId.isEmpty()) url.append("/").append(encode(collectionId));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("tags", Arrays.asList(tags.split("\\s*,\\s*")));
				HttpResponse<String> response = delete(url.toString(), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String collectionId = context.getParameter("collectionId", "");
				StringBuilder url = new StringBuilder(BASE_URL + "/tags");
				if (!collectionId.isEmpty()) url.append("/").append(encode(collectionId));
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown tag operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				HttpResponse<String> response = get(BASE_URL + "/user", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("bookmark")
						.options(List.of(
								ParameterOption.builder().name("Bookmark").value("bookmark").build(),
								ParameterOption.builder().name("Collection").value("collection").build(),
								ParameterOption.builder().name("Tag").value("tag").build(),
								ParameterOption.builder().name("User").value("user").build()
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
						.name("bookmarkId").displayName("Bookmark ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the bookmark.").build(),
				NodeParameter.builder()
						.name("collectionId").displayName("Collection ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the collection (0 for all).").build(),
				NodeParameter.builder()
						.name("link").displayName("Link")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of the bookmark.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title of the bookmark or collection.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of tags.").build(),
				NodeParameter.builder()
						.name("important").displayName("Important")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Mark as favorite.").build(),
				NodeParameter.builder()
						.name("pleaseParse").displayName("Parse Metadata")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Automatically parse cover, description, and HTML.").build(),
				NodeParameter.builder()
						.name("order").displayName("Order")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Sort order.").build(),
				NodeParameter.builder()
						.name("parentId").displayName("Parent Collection ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Parent collection ID for nested collections.").build(),
				NodeParameter.builder()
						.name("public").displayName("Public")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether the collection is public.").build(),
				NodeParameter.builder()
						.name("cover").displayName("Cover URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of the cover image.").build(),
				NodeParameter.builder()
						.name("view").displayName("View")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("Simple").value("simple").build(),
								ParameterOption.builder().name("Grid").value("grid").build(),
								ParameterOption.builder().name("Masonry").value("masonry").build()
						))
						.description("Collection view style.").build(),
				NodeParameter.builder()
						.name("sort").displayName("Sort")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Collection sort order.").build()
		);
	}
}
