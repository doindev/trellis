package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Google Books — search volumes and manage bookshelves via the Google Books API.
 */
@Node(
		type = "googleBooks",
		displayName = "Google Books",
		description = "Search volumes and manage bookshelves in Google Books",
		category = "Google",
		icon = "googleBooks",
		credentials = {"googleBooksApi"}
)
public class GoogleBooksNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.googleapis.com/books";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "volume");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "volume" -> handleVolume(context, headers, operation);
					case "bookshelf" -> handleBookshelf(context, headers, operation);
					case "bookshelfVolume" -> handleBookshelfVolume(context, headers, operation);
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

	private Map<String, Object> handleVolume(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String volumeId = context.getParameter("volumeId", "");
				HttpResponse<String> response = get(BASE_URL + "/v1/volumes/" + volumeId, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String searchQuery = context.getParameter("searchQuery", "");
				int limit = toInt(context.getParameters().get("limit"), 40);
				String url = BASE_URL + "/v1/volumes?q=" + encode(searchQuery) + "&maxResults=" + Math.min(limit, 40);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown volume operation: " + operation);
		};
	}

	private Map<String, Object> handleBookshelf(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		boolean myLibrary = toBoolean(context.getParameters().get("myLibrary"), true);
		return switch (operation) {
			case "get" -> {
				String shelfId = context.getParameter("shelfId", "");
				String url;
				if (myLibrary) {
					url = BASE_URL + "/v1/mylibrary/bookshelves/" + shelfId;
				} else {
					String userId = context.getParameter("userId", "");
					url = BASE_URL + "/v1/users/" + userId + "/bookshelves/" + shelfId;
				}
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String url;
				if (myLibrary) {
					url = BASE_URL + "/v1/mylibrary/bookshelves";
				} else {
					String userId = context.getParameter("userId", "");
					url = BASE_URL + "/v1/users/" + userId + "/bookshelves";
				}
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown bookshelf operation: " + operation);
		};
	}

	private Map<String, Object> handleBookshelfVolume(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String shelfId = context.getParameter("shelfId", "");
		String base = BASE_URL + "/v1/mylibrary/bookshelves/" + shelfId;

		return switch (operation) {
			case "add" -> {
				String volumeId = context.getParameter("volumeId", "");
				HttpResponse<String> response = post(base + "/addVolume?volumeId=" + encode(volumeId), Map.of(), headers);
				yield Map.of("success", true, "volumeId", volumeId, "statusCode", response.statusCode());
			}
			case "clear" -> {
				HttpResponse<String> response = post(base + "/clearVolumes", Map.of(), headers);
				yield Map.of("success", true, "statusCode", response.statusCode());
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 40);
				HttpResponse<String> response = get(base + "/volumes?maxResults=" + Math.min(limit, 40), headers);
				yield parseResponse(response);
			}
			case "move" -> {
				String volumeId = context.getParameter("volumeId", "");
				int position = toInt(context.getParameters().get("volumePosition"), 0);
				HttpResponse<String> response = post(base + "/moveVolume?volumeId=" + encode(volumeId) + "&volumePosition=" + position, Map.of(), headers);
				yield Map.of("success", true, "volumeId", volumeId, "position", position);
			}
			case "remove" -> {
				String volumeId = context.getParameter("volumeId", "");
				HttpResponse<String> response = post(base + "/removeVolume?volumeId=" + encode(volumeId), Map.of(), headers);
				yield Map.of("success", true, "volumeId", volumeId, "statusCode", response.statusCode());
			}
			default -> throw new IllegalArgumentException("Unknown bookshelfVolume operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("volume")
						.options(List.of(
								ParameterOption.builder().name("Bookshelf").value("bookshelf").build(),
								ParameterOption.builder().name("Bookshelf Volume").value("bookshelfVolume").build(),
								ParameterOption.builder().name("Volume").value("volume").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Clear").value("clear").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Move").value("move").build(),
								ParameterOption.builder().name("Remove").value("remove").build()
						)).build(),
				NodeParameter.builder()
						.name("volumeId").displayName("Volume ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("searchQuery").displayName("Search Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Full-text search query.").build(),
				NodeParameter.builder()
						.name("shelfId").displayName("Bookshelf ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("myLibrary").displayName("My Library")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Use your own library vs public bookshelves.").build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("User ID for public bookshelves.").build(),
				NodeParameter.builder()
						.name("volumePosition").displayName("Volume Position")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("0-indexed position for move operation.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(40)
						.description("Max results to return (1-40).").build()
		);
	}
}
