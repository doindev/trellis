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
 * Asana Trigger Node -- webhook-based trigger that fires when events
 * occur in Asana (tasks created, updated, completed, etc.).
 */
@Slf4j
@Node(
	type = "asanaTrigger",
	displayName = "Asana Trigger",
	description = "Starts the workflow when events occur in Asana",
	category = "Project Management",
	icon = "asana",
	trigger = true,
	credentials = {"asanaOAuth2Api"}
)
public class AsanaTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://app.asana.com/api/1.0";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("task")
				.options(List.of(
					ParameterOption.builder().name("Task").value("task").description("Trigger on task events").build(),
					ParameterOption.builder().name("Project").value("project").description("Trigger on project events").build(),
					ParameterOption.builder().name("Story").value("story").description("Trigger on story events (comments, etc.)").build()
				)).build(),

			NodeParameter.builder()
				.name("action").displayName("Action")
				.type(ParameterType.OPTIONS).required(true).defaultValue("changed")
				.options(List.of(
					ParameterOption.builder().name("Changed").value("changed").description("Trigger when resource is changed").build(),
					ParameterOption.builder().name("Added").value("added").description("Trigger when resource is added").build(),
					ParameterOption.builder().name("Deleted").value("deleted").description("Trigger when resource is deleted").build(),
					ParameterOption.builder().name("Removed").value("removed").description("Trigger when resource is removed").build(),
					ParameterOption.builder().name("Undeleted").value("undeleted").description("Trigger when resource is undeleted").build()
				)).build(),

			NodeParameter.builder()
				.name("projectId").displayName("Project ID")
				.type(ParameterType.STRING).required(true)
				.description("The Asana project ID to watch for events.")
				.build(),

			NodeParameter.builder()
				.name("webhookUrl").displayName("Webhook URL")
				.type(ParameterType.STRING)
				.description("The webhook URL to register with Asana. Leave empty for auto-configuration.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String resource = context.getParameter("resource", "task");
		String action = context.getParameter("action", "changed");
		String projectId = context.getParameter("projectId", "");

		try {
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData != null && !inputData.isEmpty()) {
				// Webhook data received -- pass it through with enrichment
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

			// No webhook data -- attempt to register webhook
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", credentials.getOrDefault("oauthAccessToken", "")));
			String webhookUrl = context.getParameter("webhookUrl", "");

			if (!webhookUrl.isEmpty() && !accessToken.isEmpty()) {
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("Authorization", "Bearer " + accessToken);
				headers.put("Content-Type", "application/json");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("data", Map.of(
					"resource", projectId,
					"target", webhookUrl,
					"filters", List.of(Map.of(
						"resource_type", resource,
						"action", action
					))
				));

				HttpResponse<String> response = post(BASE_URL + "/webhooks", body, headers);

				if (response.statusCode() >= 400) {
					log.warn("Failed to register Asana webhook: {}", response.body());
				} else {
					log.debug("Asana webhook registered for {}.{} on project {}", resource, action, projectId);
				}
			}

			// Return empty trigger item
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerResource", resource);
			triggerData.put("_triggerAction", action);
			triggerData.put("_triggerProjectId", projectId);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Asana Trigger error: " + e.getMessage(), e);
		}
	}
}
