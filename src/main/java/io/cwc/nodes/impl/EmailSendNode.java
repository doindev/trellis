package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import jakarta.mail.*;
import jakarta.mail.internet.*;

/**
 * Send Email — sends emails via SMTP protocol.
 */
@Node(
		type = "emailSend",
		displayName = "Send Email",
		description = "Send emails using SMTP protocol",
		category = "Communication / Email",
		icon = "emailSend",
		credentials = {"smtp"}
)
public class EmailSendNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String host = context.getCredentialString("host", "");
		int port = toInt(context.getCredentialString("port", "587"), 587);
		boolean secure = toBoolean(context.getCredentialString("secure", "false"), false);
		String user = context.getCredentialString("user", "");
		String password = context.getCredentialString("password", "");

		String fromEmail = context.getParameter("fromEmail", "");
		String toEmail = context.getParameter("toEmail", "");
		String ccEmail = context.getParameter("ccEmail", "");
		String bccEmail = context.getParameter("bccEmail", "");
		String subject = context.getParameter("subject", "");
		String emailFormat = context.getParameter("emailFormat", "html");
		String text = context.getParameter("text", "");
		String html = context.getParameter("html", "");
		String replyTo = context.getParameter("replyTo", "");
		boolean allowUnauthorizedCerts = toBoolean(context.getParameters().get("allowUnauthorizedCerts"), false);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Properties props = new Properties();
				props.put("mail.smtp.host", host);
				props.put("mail.smtp.port", String.valueOf(port));

				if (secure) {
					props.put("mail.smtp.ssl.enable", "true");
				} else {
					props.put("mail.smtp.starttls.enable", "true");
				}

				if (allowUnauthorizedCerts) {
					props.put("mail.smtp.ssl.trust", "*");
				}

				Session session;
				if (!user.isBlank()) {
					props.put("mail.smtp.auth", "true");
					session = Session.getInstance(props, new Authenticator() {
						@Override
						protected PasswordAuthentication getPasswordAuthentication() {
							return new PasswordAuthentication(user, password);
						}
					});
				} else {
					session = Session.getInstance(props);
				}

				MimeMessage message = new MimeMessage(session);
				message.setFrom(new InternetAddress(fromEmail));
				message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));

				if (!ccEmail.isBlank()) {
					message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccEmail));
				}
				if (!bccEmail.isBlank()) {
					message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccEmail));
				}
				if (!replyTo.isBlank()) {
					message.setReplyTo(InternetAddress.parse(replyTo));
				}

				message.setSubject(subject);

				switch (emailFormat) {
					case "text" -> message.setText(text);
					case "html" -> message.setContent(html, "text/html; charset=utf-8");
					case "both" -> {
						MimeMultipart multipart = new MimeMultipart("alternative");
						MimeBodyPart textPart = new MimeBodyPart();
						textPart.setText(text, "utf-8");
						multipart.addBodyPart(textPart);
						MimeBodyPart htmlPart = new MimeBodyPart();
						htmlPart.setContent(html, "text/html; charset=utf-8");
						multipart.addBodyPart(htmlPart);
						message.setContent(multipart);
					}
				}

				Transport.send(message);

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("to", toEmail);
				result.put("subject", subject);
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

	private int toInt(String value, int defaultValue) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private boolean toBoolean(String value, boolean defaultValue) {
		if (value == null || value.isBlank()) return defaultValue;
		return Boolean.parseBoolean(value);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("fromEmail").displayName("From Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender email address.").build(),
				NodeParameter.builder()
						.name("toEmail").displayName("To Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Recipient email address(es).").build(),
				NodeParameter.builder()
						.name("ccEmail").displayName("CC")
						.type(ParameterType.STRING).defaultValue("")
						.description("CC recipient(s).").build(),
				NodeParameter.builder()
						.name("bccEmail").displayName("BCC")
						.type(ParameterType.STRING).defaultValue("")
						.description("BCC recipient(s).").build(),
				NodeParameter.builder()
						.name("replyTo").displayName("Reply To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Reply-to email address.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email subject line.").build(),
				NodeParameter.builder()
						.name("emailFormat").displayName("Email Format")
						.type(ParameterType.OPTIONS)
						.defaultValue("html")
						.options(List.of(
								ParameterOption.builder().name("Text").value("text").build(),
								ParameterOption.builder().name("HTML").value("html").build(),
								ParameterOption.builder().name("Both").value("both").build()
						)).build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Plain text content.").build(),
				NodeParameter.builder()
						.name("html").displayName("HTML")
						.type(ParameterType.STRING).defaultValue("")
						.description("HTML content.").build(),
				NodeParameter.builder()
						.name("allowUnauthorizedCerts").displayName("Allow Unauthorized Certs")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Bypass SSL certificate validation.").build()
		);
	}
}
