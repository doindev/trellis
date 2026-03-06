package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Mailchimp — manage audiences, campaigns, and members via the Mailchimp API.
 */
@Slf4j
@Node(
	type = "mailchimp",
	displayName = "Mailchimp",
	description = "Manage Mailchimp audiences and campaigns",
	category = "Marketing",
	icon = "mailchimp",
	credentials = {"mailchimpApi"},
	searchOnly = true
)
public class MailchimpNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("member")
			.options(List.of(
				ParameterOption.builder().name("Campaign").value("campaign").build(),
				ParameterOption.builder().name("List Group").value("listGroup").build(),
				ParameterOption.builder().name("Member").value("member").build(),
				ParameterOption.builder().name("Member Tag").value("memberTag").build()
			)).build());

		// Campaign operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Replicate").value("replicate").build(),
				ParameterOption.builder().name("Resend").value("resend").build(),
				ParameterOption.builder().name("Send").value("send").build()
			)).build());

		// List Group operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("listGroup"))))
			.options(List.of(
				ParameterOption.builder().name("Get All").value("getAll").build()
			)).build());

		// Member operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Member Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("memberTag"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build()
			)).build());

		// Campaign fields
		params.add(NodeParameter.builder()
			.name("campaignId").displayName("Campaign ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"), "operation", List.of("get", "delete", "replicate", "resend", "send"))))
			.build());

		// List Group fields
		params.add(NodeParameter.builder()
			.name("listGroupListId").displayName("List ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("listGroup"))))
			.build());

		params.add(NodeParameter.builder()
			.name("groupCategoryId").displayName("Group Category ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("listGroup"))))
			.build());

		// Member fields
		params.add(NodeParameter.builder()
			.name("listId").displayName("List ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create", "get", "update", "delete"))))
			.build());

		params.add(NodeParameter.builder()
			.name("status").displayName("Status")
			.type(ParameterType.OPTIONS).defaultValue("subscribed")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create", "update"))))
			.options(List.of(
				ParameterOption.builder().name("Subscribed").value("subscribed").build(),
				ParameterOption.builder().name("Unsubscribed").value("unsubscribed").build(),
				ParameterOption.builder().name("Cleaned").value("cleaned").build(),
				ParameterOption.builder().name("Pending").value("pending").build(),
				ParameterOption.builder().name("Transactional").value("transactional").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("mergeFields").displayName("Merge Fields (JSON)")
			.type(ParameterType.STRING).defaultValue("{}")
			.description("Merge fields as JSON object, e.g. {\"FNAME\":\"John\",\"LNAME\":\"Doe\"}")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create", "update"))))
			.build());

		// Member Tag fields
		params.add(NodeParameter.builder()
			.name("memberTagListId").displayName("List ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("memberTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("memberTagEmail").displayName("Email")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("memberTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagNames").displayName("Tag Names")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.description("Comma-separated tag names")
			.displayOptions(Map.of("show", Map.of("resource", List.of("memberTag"))))
			.build());

		// Pagination
		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(10)
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("offset").displayName("Offset")
			.type(ParameterType.NUMBER).defaultValue(0)
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey", "");
			String dc = extractDataCenter(apiKey);
			String baseUrl = "https://" + dc + ".api.mailchimp.com/3.0";

			Map<String, String> headers = buildAuthHeaders(apiKey);

			String resource = context.getParameter("resource", "member");
			String operation = context.getParameter("operation", "get");

			return switch (resource) {
				case "campaign" -> executeCampaign(context, baseUrl, headers, operation);
				case "listGroup" -> executeListGroup(context, baseUrl, headers, operation);
				case "member" -> executeMember(context, baseUrl, headers, operation);
				case "memberTag" -> executeMemberTag(context, baseUrl, headers, operation);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Mailchimp API error: " + e.getMessage(), e);
		}
	}

	// ========================= Campaign =========================

	private NodeExecutionResult executeCampaign(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "delete": {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = delete(baseUrl + "/campaigns/" + encode(campaignId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = get(baseUrl + "/campaigns/" + encode(campaignId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 10), 10);
				int offset = toInt(context.getParameter("offset", 0), 0);
				String url = baseUrl + "/campaigns?count=" + limit + "&offset=" + offset;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "campaigns");
			}
			case "replicate": {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = post(baseUrl + "/campaigns/" + encode(campaignId) + "/actions/replicate", Map.of(), headers);
				return toResult(response);
			}
			case "resend": {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = post(baseUrl + "/campaigns/" + encode(campaignId) + "/actions/resend", Map.of(), headers);
				return toResult(response);
			}
			case "send": {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = post(baseUrl + "/campaigns/" + encode(campaignId) + "/actions/send", Map.of(), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown campaign operation: " + operation);
		}
	}

	// ========================= List Group =========================

	private NodeExecutionResult executeListGroup(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		if ("getAll".equals(operation)) {
			String listId = context.getParameter("listGroupListId", "");
			String categoryId = context.getParameter("groupCategoryId", "");
			String url = baseUrl + "/lists/" + encode(listId) + "/interest-categories/" + encode(categoryId) + "/interests";
			HttpResponse<String> response = get(url, headers);
			return toListResult(response, "interests");
		}
		return NodeExecutionResult.error("Unknown listGroup operation: " + operation);
	}

	// ========================= Member =========================

	private NodeExecutionResult executeMember(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String listId = context.getParameter("listId", "");

		switch (operation) {
			case "create": {
				String email = context.getParameter("email", "");
				String status = context.getParameter("status", "subscribed");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email_address", email);
				body.put("status", status);
				addMergeFields(body, context);
				HttpResponse<String> response = post(baseUrl + "/lists/" + encode(listId) + "/members", body, headers);
				return toResult(response);
			}
			case "delete": {
				String email = context.getParameter("email", "");
				String subscriberHash = md5Hash(email.toLowerCase());
				HttpResponse<String> response = delete(baseUrl + "/lists/" + encode(listId) + "/members/" + subscriberHash, headers);
				return toDeleteResult(response);
			}
			case "get": {
				String email = context.getParameter("email", "");
				String subscriberHash = md5Hash(email.toLowerCase());
				HttpResponse<String> response = get(baseUrl + "/lists/" + encode(listId) + "/members/" + subscriberHash, headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 10), 10);
				int offset = toInt(context.getParameter("offset", 0), 0);
				String url = baseUrl + "/lists/" + encode(listId) + "/members?count=" + limit + "&offset=" + offset;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "members");
			}
			case "update": {
				String email = context.getParameter("email", "");
				String subscriberHash = md5Hash(email.toLowerCase());
				String status = context.getParameter("status", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!status.isEmpty()) body.put("status", status);
				addMergeFields(body, context);
				HttpResponse<String> response = patch(baseUrl + "/lists/" + encode(listId) + "/members/" + subscriberHash, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown member operation: " + operation);
		}
	}

	// ========================= Member Tag =========================

	private NodeExecutionResult executeMemberTag(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String listId = context.getParameter("memberTagListId", "");
		String email = context.getParameter("memberTagEmail", "");
		String subscriberHash = md5Hash(email.toLowerCase());
		String tagNamesStr = context.getParameter("tagNames", "");

		String tagStatus = "create".equals(operation) ? "active" : "inactive";
		List<Map<String, String>> tags = Arrays.stream(tagNamesStr.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(name -> Map.of("name", name, "status", tagStatus))
			.toList();

		Map<String, Object> body = Map.of("tags", tags);
		HttpResponse<String> response = post(baseUrl + "/lists/" + encode(listId) + "/members/" + subscriberHash + "/tags", body, headers);
		return toResult(response);
	}

	// ========================= Helpers =========================

	private String extractDataCenter(String apiKey) {
		if (apiKey != null && apiKey.contains("-")) {
			return apiKey.substring(apiKey.lastIndexOf("-") + 1);
		}
		return "us1";
	}

	private Map<String, String> buildAuthHeaders(String apiKey) {
		String credentials = Base64.getEncoder().encodeToString(("anystring:" + apiKey).getBytes());
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private String md5Hash(String input) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
			byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return input;
		}
	}

	private void addMergeFields(Map<String, Object> body, NodeExecutionContext context) {
		String mergeFieldsJson = context.getParameter("mergeFields", "{}");
		try {
			Map<String, Object> mergeFields = objectMapper.readValue(mergeFieldsJson,
				new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
			if (!mergeFields.isEmpty()) {
				body.put("merge_fields", mergeFields);
			}
		} catch (Exception ignored) {
			// ignore invalid JSON
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
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

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Mailchimp API error (HTTP " + response.statusCode() + "): " + body);
	}
}
