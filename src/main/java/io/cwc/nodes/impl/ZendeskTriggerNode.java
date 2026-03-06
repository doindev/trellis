package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Zendesk Trigger Node -- polls for new or updated tickets in Zendesk.
 */
@Slf4j
@Node(
	type = "zendeskTrigger",
	displayName = "Zendesk Trigger",
	description = "Starts the workflow when tickets are created or updated in Zendesk",
	category = "Customer Support",
	icon = "zendesk",
	trigger = true,
	polling = true,
	credentials = {"zendeskApi"}
)
public class ZendeskTriggerNode extends AbstractApiNode {

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
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("newTicket")
				.options(List.of(
					ParameterOption.builder().name("New Ticket").value("newTicket").description("Trigger when a new ticket is created").build(),
					ParameterOption.builder().name("Updated Ticket").value("updatedTicket").description("Trigger when a ticket is updated").build()
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
		String event = context.getParameter("event", "newTicket");
		int limit = toInt(context.getParameters().get("limit"), 10);

		try {
			String subdomain = String.valueOf(credentials.getOrDefault("subdomain", ""));
			String baseUrl = "https://" + subdomain + ".zendesk.com/api/v2";
			Map<String, String> headers = authHeaders(credentials);

			String sortOrder = "newTicket".equals(event) ? "created_at" : "updated_at";
			String query = "type:ticket sort_by:" + sortOrder + " sort_order:desc";
			String url = baseUrl + "/search.json?query=" + encode(query) + "&per_page=" + Math.min(limit, 100);

			HttpResponse<String> response = get(url, headers);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Zendesk Trigger API error (HTTP " + response.statusCode() + "): " + body);
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
						enriched.put("_triggerEvent", event);
						enriched.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(enriched));
					}
				}
				return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
			}

			Map<String, Object> triggerData = new LinkedHashMap<>(parsed);
			triggerData.put("_triggerEvent", event);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Zendesk Trigger error: " + e.getMessage(), e);
		}
	}

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		String accessToken = stringVal(credentials, "accessToken");
		if (!accessToken.isEmpty()) {
			headers.put("Authorization", "Bearer " + accessToken);
		} else {
			String email = stringVal(credentials, "email");
			String apiToken = stringVal(credentials, "apiToken");
			if (!email.isEmpty() && !apiToken.isEmpty()) {
				String encoded = Base64.getEncoder().encodeToString((email + "/token:" + apiToken).getBytes());
				headers.put("Authorization", "Basic " + encoded);
			}
		}
		return headers;
	}

	private String stringVal(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return val != null ? String.valueOf(val) : "";
	}
}
