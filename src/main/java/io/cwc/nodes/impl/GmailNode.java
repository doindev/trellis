package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Gmail — interact with the Gmail API to manage drafts, labels,
 * messages, and threads. Uses the Gmail REST API via Google OAuth2.
 */
@Node(
		type = "gmail",
		displayName = "Gmail",
		description = "Interact with the Gmail API",
		category = "Communication / Email",
		icon = "gmail",
		credentials = {"gmailOAuth2Api"}
)
public class GmailNode extends AbstractApiNode {

	private static final String BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String token = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "message");
		String operation = context.getParameter("operation", "send");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "draft" -> handleDraft(context, operation, headers);
					case "label" -> handleLabel(context, operation, headers);
					case "message" -> handleMessage(context, operation, headers);
					case "thread" -> handleThread(context, operation, headers);
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

	// ========================= Draft =========================

	private Map<String, Object> handleDraft(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String to = context.getParameter("to", "");
				String subject = context.getParameter("subject", "");
				String body = context.getParameter("body", "");
				String cc = context.getParameter("cc", "");
				String bcc = context.getParameter("bcc", "");

				String rawMessage = buildRfc2822Message(to, subject, body, cc, bcc);
				String encoded = base64UrlEncode(rawMessage);

				Map<String, Object> message = new LinkedHashMap<>();
				message.put("raw", encoded);
				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("message", message);

				HttpResponse<String> response = post(BASE_URL + "/drafts", requestBody, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String draftId = context.getParameter("draftId", "");
				HttpResponse<String> response = delete(BASE_URL + "/drafts/" + encode(draftId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("draftId", draftId);
				yield result;
			}
			case "get" -> {
				String draftId = context.getParameter("draftId", "");
				String format = context.getParameter("format", "full");
				HttpResponse<String> response = get(BASE_URL + "/drafts/" + encode(draftId) + "?format=" + encode(format), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int maxResults = toInt(context.getParameters().get("limit"), 100);
				String pageToken = context.getParameter("pageToken", "");
				String url = BASE_URL + "/drafts?maxResults=" + maxResults;
				if (!pageToken.isBlank()) url += "&pageToken=" + encode(pageToken);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown draft operation: " + operation);
		};
	}

	// ========================= Label =========================

	private Map<String, Object> handleLabel(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String labelName = context.getParameter("labelName", "");
				String labelListVisibility = context.getParameter("labelListVisibility", "labelShow");
				String messageListVisibility = context.getParameter("messageListVisibility", "show");

				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("name", labelName);
				requestBody.put("labelListVisibility", labelListVisibility);
				requestBody.put("messageListVisibility", messageListVisibility);

				HttpResponse<String> response = post(BASE_URL + "/labels", requestBody, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String labelId = context.getParameter("labelId", "");
				HttpResponse<String> response = delete(BASE_URL + "/labels/" + encode(labelId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("labelId", labelId);
				yield result;
			}
			case "get" -> {
				String labelId = context.getParameter("labelId", "");
				HttpResponse<String> response = get(BASE_URL + "/labels/" + encode(labelId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/labels", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown label operation: " + operation);
		};
	}

	// ========================= Message =========================

	private Map<String, Object> handleMessage(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "send" -> {
				String to = context.getParameter("to", "");
				String subject = context.getParameter("subject", "");
				String body = context.getParameter("body", "");
				String cc = context.getParameter("cc", "");
				String bcc = context.getParameter("bcc", "");

				String rawMessage = buildRfc2822Message(to, subject, body, cc, bcc);
				String encoded = base64UrlEncode(rawMessage);

				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("raw", encoded);

				HttpResponse<String> response = post(BASE_URL + "/messages/send", requestBody, headers);
				yield parseResponse(response);
			}
			case "reply" -> {
				String messageId = context.getParameter("messageId", "");
				String to = context.getParameter("to", "");
				String body = context.getParameter("body", "");
				String threadId = context.getParameter("threadId", "");

				String rawMessage = buildRfc2822Message(to, "Re: ", body, "", "");
				String encoded = base64UrlEncode(rawMessage);

				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("raw", encoded);
				if (!threadId.isBlank()) requestBody.put("threadId", threadId);

				HttpResponse<String> response = post(BASE_URL + "/messages/send", requestBody, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String messageId = context.getParameter("messageId", "");
				String format = context.getParameter("format", "full");
				HttpResponse<String> response = get(BASE_URL + "/messages/" + encode(messageId) + "?format=" + encode(format), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int maxResults = toInt(context.getParameters().get("limit"), 100);
				String query = context.getParameter("query", "");
				String labelIds = context.getParameter("labelIds", "");
				String pageToken = context.getParameter("pageToken", "");
				String url = BASE_URL + "/messages?maxResults=" + maxResults;
				if (!query.isBlank()) url += "&q=" + encode(query);
				if (!labelIds.isBlank()) url += "&labelIds=" + encode(labelIds);
				if (!pageToken.isBlank()) url += "&pageToken=" + encode(pageToken);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String messageId = context.getParameter("messageId", "");
				HttpResponse<String> response = delete(BASE_URL + "/messages/" + encode(messageId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("messageId", messageId);
				yield result;
			}
			case "addLabel" -> {
				String messageId = context.getParameter("messageId", "");
				String labelIds = context.getParameter("labelIds", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("addLabelIds", List.of(labelIds.split(",")));
				HttpResponse<String> response = post(BASE_URL + "/messages/" + encode(messageId) + "/modify", requestBody, headers);
				yield parseResponse(response);
			}
			case "removeLabel" -> {
				String messageId = context.getParameter("messageId", "");
				String labelIds = context.getParameter("labelIds", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("removeLabelIds", List.of(labelIds.split(",")));
				HttpResponse<String> response = post(BASE_URL + "/messages/" + encode(messageId) + "/modify", requestBody, headers);
				yield parseResponse(response);
			}
			case "markAsRead" -> {
				String messageId = context.getParameter("messageId", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("removeLabelIds", List.of("UNREAD"));
				HttpResponse<String> response = post(BASE_URL + "/messages/" + encode(messageId) + "/modify", requestBody, headers);
				yield parseResponse(response);
			}
			case "markAsUnread" -> {
				String messageId = context.getParameter("messageId", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("addLabelIds", List.of("UNREAD"));
				HttpResponse<String> response = post(BASE_URL + "/messages/" + encode(messageId) + "/modify", requestBody, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown message operation: " + operation);
		};
	}

	// ========================= Thread =========================

	private Map<String, Object> handleThread(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "get" -> {
				String threadId = context.getParameter("threadId", "");
				String format = context.getParameter("format", "full");
				HttpResponse<String> response = get(BASE_URL + "/threads/" + encode(threadId) + "?format=" + encode(format), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int maxResults = toInt(context.getParameters().get("limit"), 100);
				String query = context.getParameter("query", "");
				String labelIds = context.getParameter("labelIds", "");
				String pageToken = context.getParameter("pageToken", "");
				String url = BASE_URL + "/threads?maxResults=" + maxResults;
				if (!query.isBlank()) url += "&q=" + encode(query);
				if (!labelIds.isBlank()) url += "&labelIds=" + encode(labelIds);
				if (!pageToken.isBlank()) url += "&pageToken=" + encode(pageToken);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String threadId = context.getParameter("threadId", "");
				HttpResponse<String> response = delete(BASE_URL + "/threads/" + encode(threadId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("threadId", threadId);
				yield result;
			}
			case "addLabel" -> {
				String threadId = context.getParameter("threadId", "");
				String labelIds = context.getParameter("labelIds", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("addLabelIds", List.of(labelIds.split(",")));
				HttpResponse<String> response = post(BASE_URL + "/threads/" + encode(threadId) + "/modify", requestBody, headers);
				yield parseResponse(response);
			}
			case "removeLabel" -> {
				String threadId = context.getParameter("threadId", "");
				String labelIds = context.getParameter("labelIds", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("removeLabelIds", List.of(labelIds.split(",")));
				HttpResponse<String> response = post(BASE_URL + "/threads/" + encode(threadId) + "/modify", requestBody, headers);
				yield parseResponse(response);
			}
			case "reply" -> {
				String threadId = context.getParameter("threadId", "");
				String to = context.getParameter("to", "");
				String body = context.getParameter("body", "");

				String rawMessage = buildRfc2822Message(to, "Re: ", body, "", "");
				String encoded = base64UrlEncode(rawMessage);

				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("raw", encoded);
				requestBody.put("threadId", threadId);

				HttpResponse<String> response = post(BASE_URL + "/messages/send", requestBody, headers);
				yield parseResponse(response);
			}
			case "trash" -> {
				String threadId = context.getParameter("threadId", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				HttpResponse<String> response = post(BASE_URL + "/threads/" + encode(threadId) + "/trash", requestBody, headers);
				yield parseResponse(response);
			}
			case "untrash" -> {
				String threadId = context.getParameter("threadId", "");
				Map<String, Object> requestBody = new LinkedHashMap<>();
				HttpResponse<String> response = post(BASE_URL + "/threads/" + encode(threadId) + "/untrash", requestBody, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown thread operation: " + operation);
		};
	}

	// ========================= Helpers =========================

	/**
	 * Builds an RFC 2822 formatted email message.
	 */
	private String buildRfc2822Message(String to, String subject, String body, String cc, String bcc) {
		StringBuilder sb = new StringBuilder();
		sb.append("To: ").append(to).append("\r\n");
		if (!cc.isBlank()) sb.append("Cc: ").append(cc).append("\r\n");
		if (!bcc.isBlank()) sb.append("Bcc: ").append(bcc).append("\r\n");
		sb.append("Subject: ").append(subject).append("\r\n");
		sb.append("Content-Type: text/html; charset=UTF-8\r\n");
		sb.append("\r\n");
		sb.append(body);
		return sb.toString();
	}

	/**
	 * Base64url encodes a string (RFC 4648 section 5, no padding).
	 */
	private String base64UrlEncode(String input) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(input.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("message")
						.options(List.of(
								ParameterOption.builder().name("Draft").value("draft").build(),
								ParameterOption.builder().name("Label").value("label").build(),
								ParameterOption.builder().name("Message").value("message").build(),
								ParameterOption.builder().name("Thread").value("thread").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Reply").value("reply").build(),
								ParameterOption.builder().name("Add Label").value("addLabel").build(),
								ParameterOption.builder().name("Remove Label").value("removeLabel").build(),
								ParameterOption.builder().name("Mark As Read").value("markAsRead").build(),
								ParameterOption.builder().name("Mark As Unread").value("markAsUnread").build(),
								ParameterOption.builder().name("Trash").value("trash").build(),
								ParameterOption.builder().name("Untrash").value("untrash").build()
						)).build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email address of the recipient.").build(),
				NodeParameter.builder()
						.name("cc").displayName("CC")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated CC email addresses.").build(),
				NodeParameter.builder()
						.name("bcc").displayName("BCC")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated BCC email addresses.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email subject line.").build(),
				NodeParameter.builder()
						.name("body").displayName("Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email body content (HTML supported).").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the message.").build(),
				NodeParameter.builder()
						.name("threadId").displayName("Thread ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the email thread.").build(),
				NodeParameter.builder()
						.name("draftId").displayName("Draft ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the draft.").build(),
				NodeParameter.builder()
						.name("labelId").displayName("Label ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the label.").build(),
				NodeParameter.builder()
						.name("labelIds").displayName("Label IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated label IDs.").build(),
				NodeParameter.builder()
						.name("labelName").displayName("Label Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name for the new label.").build(),
				NodeParameter.builder()
						.name("labelListVisibility").displayName("Label List Visibility")
						.type(ParameterType.OPTIONS).defaultValue("labelShow")
						.options(List.of(
								ParameterOption.builder().name("Show").value("labelShow").build(),
								ParameterOption.builder().name("Show If Unread").value("labelShowIfUnread").build(),
								ParameterOption.builder().name("Hide").value("labelHide").build()
						)).build(),
				NodeParameter.builder()
						.name("messageListVisibility").displayName("Message List Visibility")
						.type(ParameterType.OPTIONS).defaultValue("show")
						.options(List.of(
								ParameterOption.builder().name("Show").value("show").build(),
								ParameterOption.builder().name("Hide").value("hide").build()
						)).build(),
				NodeParameter.builder()
						.name("format").displayName("Format")
						.type(ParameterType.OPTIONS).defaultValue("full")
						.options(List.of(
								ParameterOption.builder().name("Full").value("full").build(),
								ParameterOption.builder().name("Metadata").value("metadata").build(),
								ParameterOption.builder().name("Minimal").value("minimal").build(),
								ParameterOption.builder().name("Raw").value("raw").build()
						)).build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Gmail search query (same format as Gmail search box).").build(),
				NodeParameter.builder()
						.name("pageToken").displayName("Page Token")
						.type(ParameterType.STRING).defaultValue("")
						.description("Token for the next page of results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Maximum number of results to return.").build()
		);
	}
}
