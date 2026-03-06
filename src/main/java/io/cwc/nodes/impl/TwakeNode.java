package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Twake — send messages to channels in the Twake collaborative workspace.
 */
@Node(
		type = "twake",
		displayName = "Twake",
		description = "Send messages to Twake channels",
		category = "Communication / Chat & Messaging",
		icon = "twake",
		credentials = {"twakeCloudApi"}
)
public class TwakeNode extends AbstractApiNode {

	private static final String BASE_URL = "https://plugins.twake.app/plugins/n8n";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String channelId = context.getParameter("channelId", "");
		String content = context.getParameter("content", "");
		String senderName = context.getParameter("senderName", "");
		String senderIcon = context.getParameter("senderIcon", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + apiKey);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> contentObj = new LinkedHashMap<>();
				contentObj.put("formatted", content);

				Map<String, Object> hiddenData = new LinkedHashMap<>();
				hiddenData.put("allow_delete", "everyone");

				Map<String, Object> messageObj = new LinkedHashMap<>();
				messageObj.put("channel_id", channelId);
				messageObj.put("content", contentObj);
				messageObj.put("hidden_data", hiddenData);

				if (!senderName.isBlank()) {
					messageObj.put("custom_title", senderName);
				}
				if (!senderIcon.isBlank()) {
					messageObj.put("custom_icon", senderIcon);
				}

				Map<String, Object> body = Map.of("object", messageObj);

				HttpResponse<String> response = post(BASE_URL + "/actions/message/save", body, headers);
				Map<String, Object> result = parseResponse(response);
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("channelId").displayName("Channel ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the channel to send the message to.").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message content.").build(),
				NodeParameter.builder()
						.name("senderName").displayName("Sender Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Custom sender display name.").build(),
				NodeParameter.builder()
						.name("senderIcon").displayName("Sender Icon URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of custom sender icon.").build()
		);
	}
}
