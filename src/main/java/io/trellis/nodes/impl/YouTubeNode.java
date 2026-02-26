package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * YouTube — manage channels, playlists, playlist items, and videos via the YouTube Data API v3.
 */
@Node(
		type = "youTube",
		displayName = "YouTube",
		description = "Manage YouTube channels, playlists, and videos",
		category = "Google",
		icon = "youTube",
		credentials = {"youTubeOAuth2Api"}
)
public class YouTubeNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.googleapis.com/youtube/v3";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("video")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Channel").value("channel")
								.description("Get channel information").build(),
						ParameterOption.builder().name("Playlist").value("playlist")
								.description("Manage playlists").build(),
						ParameterOption.builder().name("Playlist Item").value("playlistItem")
								.description("Manage playlist items").build(),
						ParameterOption.builder().name("Video").value("video")
								.description("Manage videos").build()
				)).build());

		// Channel operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("get")
				.displayOptions(Map.of("show", Map.of("resource", List.of("channel"))))
				.options(List.of(
						ParameterOption.builder().name("Get").value("get").description("Get a channel").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all channels").build()
				)).build());

		// Playlist operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a playlist").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a playlist").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a playlist").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all playlists").build()
				)).build());

		// Playlist Item operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlistItem"))))
				.options(List.of(
						ParameterOption.builder().name("Add").value("add").description("Add a video to a playlist").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Remove an item from a playlist").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all items in a playlist").build()
				)).build());

		// Video operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("get")
				.displayOptions(Map.of("show", Map.of("resource", List.of("video"))))
				.options(List.of(
						ParameterOption.builder().name("Delete").value("delete").description("Delete a video").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a video").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all videos for a channel").build(),
						ParameterOption.builder().name("Rate").value("rate").description("Rate a video").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a video").build()
				)).build());

		// Channel > Get: channelId
		params.add(NodeParameter.builder()
				.name("channelId").displayName("Channel ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the YouTube channel.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("channel"), "operation", List.of("get"))))
				.build());

		// Channel > GetAll: use mine or by username
		params.add(NodeParameter.builder()
				.name("channelBy").displayName("Get Channels By")
				.type(ParameterType.OPTIONS).required(true).defaultValue("mine")
				.displayOptions(Map.of("show", Map.of("resource", List.of("channel"), "operation", List.of("getAll"))))
				.options(List.of(
						ParameterOption.builder().name("Mine").value("mine").description("Get my channels").build(),
						ParameterOption.builder().name("Username").value("username").description("Get by username").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("username").displayName("Username")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("channel"), "operation", List.of("getAll"), "channelBy", List.of("username"))))
				.build());

		// Playlist > Create: title, description
		params.add(NodeParameter.builder()
				.name("playlistTitle").displayName("Title")
				.type(ParameterType.STRING).required(true)
				.description("The title of the playlist.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("playlistAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 4)).build(),
						NodeParameter.builder().name("privacyStatus").displayName("Privacy Status")
								.type(ParameterType.OPTIONS).defaultValue("private")
								.options(List.of(
										ParameterOption.builder().name("Public").value("public").build(),
										ParameterOption.builder().name("Unlisted").value("unlisted").build(),
										ParameterOption.builder().name("Private").value("private").build()
								)).build(),
						NodeParameter.builder().name("tags").displayName("Tags")
								.type(ParameterType.STRING)
								.description("Comma-separated tags.").build()
				)).build());

		// Playlist > Get / Delete: playlistId
		params.add(NodeParameter.builder()
				.name("playlistId").displayName("Playlist ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("get", "delete"))))
				.build());

		// PlaylistItem > Add: playlistId, videoId
		params.add(NodeParameter.builder()
				.name("playlistId").displayName("Playlist ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlistItem"), "operation", List.of("add", "getAll"))))
				.build());

		params.add(NodeParameter.builder()
				.name("videoId").displayName("Video ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the video to add.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlistItem"), "operation", List.of("add"))))
				.build());

		// PlaylistItem > Delete: playlistItemId
		params.add(NodeParameter.builder()
				.name("playlistItemId").displayName("Playlist Item ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the playlist item to remove.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("playlistItem"), "operation", List.of("delete"))))
				.build());

		// Video > Get / Delete / Rate / Update: videoId
		params.add(NodeParameter.builder()
				.name("videoId").displayName("Video ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("video"), "operation", List.of("get", "delete", "rate", "update"))))
				.build());

		// Video > GetAll: channelId for search
		params.add(NodeParameter.builder()
				.name("channelId").displayName("Channel ID")
				.type(ParameterType.STRING).required(true)
				.description("The channel ID to list videos from.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("video"), "operation", List.of("getAll"))))
				.build());

		// Video > Rate: rating
		params.add(NodeParameter.builder()
				.name("rating").displayName("Rating")
				.type(ParameterType.OPTIONS).required(true).defaultValue("like")
				.displayOptions(Map.of("show", Map.of("resource", List.of("video"), "operation", List.of("rate"))))
				.options(List.of(
						ParameterOption.builder().name("Like").value("like").build(),
						ParameterOption.builder().name("Dislike").value("dislike").build(),
						ParameterOption.builder().name("None").value("none").description("Remove rating").build()
				)).build());

		// Video > Update: update fields
		params.add(NodeParameter.builder()
				.name("videoUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("video"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("title").displayName("Title")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 4)).build(),
						NodeParameter.builder().name("tags").displayName("Tags")
								.type(ParameterType.STRING)
								.description("Comma-separated tags.").build(),
						NodeParameter.builder().name("categoryId").displayName("Category ID")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("privacyStatus").displayName("Privacy Status")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Public").value("public").build(),
										ParameterOption.builder().name("Unlisted").value("unlisted").build(),
										ParameterOption.builder().name("Private").value("private").build()
								)).build(),
						NodeParameter.builder().name("embeddable").displayName("Embeddable")
								.type(ParameterType.BOOLEAN).build(),
						NodeParameter.builder().name("publicStatsViewable").displayName("Public Stats Viewable")
								.type(ParameterType.BOOLEAN).build()
				)).build());

		// Limit for getAll operations
		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(50)
				.description("Maximum number of results to return.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "video");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "channel" -> executeChannel(context, credentials);
				case "playlist" -> executePlaylist(context, credentials);
				case "playlistItem" -> executePlaylistItem(context, credentials);
				case "video" -> executeVideo(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "YouTube error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeChannel(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "get");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "get": {
				String channelId = context.getParameter("channelId", "");
				String url = BASE_URL + "/channels?part=snippet,contentDetails,statistics&id=" + encode(channelId);
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List && !((List<?>) items).isEmpty()) {
					return NodeExecutionResult.success(List.of(wrapInJson(((List<?>) items).get(0))));
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String channelBy = context.getParameter("channelBy", "mine");
				int limit = toInt(context.getParameter("limit", 50), 50);

				String url;
				if ("mine".equals(channelBy)) {
					url = BASE_URL + "/channels?part=snippet,contentDetails,statistics&mine=true&maxResults=" + limit;
				} else {
					String username = context.getParameter("username", "");
					url = BASE_URL + "/channels?part=snippet,contentDetails,statistics&forUsername=" + encode(username) + "&maxResults=" + limit;
				}

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List) {
					List<Map<String, Object>> channelItems = new ArrayList<>();
					for (Object item : (List<?>) items) {
						if (item instanceof Map) {
							channelItems.add(wrapInJson(item));
						}
					}
					return channelItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(channelItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown channel operation: " + operation);
		}
	}

	private NodeExecutionResult executePlaylist(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "create": {
				String title = context.getParameter("playlistTitle", "");
				Map<String, Object> additionalFields = context.getParameter("playlistAdditionalFields", Map.of());

				Map<String, Object> snippet = new LinkedHashMap<>();
				snippet.put("title", title);
				if (additionalFields.get("description") != null) {
					snippet.put("description", additionalFields.get("description"));
				}
				if (additionalFields.get("tags") != null) {
					String tagsStr = (String) additionalFields.get("tags");
					snippet.put("tags", Arrays.asList(tagsStr.split(",")));
				}

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("snippet", snippet);

				if (additionalFields.get("privacyStatus") != null) {
					body.put("status", Map.of("privacyStatus", additionalFields.get("privacyStatus")));
				} else {
					body.put("status", Map.of("privacyStatus", "private"));
				}

				HttpResponse<String> response = post(BASE_URL + "/playlists?part=snippet,status", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String playlistId = context.getParameter("playlistId", "");
				HttpResponse<String> response = delete(BASE_URL + "/playlists?id=" + encode(playlistId), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", playlistId))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String playlistId = context.getParameter("playlistId", "");
				String url = BASE_URL + "/playlists?part=snippet,contentDetails,status&id=" + encode(playlistId);
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List && !((List<?>) items).isEmpty()) {
					return NodeExecutionResult.success(List.of(wrapInJson(((List<?>) items).get(0))));
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = BASE_URL + "/playlists?part=snippet,contentDetails,status&mine=true&maxResults=" + limit;

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List) {
					List<Map<String, Object>> playlistItems = new ArrayList<>();
					for (Object item : (List<?>) items) {
						if (item instanceof Map) {
							playlistItems.add(wrapInJson(item));
						}
					}
					return playlistItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(playlistItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown playlist operation: " + operation);
		}
	}

	private NodeExecutionResult executePlaylistItem(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "add": {
				String playlistId = context.getParameter("playlistId", "");
				String videoId = context.getParameter("videoId", "");

				Map<String, Object> body = Map.of(
						"snippet", Map.of(
								"playlistId", playlistId,
								"resourceId", Map.of(
										"kind", "youtube#video",
										"videoId", videoId
								)
						)
				);

				HttpResponse<String> response = post(BASE_URL + "/playlistItems?part=snippet", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String playlistItemId = context.getParameter("playlistItemId", "");
				HttpResponse<String> response = delete(BASE_URL + "/playlistItems?id=" + encode(playlistItemId), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", playlistItemId))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String playlistId = context.getParameter("playlistId", "");
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = BASE_URL + "/playlistItems?part=snippet,contentDetails&playlistId=" + encode(playlistId) + "&maxResults=" + limit;

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List) {
					List<Map<String, Object>> playlistItemsList = new ArrayList<>();
					for (Object item : (List<?>) items) {
						if (item instanceof Map) {
							playlistItemsList.add(wrapInJson(item));
						}
					}
					return playlistItemsList.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(playlistItemsList);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown playlist item operation: " + operation);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeVideo(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "get");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		switch (operation) {
			case "delete": {
				String videoId = context.getParameter("videoId", "");
				HttpResponse<String> response = delete(BASE_URL + "/videos?id=" + encode(videoId), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", videoId))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String videoId = context.getParameter("videoId", "");
				String url = BASE_URL + "/videos?part=snippet,contentDetails,statistics,status&id=" + encode(videoId);
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List && !((List<?>) items).isEmpty()) {
					return NodeExecutionResult.success(List.of(wrapInJson(((List<?>) items).get(0))));
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String channelId = context.getParameter("channelId", "");
				int limit = toInt(context.getParameter("limit", 50), 50);

				// Use search endpoint to list videos by channel
				String url = BASE_URL.replace("/youtube/v3", "/youtube/v3")
						+ "/../search?part=snippet&channelId=" + encode(channelId)
						+ "&type=video&maxResults=" + limit + "&order=date";
				// Correct URL construction
				url = "https://www.googleapis.com/youtube/v3/search?part=snippet&channelId=" + encode(channelId)
						+ "&type=video&maxResults=" + limit + "&order=date";

				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object items = result.get("items");
				if (items instanceof List) {
					List<Map<String, Object>> videoItems = new ArrayList<>();
					for (Object item : (List<?>) items) {
						if (item instanceof Map) {
							videoItems.add(wrapInJson(item));
						}
					}
					return videoItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(videoItems);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "rate": {
				String videoId = context.getParameter("videoId", "");
				String rating = context.getParameter("rating", "like");
				String url = BASE_URL + "/videos/rate?id=" + encode(videoId) + "&rating=" + rating;
				HttpResponse<String> response = post(url, Map.of(), headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "videoId", videoId, "rating", rating))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String videoId = context.getParameter("videoId", "");
				Map<String, Object> updateFields = context.getParameter("videoUpdateFields", Map.of());

				// First, get the existing video to preserve required fields
				String getUrl = BASE_URL + "/videos?part=snippet,status&id=" + encode(videoId);
				HttpResponse<String> getResponse = get(getUrl, headers);
				Map<String, Object> getResult = parseResponse(getResponse);

				Object existingItems = getResult.get("items");
				if (!(existingItems instanceof List) || ((List<?>) existingItems).isEmpty()) {
					return NodeExecutionResult.error("Video not found: " + videoId);
				}
				Map<String, Object> existingVideo = (Map<String, Object>) ((List<?>) existingItems).get(0);
				Map<String, Object> existingSnippet = existingVideo.get("snippet") instanceof Map
						? new LinkedHashMap<>((Map<String, Object>) existingVideo.get("snippet"))
						: new LinkedHashMap<>();

				if (updateFields.get("title") != null) {
					existingSnippet.put("title", updateFields.get("title"));
				}
				if (updateFields.get("description") != null) {
					existingSnippet.put("description", updateFields.get("description"));
				}
				if (updateFields.get("tags") != null) {
					String tagsStr = (String) updateFields.get("tags");
					existingSnippet.put("tags", Arrays.asList(tagsStr.split(",")));
				}
				if (updateFields.get("categoryId") != null) {
					existingSnippet.put("categoryId", updateFields.get("categoryId"));
				}

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", videoId);
				body.put("snippet", existingSnippet);

				Map<String, Object> status = new LinkedHashMap<>();
				if (updateFields.get("privacyStatus") != null) {
					status.put("privacyStatus", updateFields.get("privacyStatus"));
				}
				if (updateFields.get("embeddable") != null) {
					status.put("embeddable", toBoolean(updateFields.get("embeddable"), true));
				}
				if (updateFields.get("publicStatsViewable") != null) {
					status.put("publicStatsViewable", toBoolean(updateFields.get("publicStatsViewable"), true));
				}
				if (!status.isEmpty()) {
					body.put("status", status);
				}

				HttpResponse<String> response = put(BASE_URL + "/videos?part=snippet,status", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown video operation: " + operation);
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
