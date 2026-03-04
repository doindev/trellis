package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * UptimeRobot — monitor website uptime using the UptimeRobot API.
 * Note: UptimeRobot API uses POST for all operations.
 */
@Node(
		type = "uptimeRobot",
		displayName = "UptimeRobot",
		description = "Monitor website uptime with UptimeRobot",
		category = "Miscellaneous",
		icon = "uptimeRobot",
		credentials = {"uptimeRobotApi"},
		searchOnly = true
)
public class UptimeRobotNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.uptimerobot.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String resource = context.getParameter("resource", "monitor");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = Map.of("Content-Type", "application/json", "Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "account" -> {
						Map<String, Object> body = Map.of("api_key", apiKey);
						HttpResponse<String> response = post(BASE_URL + "/getAccountDetails", body, headers);
						yield parseResponse(response);
					}
					case "monitor" -> handleMonitor(context, apiKey, headers, operation);
					case "alertContact" -> handleAlertContact(context, apiKey, headers, operation);
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

	private Map<String, Object> handleMonitor(NodeExecutionContext context, String apiKey, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("api_key", apiKey);
				body.put("friendly_name", context.getParameter("friendlyName", ""));
				body.put("url", context.getParameter("url", ""));
				body.put("type", toInt(context.getParameters().get("monitorType"), 1));
				int interval = toInt(context.getParameters().get("interval"), 300);
				if (interval > 0) body.put("interval", interval);
				HttpResponse<String> response = post(BASE_URL + "/newMonitor", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				Map<String, Object> body = Map.of("api_key", apiKey, "id", context.getParameter("monitorId", ""));
				HttpResponse<String> response = post(BASE_URL + "/deleteMonitor", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				Map<String, Object> body = Map.of("api_key", apiKey, "monitors", context.getParameter("monitorId", ""));
				HttpResponse<String> response = post(BASE_URL + "/getMonitors", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				Map<String, Object> body = Map.of("api_key", apiKey, "limit", limit);
				HttpResponse<String> response = post(BASE_URL + "/getMonitors", body, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("api_key", apiKey);
				body.put("id", context.getParameter("monitorId", ""));
				String friendlyName = context.getParameter("friendlyName", "");
				if (!friendlyName.isEmpty()) body.put("friendly_name", friendlyName);
				String monitorUrl = context.getParameter("url", "");
				if (!monitorUrl.isEmpty()) body.put("url", monitorUrl);
				HttpResponse<String> response = post(BASE_URL + "/editMonitor", body, headers);
				yield parseResponse(response);
			}
			case "reset" -> {
				Map<String, Object> body = Map.of("api_key", apiKey, "id", context.getParameter("monitorId", ""));
				HttpResponse<String> response = post(BASE_URL + "/resetMonitor", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown monitor operation: " + operation);
		};
	}

	private Map<String, Object> handleAlertContact(NodeExecutionContext context, String apiKey, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("api_key", apiKey);
				body.put("friendly_name", context.getParameter("friendlyName", ""));
				body.put("type", toInt(context.getParameters().get("alertType"), 2));
				body.put("value", context.getParameter("alertValue", ""));
				HttpResponse<String> response = post(BASE_URL + "/newAlertContact", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				Map<String, Object> body = Map.of("api_key", apiKey, "id", context.getParameter("alertContactId", ""));
				HttpResponse<String> response = post(BASE_URL + "/deleteAlertContact", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				Map<String, Object> body = Map.of("api_key", apiKey);
				HttpResponse<String> response = post(BASE_URL + "/getAlertContacts", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown alert contact operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("monitor")
						.options(List.of(
								ParameterOption.builder().name("Account").value("account").build(),
								ParameterOption.builder().name("Alert Contact").value("alertContact").build(),
								ParameterOption.builder().name("Monitor").value("monitor").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Reset").value("reset").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("monitorId").displayName("Monitor ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("friendlyName").displayName("Friendly Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("monitorType").displayName("Monitor Type")
						.type(ParameterType.OPTIONS).defaultValue("1")
						.options(List.of(
								ParameterOption.builder().name("HTTP(S)").value("1").build(),
								ParameterOption.builder().name("Keyword").value("2").build(),
								ParameterOption.builder().name("Ping").value("3").build(),
								ParameterOption.builder().name("Port").value("4").build(),
								ParameterOption.builder().name("Heartbeat").value("5").build()
						)).build(),
				NodeParameter.builder()
						.name("interval").displayName("Interval (seconds)")
						.type(ParameterType.NUMBER).defaultValue(300).build(),
				NodeParameter.builder()
						.name("alertContactId").displayName("Alert Contact ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("alertType").displayName("Alert Type")
						.type(ParameterType.OPTIONS).defaultValue("2")
						.options(List.of(
								ParameterOption.builder().name("SMS").value("1").build(),
								ParameterOption.builder().name("E-Mail").value("2").build(),
								ParameterOption.builder().name("Webhook").value("5").build()
						)).build(),
				NodeParameter.builder()
						.name("alertValue").displayName("Alert Value")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email, phone, or webhook URL.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max results to return.").build()
		);
	}
}
