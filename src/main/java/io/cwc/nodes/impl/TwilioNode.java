package io.cwc.nodes.impl;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Twilio — send SMS, MMS and WhatsApp messages and make voice calls
 * via the Twilio REST API.
 */
@Node(
		type = "twilio",
		displayName = "Twilio",
		description = "Send SMS and make calls with Twilio",
		category = "Communication",
		icon = "twilio",
		credentials = {"twilioApi"}
)
public class TwilioNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accountSid = context.getCredentialString("accountSid", "");
		String authToken = context.getCredentialString("authToken", "");
		String resource = context.getParameter("resource", "sms");
		String operation = context.getParameter("operation", "send");

		String baseUrl = "https://api.twilio.com/2010-04-01/Accounts/" + encode(accountSid);

		// Basic auth header
		String credentials = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "sms" -> handleSms(context, operation, baseUrl, headers);
					case "call" -> handleCall(context, operation, baseUrl, headers);
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

	// ========================= SMS =========================

	private Map<String, Object> handleSms(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if (!"send".equals(operation)) {
			throw new IllegalArgumentException("Unknown SMS operation: " + operation);
		}

		String from = context.getParameter("from", "");
		String to = context.getParameter("to", "");
		String body = context.getParameter("body", "");
		boolean useWhatsApp = toBoolean(context.getParameters().get("useWhatsApp"), false);
		String statusCallback = context.getParameter("statusCallback", "");
		String mediaUrl = context.getParameter("mediaUrl", "");

		// Prefix with whatsapp: if using WhatsApp
		if (useWhatsApp) {
			if (!from.startsWith("whatsapp:")) from = "whatsapp:" + from;
			if (!to.startsWith("whatsapp:")) to = "whatsapp:" + to;
		}

		// Twilio Messages API uses form-encoded POST
		StringBuilder formBody = new StringBuilder();
		formBody.append("From=").append(encode(from));
		formBody.append("&To=").append(encode(to));
		formBody.append("&Body=").append(encode(body));

		if (!statusCallback.isBlank()) {
			formBody.append("&StatusCallback=").append(encode(statusCallback));
		}
		if (!mediaUrl.isBlank()) {
			formBody.append("&MediaUrl=").append(encode(mediaUrl));
		}

		headers.put("Content-Type", "application/x-www-form-urlencoded");

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/Messages.json"))
				.header("Authorization", headers.get("Authorization"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		return parseResponse(response);
	}

	// ========================= Call =========================

	private Map<String, Object> handleCall(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if (!"make".equals(operation)) {
			throw new IllegalArgumentException("Unknown call operation: " + operation);
		}

		String from = context.getParameter("from", "");
		String to = context.getParameter("to", "");
		String twiml = context.getParameter("twiml", "");
		String url = context.getParameter("url", "");
		String statusCallback = context.getParameter("statusCallback", "");
		boolean useWhatsApp = toBoolean(context.getParameters().get("useWhatsApp"), false);

		if (useWhatsApp) {
			if (!from.startsWith("whatsapp:")) from = "whatsapp:" + from;
			if (!to.startsWith("whatsapp:")) to = "whatsapp:" + to;
		}

		// Twilio Calls API uses form-encoded POST
		StringBuilder formBody = new StringBuilder();
		formBody.append("From=").append(encode(from));
		formBody.append("&To=").append(encode(to));

		if (!twiml.isBlank()) {
			formBody.append("&Twiml=").append(encode(twiml));
		} else if (!url.isBlank()) {
			formBody.append("&Url=").append(encode(url));
		}

		if (!statusCallback.isBlank()) {
			formBody.append("&StatusCallback=").append(encode(statusCallback));
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/Calls.json"))
				.header("Authorization", headers.get("Authorization"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("sms")
						.options(List.of(
								ParameterOption.builder().name("SMS").value("sms").build(),
								ParameterOption.builder().name("Call").value("call").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Make").value("make").build()
						)).build(),
				NodeParameter.builder()
						.name("from").displayName("From")
						.type(ParameterType.STRING).defaultValue("")
						.description("The Twilio phone number to send from.").build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.description("The phone number to send to.").build(),
				NodeParameter.builder()
						.name("body").displayName("Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("The SMS message body.").build(),
				NodeParameter.builder()
						.name("twiml").displayName("TwiML")
						.type(ParameterType.STRING).defaultValue("")
						.description("TwiML instructions for the call.").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL that returns TwiML instructions for the call.").build(),
				NodeParameter.builder()
						.name("useWhatsApp").displayName("Use WhatsApp")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Send via WhatsApp (prefixes numbers with 'whatsapp:').").build(),
				NodeParameter.builder()
						.name("statusCallback").displayName("Status Callback URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL for status callback notifications.").build(),
				NodeParameter.builder()
						.name("mediaUrl").displayName("Media URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of media to include in the message (MMS).").build()
		);
	}
}
