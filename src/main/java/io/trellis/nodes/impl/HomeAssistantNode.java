package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Home Assistant Node -- interact with Home Assistant to call services,
 * get/set entity states, fire events, and render templates.
 */
@Slf4j
@Node(
	type = "homeAssistant",
	displayName = "Home Assistant",
	description = "Interact with Home Assistant to control smart home devices",
	category = "Miscellaneous",
	icon = "homeAssistant",
	credentials = {"homeAssistantApi"}
)
public class HomeAssistantNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getState")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Call Service").value("callService").description("Call a Home Assistant service").build(),
				ParameterOption.builder().name("Get State").value("getState").description("Get the state of an entity").build(),
				ParameterOption.builder().name("Set State").value("setState").description("Set the state of an entity").build(),
				ParameterOption.builder().name("Fire Event").value("fireEvent").description("Fire a Home Assistant event").build(),
				ParameterOption.builder().name("Render Template").value("renderTemplate").description("Render a Jinja2 template").build(),
				ParameterOption.builder().name("Get All States").value("getAllStates").description("Get the state of all entities").build()
			)).build());

		// Call Service parameters
		params.add(NodeParameter.builder()
			.name("domain").displayName("Domain").type(ParameterType.STRING).required(true)
			.placeHolder("light")
			.description("The service domain (e.g., light, switch, automation).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("callService"))))
			.build());

		params.add(NodeParameter.builder()
			.name("service").displayName("Service").type(ParameterType.STRING).required(true)
			.placeHolder("turn_on")
			.description("The service to call (e.g., turn_on, turn_off, toggle).")
			.displayOptions(Map.of("show", Map.of("operation", List.of("callService"))))
			.build());

		params.add(NodeParameter.builder()
			.name("serviceData").displayName("Service Data (JSON)")
			.type(ParameterType.JSON)
			.description("Additional service data as JSON. Example: {\"entity_id\": \"light.living_room\", \"brightness\": 255}")
			.displayOptions(Map.of("show", Map.of("operation", List.of("callService"))))
			.build());

		// Get State / Set State parameters
		params.add(NodeParameter.builder()
			.name("entityId").displayName("Entity ID").type(ParameterType.STRING).required(true)
			.placeHolder("light.living_room")
			.description("The entity ID to query or update.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getState", "setState"))))
			.build());

		// Set State parameters
		params.add(NodeParameter.builder()
			.name("state").displayName("State").type(ParameterType.STRING).required(true)
			.placeHolder("on")
			.description("The new state value for the entity.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("setState"))))
			.build());

		params.add(NodeParameter.builder()
			.name("stateAttributes").displayName("Attributes (JSON)")
			.type(ParameterType.JSON)
			.description("Additional attributes as JSON to set on the entity.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("setState"))))
			.build());

		// Fire Event parameters
		params.add(NodeParameter.builder()
			.name("eventType").displayName("Event Type").type(ParameterType.STRING).required(true)
			.placeHolder("custom_event")
			.description("The type of event to fire.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("fireEvent"))))
			.build());

		params.add(NodeParameter.builder()
			.name("eventData").displayName("Event Data (JSON)")
			.type(ParameterType.JSON)
			.description("Additional event data as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("fireEvent"))))
			.build());

		// Render Template parameters
		params.add(NodeParameter.builder()
			.name("template").displayName("Template").type(ParameterType.STRING).required(true)
			.placeHolder("{{ states('sensor.temperature') }}")
			.description("The Jinja2 template to render.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("renderTemplate"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "getState");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getApiBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (operation) {
				case "callService" -> executeCallService(context, baseUrl, headers);
				case "getState" -> executeGetState(context, baseUrl, headers);
				case "setState" -> executeSetState(context, baseUrl, headers);
				case "fireEvent" -> executeFireEvent(context, baseUrl, headers);
				case "renderTemplate" -> executeRenderTemplate(context, baseUrl, headers);
				case "getAllStates" -> executeGetAllStates(baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Home Assistant API error: " + e.getMessage(), e);
		}
	}

	// ========================= Call Service =========================

	private NodeExecutionResult executeCallService(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String domain = context.getParameter("domain", "");
		String service = context.getParameter("service", "");
		String serviceDataJson = context.getParameter("serviceData", "");

		Map<String, Object> body;
		if (serviceDataJson != null && !serviceDataJson.isBlank()) {
			body = parseJsonObject(serviceDataJson);
		} else {
			body = new LinkedHashMap<>();
		}

		String url = baseUrl + "/services/" + encode(domain) + "/" + encode(service);
		HttpResponse<String> response = post(url, body, headers);
		return toResult(response);
	}

	// ========================= Get State =========================

	private NodeExecutionResult executeGetState(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String entityId = context.getParameter("entityId", "");
		String url = baseUrl + "/states/" + encode(entityId);
		HttpResponse<String> response = get(url, headers);
		return toResult(response);
	}

	// ========================= Set State =========================

	private NodeExecutionResult executeSetState(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String entityId = context.getParameter("entityId", "");
		String state = context.getParameter("state", "");
		String attributesJson = context.getParameter("stateAttributes", "");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("state", state);
		if (attributesJson != null && !attributesJson.isBlank()) {
			Map<String, Object> attributes = parseJsonObject(attributesJson);
			body.put("attributes", attributes);
		}

		String url = baseUrl + "/states/" + encode(entityId);
		HttpResponse<String> response = post(url, body, headers);
		return toResult(response);
	}

	// ========================= Fire Event =========================

	private NodeExecutionResult executeFireEvent(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String eventType = context.getParameter("eventType", "");
		String eventDataJson = context.getParameter("eventData", "");

		Map<String, Object> body;
		if (eventDataJson != null && !eventDataJson.isBlank()) {
			body = parseJsonObject(eventDataJson);
		} else {
			body = new LinkedHashMap<>();
		}

		String url = baseUrl + "/events/" + encode(eventType);
		HttpResponse<String> response = post(url, body, headers);
		return toResult(response);
	}

	// ========================= Render Template =========================

	private NodeExecutionResult executeRenderTemplate(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String template = context.getParameter("template", "");

		Map<String, Object> body = Map.of("template", template);
		String url = baseUrl + "/template";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String rendered = response.body() != null ? response.body() : "";
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("result", rendered))));
	}

	// ========================= Get All States =========================

	private NodeExecutionResult executeGetAllStates(String baseUrl, Map<String, String> headers) throws Exception {
		HttpResponse<String> response = get(baseUrl + "/states", headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		List<Map<String, Object>> items = parseArrayResponse(response);
		if (items.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : items) {
			results.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(results);
	}

	// ========================= Helpers =========================

	private String getApiBaseUrl(Map<String, Object> credentials) {
		String baseUrl = String.valueOf(credentials.getOrDefault("baseUrl", "http://localhost:8123"));
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
		return baseUrl + "/api";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		// Home Assistant can return arrays for service calls
		if (body.trim().startsWith("[")) {
			List<Map<String, Object>> items = parseArrayResponse(response);
			if (items.isEmpty()) {
				return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
			}
			List<Map<String, Object>> results = new ArrayList<>();
			for (Map<String, Object> item : items) {
				results.add(wrapInJson(item));
			}
			return NodeExecutionResult.success(results);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Home Assistant API error (HTTP " + response.statusCode() + "): " + body);
	}
}
