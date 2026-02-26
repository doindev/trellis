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

@Slf4j
@Node(
	type = "twitter",
	displayName = "X (formerly Twitter)",
	description = "Post and interact on X (formerly Twitter).",
	category = "Social Media",
	icon = "twitter",
	credentials = {"twitterOAuth2Api"}
)
public class TwitterNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.twitter.com/2";

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

		// Resource
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("tweet")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Tweet").value("tweet").description("Create, delete, search, like, or retweet tweets").build(),
				ParameterOption.builder().name("User").value("user").description("Get user information").build()
			)).build());

		// Tweet operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tweet"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a new tweet").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a tweet").build(),
				ParameterOption.builder().name("Search").value("search").description("Search recent tweets").build(),
				ParameterOption.builder().name("Like").value("like").description("Like a tweet").build(),
				ParameterOption.builder().name("Retweet").value("retweet").description("Retweet a tweet").build()
			)).build());

		// User operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a user by ID or username").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get multiple users").build()
			)).build());

		// Tweet > Create: text
		params.add(NodeParameter.builder()
			.name("text").displayName("Text")
			.type(ParameterType.STRING).required(true)
			.typeOptions(Map.of("rows", 5))
			.placeHolder("What's happening?")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tweet"), "operation", List.of("create"))))
			.build());

		// Tweet > Create: additional fields
		params.add(NodeParameter.builder()
			.name("tweetAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tweet"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("replyToTweetId").displayName("Reply to Tweet ID")
					.type(ParameterType.STRING).description("Tweet ID to reply to.").build(),
				NodeParameter.builder().name("quoteTweetId").displayName("Quote Tweet ID")
					.type(ParameterType.STRING).description("Tweet ID to quote.").build(),
				NodeParameter.builder().name("pollOptions").displayName("Poll Options")
					.type(ParameterType.STRING).description("Comma-separated poll options (2-4 options).").build(),
				NodeParameter.builder().name("pollDuration").displayName("Poll Duration (minutes)")
					.type(ParameterType.NUMBER).defaultValue(1440)
					.description("Duration of the poll in minutes (5-10080).").build()
			)).build());

		// Tweet > Delete: tweetId
		params.add(NodeParameter.builder()
			.name("tweetId").displayName("Tweet ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tweet"), "operation", List.of("delete", "like", "retweet"))))
			.build());

		// Tweet > Like: userId (for the authenticated user)
		params.add(NodeParameter.builder()
			.name("userId").displayName("User ID")
			.type(ParameterType.STRING).required(true)
			.description("The authenticated user's ID.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tweet"), "operation", List.of("like", "retweet"))))
			.build());

		// Tweet > Search: query
		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Query")
			.type(ParameterType.STRING).required(true)
			.placeHolder("from:twitterdev")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tweet"), "operation", List.of("search"))))
			.build());

		// Tweet > Search: additional
		params.add(NodeParameter.builder()
			.name("searchAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tweet"), "operation", List.of("search"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("maxResults").displayName("Max Results")
					.type(ParameterType.NUMBER).defaultValue(10)
					.description("Maximum number of results (10-100).").build(),
				NodeParameter.builder().name("tweetFields").displayName("Tweet Fields")
					.type(ParameterType.STRING)
					.description("Comma-separated tweet fields to return.")
					.placeHolder("created_at,public_metrics,author_id").build(),
				NodeParameter.builder().name("expansions").displayName("Expansions")
					.type(ParameterType.STRING)
					.description("Comma-separated expansions.")
					.placeHolder("author_id,attachments.media_keys").build()
			)).build());

		// User > Get: userIdOrUsername
		params.add(NodeParameter.builder()
			.name("userIdOrUsername").displayName("User ID or Username")
			.type(ParameterType.STRING).required(true)
			.description("A user ID or @username (without the @).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get"))))
			.build());

		// User > Get: lookupBy
		params.add(NodeParameter.builder()
			.name("lookupBy").displayName("Lookup By")
			.type(ParameterType.OPTIONS).defaultValue("id")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("get"))))
			.options(List.of(
				ParameterOption.builder().name("ID").value("id").build(),
				ParameterOption.builder().name("Username").value("username").build()
			)).build());

		// User > Get: user fields
		params.add(NodeParameter.builder()
			.name("userFields").displayName("User Fields")
			.type(ParameterType.STRING)
			.description("Comma-separated user fields to return.")
			.placeHolder("created_at,description,public_metrics")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"))))
			.build());

		// User > GetAll: userIds
		params.add(NodeParameter.builder()
			.name("userIds").displayName("User IDs")
			.type(ParameterType.STRING).required(true)
			.description("Comma-separated list of user IDs.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("user"), "operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		String resource = context.getParameter("resource", "tweet");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		try {
			return switch (resource) {
				case "tweet" -> executeTweet(context, operation, headers);
				case "user" -> executeUser(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Twitter API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeTweet(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String text = context.getParameter("text", "");
				Map<String, Object> additional = context.getParameter("tweetAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("text", text);

				// Reply settings
				String replyTo = additional.get("replyToTweetId") != null ? String.valueOf(additional.get("replyToTweetId")) : "";
				if (!replyTo.isEmpty()) {
					body.put("reply", Map.of("in_reply_to_tweet_id", replyTo));
				}

				// Quote tweet
				String quoteTweetId = additional.get("quoteTweetId") != null ? String.valueOf(additional.get("quoteTweetId")) : "";
				if (!quoteTweetId.isEmpty()) {
					body.put("quote_tweet_id", quoteTweetId);
				}

				// Poll
				String pollOptions = additional.get("pollOptions") != null ? String.valueOf(additional.get("pollOptions")) : "";
				if (!pollOptions.isEmpty()) {
					List<String> options = Arrays.stream(pollOptions.split(","))
						.map(String::trim).filter(s -> !s.isEmpty()).toList();
					int duration = toInt(additional.get("pollDuration"), 1440);
					body.put("poll", Map.of("options", options, "duration_minutes", duration));
				}

				HttpResponse<String> response = post(BASE_URL + "/tweets", body, headers);
				return toResult("Twitter", response);
			}
			case "delete": {
				String tweetId = context.getParameter("tweetId", "");
				HttpResponse<String> response = delete(BASE_URL + "/tweets/" + encode(tweetId), headers);
				return toDeleteResult("Twitter", response);
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				Map<String, Object> additional = context.getParameter("searchAdditionalFields", Map.of());

				StringBuilder url = new StringBuilder(BASE_URL + "/tweets/search/recent?query=" + encode(query));

				int maxResults = toInt(additional.get("maxResults"), 10);
				url.append("&max_results=").append(maxResults);

				String tweetFields = additional.get("tweetFields") != null ? String.valueOf(additional.get("tweetFields")) : "";
				if (!tweetFields.isEmpty()) {
					url.append("&tweet.fields=").append(encode(tweetFields));
				}

				String expansions = additional.get("expansions") != null ? String.valueOf(additional.get("expansions")) : "";
				if (!expansions.isEmpty()) {
					url.append("&expansions=").append(encode(expansions));
				}

				HttpResponse<String> response = get(url.toString(), headers);
				if (response.statusCode() >= 400) {
					return apiError("Twitter", response);
				}

				Map<String, Object> parsed = parseResponse(response);
				Object data = parsed.get("data");
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
			case "like": {
				String tweetId = context.getParameter("tweetId", "");
				String userId = context.getParameter("userId", "");
				Map<String, Object> body = Map.of("tweet_id", tweetId);
				HttpResponse<String> response = post(BASE_URL + "/users/" + encode(userId) + "/likes", body, headers);
				return toResult("Twitter", response);
			}
			case "retweet": {
				String tweetId = context.getParameter("tweetId", "");
				String userId = context.getParameter("userId", "");
				Map<String, Object> body = Map.of("tweet_id", tweetId);
				HttpResponse<String> response = post(BASE_URL + "/users/" + encode(userId) + "/retweets", body, headers);
				return toResult("Twitter", response);
			}
			default:
				return NodeExecutionResult.error("Unknown tweet operation: " + operation);
		}
	}

	private NodeExecutionResult executeUser(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		String userFields = context.getParameter("userFields", "");

		switch (operation) {
			case "get": {
				String userIdOrUsername = context.getParameter("userIdOrUsername", "");
				String lookupBy = context.getParameter("lookupBy", "id");

				String url;
				if ("username".equals(lookupBy)) {
					url = BASE_URL + "/users/by/username/" + encode(userIdOrUsername);
				} else {
					url = BASE_URL + "/users/" + encode(userIdOrUsername);
				}

				if (!userFields.isEmpty()) {
					url += "?user.fields=" + encode(userFields);
				}

				HttpResponse<String> response = get(url, headers);
				return toResult("Twitter", response);
			}
			case "getAll": {
				String userIds = context.getParameter("userIds", "");
				String url = BASE_URL + "/users?ids=" + encode(userIds);
				if (!userFields.isEmpty()) {
					url += "&user.fields=" + encode(userFields);
				}

				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError("Twitter", response);
				}

				Map<String, Object> parsed = parseResponse(response);
				Object data = parsed.get("data");
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
			default:
				return NodeExecutionResult.error("Unknown user operation: " + operation);
		}
	}

	private NodeExecutionResult toResult(String apiName, HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(apiName, response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(String apiName, HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(apiName, response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(apiName, response);
	}

	private NodeExecutionResult apiError(String apiName, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error(apiName + " API error (HTTP " + response.statusCode() + "): " + body);
	}
}
