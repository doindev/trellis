package io.trellis.nodes.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook Node - triggers workflows from incoming HTTP requests.
 * Supports configurable HTTP methods, paths, authentication, and response modes.
 */
@Slf4j
@Node(
	type = "webhook",
	displayName = "Webhook",
	description = "Starts the workflow when an HTTP request is received at the configured path.",
	category = "Core Triggers",
	icon = "webhook",
	trigger = true
)
public class WebhookNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("httpMethod")
				.displayName("HTTP Method")
				.description("The HTTP method to listen for.")
				.type(ParameterType.OPTIONS)
				.defaultValue("POST")
				.required(true)
				.options(List.of(
					ParameterOption.builder().name("GET").value("GET").build(),
					ParameterOption.builder().name("POST").value("POST").build(),
					ParameterOption.builder().name("PUT").value("PUT").build(),
					ParameterOption.builder().name("DELETE").value("DELETE").build(),
					ParameterOption.builder().name("PATCH").value("PATCH").build(),
					ParameterOption.builder().name("HEAD").value("HEAD").build()
				))
				.build(),

			NodeParameter.builder()
				.name("path")
				.displayName("Path")
				.description("The webhook path to listen on (e.g., '/my-webhook').")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("/webhook-path")
				.build(),

			NodeParameter.builder()
				.name("securityChain")
				.displayName("Authentication")
				.description("The authentication method for incoming requests.")
				.type(ParameterType.OPTIONS)
				.defaultValue("none")
				.options(List.of(
					ParameterOption.builder().name("None").value("none").build(),
					ParameterOption.builder().name("Basic Auth").value("basicAuth").build(),
					ParameterOption.builder().name("API Key").value("apiKey").build(),
					ParameterOption.builder().name("JWT").value("jwt").build(),
					ParameterOption.builder().name("OAuth2").value("oauth2").build(),
					ParameterOption.builder().name("Session").value("session").build(),
					ParameterOption.builder().name("Entra ID").value("entra").build()
				))
				.build(),

			NodeParameter.builder()
				.name("responseMode")
				.displayName("Respond")
				.description("When to respond to the webhook request.")
				.type(ParameterType.OPTIONS)
				.defaultValue("onReceived")
				.options(List.of(
					ParameterOption.builder()
						.name("Immediately")
						.value("onReceived")
						.description("Respond as soon as the webhook is received")
						.build(),
					ParameterOption.builder()
						.name("When Last Node Finishes")
						.value("lastNode")
						.description("Respond after the entire workflow has finished executing")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("responseCode")
				.displayName("Response Code")
				.description("The HTTP status code to return.")
				.type(ParameterType.NUMBER)
				.defaultValue(200)
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String httpMethod = context.getParameter("httpMethod", "POST");
		String path = context.getParameter("path", "/");
		String responseMode = context.getParameter("responseMode", "onReceived");
		int responseCode = toInt(context.getParameter("responseCode", 200), 200);

		log.debug("Webhook triggered: method={}, path={}, workflow={}", httpMethod, path, context.getWorkflowId());

		List<Map<String, Object>> inputData = context.getInputData();

		// If there is input data from the webhook request, pass it through
		if (inputData != null && !inputData.isEmpty()) {
			// Enrich items with webhook metadata
			List<Map<String, Object>> outputItems = new ArrayList<>();
			for (Map<String, Object> item : inputData) {
				Map<String, Object> enriched = deepClone(item);
				Map<String, Object> json = unwrapJson(enriched);
				json.put("_webhookPath", path);
				json.put("_webhookMethod", httpMethod);
				json.put("_webhookTimestamp", Instant.now().toString());
				outputItems.add(wrapInJson(json));
			}
			return NodeExecutionResult.success(outputItems);
		}

		// No incoming data - produce a trigger item with webhook metadata
		Map<String, Object> webhookData = new HashMap<>();
		webhookData.put("_webhookPath", path);
		webhookData.put("_webhookMethod", httpMethod);
		webhookData.put("_webhookTimestamp", Instant.now().toString());
		webhookData.put("responseMode", responseMode);
		webhookData.put("responseCode", responseCode);

		Map<String, Object> triggerItem = createTriggerItem(webhookData);
		return NodeExecutionResult.success(List.of(triggerItem));
	}
}
