package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Spotify Node -- interact with albums, artists, player, playlists, and tracks
 * via the Spotify Web API.
 */
@Slf4j
@Node(
	type = "spotify",
	displayName = "Spotify",
	description = "Interact with albums, artists, player controls, playlists, and tracks in Spotify",
	category = "Miscellaneous",
	icon = "spotify",
	credentials = {"spotifyOAuth2Api"},
	searchOnly = true
)
public class SpotifyNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.spotify.com/v1";

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

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("track")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Album").value("album").description("Interact with albums").build(),
				ParameterOption.builder().name("Artist").value("artist").description("Interact with artists").build(),
				ParameterOption.builder().name("Player").value("player").description("Control playback").build(),
				ParameterOption.builder().name("Playlist").value("playlist").description("Manage playlists").build(),
				ParameterOption.builder().name("Track").value("track").description("Interact with tracks").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addAlbumParameters(params);
		addArtistParameters(params);
		addPlayerParameters(params);
		addPlaylistParameters(params);
		addTrackParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Album operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("album"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get an album").build(),
				ParameterOption.builder().name("Get Tracks").value("getTracks").description("Get an album's tracks").build()
			)).build());

		// Artist operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("artist"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get an artist").build(),
				ParameterOption.builder().name("Get Albums").value("getAlbums").description("Get an artist's albums").build(),
				ParameterOption.builder().name("Get Top Tracks").value("getTopTracks").description("Get an artist's top tracks").build(),
				ParameterOption.builder().name("Get Related Artists").value("getRelated").description("Get related artists").build()
			)).build());

		// Player operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getCurrentlyPlaying")
			.displayOptions(Map.of("show", Map.of("resource", List.of("player"))))
			.options(List.of(
				ParameterOption.builder().name("Get Currently Playing").value("getCurrentlyPlaying").description("Get the currently playing track").build(),
				ParameterOption.builder().name("Pause").value("pause").description("Pause playback").build(),
				ParameterOption.builder().name("Play").value("play").description("Start or resume playback").build(),
				ParameterOption.builder().name("Skip to Next").value("skipToNext").description("Skip to next track").build(),
				ParameterOption.builder().name("Skip to Previous").value("skipToPrevious").description("Skip to previous track").build(),
				ParameterOption.builder().name("Set Volume").value("setVolume").description("Set the playback volume").build()
			)).build());

		// Playlist operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a playlist").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get the current user's playlists").build(),
				ParameterOption.builder().name("Get Tracks").value("getTracks").description("Get a playlist's tracks").build(),
				ParameterOption.builder().name("Add Tracks").value("add").description("Add tracks to a playlist").build(),
				ParameterOption.builder().name("Create").value("create").description("Create a new playlist").build()
			)).build());

		// Track operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("track"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a track").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for tracks").build()
			)).build());
	}

	// ========================= Album Parameters =========================

	private void addAlbumParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("albumId").displayName("Album ID").type(ParameterType.STRING).required(true)
			.description("The Spotify ID of the album.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("album"))))
			.build());

		params.add(NodeParameter.builder()
			.name("albumLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(20)
			.displayOptions(Map.of("show", Map.of("resource", List.of("album"), "operation", List.of("getTracks"))))
			.build());
	}

	// ========================= Artist Parameters =========================

	private void addArtistParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("artistId").displayName("Artist ID").type(ParameterType.STRING).required(true)
			.description("The Spotify ID of the artist.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("artist"))))
			.build());

		params.add(NodeParameter.builder()
			.name("artistMarket").displayName("Market").type(ParameterType.STRING).defaultValue("US")
			.description("An ISO 3166-1 alpha-2 country code for the market.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("artist"), "operation", List.of("getTopTracks"))))
			.build());

		params.add(NodeParameter.builder()
			.name("artistAlbumLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(20)
			.displayOptions(Map.of("show", Map.of("resource", List.of("artist"), "operation", List.of("getAlbums"))))
			.build());
	}

	// ========================= Player Parameters =========================

	private void addPlayerParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("playerVolume").displayName("Volume (0-100)").type(ParameterType.NUMBER).required(true)
			.defaultValue(50)
			.displayOptions(Map.of("show", Map.of("resource", List.of("player"), "operation", List.of("setVolume"))))
			.build());

		params.add(NodeParameter.builder()
			.name("playerContextUri").displayName("Context URI").type(ParameterType.STRING)
			.description("Spotify URI of the context to play (album, artist, playlist). Example: spotify:album:1DFixLWuPkv3KT3TnV35m3")
			.displayOptions(Map.of("show", Map.of("resource", List.of("player"), "operation", List.of("play"))))
			.build());

		params.add(NodeParameter.builder()
			.name("playerTrackUris").displayName("Track URIs").type(ParameterType.STRING)
			.description("Comma-separated Spotify track URIs to play. Example: spotify:track:4iV5W9uYEdYUVa79Axb7Rh")
			.displayOptions(Map.of("show", Map.of("resource", List.of("player"), "operation", List.of("play"))))
			.build());

		params.add(NodeParameter.builder()
			.name("playerDeviceId").displayName("Device ID").type(ParameterType.STRING)
			.description("The ID of the device to target. If not supplied, the currently active device is used.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("player"), "operation", List.of("play", "pause", "skipToNext", "skipToPrevious", "setVolume"))))
			.build());
	}

	// ========================= Playlist Parameters =========================

	private void addPlaylistParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("playlistId").displayName("Playlist ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("get", "getTracks", "add"))))
			.build());

		params.add(NodeParameter.builder()
			.name("playlistTrackUris").displayName("Track URIs").type(ParameterType.STRING).required(true)
			.description("Comma-separated Spotify track URIs to add. Example: spotify:track:4iV5W9uYEdYUVa79Axb7Rh")
			.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("add"))))
			.build());

		params.add(NodeParameter.builder()
			.name("playlistName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("playlistAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("public").displayName("Public").type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder().name("collaborative").displayName("Collaborative").type(ParameterType.BOOLEAN).defaultValue(false).build()
			)).build());

		params.add(NodeParameter.builder()
			.name("playlistLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(20)
			.displayOptions(Map.of("show", Map.of("resource", List.of("playlist"), "operation", List.of("getAll", "getTracks"))))
			.build());
	}

	// ========================= Track Parameters =========================

	private void addTrackParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("trackId").displayName("Track ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("track"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("trackSearchQuery").displayName("Search Query").type(ParameterType.STRING).required(true)
			.placeHolder("Bohemian Rhapsody")
			.displayOptions(Map.of("show", Map.of("resource", List.of("track"), "operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("trackSearchLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(20)
			.displayOptions(Map.of("show", Map.of("resource", List.of("track"), "operation", List.of("search"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "track");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "album" -> executeAlbum(context, headers);
				case "artist" -> executeArtist(context, headers);
				case "player" -> executePlayer(context, headers);
				case "playlist" -> executePlaylist(context, headers);
				case "track" -> executeTrack(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Spotify API error: " + e.getMessage(), e);
		}
	}

	// ========================= Album Execute =========================

	private NodeExecutionResult executeAlbum(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "get");
		String albumId = context.getParameter("albumId", "");

		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/albums/" + encode(albumId), headers);
				return toResult(response);
			}
			case "getTracks": {
				int limit = toInt(context.getParameter("albumLimit", 20), 20);
				String url = buildUrl(BASE_URL + "/albums/" + encode(albumId) + "/tracks", Map.of("limit", limit));
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "items");
			}
			default:
				return NodeExecutionResult.error("Unknown album operation: " + operation);
		}
	}

	// ========================= Artist Execute =========================

	private NodeExecutionResult executeArtist(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "get");
		String artistId = context.getParameter("artistId", "");

		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/artists/" + encode(artistId), headers);
				return toResult(response);
			}
			case "getAlbums": {
				int limit = toInt(context.getParameter("artistAlbumLimit", 20), 20);
				String url = buildUrl(BASE_URL + "/artists/" + encode(artistId) + "/albums", Map.of("limit", limit));
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "items");
			}
			case "getTopTracks": {
				String market = context.getParameter("artistMarket", "US");
				String url = buildUrl(BASE_URL + "/artists/" + encode(artistId) + "/top-tracks", Map.of("market", market));
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "tracks");
			}
			case "getRelated": {
				HttpResponse<String> response = get(BASE_URL + "/artists/" + encode(artistId) + "/related-artists", headers);
				return toListResult(response, "artists");
			}
			default:
				return NodeExecutionResult.error("Unknown artist operation: " + operation);
		}
	}

	// ========================= Player Execute =========================

	private NodeExecutionResult executePlayer(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getCurrentlyPlaying");
		String deviceId = context.getParameter("playerDeviceId", "");

		switch (operation) {
			case "getCurrentlyPlaying": {
				HttpResponse<String> response = get(BASE_URL + "/me/player/currently-playing", headers);
				if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("isPlaying", false))));
				}
				return toResult(response);
			}
			case "pause": {
				String url = BASE_URL + "/me/player/pause";
				if (!deviceId.isEmpty()) {
					url = buildUrl(url, Map.of("device_id", deviceId));
				}
				HttpResponse<String> response = put(url, Map.of(), headers);
				return toCommandResult(response);
			}
			case "play": {
				String contextUri = context.getParameter("playerContextUri", "");
				String trackUris = context.getParameter("playerTrackUris", "");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!contextUri.isEmpty()) {
					body.put("context_uri", contextUri);
				}
				if (!trackUris.isEmpty()) {
					body.put("uris", Arrays.asList(trackUris.split("\\s*,\\s*")));
				}

				String url = BASE_URL + "/me/player/play";
				if (!deviceId.isEmpty()) {
					url = buildUrl(url, Map.of("device_id", deviceId));
				}
				HttpResponse<String> response = put(url, body, headers);
				return toCommandResult(response);
			}
			case "skipToNext": {
				String url = BASE_URL + "/me/player/next";
				if (!deviceId.isEmpty()) {
					url = buildUrl(url, Map.of("device_id", deviceId));
				}
				HttpResponse<String> response = post(url, Map.of(), headers);
				return toCommandResult(response);
			}
			case "skipToPrevious": {
				String url = BASE_URL + "/me/player/previous";
				if (!deviceId.isEmpty()) {
					url = buildUrl(url, Map.of("device_id", deviceId));
				}
				HttpResponse<String> response = post(url, Map.of(), headers);
				return toCommandResult(response);
			}
			case "setVolume": {
				int volume = toInt(context.getParameter("playerVolume", 50), 50);
				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("volume_percent", volume);
				if (!deviceId.isEmpty()) {
					queryParams.put("device_id", deviceId);
				}
				String url = buildUrl(BASE_URL + "/me/player/volume", queryParams);
				HttpResponse<String> response = put(url, Map.of(), headers);
				return toCommandResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown player operation: " + operation);
		}
	}

	// ========================= Playlist Execute =========================

	private NodeExecutionResult executePlaylist(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "get": {
				String playlistId = context.getParameter("playlistId", "");
				HttpResponse<String> response = get(BASE_URL + "/playlists/" + encode(playlistId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("playlistLimit", 20), 20);
				String url = buildUrl(BASE_URL + "/me/playlists", Map.of("limit", limit));
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "items");
			}
			case "getTracks": {
				String playlistId = context.getParameter("playlistId", "");
				int limit = toInt(context.getParameter("playlistLimit", 20), 20);
				String url = buildUrl(BASE_URL + "/playlists/" + encode(playlistId) + "/tracks", Map.of("limit", limit));
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "items");
			}
			case "add": {
				String playlistId = context.getParameter("playlistId", "");
				String trackUris = context.getParameter("playlistTrackUris", "");
				Map<String, Object> body = Map.of("uris", Arrays.asList(trackUris.split("\\s*,\\s*")));
				HttpResponse<String> response = post(BASE_URL + "/playlists/" + encode(playlistId) + "/tracks", body, headers);
				return toResult(response);
			}
			case "create": {
				String name = context.getParameter("playlistName", "");
				Map<String, Object> additional = context.getParameter("playlistAdditionalFields", Map.of());

				// Get current user ID first
				HttpResponse<String> meResponse = get(BASE_URL + "/me", headers);
				Map<String, Object> me = parseResponse(meResponse);
				String userId = String.valueOf(me.get("id"));

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				putIfPresent(body, "description", additional.get("description"));
				if (additional.get("public") != null) {
					body.put("public", toBoolean(additional.get("public"), true));
				}
				if (additional.get("collaborative") != null) {
					body.put("collaborative", toBoolean(additional.get("collaborative"), false));
				}

				HttpResponse<String> response = post(BASE_URL + "/users/" + encode(userId) + "/playlists", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown playlist operation: " + operation);
		}
	}

	// ========================= Track Execute =========================

	private NodeExecutionResult executeTrack(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "get");

		switch (operation) {
			case "get": {
				String trackId = context.getParameter("trackId", "");
				HttpResponse<String> response = get(BASE_URL + "/tracks/" + encode(trackId), headers);
				return toResult(response);
			}
			case "search": {
				String query = context.getParameter("trackSearchQuery", "");
				int limit = toInt(context.getParameter("trackSearchLimit", 20), 20);
				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("q", query);
				queryParams.put("type", "track");
				queryParams.put("limit", limit);
				String url = buildUrl(BASE_URL + "/search", queryParams);
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "tracks.items");
			}
			default:
				return NodeExecutionResult.error("Unknown track operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataPath) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);

		// Navigate dot-separated path
		Object data = parsed;
		for (String key : dataPath.split("\\.")) {
			if (data instanceof Map) {
				data = ((Map<String, Object>) data).get(key);
			} else {
				break;
			}
		}

		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toCommandResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Spotify API error (HTTP " + response.statusCode() + "): " + body);
	}
}
