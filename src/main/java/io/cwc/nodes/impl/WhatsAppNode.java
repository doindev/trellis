package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * WhatsApp Business Cloud — send text messages, templates, and media
 * via the WhatsApp Business Cloud API (Meta Graph API).
 */
@Node(
		type = "whatsApp",
		displayName = "WhatsApp Business Cloud",
		description = "Send messages via the WhatsApp Business Cloud API",
		category = "Communication",
		icon = "whatsApp",
		credentials = {"whatsAppBusinessCloudApi"}
)
public class WhatsAppNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.facebook.com/v17.0";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String phoneNumberId = context.getParameter("phoneNumberId", "");
		String operation = context.getParameter("operation", "sendText");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		String messagesUrl = BASE_URL + "/" + encode(phoneNumberId) + "/messages";

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "sendText" -> sendText(context, messagesUrl, headers);
					case "sendTemplate" -> sendTemplate(context, messagesUrl, headers);
					case "sendMedia" -> sendMedia(context, messagesUrl, headers);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	// ========================= Send Text =========================

	private Map<String, Object> sendText(NodeExecutionContext context, String url,
			Map<String, String> headers) throws Exception {
		String recipientPhone = context.getParameter("recipientPhone", "");
		String messageBody = context.getParameter("messageBody", "");
		boolean previewUrl = toBoolean(context.getParameters().get("previewUrl"), false);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("messaging_product", "whatsapp");
		body.put("recipient_type", "individual");
		body.put("to", recipientPhone);
		body.put("type", "text");

		Map<String, Object> text = new LinkedHashMap<>();
		text.put("preview_url", previewUrl);
		text.put("body", messageBody);
		body.put("text", text);

		HttpResponse<String> response = post(url, body, headers);
		return parseResponse(response);
	}

	// ========================= Send Template =========================

	private Map<String, Object> sendTemplate(NodeExecutionContext context, String url,
			Map<String, String> headers) throws Exception {
		String recipientPhone = context.getParameter("recipientPhone", "");
		String templateName = context.getParameter("templateName", "");
		String languageCode = context.getParameter("languageCode", "en_US");
		String componentsJson = context.getParameter("templateComponents", "[]");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("messaging_product", "whatsapp");
		body.put("recipient_type", "individual");
		body.put("to", recipientPhone);
		body.put("type", "template");

		Map<String, Object> template = new LinkedHashMap<>();
		template.put("name", templateName);
		template.put("language", Map.of("code", languageCode));

		List<Map<String, Object>> components = parseJsonArray(componentsJson);
		if (!components.isEmpty()) {
			template.put("components", components);
		}

		body.put("template", template);

		HttpResponse<String> response = post(url, body, headers);
		return parseResponse(response);
	}

	// ========================= Send Media =========================

	private Map<String, Object> sendMedia(NodeExecutionContext context, String url,
			Map<String, String> headers) throws Exception {
		String recipientPhone = context.getParameter("recipientPhone", "");
		String mediaType = context.getParameter("mediaType", "image");
		String mediaUrl = context.getParameter("mediaUrl", "");
		String mediaId = context.getParameter("mediaId", "");
		String caption = context.getParameter("caption", "");
		String filename = context.getParameter("filename", "");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("messaging_product", "whatsapp");
		body.put("recipient_type", "individual");
		body.put("to", recipientPhone);
		body.put("type", mediaType);

		Map<String, Object> mediaObj = new LinkedHashMap<>();
		if (!mediaId.isEmpty()) {
			mediaObj.put("id", mediaId);
		} else if (!mediaUrl.isEmpty()) {
			mediaObj.put("link", mediaUrl);
		}
		if (!caption.isEmpty()) mediaObj.put("caption", caption);
		if (!filename.isEmpty()) mediaObj.put("filename", filename);

		body.put(mediaType, mediaObj);

		HttpResponse<String> response = post(url, body, headers);
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("sendText")
						.options(List.of(
								ParameterOption.builder().name("Send Text Message").value("sendText").build(),
								ParameterOption.builder().name("Send Template Message").value("sendTemplate").build(),
								ParameterOption.builder().name("Send Media Message").value("sendMedia").build()
						)).build(),
				NodeParameter.builder()
						.name("phoneNumberId").displayName("Phone Number ID")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The Phone Number ID from your WhatsApp Business account.").build(),
				NodeParameter.builder()
						.name("recipientPhone").displayName("Recipient Phone Number")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The recipient's phone number in E.164 format (e.g., +1234567890).").build(),
				NodeParameter.builder()
						.name("messageBody").displayName("Message Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("The text content of the message.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendText")))).build(),
				NodeParameter.builder()
						.name("previewUrl").displayName("Preview URL")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to show a URL preview in the message.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendText")))).build(),
				NodeParameter.builder()
						.name("templateName").displayName("Template Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the approved message template.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendTemplate")))).build(),
				NodeParameter.builder()
						.name("languageCode").displayName("Language Code")
						.type(ParameterType.STRING).defaultValue("en_US")
						.description("The language code for the template (e.g., en_US).")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendTemplate")))).build(),
				NodeParameter.builder()
						.name("templateComponents").displayName("Template Components (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of template component objects for dynamic content.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendTemplate")))).build(),
				NodeParameter.builder()
						.name("mediaType").displayName("Media Type")
						.type(ParameterType.OPTIONS).defaultValue("image")
						.options(List.of(
								ParameterOption.builder().name("Image").value("image").build(),
								ParameterOption.builder().name("Video").value("video").build(),
								ParameterOption.builder().name("Audio").value("audio").build(),
								ParameterOption.builder().name("Document").value("document").build(),
								ParameterOption.builder().name("Sticker").value("sticker").build()
						))
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendMedia")))).build(),
				NodeParameter.builder()
						.name("mediaUrl").displayName("Media URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("Public URL of the media file to send.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendMedia")))).build(),
				NodeParameter.builder()
						.name("mediaId").displayName("Media ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of a previously uploaded media. Takes priority over Media URL.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendMedia")))).build(),
				NodeParameter.builder()
						.name("caption").displayName("Caption")
						.type(ParameterType.STRING).defaultValue("")
						.description("Caption for the media message.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendMedia")))).build(),
				NodeParameter.builder()
						.name("filename").displayName("Filename")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filename for document type media.")
						.displayOptions(Map.of("show", Map.of("operation", List.of("sendMedia")))).build()
		);
	}
}
