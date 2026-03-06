package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Line — send notifications via the LINE Notify API.
 * Note: LINE Notify was discontinued April 1, 2025, but this node
 * is preserved for compatibility with existing workflows.
 */
@Node(
		type = "line",
		displayName = "Line",
		description = "Send notifications via LINE Notify",
		category = "Communication / Chat & Messaging",
		icon = "line",
		credentials = {"lineNotifyApi"}
)
public class LineNode extends AbstractApiNode {

	private static final String NOTIFY_URL = "https://notify-api.line.me/api/notify";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String message = context.getParameter("message", "");
		boolean notificationDisabled = toBoolean(context.getParameters().get("notificationDisabled"), false);
		String imageFullsize = context.getParameter("imageFullsize", "");
		String imageThumbnail = context.getParameter("imageThumbnail", "");
		String stickerPackageId = context.getParameter("stickerPackageId", "");
		String stickerId = context.getParameter("stickerId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// Build form body as query params since LINE Notify uses form data
				StringBuilder formBody = new StringBuilder();
				formBody.append("message=").append(encode(message));

				if (notificationDisabled) {
					formBody.append("&notificationDisabled=true");
				}
				if (!imageFullsize.isBlank()) {
					formBody.append("&imageFullsize=").append(encode(imageFullsize));
				}
				if (!imageThumbnail.isBlank()) {
					formBody.append("&imageThumbnail=").append(encode(imageThumbnail));
				}
				if (!stickerPackageId.isBlank()) {
					formBody.append("&stickerPackageId=").append(encode(stickerPackageId));
				}
				if (!stickerId.isBlank()) {
					formBody.append("&stickerId=").append(encode(stickerId));
				}

				headers.put("Content-Type", "application/x-www-form-urlencoded");

				// Use the HttpClient directly for form post
				java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
						.uri(java.net.URI.create(NOTIFY_URL))
						.header("Authorization", "Bearer " + accessToken)
						.header("Content-Type", "application/x-www-form-urlencoded")
						.POST(java.net.http.HttpRequest.BodyPublishers.ofString(formBody.toString()))
						.build();

				java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
				HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

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
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("The notification message to send.").build(),
				NodeParameter.builder()
						.name("imageFullsize").displayName("Image Full Size URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("HTTP/HTTPS URL. Maximum size of 2048x2048px JPEG.").build(),
				NodeParameter.builder()
						.name("imageThumbnail").displayName("Image Thumbnail URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("HTTP/HTTPS URL. Maximum size of 240x240px JPEG.").build(),
				NodeParameter.builder()
						.name("notificationDisabled").displayName("Notification Disabled")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to disable push notification.").build(),
				NodeParameter.builder()
						.name("stickerPackageId").displayName("Sticker Package ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Package ID of the sticker.").build(),
				NodeParameter.builder()
						.name("stickerId").displayName("Sticker ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sticker ID.").build()
		);
	}
}
