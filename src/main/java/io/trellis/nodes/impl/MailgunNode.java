package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Mailgun — send emails via the Mailgun API.
 */
@Node(
		type = "mailgun",
		displayName = "Mailgun",
		description = "Send emails using the Mailgun API",
		category = "Communication / Email",
		icon = "mailgun",
		credentials = {"mailgunApi"}
)
public class MailgunNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiDomain = context.getCredentialString("apiDomain", "api.mailgun.net");
		String emailDomain = context.getCredentialString("emailDomain", "");
		String apiKey = context.getCredentialString("apiKey", "");

		String fromEmail = context.getParameter("fromEmail", "");
		String toEmail = context.getParameter("toEmail", "");
		String ccEmail = context.getParameter("ccEmail", "");
		String bccEmail = context.getParameter("bccEmail", "");
		String subject = context.getParameter("subject", "");
		String text = context.getParameter("text", "");
		String html = context.getParameter("html", "");

		String url = "https://" + apiDomain + "/v3/" + emailDomain + "/messages";

		// Basic auth: api:{apiKey}
		String credentials = Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes());

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// Build form body
				StringBuilder formBody = new StringBuilder();
				formBody.append("from=").append(encode(fromEmail));
				formBody.append("&to=").append(encode(toEmail));
				if (!ccEmail.isBlank()) formBody.append("&cc=").append(encode(ccEmail));
				if (!bccEmail.isBlank()) formBody.append("&bcc=").append(encode(bccEmail));
				if (!subject.isBlank()) formBody.append("&subject=").append(encode(subject));
				if (!text.isBlank()) formBody.append("&text=").append(encode(text));
				if (!html.isBlank()) formBody.append("&html=").append(encode(html));

				java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
						.uri(java.net.URI.create(url))
						.header("Authorization", "Basic " + credentials)
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
						.name("fromEmail").displayName("From Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address of the sender.").build(),
				NodeParameter.builder()
						.name("toEmail").displayName("To Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address(es) of the recipient(s), comma-separated.").build(),
				NodeParameter.builder()
						.name("ccEmail").displayName("CC")
						.type(ParameterType.STRING).defaultValue("")
						.description("CC recipient(s).").build(),
				NodeParameter.builder()
						.name("bccEmail").displayName("BCC")
						.type(ParameterType.STRING).defaultValue("")
						.description("BCC recipient(s).").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email subject line.").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Plain text body of the email.").build(),
				NodeParameter.builder()
						.name("html").displayName("HTML")
						.type(ParameterType.STRING).defaultValue("")
						.description("HTML body of the email.").build()
		);
	}
}
