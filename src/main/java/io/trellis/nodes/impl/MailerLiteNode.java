package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * MailerLite — manage subscribers via the MailerLite API.
 */
@Node(
		type = "mailerLite",
		displayName = "MailerLite",
		description = "Manage subscribers in MailerLite",
		category = "Marketing",
		icon = "mailerLite",
		credentials = {"mailerLiteApi"}
)
public class MailerLiteNode extends AbstractApiNode {

	private static final String BASE_URL = "https://connect.mailerlite.com/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> {
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("email", context.getParameter("email", ""));
						String status = context.getParameter("status", "");
						if (!status.isEmpty()) body.put("status", status);
						HttpResponse<String> response = post(BASE_URL + "/subscribers", body, headers);
						yield parseResponse(response);
					}
					case "get" -> {
						String subscriberId = context.getParameter("subscriberId", "");
						HttpResponse<String> response = get(BASE_URL + "/subscribers/" + encode(subscriberId), headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 50);
						String url = BASE_URL + "/subscribers?limit=" + limit;
						String statusFilter = context.getParameter("statusFilter", "");
						if (!statusFilter.isEmpty()) url += "&filter[status]=" + encode(statusFilter);
						HttpResponse<String> response = get(url, headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String subscriberId = context.getParameter("subscriberId", "");
						Map<String, Object> body = new LinkedHashMap<>();
						String status = context.getParameter("status", "");
						if (!status.isEmpty()) body.put("status", status);
						HttpResponse<String> response = put(BASE_URL + "/subscribers/" + encode(subscriberId), body, headers);
						yield parseResponse(response);
					}
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("subscriberId").displayName("Subscriber ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Subscriber email or ID.").build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Active").value("active").build(),
								ParameterOption.builder().name("Unsubscribed").value("unsubscribed").build(),
								ParameterOption.builder().name("Unconfirmed").value("unconfirmed").build(),
								ParameterOption.builder().name("Bounced").value("bounced").build(),
								ParameterOption.builder().name("Junk").value("junk").build()
						)).build(),
				NodeParameter.builder()
						.name("statusFilter").displayName("Status Filter")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter subscribers by status.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max results to return (1-100).").build()
		);
	}
}
