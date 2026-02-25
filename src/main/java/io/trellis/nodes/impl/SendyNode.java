package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sendy — manage subscribers and campaigns via a self-hosted Sendy instance.
 * Uses form-encoded POST requests with api_key in body.
 */
@Node(
		type = "sendy",
		displayName = "Sendy",
		description = "Manage subscribers and campaigns via Sendy",
		category = "Marketing",
		icon = "envelope",
		credentials = {"sendyApi"}
)
public class SendyNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String baseUrl = context.getCredentialString("url", "");
			String apiKey = context.getCredentialString("apiKey", "");
			String resource = context.getParameter("resource", "subscriber");
			String operation = context.getParameter("operation", "add");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, String> formData = new LinkedHashMap<>();
					formData.put("api_key", apiKey);
					formData.put("boolean", "true");

					String endpoint;
					if ("campaign".equals(resource)) {
						endpoint = baseUrl + "/api/campaigns/create.php";
						formData.put("from_name", context.getParameter("fromName", ""));
						formData.put("from_email", context.getParameter("fromEmail", ""));
						formData.put("reply_to", context.getParameter("replyTo", ""));
						formData.put("title", context.getParameter("title", ""));
						formData.put("subject", context.getParameter("subject", ""));
						formData.put("html_text", context.getParameter("htmlText", ""));
						boolean sendCampaign = toBoolean(context.getParameters().get("sendCampaign"), false);
						formData.put("send_campaign", sendCampaign ? "1" : "0");
						if (!sendCampaign) {
							formData.put("brand_id", context.getParameter("brandId", ""));
						}
					} else {
						// subscriber operations
						switch (operation) {
							case "add" -> {
								endpoint = baseUrl + "/subscribe";
								formData.put("email", context.getParameter("email", ""));
								formData.put("list", context.getParameter("listId", ""));
							}
							case "count" -> {
								endpoint = baseUrl + "/api/subscribers/active-subscriber-count.php";
								formData.put("list_id", context.getParameter("listId", ""));
							}
							case "delete" -> {
								endpoint = baseUrl + "/api/subscribers/delete.php";
								formData.put("email", context.getParameter("email", ""));
								formData.put("list_id", context.getParameter("listId", ""));
							}
							case "remove" -> {
								endpoint = baseUrl + "/unsubscribe";
								formData.put("email", context.getParameter("email", ""));
								formData.put("list", context.getParameter("listId", ""));
							}
							case "status" -> {
								endpoint = baseUrl + "/api/subscribers/subscription-status.php";
								formData.put("email", context.getParameter("email", ""));
								formData.put("list_id", context.getParameter("listId", ""));
							}
							default -> endpoint = baseUrl + "/subscribe";
						}
					}

					// Send form-encoded request
					StringBuilder formBody = new StringBuilder();
					for (var entry : formData.entrySet()) {
						if (formBody.length() > 0) formBody.append("&");
						formBody.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
								.append("=")
								.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
					}

					HttpRequest request = HttpRequest.newBuilder()
							.uri(URI.create(endpoint))
							.header("Content-Type", "application/x-www-form-urlencoded")
							.POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
							.build();
					HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					results.add(wrapInJson(Map.of("response", response.body())));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("subscriber")
						.options(List.of(
								ParameterOption.builder().name("Subscriber").value("subscriber").build(),
								ParameterOption.builder().name("Campaign").value("campaign").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("add")
						.options(List.of(
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Count").value("count").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Status").value("status").build()
						)).build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("fromName").displayName("From Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("fromEmail").displayName("From Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("replyTo").displayName("Reply To")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("htmlText").displayName("HTML Text")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sendCampaign").displayName("Send Campaign")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("brandId").displayName("Brand ID")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
