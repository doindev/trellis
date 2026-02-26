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
 * Microsoft Agent 365 Trigger Node -- polls for new activities/messages from
 * Microsoft 365 Copilot/Agent via the Microsoft Graph API.
 */
@Slf4j
@Node(
	type = "microsoftAgent365Trigger",
	displayName = "Microsoft Agent 365 Trigger",
	description = "Poll for new activities and messages from Microsoft 365 Copilot/Agent.",
	category = "Standalone AI Services",
	icon = "microsoft",
	credentials = {"microsoftAgent365TriggerOAuth2Api"},
	trigger = true,
	polling = true
)
public class MicrosoftAgent365TriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0";

	@Override
	public List<NodeInput> getInputs() {
		// Trigger nodes have no inputs
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource to Poll")
			.type(ParameterType.OPTIONS).required(true).defaultValue("activities")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Activities").value("activities").description("Poll for recent user activities").build(),
				ParameterOption.builder().name("Call Records").value("callRecords").description("Poll for new call records").build(),
				ParameterOption.builder().name("Messages").value("messages").description("Poll for new messages").build(),
				ParameterOption.builder().name("Chat Messages").value("chatMessages").description("Poll for new chat messages").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("chatId").displayName("Chat ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the chat to poll for messages.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("chatMessages"))))
			.build());

		params.add(NodeParameter.builder()
			.name("mailFolder").displayName("Mail Folder")
			.type(ParameterType.OPTIONS).defaultValue("inbox")
			.displayOptions(Map.of("show", Map.of("resource", List.of("messages"))))
			.options(List.of(
				ParameterOption.builder().name("Inbox").value("inbox").build(),
				ParameterOption.builder().name("Sent Items").value("sentitems").build(),
				ParameterOption.builder().name("Drafts").value("drafts").build(),
				ParameterOption.builder().name("Deleted Items").value("deleteditems").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("onlyUnread").displayName("Only Unread")
			.type(ParameterType.BOOLEAN).defaultValue(true)
			.description("Only return unread messages.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("messages"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Max number of results to return.")
			.build());

		params.add(NodeParameter.builder()
			.name("sinceMinutes").displayName("Since (minutes ago)")
			.type(ParameterType.NUMBER).defaultValue(60)
			.description("Only return items created/modified within this many minutes. Used for activities and call records.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("activities", "callRecords"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "activities");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "activities" -> pollActivities(context, headers);
				case "callRecords" -> pollCallRecords(context, headers);
				case "messages" -> pollMessages(context, headers);
				case "chatMessages" -> pollChatMessages(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Microsoft Agent 365 Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Poll Activities =========================

	private NodeExecutionResult pollActivities(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		int limit = getLimit(context);
		int sinceMinutes = toInt(context.getParameter("sinceMinutes", 60), 60);

		// Build the URL with $top and filter by recent activity
		String url = BASE_URL + "/me/activities/recent?$top=" + limit;

		HttpResponse<String> response = get(url, headers);
		return toListResult(response, "value");
	}

	// ========================= Poll Call Records =========================

	private NodeExecutionResult pollCallRecords(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		int limit = getLimit(context);
		int sinceMinutes = toInt(context.getParameter("sinceMinutes", 60), 60);

		// Calculate the filter date
		java.time.Instant since = java.time.Instant.now().minusSeconds(sinceMinutes * 60L);
		String sinceStr = since.toString();

		String url = BASE_URL + "/communications/callRecords?$top=" + limit
			+ "&$filter=startDateTime ge " + encode(sinceStr)
			+ "&$orderby=startDateTime desc";

		HttpResponse<String> response = get(url, headers);
		return toListResult(response, "value");
	}

	// ========================= Poll Messages =========================

	private NodeExecutionResult pollMessages(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		int limit = getLimit(context);
		String mailFolder = context.getParameter("mailFolder", "inbox");
		boolean onlyUnread = toBoolean(context.getParameter("onlyUnread", true), true);

		String url = BASE_URL + "/me/mailFolders/" + encode(mailFolder) + "/messages?$top=" + limit
			+ "&$orderby=receivedDateTime desc";

		if (onlyUnread) {
			url += "&$filter=isRead eq false";
		}

		// Check static data for last poll timestamp
		Map<String, Object> staticData = context.getStaticData();
		if (staticData != null) {
			Object lastPollTime = staticData.get("lastPollTime");
			if (lastPollTime != null) {
				String filterPrefix = onlyUnread ? " and " : "&$filter=";
				url += filterPrefix + "receivedDateTime ge " + encode(String.valueOf(lastPollTime));
			}
		}

		HttpResponse<String> response = get(url, headers);

		// Update last poll timestamp
		if (staticData != null) {
			staticData.put("lastPollTime", java.time.Instant.now().toString());
		}

		return toListResult(response, "value");
	}

	// ========================= Poll Chat Messages =========================

	private NodeExecutionResult pollChatMessages(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		int limit = getLimit(context);
		String chatId = context.getParameter("chatId", "");

		if (chatId.isBlank()) {
			return NodeExecutionResult.error("Chat ID is required for polling chat messages.");
		}

		String url = BASE_URL + "/chats/" + encode(chatId) + "/messages?$top=" + limit
			+ "&$orderby=createdDateTime desc";

		// Check static data for last poll timestamp
		Map<String, Object> staticData = context.getStaticData();
		if (staticData != null) {
			Object lastPollTime = staticData.get("lastChatPollTime");
			if (lastPollTime != null) {
				url += "&$filter=createdDateTime ge " + encode(String.valueOf(lastPollTime));
			}
		}

		HttpResponse<String> response = get(url, headers);

		// Update last poll timestamp
		if (staticData != null) {
			staticData.put("lastChatPollTime", java.time.Instant.now().toString());
		}

		return toListResult(response, "value");
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		String token = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 50);
		return returnAll ? 999 : Math.min(limit, 999);
	}

	@SuppressWarnings("unchecked")
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
					Map<String, Object> triggerItem = new LinkedHashMap<>((Map<String, Object>) item);
					triggerItem.put("_triggerTimestamp", System.currentTimeMillis());
					items.add(wrapInJson(triggerItem));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Microsoft Graph API error (HTTP " + response.statusCode() + "): " + body);
	}
}
