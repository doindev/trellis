package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Customer.io — track customers and events via the Customer.io Tracking API.
 */
@Slf4j
@Node(
	type = "customerIo",
	displayName = "Customer.io",
	description = "Track customers and events in Customer.io",
	category = "Marketing",
	icon = "customerIo",
	credentials = {"customerIoApi"},
	searchOnly = true
)
public class CustomerIoNode extends AbstractApiNode {

	private static final String TRACKING_BASE_URL = "https://track.customer.io/api/v1";
	private static final String BETA_BASE_URL = "https://beta-api.customer.io/v1/api";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("customer")
			.options(List.of(
				ParameterOption.builder().name("Customer").value("customer").build(),
				ParameterOption.builder().name("Event").value("event").build(),
				ParameterOption.builder().name("Segment").value("segment").build(),
				ParameterOption.builder().name("Campaign").value("campaign").build()
			)).build());

		// Customer operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("upsert")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"))))
			.options(List.of(
				ParameterOption.builder().name("Upsert").value("upsert").description("Create or update a customer").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a customer").build()
			)).build());

		// Event operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("track")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.options(List.of(
				ParameterOption.builder().name("Track").value("track").description("Track an event for a customer").build(),
				ParameterOption.builder().name("Track Anonymous").value("trackAnonymous").description("Track an anonymous event").build()
			)).build());

		// Segment operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("segment"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").description("Add customers to a segment").build(),
				ParameterOption.builder().name("Remove").value("remove").description("Remove customers from a segment").build()
			)).build());

		// Campaign operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a campaign").build(),
				ParameterOption.builder().name("Get All").value("getAll").description("Get all campaigns").build(),
				ParameterOption.builder().name("Get Metrics").value("getMetrics").description("Get campaign metrics").build()
			)).build());

		// Customer fields
		params.add(NodeParameter.builder()
			.name("customerId").displayName("Customer ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("upsert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.STRING).defaultValue("{}")
			.description("Additional attributes as JSON object")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("upsert"))))
			.build());

		// Event fields
		params.add(NodeParameter.builder()
			.name("eventCustomerId").displayName("Customer ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("track"))))
			.build());

		params.add(NodeParameter.builder()
			.name("eventName").displayName("Event Name")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.build());

		params.add(NodeParameter.builder()
			.name("eventData").displayName("Event Data (JSON)")
			.type(ParameterType.STRING).defaultValue("{}")
			.description("Additional event data as JSON object")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.build());

		// Segment fields
		params.add(NodeParameter.builder()
			.name("segmentId").displayName("Segment ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("segment"))))
			.build());

		params.add(NodeParameter.builder()
			.name("customerIds").displayName("Customer IDs")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.description("Comma-separated list of customer IDs")
			.displayOptions(Map.of("show", Map.of("resource", List.of("segment"))))
			.build());

		// Campaign fields
		params.add(NodeParameter.builder()
			.name("campaignId").displayName("Campaign ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"), "operation", List.of("get", "getMetrics"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String siteId = context.getCredentialString("siteId", "");
			String apiKey = context.getCredentialString("apiKey", "");

			String resource = context.getParameter("resource", "customer");
			String operation = context.getParameter("operation", "upsert");

			Map<String, String> headers = buildAuthHeaders(siteId, apiKey);

			return switch (resource) {
				case "customer" -> executeCustomer(context, headers, operation);
				case "event" -> executeEvent(context, headers, operation);
				case "segment" -> executeSegment(context, headers, operation);
				case "campaign" -> executeCampaign(context, headers, operation);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Customer.io API error: " + e.getMessage(), e);
		}
	}

	// ========================= Customer =========================

	private NodeExecutionResult executeCustomer(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		String customerId = context.getParameter("customerId", "");

		switch (operation) {
			case "upsert": {
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String additionalFields = context.getParameter("additionalFields", "{}");
				try {
					Map<String, Object> extra = objectMapper.readValue(additionalFields, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
					body.putAll(extra);
				} catch (Exception ignored) {
					// ignore invalid JSON
				}
				HttpResponse<String> response = put(TRACKING_BASE_URL + "/customers/" + encode(customerId), body, headers);
				return toResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(TRACKING_BASE_URL + "/customers/" + encode(customerId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown customer operation: " + operation);
		}
	}

	// ========================= Event =========================

	private NodeExecutionResult executeEvent(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		String eventName = context.getParameter("eventName", "");
		String eventDataJson = context.getParameter("eventData", "{}");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", eventName);
		try {
			Map<String, Object> data = objectMapper.readValue(eventDataJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
			body.put("data", data);
		} catch (Exception ignored) {
			body.put("data", Map.of());
		}

		switch (operation) {
			case "track": {
				String customerId = context.getParameter("eventCustomerId", "");
				HttpResponse<String> response = post(TRACKING_BASE_URL + "/customers/" + encode(customerId) + "/events", body, headers);
				return toResult(response);
			}
			case "trackAnonymous": {
				HttpResponse<String> response = post(TRACKING_BASE_URL + "/events", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown event operation: " + operation);
		}
	}

	// ========================= Segment =========================

	private NodeExecutionResult executeSegment(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		String segmentId = context.getParameter("segmentId", "");
		String customerIdsStr = context.getParameter("customerIds", "");
		List<String> customerIds = Arrays.stream(customerIdsStr.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toList();

		Map<String, Object> body = Map.of("ids", customerIds);

		String endpoint = switch (operation) {
			case "add" -> TRACKING_BASE_URL + "/segments/" + encode(segmentId) + "/add_customers";
			case "remove" -> TRACKING_BASE_URL + "/segments/" + encode(segmentId) + "/remove_customers";
			default -> throw new IllegalArgumentException("Unknown segment operation: " + operation);
		};

		HttpResponse<String> response = post(endpoint, body, headers);
		return toResult(response);
	}

	// ========================= Campaign =========================

	private NodeExecutionResult executeCampaign(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "get": {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = get(BETA_BASE_URL + "/campaigns/" + encode(campaignId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BETA_BASE_URL + "/campaigns", headers);
				return toListResult(response, "campaigns");
			}
			case "getMetrics": {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = get(BETA_BASE_URL + "/campaigns/" + encode(campaignId) + "/metrics", headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown campaign operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> buildAuthHeaders(String siteId, String apiKey) {
		String credentials = Base64.getEncoder().encodeToString((siteId + ":" + apiKey).getBytes());
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Customer.io API error (HTTP " + response.statusCode() + "): " + body);
	}
}
