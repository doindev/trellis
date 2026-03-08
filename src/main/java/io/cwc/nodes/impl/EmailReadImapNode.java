package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.extern.slf4j.Slf4j;

/**
 * Email Read IMAP Node -- polls an IMAP server for new emails.
 * Extends AbstractTriggerNode (not AbstractApiNode).
 */
@Slf4j
@Node(
	type = "emailReadImap",
	displayName = "Email Trigger (IMAP)",
	description = "Polls an IMAP email server for new messages",
	category = "Core Triggers",
	icon = "email",
	trigger = true,
	polling = true,
	credentials = {"imapApi"}
)
public class EmailReadImapNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("host").displayName("IMAP Host")
			.type(ParameterType.STRING).required(true)
			.description("IMAP server hostname (e.g. imap.gmail.com).")
			.placeHolder("imap.gmail.com")
			.build());

		params.add(NodeParameter.builder()
			.name("port").displayName("Port")
			.type(ParameterType.NUMBER).defaultValue(993)
			.description("IMAP server port.")
			.build());

		params.add(NodeParameter.builder()
			.name("username").displayName("Username")
			.type(ParameterType.STRING).required(true)
			.description("IMAP login username (usually email address).")
			.build());

		params.add(NodeParameter.builder()
			.name("password").displayName("Password")
			.type(ParameterType.STRING).required(true)
			.description("IMAP login password or app password.")
			.build());

		params.add(NodeParameter.builder()
			.name("mailbox").displayName("Mailbox")
			.type(ParameterType.STRING).defaultValue("INBOX")
			.description("Mailbox folder to poll (e.g. INBOX).")
			.build());

		params.add(NodeParameter.builder()
			.name("useSSL").displayName("Use SSL")
			.type(ParameterType.BOOLEAN).defaultValue(true)
			.description("Whether to use SSL/TLS for connection.")
			.build());

		params.add(NodeParameter.builder()
			.name("maxMessages").displayName("Max Messages Per Poll")
			.type(ParameterType.NUMBER).defaultValue(10)
			.description("Maximum number of new messages to fetch per poll.")
			.build());

		params.add(NodeParameter.builder()
			.name("markAsRead").displayName("Mark as Read")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Mark fetched messages as read.")
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		// Get IMAP parameters (from params first, then fall back to credentials)
		String host = context.getParameter("host",
				String.valueOf(credentials.getOrDefault("host", "")));
		int port = toInt(context.getParameter("port",
				credentials.getOrDefault("port", 993)), 993);
		String username = context.getParameter("username",
				String.valueOf(credentials.getOrDefault("username",
						credentials.getOrDefault("user", ""))));
		String password = context.getParameter("password",
				String.valueOf(credentials.getOrDefault("password", "")));
		String mailbox = context.getParameter("mailbox", "INBOX");
		boolean useSSL = toBoolean(context.getParameter("useSSL", true), true);
		int maxMessages = toInt(context.getParameter("maxMessages", 10), 10);
		boolean markAsRead = toBoolean(context.getParameter("markAsRead", false), false);

		Store store = null;
		Folder folder = null;

		try {
			// Configure IMAP properties
			Properties props = new Properties();
			if (useSSL) {
				props.put("mail.store.protocol", "imaps");
				props.put("mail.imaps.host", host);
				props.put("mail.imaps.port", String.valueOf(port));
				props.put("mail.imaps.ssl.enable", "true");
				props.put("mail.imaps.ssl.trust", "*");
			} else {
				props.put("mail.store.protocol", "imap");
				props.put("mail.imap.host", host);
				props.put("mail.imap.port", String.valueOf(port));
			}
			props.put("mail.imap.connectiontimeout", "10000");
			props.put("mail.imap.timeout", "10000");

			// Connect
			Session session = Session.getInstance(props);
			store = session.getStore(useSSL ? "imaps" : "imap");
			store.connect(host, port, username, password);

			// Open folder
			folder = store.getFolder(mailbox);
			int openMode = markAsRead ? Folder.READ_WRITE : Folder.READ_ONLY;
			folder.open(openMode);

			// Get last poll timestamp
			long lastPollTimestamp = staticData.containsKey("lastPollTimestamp")
				? ((Number) staticData.get("lastPollTimestamp")).longValue()
				: System.currentTimeMillis() - (5 * 60 * 1000); // 5 minutes ago on first poll

			// Search for messages since last poll
			Date sinceDate = new Date(lastPollTimestamp);
			ReceivedDateTerm dateTerm = new ReceivedDateTerm(ComparisonTerm.GE, sinceDate);
			Message[] messages = folder.search(dateTerm);

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTimestamp", System.currentTimeMillis());

			if (messages == null || messages.length == 0) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			// Limit messages
			int count = Math.min(messages.length, maxMessages);
			List<Map<String, Object>> items = new ArrayList<>();

			for (int i = 0; i < count; i++) {
				Message msg = messages[messages.length - 1 - i]; // newest first
				try {
					Map<String, Object> emailData = extractEmailData(msg);
					emailData.put("_triggerTimestamp", System.currentTimeMillis());
					items.add(wrapInJson(emailData));

					// Mark as read if requested
					if (markAsRead) {
						msg.setFlag(jakarta.mail.Flags.Flag.SEEN, true);
					}
				} catch (Exception e) {
					log.warn("Failed to process email message: {}", e.getMessage());
				}
			}

			log.debug("IMAP trigger: found {} new emails", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "IMAP email error: " + e.getMessage(), e);
		} finally {
			try {
				if (folder != null && folder.isOpen()) folder.close(false);
				if (store != null && store.isConnected()) store.close();
			} catch (Exception ignore) {
				// Ignore cleanup errors
			}
		}
	}

	// ========================= Helpers =========================

	private Map<String, Object> extractEmailData(Message message) throws Exception {
		Map<String, Object> data = new LinkedHashMap<>();

		data.put("messageNumber", message.getMessageNumber());
		data.put("subject", message.getSubject());
		data.put("from", message.getFrom() != null ? Arrays.toString(message.getFrom()) : "");
		data.put("to", message.getAllRecipients() != null ? Arrays.toString(message.getAllRecipients()) : "");
		data.put("sentDate", message.getSentDate() != null ? message.getSentDate().toInstant().toString() : "");
		data.put("receivedDate", message.getReceivedDate() != null ? message.getReceivedDate().toInstant().toString() : "");
		data.put("contentType", message.getContentType());

		// Extract text body
		String textBody = getTextContent(message);
		data.put("body", textBody);

		// Message ID
		if (message instanceof MimeMessage) {
			data.put("messageId", ((MimeMessage) message).getMessageID());
		}

		return data;
	}

	private String getTextContent(Message message) {
		try {
			Object content = message.getContent();
			if (content instanceof String) {
				return (String) content;
			}
			if (content instanceof MimeMultipart) {
				return getTextFromMultipart((MimeMultipart) content);
			}
			return content != null ? content.toString() : "";
		} catch (Exception e) {
			return "(unable to read content: " + e.getMessage() + ")";
		}
	}

	private String getTextFromMultipart(MimeMultipart multipart) throws Exception {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < multipart.getCount(); i++) {
			var bodyPart = multipart.getBodyPart(i);
			if (bodyPart.isMimeType("text/plain")) {
				sb.append(bodyPart.getContent().toString());
			} else if (bodyPart.isMimeType("text/html") && sb.isEmpty()) {
				sb.append(bodyPart.getContent().toString());
			} else if (bodyPart.getContent() instanceof MimeMultipart) {
				sb.append(getTextFromMultipart((MimeMultipart) bodyPart.getContent()));
			}
		}
		return sb.toString();
	}
}
