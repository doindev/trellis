package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * PostHog — send events and manage identities using the PostHog API.
 */
@Node(
		type = "postHog",
		displayName = "PostHog",
		description = "Send events and identify users in PostHog",
		category = "Miscellaneous",
		icon = "postHog",
		credentials = {"postHogApi"},
		searchOnly = true
)
public class PostHogNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");
		String url = (String) credentials.getOrDefault("url", "https://app.posthog.com");
		// Strip trailing slash
		if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

		String resource = context.getParameter("resource", "event");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = Map.of("Content-Type", "application/json", "Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "event" -> {
						String distinctId = context.getParameter("distinctId", "");
						String eventName = context.getParameter("eventName", "");
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("api_key", apiKey);
						body.put("event", eventName);
						body.put("distinct_id", distinctId);
						String properties = context.getParameter("properties", "");
						if (!properties.isEmpty()) body.put("properties", parseJson(properties));
						HttpResponse<String> response = post(url + "/capture", body, headers);
						yield parseResponse(response);
					}
					case "identity" -> {
						String distinctId = context.getParameter("distinctId", "");
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("api_key", apiKey);
						body.put("type", "identify");
						body.put("distinct_id", distinctId);
						String properties = context.getParameter("properties", "");
						if (!properties.isEmpty()) {
							body.put("$set", parseJson(properties));
						}
						HttpResponse<String> response = post(url + "/batch", body, headers);
						yield parseResponse(response);
					}
					case "alias" -> {
						String distinctId = context.getParameter("distinctId", "");
						String alias = context.getParameter("alias", "");
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("api_key", apiKey);
						body.put("type", "alias");
						body.put("distinct_id", distinctId);
						body.put("alias", alias);
						HttpResponse<String> response = post(url + "/batch", body, headers);
						yield parseResponse(response);
					}
					case "track" -> {
						String distinctId = context.getParameter("distinctId", "");
						String name = context.getParameter("pageName", "");
						String type = operation.equals("page") ? "page" : "screen";
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("api_key", apiKey);
						body.put("type", type);
						body.put("distinct_id", distinctId);
						body.put("name", name);
						String properties = context.getParameter("properties", "");
						if (!properties.isEmpty()) body.put("properties", parseJson(properties));
						HttpResponse<String> response = post(url + "/batch", body, headers);
						yield parseResponse(response);
					}
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("event")
						.options(List.of(
								ParameterOption.builder().name("Alias").value("alias").build(),
								ParameterOption.builder().name("Event").value("event").build(),
								ParameterOption.builder().name("Identity").value("identity").build(),
								ParameterOption.builder().name("Track").value("track").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Page").value("page").build(),
								ParameterOption.builder().name("Screen").value("screen").build()
						)).build(),
				NodeParameter.builder()
						.name("distinctId").displayName("Distinct ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The user's unique identifier.").required(true).build(),
				NodeParameter.builder()
						.name("eventName").displayName("Event Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("alias").displayName("Alias")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("pageName").displayName("Page/Screen Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("properties").displayName("Properties (JSON)")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON object of event/identity properties.").build()
		);
	}
}
