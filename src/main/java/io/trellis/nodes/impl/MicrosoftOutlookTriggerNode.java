package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * Microsoft Outlook Trigger Node -- polls for new emails using the Microsoft Graph API.
 */
@Slf4j
@Node(
	type = "microsoftOutlookTrigger",
	displayName = "Microsoft Outlook Trigger",
	description = "Polls for new emails in Microsoft Outlook using the Graph API",
	category = "Communication / Email",
	icon = "microsoftOutlook",
	trigger = true,
	polling = true,
	credentials = {"microsoftOutlookOAuth2Api"}
)
public class MicrosoftOutlookTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0/me";
	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
			.withZone(ZoneOffset.UTC);

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("folderId").displayName("Folder")
			.type(ParameterType.OPTIONS).defaultValue("inbox")
			.options(List.of(
				ParameterOption.builder().name("Inbox").value("inbox").description("Poll inbox").build(),
				ParameterOption.builder().name("Sent Items").value("sentitems").description("Poll sent items").build(),
				ParameterOption.builder().name("Drafts").value("drafts").description("Poll drafts").build(),
				ParameterOption.builder().name("Custom Folder ID").value("custom").description("Enter a custom folder ID").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("customFolderId").displayName("Custom Folder ID")
			.type(ParameterType.STRING)
			.description("The ID of the custom folder to poll.")
			.displayOptions(Map.of("show", Map.of("folderId", List.of("custom"))))
			.build());

		params.add(NodeParameter.builder()
			.name("includeSpam").displayName("Include Spam/Junk")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to include messages from junk mail folder.")
			.build());

		params.add(NodeParameter.builder()
			.name("maxResults").displayName("Max Results Per Poll")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Maximum number of emails to return per poll.")
			.build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		try {
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			Map<String, String> headers = authHeaders(accessToken);

			String folderId = context.getParameter("folderId", "inbox");
			if ("custom".equals(folderId)) {
				folderId = context.getParameter("customFolderId", "inbox");
			}
			int maxResults = toInt(context.getParameter("maxResults", 50), 50);

			// Build URL with filter
			String lastPollTime = (String) staticData.get("lastPollTime");
			String url;

			if ("inbox".equals(folderId) || "sentitems".equals(folderId) || "drafts".equals(folderId)) {
				url = BASE_URL + "/mailFolders/" + folderId + "/messages";
			} else {
				url = BASE_URL + "/mailFolders/" + encode(folderId) + "/messages";
			}

			url += "?$top=" + maxResults + "&$orderby=receivedDateTime%20desc";

			if (lastPollTime != null) {
				url += "&$filter=receivedDateTime%20gt%20" + encode(lastPollTime);
			}

			HttpResponse<String> response = get(url, headers);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Outlook Trigger API error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object messagesObj = parsed.get("value");

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTime", ISO_FORMATTER.format(Instant.now()));

			List<Map<String, Object>> items = new ArrayList<>();
			if (messagesObj instanceof List) {
				for (Object msg : (List<?>) messagesObj) {
					if (msg instanceof Map) {
						Map<String, Object> msgMap = new LinkedHashMap<>((Map<String, Object>) msg);
						msgMap.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(msgMap));
					}
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Outlook trigger: found {} new emails", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Microsoft Outlook Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
