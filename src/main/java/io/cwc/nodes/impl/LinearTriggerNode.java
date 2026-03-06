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
 * Linear Trigger Node -- webhook-based trigger that fires when events
 * occur in Linear (issues created, updated, comments added, etc.).
 */
@Slf4j
@Node(
	type = "linearTrigger",
	displayName = "Linear Trigger",
	description = "Starts the workflow when events occur in Linear",
	category = "Project Management",
	icon = "linear",
	trigger = true,
	credentials = {"linearApi"}
)
public class LinearTriggerNode extends AbstractApiNode {

	private static final String GRAPHQL_URL = "https://api.linear.app/graphql";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("Issue")
				.options(List.of(
					ParameterOption.builder().name("Issue").value("Issue").description("Trigger on issue events").build(),
					ParameterOption.builder().name("Comment").value("Comment").description("Trigger on comment events").build(),
					ParameterOption.builder().name("Project").value("Project").description("Trigger on project events").build(),
					ParameterOption.builder().name("Cycle").value("Cycle").description("Trigger on cycle events").build()
				)).build(),

			NodeParameter.builder()
				.name("action").displayName("Action")
				.type(ParameterType.OPTIONS).required(true).defaultValue("create")
				.options(List.of(
					ParameterOption.builder().name("Create").value("create").description("Trigger when resource is created").build(),
					ParameterOption.builder().name("Update").value("update").description("Trigger when resource is updated").build(),
					ParameterOption.builder().name("Remove").value("remove").description("Trigger when resource is removed").build()
				)).build(),

			NodeParameter.builder()
				.name("teamId").displayName("Team ID")
				.type(ParameterType.STRING)
				.description("Optional team ID to filter events.")
				.build(),

			NodeParameter.builder()
				.name("webhookUrl").displayName("Webhook URL")
				.type(ParameterType.STRING)
				.description("The webhook URL to register with Linear. Leave empty for auto-configuration.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String resource = context.getParameter("resource", "Issue");
		String action = context.getParameter("action", "create");

		try {
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData != null && !inputData.isEmpty()) {
				// Webhook data received -- pass it through
				List<Map<String, Object>> results = new ArrayList<>();
				for (Map<String, Object> item : inputData) {
					Map<String, Object> enriched = new LinkedHashMap<>(unwrapJson(item));
					enriched.put("_triggerResource", resource);
					enriched.put("_triggerAction", action);
					enriched.put("_triggerTimestamp", System.currentTimeMillis());
					results.add(wrapInJson(enriched));
				}
				return NodeExecutionResult.success(results);
			}

			// No webhook data -- attempt to register webhook via GraphQL
			String apiKey = String.valueOf(credentials.getOrDefault("apiKey", credentials.getOrDefault("accessToken", "")));
			String webhookUrl = context.getParameter("webhookUrl", "");
			String teamId = context.getParameter("teamId", "");

			if (!webhookUrl.isEmpty() && !apiKey.isEmpty()) {
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("Authorization", "Bearer " + apiKey);
				headers.put("Content-Type", "application/json");

				StringBuilder mutation = new StringBuilder();
				mutation.append("mutation { webhookCreate(input: { url: \"")
					.append(escapeGraphQL(webhookUrl)).append("\"");
				if (!teamId.isEmpty()) {
					mutation.append(", teamId: \"").append(escapeGraphQL(teamId)).append("\"");
				}
				mutation.append(", resourceTypes: [\"").append(escapeGraphQL(resource)).append("\"]");
				mutation.append(" }) { success webhook { id } } }");

				Map<String, Object> body = Map.of("query", mutation.toString());
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);

				if (response.statusCode() >= 400) {
					log.warn("Failed to register Linear webhook: {}", response.body());
				} else {
					log.debug("Linear webhook registered for {}.{}", resource, action);
				}
			}

			// Return empty trigger item
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerResource", resource);
			triggerData.put("_triggerAction", action);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Linear Trigger error: " + e.getMessage(), e);
		}
	}

	private String escapeGraphQL(String value) {
		if (value == null) return "";
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}
}
