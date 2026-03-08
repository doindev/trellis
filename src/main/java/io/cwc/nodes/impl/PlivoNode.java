package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Plivo — send SMS, MMS messages and make voice calls via the Plivo API.
 */
@Node(
		type = "plivo",
		displayName = "Plivo",
		description = "Send SMS, MMS and make calls with Plivo",
		category = "Communication / Chat & Messaging",
		icon = "plivo",
		credentials = {"plivoApi"}
)
public class PlivoNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String authId = context.getCredentialString("authId", "");
		String authToken = context.getCredentialString("authToken", "");
		String resource = context.getParameter("resource", "sms");

		String baseUrl = "https://api.plivo.com/v1/Account/" + encode(authId);

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		// Basic auth
		String credentials = Base64.getEncoder().encodeToString((authId + ":" + authToken).getBytes());
		headers.put("Authorization", "Basic " + credentials);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "sms" -> handleSms(context, baseUrl, headers);
					case "mms" -> handleMms(context, baseUrl, headers);
					case "call" -> handleCall(context, baseUrl, headers);
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

	private Map<String, Object> handleSms(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String from = context.getParameter("from", "");
		String to = context.getParameter("to", "");
		String message = context.getParameter("message", "");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("src", from);
		body.put("dst", to);
		body.put("text", message);

		HttpResponse<String> response = post(baseUrl + "/Message/", body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleMms(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String from = context.getParameter("from", "");
		String to = context.getParameter("to", "");
		String message = context.getParameter("message", "");
		String mediaUrls = context.getParameter("mediaUrls", "");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("src", from);
		body.put("dst", to);
		body.put("text", message);
		body.put("type", "mms");

		if (!mediaUrls.isBlank()) {
			List<String> urls = new ArrayList<>();
			for (String url : mediaUrls.split(",")) {
				String trimmed = url.trim();
				if (!trimmed.isEmpty()) {
					urls.add(trimmed);
				}
			}
			body.put("media_urls", urls);
		}

		HttpResponse<String> response = post(baseUrl + "/Message/", body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleCall(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String from = context.getParameter("from", "");
		String to = context.getParameter("to", "");
		String answerUrl = context.getParameter("answerUrl", "");
		String answerMethod = context.getParameter("answerMethod", "POST");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", from);
		body.put("to", to);
		body.put("answer_url", answerUrl);
		body.put("answer_method", answerMethod);

		HttpResponse<String> response = post(baseUrl + "/Call/", body, headers);
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
								ParameterOption.builder().name("MMS").value("mms").build(),
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
						.description("Plivo phone number to send from.").build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Phone number to send to.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message content.").build(),
				NodeParameter.builder()
						.name("mediaUrls").displayName("Media URLs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of media URLs (MMS only).").build(),
				NodeParameter.builder()
						.name("answerUrl").displayName("Answer URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL invoked by Plivo when call is answered (must return XML).").build(),
				NodeParameter.builder()
						.name("answerMethod").displayName("Answer Method")
						.type(ParameterType.OPTIONS)
						.defaultValue("POST")
						.options(List.of(
								ParameterOption.builder().name("GET").value("GET").build(),
								ParameterOption.builder().name("POST").value("POST").build()
						)).build()
		);
	}
}
