package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Mailjet — send emails and SMS via the Mailjet API.
 */
@Node(
		type = "mailjet",
		displayName = "Mailjet",
		description = "Send emails and SMS using the Mailjet API",
		category = "Communication / Email",
		icon = "mailjet",
		credentials = {"mailjetEmailApi", "mailjetSmsApi"}
)
public class MailjetNode extends AbstractApiNode {

	private static final String EMAIL_API_URL = "https://api.mailjet.com/v3.1/send";
	private static final String SMS_API_URL = "https://api.mailjet.com/v4/sms-send";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "email");
		String operation = context.getParameter("operation", "send");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "email" -> handleEmail(context, operation);
					case "sms" -> handleSms(context);
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

	private Map<String, Object> handleEmail(NodeExecutionContext context, String operation) throws Exception {
		String apiKeyPublic = context.getCredentialString("apiKey", "");
		String apiKeyPrivate = context.getCredentialString("secretKey", "");
		String fromEmail = context.getParameter("fromEmail", "");
		String fromName = context.getParameter("fromName", "");
		String toEmail = context.getParameter("toEmail", "");
		String subject = context.getParameter("subject", "");
		String htmlBody = context.getParameter("htmlBody", "");
		String textBody = context.getParameter("textBody", "");
		String ccAddresses = context.getParameter("ccAddresses", "");
		String bccEmail = context.getParameter("bccEmail", "");
		String replyTo = context.getParameter("replyTo", "");
		boolean trackOpens = toBoolean(context.getParameters().get("trackOpens"), false);
		boolean trackClicks = toBoolean(context.getParameters().get("trackClicks"), false);
		String templateId = context.getParameter("templateId", "");

		String credentials = Base64.getEncoder().encodeToString((apiKeyPublic + ":" + apiKeyPrivate).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Content-Type", "application/json");

		// Build the message
		Map<String, Object> fromObj = new LinkedHashMap<>();
		fromObj.put("Email", fromEmail);
		if (!fromName.isBlank()) fromObj.put("Name", fromName);

		List<Map<String, String>> toList = new ArrayList<>();
		for (String email : toEmail.split(",")) {
			String trimmed = email.trim();
			if (!trimmed.isEmpty()) {
				toList.add(Map.of("Email", trimmed));
			}
		}

		Map<String, Object> message = new LinkedHashMap<>();
		message.put("From", fromObj);
		message.put("To", toList);

		if (!subject.isBlank()) message.put("Subject", subject);
		if (!htmlBody.isBlank()) message.put("HTMLPart", htmlBody);
		if (!textBody.isBlank()) message.put("TextPart", textBody);

		if (!ccAddresses.isBlank()) {
			List<Map<String, String>> ccList = new ArrayList<>();
			for (String email : ccAddresses.split(",")) {
				String trimmed = email.trim();
				if (!trimmed.isEmpty()) {
					ccList.add(Map.of("Email", trimmed));
				}
			}
			message.put("Cc", ccList);
		}

		if (!bccEmail.isBlank()) {
			List<Map<String, String>> bccList = new ArrayList<>();
			for (String email : bccEmail.split(",")) {
				String trimmed = email.trim();
				if (!trimmed.isEmpty()) {
					bccList.add(Map.of("Email", trimmed));
				}
			}
			message.put("Bcc", bccList);
		}

		if (!replyTo.isBlank()) {
			message.put("ReplyTo", Map.of("Email", replyTo));
		}

		if (trackOpens) {
			message.put("TrackOpens", "enabled");
		}
		if (trackClicks) {
			message.put("TrackClicks", "enabled");
		}

		if ("sendTemplate".equals(operation) && !templateId.isBlank()) {
			message.put("TemplateID", Integer.parseInt(templateId));
			message.put("TemplateLanguage", true);
		}

		Map<String, Object> body = Map.of("Messages", List.of(message));

		HttpResponse<String> response = post(EMAIL_API_URL, body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleSms(NodeExecutionContext context) throws Exception {
		String smsToken = context.getCredentialString("token", "");
		String from = context.getParameter("smsFrom", "");
		String to = context.getParameter("smsTo", "");
		String text = context.getParameter("smsText", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + smsToken);
		headers.put("Content-Type", "application/json");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("From", from);
		body.put("To", to);
		body.put("Text", text);

		HttpResponse<String> response = post(SMS_API_URL, body, headers);
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("email")
						.options(List.of(
								ParameterOption.builder().name("Email").value("email").build(),
								ParameterOption.builder().name("SMS").value("sms").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Send Template").value("sendTemplate").build()
						)).build(),
				NodeParameter.builder()
						.name("fromEmail").displayName("From Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender email address.").build(),
				NodeParameter.builder()
						.name("fromName").displayName("From Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender display name.").build(),
				NodeParameter.builder()
						.name("toEmail").displayName("To Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Recipient email(s), comma-separated.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email subject line.").build(),
				NodeParameter.builder()
						.name("htmlBody").displayName("HTML Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("HTML content of the email.").build(),
				NodeParameter.builder()
						.name("textBody").displayName("Text Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("Plain text content of the email.").build(),
				NodeParameter.builder()
						.name("ccAddresses").displayName("CC")
						.type(ParameterType.STRING).defaultValue("")
						.description("CC recipients, comma-separated.").build(),
				NodeParameter.builder()
						.name("bccEmail").displayName("BCC")
						.type(ParameterType.STRING).defaultValue("")
						.description("BCC recipients, comma-separated.").build(),
				NodeParameter.builder()
						.name("replyTo").displayName("Reply To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Reply-to email address.").build(),
				NodeParameter.builder()
						.name("trackOpens").displayName("Track Opens")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Track email opens.").build(),
				NodeParameter.builder()
						.name("trackClicks").displayName("Track Clicks")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Track link clicks.").build(),
				NodeParameter.builder()
						.name("templateId").displayName("Template ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the email template to use.").build(),
				NodeParameter.builder()
						.name("smsFrom").displayName("SMS From")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender name (3-11 alphanumeric characters).").build(),
				NodeParameter.builder()
						.name("smsTo").displayName("SMS To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Recipient phone number in E.164 format.").build(),
				NodeParameter.builder()
						.name("smsText").displayName("SMS Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("SMS message body.").build()
		);
	}
}
