package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * SIGNL4 — send and resolve alerts via the SIGNL4 mobile alerting platform.
 */
@Node(
		type = "signl4",
		displayName = "SIGNL4",
		description = "Send and resolve alerts with SIGNL4",
		category = "Communication / Chat & Messaging",
		icon = "signl4",
		credentials = {"signl4Api"}
)
public class Signl4Node extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String teamSecret = context.getCredentialString("teamSecret", "");
		String operation = context.getParameter("operation", "send");

		String webhookUrl = "https://connect.signl4.com/webhook/" + encode(teamSecret);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "send" -> handleSend(context, webhookUrl);
					case "resolve" -> handleResolve(context, webhookUrl);
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

	private Map<String, Object> handleSend(NodeExecutionContext context, String webhookUrl) throws Exception {
		String message = context.getParameter("message", "");
		String title = context.getParameter("title", "");
		String service = context.getParameter("service", "");
		String alertingScenario = context.getParameter("alertingScenario", "");
		boolean filtering = toBoolean(context.getParameters().get("filtering"), false);
		String externalId = context.getParameter("externalId", "");
		String latitude = context.getParameter("latitude", "");
		String longitude = context.getParameter("longitude", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("X-S4-Status", "new");
		headers.put("X-S4-SourceSystem", "n8n");

		if (!alertingScenario.isBlank()) {
			headers.put("X-S4-AlertingScenario", alertingScenario);
		}
		if (filtering) {
			headers.put("X-S4-Filtering", "true");
		}
		if (!externalId.isBlank()) {
			headers.put("X-S4-ExternalID", externalId);
		}
		if (!latitude.isBlank() && !longitude.isBlank()) {
			headers.put("X-S4-Location", latitude + "," + longitude);
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("message", message);
		if (!title.isBlank()) {
			body.put("title", title);
		}
		if (!service.isBlank()) {
			body.put("service", service);
		}

		HttpResponse<String> response = post(webhookUrl, body, headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("statusCode", response.statusCode());
		if (response.body() != null && !response.body().isBlank()) {
			result.put("response", response.body());
		}
		return result;
	}

	private Map<String, Object> handleResolve(NodeExecutionContext context, String webhookUrl) throws Exception {
		String externalId = context.getParameter("externalId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("X-S4-Status", "resolved");
		headers.put("X-S4-SourceSystem", "n8n");
		headers.put("X-S4-ExternalID", externalId);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("externalId", externalId);

		HttpResponse<String> response = post(webhookUrl, body, headers);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("statusCode", response.statusCode());
		result.put("externalId", externalId);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Resolve").value("resolve").build()
						)).build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("The alert message body.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Alert title.").build(),
				NodeParameter.builder()
						.name("service").displayName("Service")
						.type(ParameterType.STRING).defaultValue("")
						.description("Service or system name.").build(),
				NodeParameter.builder()
						.name("externalId").displayName("External ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("External reference ID for linking/resolving alerts.").build(),
				NodeParameter.builder()
						.name("alertingScenario").displayName("Alerting Scenario")
						.type(ParameterType.OPTIONS)
						.defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Default").value("").build(),
								ParameterOption.builder().name("Single Ack").value("single_ack").build(),
								ParameterOption.builder().name("Multi Ack").value("multi_ack").build()
						)).build(),
				NodeParameter.builder()
						.name("filtering").displayName("Filtering")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Enable alert filtering.").build(),
				NodeParameter.builder()
						.name("latitude").displayName("Latitude")
						.type(ParameterType.STRING).defaultValue("")
						.description("Location latitude.").build(),
				NodeParameter.builder()
						.name("longitude").displayName("Longitude")
						.type(ParameterType.STRING).defaultValue("")
						.description("Location longitude.").build()
		);
	}
}
