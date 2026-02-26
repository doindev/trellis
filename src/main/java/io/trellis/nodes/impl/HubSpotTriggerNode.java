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
 * HubSpot Trigger Node -- polls for new or updated contacts, companies,
 * or deals in HubSpot CRM.
 */
@Slf4j
@Node(
	type = "hubspotTrigger",
	displayName = "HubSpot Trigger",
	description = "Starts the workflow when contacts, companies, or deals are created or updated in HubSpot",
	category = "CRM & Sales",
	icon = "hubspot",
	trigger = true,
	polling = true,
	credentials = {"hubspotApi"}
)
public class HubSpotTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.hubapi.com";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
				.options(List.of(
					ParameterOption.builder().name("Contact").value("contact").description("Poll for new/updated contacts").build(),
					ParameterOption.builder().name("Company").value("company").description("Poll for new/updated companies").build(),
					ParameterOption.builder().name("Deal").value("deal").description("Poll for new/updated deals").build()
				)).build(),

			NodeParameter.builder()
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("created")
				.options(List.of(
					ParameterOption.builder().name("Created").value("created").description("Trigger when a record is created").build(),
					ParameterOption.builder().name("Updated").value("updated").description("Trigger when a record is updated").build()
				)).build(),

			NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(10)
				.description("Max number of results to return per poll.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String resource = context.getParameter("resource", "contact");
		String event = context.getParameter("event", "created");
		int limit = toInt(context.getParameters().get("limit"), 10);

		try {
			Map<String, String> headers = authHeaders(credentials);
			String objectType = switch (resource) {
				case "contact" -> "contacts";
				case "company" -> "companies";
				case "deal" -> "deals";
				default -> "contacts";
			};

			String sortField = "created".equals(event) ? "-createdate" : "-hs_lastmodifieddate";
			String url = BASE_URL + "/crm/v3/objects/" + objectType + "?limit=" + Math.min(limit, 100) + "&sort=" + encode(sortField);

			HttpResponse<String> response = get(url, headers);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("HubSpot Trigger API error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object results = parsed.get("results");

			if (results instanceof List) {
				List<Map<String, Object>> items = new ArrayList<>();
				for (Object item : (List<?>) results) {
					if (item instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> record = (Map<String, Object>) item;
						Map<String, Object> enriched = new LinkedHashMap<>(record);
						enriched.put("_triggerResource", resource);
						enriched.put("_triggerEvent", event);
						enriched.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(enriched));
					}
				}
				return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
			}

			// If no results array, return the full response
			Map<String, Object> triggerData = new LinkedHashMap<>(parsed);
			triggerData.put("_triggerResource", resource);
			triggerData.put("_triggerEvent", event);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "HubSpot Trigger error: " + e.getMessage(), e);
		}
	}

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		String token = String.valueOf(credentials.getOrDefault("accessToken",
			credentials.getOrDefault("apiKey", "")));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
