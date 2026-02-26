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
 * Microsoft Outlook Node -- manage drafts, folders, and messages
 * via the Microsoft Graph API.
 */
@Slf4j
@Node(
	type = "microsoftOutlook",
	displayName = "Microsoft Outlook",
	description = "Manage drafts, folders, and messages in Microsoft Outlook",
	category = "Communication / Email",
	icon = "microsoftOutlook",
	credentials = {"microsoftOutlookOAuth2Api"}
)
public class MicrosoftOutlookNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0/me";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("message")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Draft").value("draft").description("Manage drafts").build(),
				ParameterOption.builder().name("Folder").value("folder").description("Manage mail folders").build(),
				ParameterOption.builder().name("Folder Message").value("folderMessage").description("Get messages in a folder").build(),
				ParameterOption.builder().name("Message").value("message").description("Manage messages").build()
			)).build());

		// Draft operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("draft"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a draft").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a draft").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a draft").build(),
				ParameterOption.builder().name("Send").value("send").description("Send a draft").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a draft").build()
			)).build());

		// Folder operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a folder").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a folder").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a folder").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many folders").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a folder").build()
			)).build());

		// Folder Message operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folderMessage"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get messages in a folder").build()
			)).build());

		// Message operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").description("Delete a message").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a message").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many messages").build(),
				ParameterOption.builder().name("Move").value("move").description("Move a message to a folder").build(),
				ParameterOption.builder().name("Reply").value("reply").description("Reply to a message").build(),
				ParameterOption.builder().name("Send").value("send").description("Send a new message").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a message").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("messageId").displayName("Message ID")
			.type(ParameterType.STRING)
			.description("The ID of the message.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("draft", "message"))))
			.build());

		params.add(NodeParameter.builder()
			.name("folderId").displayName("Folder ID")
			.type(ParameterType.STRING)
			.description("The ID of the mail folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder", "folderMessage", "message"))))
			.build());

		params.add(NodeParameter.builder()
			.name("subject").displayName("Subject")
			.type(ParameterType.STRING)
			.description("Subject of the email.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("draft", "message"), "operation", List.of("create", "send", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("bodyContent").displayName("Body Content")
			.type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 6))
			.description("Body content of the email.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("draft", "message"), "operation", List.of("create", "send", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("bodyContentType").displayName("Body Content Type")
			.type(ParameterType.OPTIONS).defaultValue("Text")
			.displayOptions(Map.of("show", Map.of("resource", List.of("draft", "message"), "operation", List.of("create", "send", "update"))))
			.options(List.of(
				ParameterOption.builder().name("Text").value("Text").build(),
				ParameterOption.builder().name("HTML").value("HTML").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("toRecipients").displayName("To Recipients")
			.type(ParameterType.STRING)
			.description("Comma-separated email addresses of recipients.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("draft", "message"), "operation", List.of("create", "send", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("replyComment").displayName("Reply Comment")
			.type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 4))
			.description("Comment to include in the reply.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("reply"))))
			.build());

		params.add(NodeParameter.builder()
			.name("destinationFolderId").displayName("Destination Folder ID")
			.type(ParameterType.STRING)
			.description("The ID of the destination folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("message"), "operation", List.of("move"))))
			.build());

		params.add(NodeParameter.builder()
			.name("folderName").displayName("Folder Name")
			.type(ParameterType.STRING)
			.description("Name of the mail folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Maximum number of items to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		try {
			Map<String, String> headers = authHeaders(accessToken);
			return switch (resource) {
				case "draft" -> executeDraft(context, operation, headers);
				case "folder" -> executeFolder(context, operation, headers);
				case "folderMessage" -> executeFolderMessage(context, operation, headers);
				case "message" -> executeMessage(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Microsoft Outlook API error: " + e.getMessage(), e);
		}
	}

	// ========================= Draft Operations =========================

	private NodeExecutionResult executeDraft(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> body = buildMessageBody(context);
				HttpResponse<String> response = post(BASE_URL + "/messages", body, headers);
				return toResult(response);
			}
			case "delete": {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = delete(BASE_URL + "/messages/" + encode(messageId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = get(BASE_URL + "/messages/" + encode(messageId), headers);
				return toResult(response);
			}
			case "send": {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = post(BASE_URL + "/messages/" + encode(messageId) + "/send", Map.of(), headers);
				if (response.statusCode() == 202 || response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "messageId", messageId))));
				}
				return toResult(response);
			}
			case "update": {
				String messageId = context.getParameter("messageId", "");
				Map<String, Object> body = buildMessageBody(context);
				HttpResponse<String> response = patch(BASE_URL + "/messages/" + encode(messageId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown draft operation: " + operation);
		}
	}

	// ========================= Folder Operations =========================

	private NodeExecutionResult executeFolder(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String folderName = context.getParameter("folderName", "");
				HttpResponse<String> response = post(BASE_URL + "/mailFolders", Map.of("displayName", folderName), headers);
				return toResult(response);
			}
			case "delete": {
				String folderId = context.getParameter("folderId", "");
				HttpResponse<String> response = delete(BASE_URL + "/mailFolders/" + encode(folderId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String folderId = context.getParameter("folderId", "");
				HttpResponse<String> response = get(BASE_URL + "/mailFolders/" + encode(folderId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(BASE_URL + "/mailFolders?$top=" + limit, headers);
				return toArrayResult(response, "value");
			}
			case "update": {
				String folderId = context.getParameter("folderId", "");
				String folderName = context.getParameter("folderName", "");
				HttpResponse<String> response = patch(BASE_URL + "/mailFolders/" + encode(folderId), Map.of("displayName", folderName), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown folder operation: " + operation);
		}
	}

	// ========================= Folder Message Operations =========================

	private NodeExecutionResult executeFolderMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		if ("getAll".equals(operation)) {
			String folderId = context.getParameter("folderId", "");
			int limit = toInt(context.getParameter("limit", 50), 50);
			HttpResponse<String> response = get(BASE_URL + "/mailFolders/" + encode(folderId) + "/messages?$top=" + limit, headers);
			return toArrayResult(response, "value");
		}
		return NodeExecutionResult.error("Unknown folderMessage operation: " + operation);
	}

	// ========================= Message Operations =========================

	private NodeExecutionResult executeMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "delete": {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = delete(BASE_URL + "/messages/" + encode(messageId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = get(BASE_URL + "/messages/" + encode(messageId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(BASE_URL + "/messages?$top=" + limit, headers);
				return toArrayResult(response, "value");
			}
			case "move": {
				String messageId = context.getParameter("messageId", "");
				String destFolderId = context.getParameter("destinationFolderId", "");
				HttpResponse<String> response = post(BASE_URL + "/messages/" + encode(messageId) + "/move",
						Map.of("destinationId", destFolderId), headers);
				return toResult(response);
			}
			case "reply": {
				String messageId = context.getParameter("messageId", "");
				String comment = context.getParameter("replyComment", "");
				HttpResponse<String> response = post(BASE_URL + "/messages/" + encode(messageId) + "/reply",
						Map.of("comment", comment), headers);
				if (response.statusCode() == 202 || response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "messageId", messageId))));
				}
				return toResult(response);
			}
			case "send": {
				Map<String, Object> messageBody = buildMessageBody(context);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("message", messageBody);
				body.put("saveToSentItems", true);
				HttpResponse<String> response = post(BASE_URL + "/sendMail", body, headers);
				if (response.statusCode() == 202 || response.statusCode() < 300) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "message", "Email sent"))));
				}
				return toResult(response);
			}
			case "update": {
				String messageId = context.getParameter("messageId", "");
				Map<String, Object> body = buildMessageBody(context);
				HttpResponse<String> response = patch(BASE_URL + "/messages/" + encode(messageId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown message operation: " + operation);
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

	private Map<String, Object> buildMessageBody(NodeExecutionContext context) {
		Map<String, Object> body = new LinkedHashMap<>();
		String subject = context.getParameter("subject", "");
		String content = context.getParameter("bodyContent", "");
		String contentType = context.getParameter("bodyContentType", "Text");
		String toRecipients = context.getParameter("toRecipients", "");

		if (!subject.isEmpty()) body.put("subject", subject);
		if (!content.isEmpty()) {
			body.put("body", Map.of("contentType", contentType, "content", content));
		}
		if (!toRecipients.isEmpty()) {
			List<Map<String, Object>> recipients = new ArrayList<>();
			for (String email : toRecipients.split(",")) {
				String trimmed = email.trim();
				if (!trimmed.isEmpty()) {
					recipients.add(Map.of("emailAddress", Map.of("address", trimmed)));
				}
			}
			if (!recipients.isEmpty()) body.put("toRecipients", recipients);
		}
		return body;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return outlookError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toArrayResult(HttpResponse<String> response, String key) throws Exception {
		if (response.statusCode() >= 400) {
			return outlookError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get(key);
		if (data instanceof List) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					results.add(wrapInJson(item));
				}
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return outlookError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult outlookError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Microsoft Outlook API error (HTTP " + response.statusCode() + "): " + body);
	}
}
