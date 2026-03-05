package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Business Profile — manage business posts and reviews via the Google My Business API.
 */
@Node(
		type = "googleBusinessProfile",
		displayName = "Google Business Profile",
		description = "Manage Google Business Profile posts and reviews",
		category = "Google",
		icon = "googleBusinessProfile",
		credentials = {"googleBusinessProfileOAuth2Api"}
)
public class GoogleBusinessProfileNode extends AbstractApiNode {

	private static final String BASE_URL = "https://mybusiness.googleapis.com/v4";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("post")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Post").value("post")
								.description("Manage business posts").build(),
						ParameterOption.builder().name("Review").value("review")
								.description("Manage business reviews").build()
				)).build());

		// Post operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("post"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a post").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a post").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a post").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all posts").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a post").build()
				)).build());

		// Review operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("review"))))
				.options(List.of(
						ParameterOption.builder().name("Get").value("get").description("Get a review").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all reviews").build(),
						ParameterOption.builder().name("Reply").value("reply").description("Reply to a review").build()
				)).build());

		// Account name (common)
		params.add(NodeParameter.builder()
				.name("account").displayName("Account")
				.type(ParameterType.STRING).required(true)
				.description("The account name (e.g., accounts/123456789).")
				.placeHolder("accounts/123456789")
				.build());

		// Location name (common)
		params.add(NodeParameter.builder()
				.name("location").displayName("Location")
				.type(ParameterType.STRING).required(true)
				.description("The location name (e.g., locations/123456789).")
				.placeHolder("locations/123456789")
				.build());

		// Post > Create parameters
		params.add(NodeParameter.builder()
				.name("postSummary").displayName("Summary")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 4))
				.description("The text content of the post.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("create", "update"))))
				.build());

		params.add(NodeParameter.builder()
				.name("postTopicType").displayName("Topic Type")
				.type(ParameterType.OPTIONS).defaultValue("STANDARD")
				.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Standard").value("STANDARD").build(),
						ParameterOption.builder().name("Event").value("EVENT").build(),
						ParameterOption.builder().name("Offer").value("OFFER").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("postAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("actionType").displayName("Action Type")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Book").value("BOOK").build(),
										ParameterOption.builder().name("Order").value("ORDER").build(),
										ParameterOption.builder().name("Shop").value("SHOP").build(),
										ParameterOption.builder().name("Learn More").value("LEARN_MORE").build(),
										ParameterOption.builder().name("Sign Up").value("SIGN_UP").build(),
										ParameterOption.builder().name("Call").value("CALL").build()
								)).build(),
						NodeParameter.builder().name("actionUrl").displayName("Action URL")
								.type(ParameterType.STRING)
								.description("The URL for the call-to-action button.")
								.build(),
						NodeParameter.builder().name("mediaUrl").displayName("Media URL")
								.type(ParameterType.STRING)
								.description("URL of a photo or video to attach.")
								.build(),
						NodeParameter.builder().name("eventTitle").displayName("Event Title")
								.type(ParameterType.STRING)
								.description("Title for event posts.")
								.build(),
						NodeParameter.builder().name("eventStartDate").displayName("Event Start Date")
								.type(ParameterType.STRING)
								.description("Start date in YYYY-MM-DD format.")
								.build(),
						NodeParameter.builder().name("eventEndDate").displayName("Event End Date")
								.type(ParameterType.STRING)
								.description("End date in YYYY-MM-DD format.")
								.build()
				)).build());

		// Post > Get / Delete / Update: postName
		params.add(NodeParameter.builder()
				.name("postName").displayName("Post Name")
				.type(ParameterType.STRING).required(true)
				.description("The post resource name (e.g., localPosts/123456789).")
				.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("get", "delete", "update"))))
				.build());

		// Review > Get: reviewName
		params.add(NodeParameter.builder()
				.name("reviewName").displayName("Review Name")
				.type(ParameterType.STRING).required(true)
				.description("The review resource name (e.g., reviews/123456789).")
				.displayOptions(Map.of("show", Map.of("resource", List.of("review"), "operation", List.of("get", "reply"))))
				.build());

		// Review > Reply: replyComment
		params.add(NodeParameter.builder()
				.name("replyComment").displayName("Reply Comment")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 4))
				.description("The reply text to the review.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("review"), "operation", List.of("reply"))))
				.build());

		// Limit for getAll
		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Maximum number of items to return.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "post");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "post" -> executePost(context, credentials);
				case "review" -> executeReview(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Business Profile error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executePost(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String account = context.getParameter("account", "");
		String location = context.getParameter("location", "");
		String locationPath = BASE_URL + "/" + account + "/" + location;

		switch (operation) {
			case "create": {
				String summary = context.getParameter("postSummary", "");
				String topicType = context.getParameter("postTopicType", "STANDARD");
				Map<String, Object> additionalFields = context.getParameter("postAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("summary", summary);
				body.put("topicType", topicType);
				body.put("languageCode", "en");

				if (additionalFields.get("actionType") != null && additionalFields.get("actionUrl") != null) {
					body.put("callToAction", Map.of(
							"actionType", additionalFields.get("actionType"),
							"url", additionalFields.get("actionUrl")
					));
				}

				if (additionalFields.get("mediaUrl") != null) {
					body.put("media", List.of(Map.of(
							"mediaFormat", "PHOTO",
							"sourceUrl", additionalFields.get("mediaUrl")
					)));
				}

				if ("EVENT".equals(topicType) && additionalFields.get("eventTitle") != null) {
					Map<String, Object> event = new LinkedHashMap<>();
					event.put("title", additionalFields.get("eventTitle"));
					if (additionalFields.get("eventStartDate") != null) {
						event.put("schedule", buildEventSchedule(
								(String) additionalFields.get("eventStartDate"),
								(String) additionalFields.get("eventEndDate")));
					}
					body.put("event", event);
				}

				HttpResponse<String> response = post(locationPath + "/localPosts", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String postName = context.getParameter("postName", "");
				HttpResponse<String> response = delete(locationPath + "/" + postName, headers);
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", postName))));
				}
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get": {
				String postName = context.getParameter("postName", "");
				HttpResponse<String> response = get(locationPath + "/" + postName, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 100), 100);
				String url = locationPath + "/localPosts?pageSize=" + limit;
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object posts = result.get("localPosts");
				if (posts instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object post : (List<?>) posts) {
						if (post instanceof Map) {
							items.add(wrapInJson(post));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update": {
				String postName = context.getParameter("postName", "");
				String summary = context.getParameter("postSummary", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("summary", summary);

				HttpResponse<String> response = patch(locationPath + "/" + postName, body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown post operation: " + operation);
		}
	}

	private NodeExecutionResult executeReview(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String account = context.getParameter("account", "");
		String location = context.getParameter("location", "");
		String locationPath = BASE_URL + "/" + account + "/" + location;

		switch (operation) {
			case "get": {
				String reviewName = context.getParameter("reviewName", "");
				HttpResponse<String> response = get(locationPath + "/" + reviewName, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 100), 100);
				String url = locationPath + "/reviews?pageSize=" + limit;
				HttpResponse<String> response = get(url, headers);
				Map<String, Object> result = parseResponse(response);

				Object reviews = result.get("reviews");
				if (reviews instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object review : (List<?>) reviews) {
						if (review instanceof Map) {
							items.add(wrapInJson(review));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "reply": {
				String reviewName = context.getParameter("reviewName", "");
				String replyComment = context.getParameter("replyComment", "");

				Map<String, Object> body = Map.of("comment", replyComment);

				HttpResponse<String> response = put(locationPath + "/" + reviewName + "/reply", body, headers);
				Map<String, Object> result = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default:
				return NodeExecutionResult.error("Unknown review operation: " + operation);
		}
	}

	private Map<String, Object> buildEventSchedule(String startDate, String endDate) {
		Map<String, Object> schedule = new LinkedHashMap<>();
		if (startDate != null && !startDate.isEmpty()) {
			schedule.put("startDate", parseDateString(startDate));
		}
		if (endDate != null && !endDate.isEmpty()) {
			schedule.put("endDate", parseDateString(endDate));
		}
		return schedule;
	}

	private Map<String, Object> parseDateString(String date) {
		String[] parts = date.split("-");
		if (parts.length == 3) {
			return Map.of("year", Integer.parseInt(parts[0]),
					"month", Integer.parseInt(parts[1]),
					"day", Integer.parseInt(parts[2]));
		}
		return Map.of();
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
