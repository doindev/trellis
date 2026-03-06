package io.cwc.nodes.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP Poll Trigger Node - polls an HTTP endpoint at intervals and triggers on change.
 * Uses staticData to store the hash of the previous response for change detection.
 */
@Slf4j
@Node(
	type = "httpPollTrigger",
	displayName = "HTTP Poll Trigger",
	description = "Poll an HTTP endpoint at intervals and trigger when the response changes.",
	category = "Core Triggers",
	icon = "radar",
	trigger = true,
	polling = true
)
public class HttpPollTriggerNode extends AbstractTriggerNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("url")
				.displayName("URL")
				.description("The URL to poll.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("https://api.example.com/data")
				.build(),

			NodeParameter.builder()
				.name("method")
				.displayName("HTTP Method")
				.description("The HTTP method to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("GET")
				.options(List.of(
					ParameterOption.builder().name("GET").value("GET").build(),
					ParameterOption.builder().name("POST").value("POST").build()
				))
				.build(),

			NodeParameter.builder()
				.name("headers")
				.displayName("Headers")
				.description("Custom HTTP headers.")
				.type(ParameterType.FIXED_COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("name")
						.displayName("Header Name")
						.type(ParameterType.STRING)
						.build(),
					NodeParameter.builder()
						.name("value")
						.displayName("Header Value")
						.type(ParameterType.STRING)
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("body")
				.displayName("Request Body")
				.description("The request body for POST requests.")
				.type(ParameterType.JSON)
				.displayOptions(Map.of("show", Map.of("method", List.of("POST"))))
				.build(),

			NodeParameter.builder()
				.name("pollInterval")
				.displayName("Poll Interval (seconds)")
				.description("How often to poll the endpoint.")
				.type(ParameterType.NUMBER)
				.defaultValue(60)
				.minValue(5)
				.isNodeSetting(true)
				.build(),

			NodeParameter.builder()
				.name("triggerOn")
				.displayName("Trigger On")
				.description("When to trigger the workflow.")
				.type(ParameterType.OPTIONS)
				.defaultValue("always")
				.options(List.of(
					ParameterOption.builder().name("Always").value("always")
						.description("Trigger on every poll").build(),
					ParameterOption.builder().name("Response Changed").value("responseChanged")
						.description("Trigger only when the response body changes").build(),
					ParameterOption.builder().name("Status Code Changed").value("statusCodeChanged")
						.description("Trigger only when the HTTP status code changes").build(),
					ParameterOption.builder().name("JSON Field Changed").value("jsonFieldChanged")
						.description("Trigger only when a specific JSON field changes").build()
				))
				.build(),

			NodeParameter.builder()
				.name("jsonField")
				.displayName("JSON Field")
				.description("The JSON field to monitor for changes (dot notation supported).")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("triggerOn", List.of("jsonFieldChanged"))))
				.placeHolder("data.lastModified")
				.build(),

			NodeParameter.builder()
				.name("authentication")
				.displayName("Authentication")
				.description("Authentication method to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("none")
				.options(List.of(
					ParameterOption.builder().name("None").value("none").build(),
					ParameterOption.builder().name("Basic Auth").value("basicAuth").build(),
					ParameterOption.builder().name("Bearer Token").value("bearerToken").build(),
					ParameterOption.builder().name("API Key").value("apiKey").build()
				))
				.build(),

			NodeParameter.builder()
				.name("username")
				.displayName("Username")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("authentication", List.of("basicAuth"))))
				.build(),

			NodeParameter.builder()
				.name("password")
				.displayName("Password")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("authentication", List.of("basicAuth"))))
				.build(),

			NodeParameter.builder()
				.name("token")
				.displayName("Token")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("authentication", List.of("bearerToken"))))
				.build(),

			NodeParameter.builder()
				.name("apiKeyName")
				.displayName("API Key Header Name")
				.type(ParameterType.STRING)
				.defaultValue("X-API-Key")
				.displayOptions(Map.of("show", Map.of("authentication", List.of("apiKey"))))
				.build(),

			NodeParameter.builder()
				.name("apiKeyValue")
				.displayName("API Key Value")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("authentication", List.of("apiKey"))))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String url = context.getParameter("url", "");
		if (url == null || url.isEmpty()) {
			return NodeExecutionResult.error("URL is required");
		}

		String method = context.getParameter("method", "GET");
		String triggerOn = context.getParameter("triggerOn", "always");

		try {
			// Build HTTP request
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(30));

			// Add authentication headers
			addAuthentication(requestBuilder, context);

			// Add custom headers
			Object headersObj = context.getParameter("headers", null);
			if (headersObj instanceof List) {
				for (Object h : (List<?>) headersObj) {
					if (h instanceof Map) {
						Map<String, Object> header = (Map<String, Object>) h;
						String name = toString(header.get("name"));
						String value = toString(header.get("value"));
						if (name != null && !name.isEmpty()) {
							requestBuilder.header(name, value != null ? value : "");
						}
					}
				}
			}

			// Set method and body
			if ("POST".equals(method)) {
				String body = context.getParameter("body", "");
				requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
				requestBuilder.header("Content-Type", "application/json");
			} else {
				requestBuilder.GET();
			}

			HttpResponse<String> response = HTTP_CLIENT.send(
				requestBuilder.build(),
				HttpResponse.BodyHandlers.ofString()
			);

			String responseBody = response.body();
			int statusCode = response.statusCode();

			// Check if we should trigger
			Map<String, Object> staticData = context.getWorkflowStaticData();
			String nodeKey = "httpPoll_" + context.getNodeId();

			boolean shouldTrigger = switch (triggerOn) {
				case "responseChanged" -> {
					String currentHash = hash(responseBody);
					String previousHash = (String) staticData.get(nodeKey + "_bodyHash");
					staticData.put(nodeKey + "_bodyHash", currentHash);
					yield previousHash == null || !previousHash.equals(currentHash);
				}
				case "statusCodeChanged" -> {
					Object prevStatus = staticData.get(nodeKey + "_statusCode");
					staticData.put(nodeKey + "_statusCode", statusCode);
					yield prevStatus == null || !String.valueOf(prevStatus).equals(String.valueOf(statusCode));
				}
				case "jsonFieldChanged" -> {
					String jsonField = context.getParameter("jsonField", "");
					try {
						Map<String, Object> jsonResponse = MAPPER.readValue(
							responseBody, new TypeReference<Map<String, Object>>() {});
						Object fieldValue = resolveField(jsonResponse, jsonField);
						String currentValue = fieldValue != null ? String.valueOf(fieldValue) : "";
						String previousValue = (String) staticData.get(nodeKey + "_fieldValue");
						staticData.put(nodeKey + "_fieldValue", currentValue);
						yield previousValue == null || !previousValue.equals(currentValue);
					} catch (Exception e) {
						log.warn("Could not parse JSON response for field comparison", e);
						yield true;
					}
				}
				default -> true; // "always"
			};

			if (!shouldTrigger) {
				log.debug("HTTP poll trigger: no change detected for {}", url);
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(staticData)
					.build();
			}

			// Build trigger output
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("statusCode", statusCode);
			triggerData.put("url", url);
			triggerData.put("method", method);

			// Try to parse response as JSON
			try {
				Object jsonBody = MAPPER.readValue(responseBody, Object.class);
				triggerData.put("body", jsonBody);
			} catch (Exception e) {
				triggerData.put("body", responseBody);
			}

			// Add response headers
			Map<String, String> responseHeaders = new LinkedHashMap<>();
			response.headers().map().forEach((key, values) -> {
				if (values != null && !values.isEmpty()) {
					responseHeaders.put(key, values.get(0));
				}
			});
			triggerData.put("headers", responseHeaders);

			Map<String, Object> triggerItem = createTriggerItem(triggerData);

			log.debug("HTTP poll trigger fired for {}: status={}", url, statusCode);

			return NodeExecutionResult.builder()
				.output(List.of(List.of(triggerItem)))
				.staticData(staticData)
				.build();

		} catch (Exception e) {
			return NodeExecutionResult.error("HTTP poll failed: " + e.getMessage(), e);
		}
	}

	private void addAuthentication(HttpRequest.Builder builder, NodeExecutionContext context) {
		String authType = context.getParameter("authentication", "none");

		switch (authType) {
			case "basicAuth": {
				String username = context.getParameter("username", "");
				String password = context.getParameter("password", "");
				String credentials = Base64.getEncoder().encodeToString(
					(username + ":" + password).getBytes(StandardCharsets.UTF_8));
				builder.header("Authorization", "Basic " + credentials);
				break;
			}
			case "bearerToken": {
				String token = context.getParameter("token", "");
				builder.header("Authorization", "Bearer " + token);
				break;
			}
			case "apiKey": {
				String headerName = context.getParameter("apiKeyName", "X-API-Key");
				String headerValue = context.getParameter("apiKeyValue", "");
				builder.header(headerName, headerValue);
				break;
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Object resolveField(Map<String, Object> data, String field) {
		if (field == null || field.isEmpty()) return data;

		String[] parts = field.split("\\.");
		Object current = data;
		for (String part : parts) {
			if (current instanceof Map) {
				current = ((Map<String, Object>) current).get(part);
			} else {
				return null;
			}
		}
		return current;
	}

	private String hash(String content) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return String.valueOf(content.hashCode());
		}
	}
}
