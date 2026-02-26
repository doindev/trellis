package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Storyblok — manage stories via the Storyblok Content API and Management API.
 */
@Node(
		type = "storyblok",
		displayName = "Storyblok",
		description = "Get, publish and unpublish stories in Storyblok CMS",
		category = "CMS / Website Builders",
		icon = "storyblok",
		credentials = {"storyblokContentApi", "storyblokManagementApi"}
)
public class StoryblokNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String source = context.getParameter("source", "contentApi");
		String operation = context.getParameter("operation", "getMany");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (source) {
					case "contentApi" -> handleContentApi(context, operation, headers);
					case "managementApi" -> handleManagementApi(context, operation, headers);
					default -> throw new IllegalArgumentException("Unknown source: " + source);
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

	private Map<String, Object> handleContentApi(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String apiKey = context.getCredentialString("apiKey", "");
		String baseUrl = "https://api.storyblok.com/v1/cdn/stories";

		return switch (operation) {
			case "get" -> {
				String identifier = context.getParameter("identifier", "");
				String url = baseUrl + "/" + encode(identifier) + "?token=" + encode(apiKey);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "getMany" -> {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 50);
				String startsWith = context.getParameter("startsWith", "");

				String url = baseUrl + "?token=" + encode(apiKey);
				if (!startsWith.isBlank()) {
					url += "&starts_with=" + encode(startsWith);
				}
				if (!returnAll) {
					url += "&per_page=" + limit;
				} else {
					url += "&per_page=100";
				}

				if (returnAll) {
					List<Object> allStories = new ArrayList<>();
					int page = 1;
					boolean hasMore = true;
					while (hasMore) {
						String pageUrl = url + "&page=" + page;
						HttpResponse<String> response = get(pageUrl, headers);
						Map<String, Object> parsed = parseResponse(response);
						@SuppressWarnings("unchecked")
						List<Object> stories = (List<Object>) parsed.get("stories");
						if (stories != null && !stories.isEmpty()) {
							allStories.addAll(stories);
							page++;
						} else {
							hasMore = false;
						}
					}
					Map<String, Object> result = new LinkedHashMap<>();
					result.put("stories", allStories);
					yield result;
				} else {
					HttpResponse<String> response = get(url, headers);
					yield parseResponse(response);
				}
			}
			default -> throw new IllegalArgumentException("Unknown content API operation: " + operation);
		};
	}

	private Map<String, Object> handleManagementApi(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String accessToken = context.getCredentialString("accessToken", "");
		headers.put("Authorization", accessToken);
		String space = context.getParameter("space", "");
		String baseUrl = "https://mapi.storyblok.com/v1/spaces/" + encode(space) + "/stories";

		return switch (operation) {
			case "delete" -> {
				String storyId = context.getParameter("storyId", "");
				HttpResponse<String> response = delete(baseUrl + "/" + encode(storyId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("storyId", storyId);
				yield result;
			}
			case "get" -> {
				String storyId = context.getParameter("storyId", "");
				HttpResponse<String> response = get(baseUrl + "/" + encode(storyId), headers);
				yield parseResponse(response);
			}
			case "getMany" -> {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 50);
				String startsWith = context.getParameter("startsWith", "");

				String url = baseUrl + "?";
				if (!startsWith.isBlank()) {
					url += "starts_with=" + encode(startsWith) + "&";
				}
				if (!returnAll) {
					url += "per_page=" + limit;
				} else {
					url += "per_page=100";
				}

				if (returnAll) {
					List<Object> allStories = new ArrayList<>();
					int page = 1;
					boolean hasMore = true;
					while (hasMore) {
						String pageUrl = url + "&page=" + page;
						HttpResponse<String> response = get(pageUrl, headers);
						Map<String, Object> parsed = parseResponse(response);
						@SuppressWarnings("unchecked")
						List<Object> stories = (List<Object>) parsed.get("stories");
						if (stories != null && !stories.isEmpty()) {
							allStories.addAll(stories);
							page++;
						} else {
							hasMore = false;
						}
					}
					Map<String, Object> result = new LinkedHashMap<>();
					result.put("stories", allStories);
					yield result;
				} else {
					HttpResponse<String> response = get(url, headers);
					yield parseResponse(response);
				}
			}
			case "publish" -> {
				String storyId = context.getParameter("storyId", "");
				String releaseId = context.getParameter("releaseId", "");
				String language = context.getParameter("language", "");

				String url = baseUrl + "/" + encode(storyId) + "/publish";
				String sep = "?";
				if (!releaseId.isBlank()) {
					url += sep + "release_id=" + encode(releaseId);
					sep = "&";
				}
				if (!language.isBlank()) {
					url += sep + "lang=" + encode(language);
				}

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("storyId", storyId);
				yield result;
			}
			case "unpublish" -> {
				String storyId = context.getParameter("storyId", "");
				String url = baseUrl + "/" + encode(storyId) + "/unpublish";
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("storyId", storyId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown management API operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("source").displayName("Source")
						.type(ParameterType.OPTIONS)
						.defaultValue("contentApi")
						.options(List.of(
								ParameterOption.builder().name("Content API").value("contentApi").build(),
								ParameterOption.builder().name("Management API").value("managementApi").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("getMany")
						.options(List.of(
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Publish").value("publish").build(),
								ParameterOption.builder().name("Unpublish").value("unpublish").build()
						)).build(),
				NodeParameter.builder()
						.name("space").displayName("Space")
						.type(ParameterType.STRING).defaultValue("")
						.description("The space ID (Management API only).").build(),
				NodeParameter.builder()
						.name("storyId").displayName("Story ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The numeric ID of the story.").build(),
				NodeParameter.builder()
						.name("identifier").displayName("Identifier")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID or slug of the story to retrieve (Content API).").build(),
				NodeParameter.builder()
						.name("startsWith").displayName("Starts With")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter stories by slug prefix.").build(),
				NodeParameter.builder()
						.name("releaseId").displayName("Release ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Numeric release identifier for publish.").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.STRING).defaultValue("")
						.description("Language code for publishing.").build(),
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
}
