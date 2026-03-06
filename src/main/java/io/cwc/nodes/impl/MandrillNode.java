package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Mandrill — send emails via the Mandrill (Mailchimp Transactional) API.
 */
@Node(
		type = "mandrill",
		displayName = "Mandrill",
		description = "Send emails and templates using the Mandrill API",
		category = "Communication / Email",
		icon = "mandrill",
		credentials = {"mandrillApi"}
)
public class MandrillNode extends AbstractApiNode {

	private static final String BASE_URL = "https://mandrillapp.com/api/1.0";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String operation = context.getParameter("operation", "sendHtml");
		String fromEmail = context.getParameter("fromEmail", "");
		String toEmail = context.getParameter("toEmail", "");
		String subject = context.getParameter("subject", "");
		String html = context.getParameter("html", "");
		String text = context.getParameter("text", "");
		String fromName = context.getParameter("fromName", "");
		String bccAddress = context.getParameter("bccAddress", "");
		String templateName = context.getParameter("templateName", "");
		boolean trackOpens = toBoolean(context.getParameters().get("trackOpens"), false);
		boolean trackClicks = toBoolean(context.getParameters().get("trackClicks"), false);
		boolean isAsync = toBoolean(context.getParameters().get("async"), false);
		boolean important = toBoolean(context.getParameters().get("important"), false);
		String tags = context.getParameter("tags", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// Build recipients
				List<Map<String, String>> toList = new ArrayList<>();
				for (String email : toEmail.split(",")) {
					String trimmed = email.trim();
					if (!trimmed.isEmpty()) {
						toList.add(Map.of("email", trimmed, "type", "to"));
					}
				}

				// Build message object
				Map<String, Object> message = new LinkedHashMap<>();
				message.put("from_email", fromEmail);
				message.put("to", toList);
				if (!subject.isBlank()) message.put("subject", subject);
				if (!html.isBlank()) message.put("html", html);
				if (!text.isBlank()) message.put("text", text);
				if (!fromName.isBlank()) message.put("from_name", fromName);
				if (!bccAddress.isBlank()) message.put("bcc_address", bccAddress);
				message.put("track_opens", trackOpens);
				message.put("track_clicks", trackClicks);
				message.put("important", important);

				if (!tags.isBlank()) {
					List<String> tagList = new ArrayList<>();
					for (String tag : tags.split(",")) {
						String trimmed = tag.trim();
						if (!trimmed.isEmpty()) tagList.add(trimmed);
					}
					message.put("tags", tagList);
				}

				// Build request body
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("key", apiKey);
				body.put("message", message);
				body.put("async", isAsync);

				String endpoint;
				if ("sendTemplate".equals(operation)) {
					body.put("template_name", templateName);
					body.put("template_content", List.of());
					endpoint = BASE_URL + "/messages/send-template.json";
				} else {
					endpoint = BASE_URL + "/messages/send.json";
				}

				HttpResponse<String> response = post(endpoint, body, headers);
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
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("sendHtml")
						.options(List.of(
								ParameterOption.builder().name("Send HTML").value("sendHtml").build(),
								ParameterOption.builder().name("Send Template").value("sendTemplate").build()
						)).build(),
				NodeParameter.builder()
						.name("fromEmail").displayName("From Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address of the sender.").build(),
				NodeParameter.builder()
						.name("fromName").displayName("From Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender display name.").build(),
				NodeParameter.builder()
						.name("toEmail").displayName("To Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Recipient email(s), comma-separated.").build(),
				NodeParameter.builder()
						.name("bccAddress").displayName("BCC Address")
						.type(ParameterType.STRING).defaultValue("")
						.description("BCC address to receive a copy.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email subject line.").build(),
				NodeParameter.builder()
						.name("html").displayName("HTML")
						.type(ParameterType.STRING).defaultValue("")
						.description("HTML content of the email.").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Plain text content.").build(),
				NodeParameter.builder()
						.name("templateName").displayName("Template Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the Mandrill template to use.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated tags for the email.").build(),
				NodeParameter.builder()
						.name("trackOpens").displayName("Track Opens")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Track email opens.").build(),
				NodeParameter.builder()
						.name("trackClicks").displayName("Track Clicks")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Track link clicks.").build(),
				NodeParameter.builder()
						.name("async").displayName("Async")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Enable background sending mode for bulk.").build(),
				NodeParameter.builder()
						.name("important").displayName("Important")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Mark as important.").build()
		);
	}
}
