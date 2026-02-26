package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Reddit — interact with Reddit posts, comments, subreddits, and user data.
 */
@Node(
		type = "reddit",
		displayName = "Reddit",
		description = "Interact with Reddit posts, comments, and subreddits",
		category = "Social Media",
		icon = "reddit",
		credentials = {"redditOAuth2Api"}
)
public class RedditNode extends AbstractApiNode {

	private static final String AUTH_URL = "https://oauth.reddit.com";
	private static final String PUBLIC_URL = "https://www.reddit.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "subreddit");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> authHeaders = new HashMap<>();
		authHeaders.put("Authorization", "Bearer " + accessToken);
		authHeaders.put("User-Agent", "n8n");
		authHeaders.put("Accept", "application/json");

		Map<String, String> publicHeaders = new HashMap<>();
		publicHeaders.put("User-Agent", "n8n");
		publicHeaders.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "post" -> handlePost(context, authHeaders, operation);
					case "postComment" -> handlePostComment(context, authHeaders, operation);
					case "profile" -> handleProfile(context, authHeaders, operation);
					case "subreddit" -> handleSubreddit(context, publicHeaders, operation);
					case "user" -> handleUser(context, publicHeaders, operation);
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

	private Map<String, Object> handlePost(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String subreddit = context.getParameter("subreddit", "");
				String title = context.getParameter("title", "");
				String kind = context.getParameter("kind", "self");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("sr", subreddit);
				body.put("title", title);
				body.put("kind", kind);
				body.put("api_type", "json");
				if ("self".equals(kind)) {
					body.put("text", context.getParameter("text", ""));
				} else {
					body.put("url", context.getParameter("url", ""));
				}
				body.put("resubmit", toBoolean(context.getParameters().get("resubmit"), true));
				HttpResponse<String> response = post(AUTH_URL + "/api/submit", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String postId = context.getParameter("postId", "");
				Map<String, Object> body = Map.of("id", "t3_" + postId, "api_type", "json");
				HttpResponse<String> response = post(AUTH_URL + "/api/del", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String subreddit = context.getParameter("subreddit", "");
				String postId = context.getParameter("postId", "");
				HttpResponse<String> response = get(AUTH_URL + "/r/" + encode(subreddit) + "/comments/" + encode(postId) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String subreddit = context.getParameter("subreddit", "");
				String category = context.getParameter("category", "hot");
				int limit = toInt(context.getParameters().get("limit"), 25);
				HttpResponse<String> response = get(AUTH_URL + "/r/" + encode(subreddit) + "/" + encode(category) + ".json?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "search" -> {
				String keyword = context.getParameter("keyword", "");
				int limit = toInt(context.getParameters().get("limit"), 25);
				String subreddit = context.getParameter("subreddit", "");
				String url;
				if (!subreddit.isEmpty()) {
					url = AUTH_URL + "/r/" + encode(subreddit) + "/search.json?q=" + encode(keyword) + "&limit=" + limit + "&restrict_sr=true";
				} else {
					url = AUTH_URL + "/search.json?q=" + encode(keyword) + "&limit=" + limit;
				}
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown post operation: " + operation);
		};
	}

	private Map<String, Object> handlePostComment(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String postId = context.getParameter("postId", "");
				String text = context.getParameter("commentText", "");
				Map<String, Object> body = Map.of("thing_id", "t3_" + postId, "text", text, "api_type", "json");
				HttpResponse<String> response = post(AUTH_URL + "/api/comment", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String commentId = context.getParameter("commentId", "");
				Map<String, Object> body = Map.of("id", "t1_" + commentId, "api_type", "json");
				HttpResponse<String> response = post(AUTH_URL + "/api/del", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String subreddit = context.getParameter("subreddit", "");
				String postId = context.getParameter("postId", "");
				int limit = toInt(context.getParameters().get("limit"), 25);
				HttpResponse<String> response = get(AUTH_URL + "/r/" + encode(subreddit) + "/comments/" + encode(postId) + ".json?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "reply" -> {
				String commentId = context.getParameter("commentId", "");
				String text = context.getParameter("replyText", "");
				Map<String, Object> body = Map.of("thing_id", "t1_" + commentId, "text", text, "api_type", "json");
				HttpResponse<String> response = post(AUTH_URL + "/api/comment", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown post comment operation: " + operation);
		};
	}

	private Map<String, Object> handleProfile(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String details = context.getParameter("details", "identity");
				String url = switch (details) {
					case "identity" -> AUTH_URL + "/api/v1/me";
					case "blockedUsers" -> AUTH_URL + "/api/v1/me/blocked";
					case "friends" -> AUTH_URL + "/api/v1/me/friends";
					case "karma" -> AUTH_URL + "/api/v1/me/karma";
					case "prefs" -> AUTH_URL + "/api/v1/me/prefs";
					case "trophies" -> AUTH_URL + "/api/v1/me/trophies";
					default -> AUTH_URL + "/api/v1/me";
				};
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown profile operation: " + operation);
		};
	}

	private Map<String, Object> handleSubreddit(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String subreddit = context.getParameter("subreddit", "");
				String content = context.getParameter("content", "about");
				HttpResponse<String> response = get(PUBLIC_URL + "/r/" + encode(subreddit) + "/" + encode(content) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String keyword = context.getParameter("keyword", "");
				int limit = toInt(context.getParameters().get("limit"), 25);
				HttpResponse<String> response;
				if (keyword.isEmpty()) {
					response = get(PUBLIC_URL + "/api/trending_subreddits.json", headers);
				} else {
					response = get(PUBLIC_URL + "/subreddits/search.json?q=" + encode(keyword) + "&limit=" + limit, headers);
				}
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown subreddit operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String username = context.getParameter("username", "");
				String details = context.getParameter("userDetails", "about");
				HttpResponse<String> response = get(PUBLIC_URL + "/user/" + encode(username) + "/" + encode(details) + ".json", headers);
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
						.type(ParameterType.OPTIONS).defaultValue("subreddit")
						.options(List.of(
								ParameterOption.builder().name("Post").value("post").build(),
								ParameterOption.builder().name("Post Comment").value("postComment").build(),
								ParameterOption.builder().name("Profile").value("profile").build(),
								ParameterOption.builder().name("Subreddit").value("subreddit").build(),
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
								ParameterOption.builder().name("Reply").value("reply").build(),
								ParameterOption.builder().name("Search").value("search").build()
						)).build(),
				NodeParameter.builder()
						.name("subreddit").displayName("Subreddit")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("postId").displayName("Post ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("commentId").displayName("Comment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("commentText").displayName("Comment Text")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("replyText").displayName("Reply Text")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("keyword").displayName("Keyword")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("kind").displayName("Kind")
						.type(ParameterType.OPTIONS).defaultValue("self")
						.options(List.of(
								ParameterOption.builder().name("Self (Text)").value("self").build(),
								ParameterOption.builder().name("Link").value("link").build()
						)).build(),
				NodeParameter.builder()
						.name("category").displayName("Category")
						.type(ParameterType.OPTIONS).defaultValue("hot")
						.options(List.of(
								ParameterOption.builder().name("Best").value("best").build(),
								ParameterOption.builder().name("Controversial").value("controversial").build(),
								ParameterOption.builder().name("Hot").value("hot").build(),
								ParameterOption.builder().name("New").value("new").build(),
								ParameterOption.builder().name("Rising").value("rising").build(),
								ParameterOption.builder().name("Top").value("top").build()
						)).build(),
				NodeParameter.builder()
						.name("details").displayName("Details")
						.type(ParameterType.OPTIONS).defaultValue("identity")
						.options(List.of(
								ParameterOption.builder().name("Blocked Users").value("blockedUsers").build(),
								ParameterOption.builder().name("Friends").value("friends").build(),
								ParameterOption.builder().name("Identity").value("identity").build(),
								ParameterOption.builder().name("Karma").value("karma").build(),
								ParameterOption.builder().name("Preferences").value("prefs").build(),
								ParameterOption.builder().name("Trophies").value("trophies").build()
						)).build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.OPTIONS).defaultValue("about")
						.options(List.of(
								ParameterOption.builder().name("About").value("about").build(),
								ParameterOption.builder().name("Rules").value("rules").build()
						)).build(),
				NodeParameter.builder()
						.name("userDetails").displayName("User Details")
						.type(ParameterType.OPTIONS).defaultValue("about")
						.options(List.of(
								ParameterOption.builder().name("About").value("about").build(),
								ParameterOption.builder().name("Comments").value("comments").build(),
								ParameterOption.builder().name("Gilded").value("gilded").build(),
								ParameterOption.builder().name("Overview").value("overview").build(),
								ParameterOption.builder().name("Submitted").value("submitted").build()
						)).build(),
				NodeParameter.builder()
						.name("resubmit").displayName("Resubmit")
						.type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(25)
						.description("Max results to return.").build()
		);
	}
}
