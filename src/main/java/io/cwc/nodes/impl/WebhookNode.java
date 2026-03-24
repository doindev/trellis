package io.cwc.nodes.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import io.cwc.service.SecurityChainInfoService;
import io.cwc.service.SecurityChainInfoService.SecurityChainInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * Webhook Node - triggers workflows from incoming HTTP requests.
 * Supports configurable HTTP methods, paths, authentication, and response modes.
 */
@Slf4j
@Node(
	type = "webhook",
	displayName = "On webhook call",
	description = "Starts the workflow when an HTTP request is received at the configured path.",
	category = "Core Triggers",
	icon = "webhook",
	trigger = true,
	triggerFavorite = true
)
public class WebhookNode extends AbstractTriggerNode {

	@Autowired
	private SecurityChainInfoService securityChainInfoService;

	private static final List<ParameterOption> HTTP_METHOD_OPTIONS = List.of(
		ParameterOption.builder().name("DELETE").value("DELETE").build(),
		ParameterOption.builder().name("GET").value("GET").build(),
		ParameterOption.builder().name("HEAD").value("HEAD").build(),
		ParameterOption.builder().name("PATCH").value("PATCH").build(),
		ParameterOption.builder().name("POST").value("POST").build(),
		ParameterOption.builder().name("PUT").value("PUT").build()
	);

	private List<ParameterOption> buildAuthenticationOptions() {
		List<ParameterOption> options = new ArrayList<>();
		for (SecurityChainInfo chain : securityChainInfoService.getAvailableChains()) {
			options.add(ParameterOption.builder()
				.name(chain.getDisplayName())
				.value(chain.getName())
				.description(chain.getDescription())
				.build());
		}
		return options;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			// 1. HTTP Method
			NodeParameter.builder()
				.name("httpMethod")
				.displayName("HTTP Method")
				.description("The HTTP method to listen for.")
				.type(ParameterType.OPTIONS)
				.defaultValue("GET")
				.required(true)
				.options(HTTP_METHOD_OPTIONS)
				.build(),

			// 2. Path
			NodeParameter.builder()
				.name("path")
				.displayName("Path")
				.description("The webhook path to listen on. Use ':' for dynamic segments (e.g., 'webhook/:id').")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("webhook")
				.build(),

			// 5. Authentication (dynamic from SecurityChainInfoService)
			NodeParameter.builder()
				.name("authentication")
				.displayName("Authentication")
				.description("The authentication method for incoming requests.")
				.type(ParameterType.OPTIONS)
				.defaultValue("none")
				.options(buildAuthenticationOptions())
				.build(),

			// 5b. Roles (shown when authentication is not "none")
			NodeParameter.builder()
				.name("roles")
				.displayName("Roles")
				.description("Restrict this webhook to users with at least one of these roles. Leave empty to allow any authenticated user.")
				.type(ParameterType.FIXED_COLLECTION)
				.defaultValue(List.of())
				.displayOptions(Map.of("hide", Map.of("authentication", List.of("none"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("roleName")
						.displayName("Role Name")
						.description("The role name required to access this webhook.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. admin")
						.build()
				))
				.build(),

			// 6. Response Mode
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
						.build(),
					ParameterOption.builder()
						.name("Using 'Respond to Webhook' Node")
						.value("responseNode")
						.description("Use a separate Respond to Webhook node to control the response")
						.build()
				))
				.build(),

			// 7. Webhook Notice (shown when responseMode is responseNode)
			NodeParameter.builder()
				.name("webhookNotice")
				.displayName("Notice")
				.description("Insert a 'Respond to Webhook' node to control when and how you respond. <a href=\"https://docs.cwc.io/nodes/respond-to-webhook\" target=\"_blank\">More info</a>")
				.type(ParameterType.NOTICE)
				.noDataExpression(true)
				.displayOptions(Map.of("show", Map.of("responseMode", List.of("responseNode"))))
				.build(),

			// 8. Response Data (shown when responseMode is lastNode)
			NodeParameter.builder()
				.name("responseData")
				.displayName("Response Data")
				.description("What data to include in the webhook response.")
				.type(ParameterType.OPTIONS)
				.defaultValue("firstEntryJson")
				.options(List.of(
					ParameterOption.builder()
						.name("All Entries")
						.value("allEntries")
						.description("Return all output entries as an array")
						.build(),
					ParameterOption.builder()
						.name("First Entry JSON")
						.value("firstEntryJson")
						.description("Return the first entry as JSON")
						.build(),
					ParameterOption.builder()
						.name("First Entry Binary")
						.value("firstEntryBinary")
						.description("Return the first entry's binary data")
						.build(),
					ParameterOption.builder()
						.name("No Response Body")
						.value("noData")
						.description("Return an empty response")
						.build()
				))
				.displayOptions(Map.of("show", Map.of("responseMode", List.of("lastNode"))))
				.build(),

			// 9. Response Binary Property Name (shown when responseData is firstEntryBinary)
			NodeParameter.builder()
				.name("responseBinaryPropertyName")
				.displayName("Response Binary Property Name")
				.description("The name of the binary property to return in the response.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.displayOptions(Map.of("show", Map.of("responseData", List.of("firstEntryBinary"))))
				.build(),

			// 10. Options (collection)
			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.description("Additional options for the webhook.")
				.type(ParameterType.COLLECTION)
				.defaultValue(Map.of())
				.nestedParameters(buildOptionsNestedParameters())
				.build()
		);
	}

	private List<NodeParameter> buildOptionsNestedParameters() {
		return List.of(
			NodeParameter.builder()
				.name("binaryPropertyName")
				.displayName("Binary Property Name")
				.description("The name of the field containing binary data in the request.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.build(),

			NodeParameter.builder()
				.name("ignoreBots")
				.displayName("Ignore Bots")
				.description("Ignore requests from known bots and crawlers.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("ipWhitelist")
				.displayName("IP(s) Allowlist")
				.description("Comma-separated list of allowed IPs or CIDR ranges. Leave empty to allow all.")
				.type(ParameterType.STRING)
				.placeHolder("e.g. 127.0.0.1, 192.168.1.0/24")
				.build(),

			NodeParameter.builder()
				.name("noResponseBody")
				.displayName("No Response Body")
				.description("Do not send a body in the response.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("/responseMode", List.of("onReceived"))))
				.build(),

			NodeParameter.builder()
				.name("rawBody")
				.displayName("Raw Body")
				.description("Return the raw request body instead of parsed data.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("responseCode")
				.displayName("Response Code")
				.description("The HTTP status code to return.")
				.type(ParameterType.OPTIONS)
				.defaultValue(200)
				.options(List.of(
					ParameterOption.builder().name("200 OK").value(200).description("Request successful").build(),
					ParameterOption.builder().name("201 Created").value(201).description("Resource created successfully").build(),
					ParameterOption.builder().name("204 No Content").value(204).description("Successful but no response body").build(),
					ParameterOption.builder().name("301 Moved Permanently").value(301).description("Resource permanently moved").build(),
					ParameterOption.builder().name("302 Found").value(302).description("Resource temporarily moved").build(),
					ParameterOption.builder().name("304 Not Modified").value(304).description("Resource has not changed").build(),
					ParameterOption.builder().name("400 Bad Request").value(400).description("Invalid request").build(),
					ParameterOption.builder().name("401 Unauthorized").value(401).description("Authentication required").build(),
					ParameterOption.builder().name("403 Forbidden").value(403).description("Access denied").build(),
					ParameterOption.builder().name("404 Not Found").value(404).description("Resource not found").build()
				))
				.displayOptions(Map.of("hide", Map.of("/responseMode", List.of("responseNode"))))
				.build(),

			NodeParameter.builder()
				.name("responseContentType")
				.displayName("Response Content-Type")
				.description("Override the Content-Type header of the response.")
				.type(ParameterType.STRING)
				.placeHolder("application/xml")
				.displayOptions(Map.of("show", Map.of(
					"/responseData", List.of("firstEntryJson"),
					"/responseMode", List.of("lastNode")
				)))
				.build(),

			NodeParameter.builder()
				.name("responseData")
				.displayName("Response Data")
				.description("Custom data to include in the immediate response body.")
				.type(ParameterType.STRING)
				.placeHolder("success")
				.displayOptions(Map.of("show", Map.of("/responseMode", List.of("onReceived"))))
				.build(),

			NodeParameter.builder()
				.name("responseHeaders")
				.displayName("Response Headers")
				.description("Custom headers to include in the webhook response.")
				.type(ParameterType.FIXED_COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("name")
						.displayName("Name")
						.type(ParameterType.STRING)
						.placeHolder("X-Custom-Header")
						.build(),
					NodeParameter.builder()
						.name("value")
						.displayName("Value")
						.type(ParameterType.STRING)
						.placeHolder("header-value")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("responsePropertyName")
				.displayName("Property Name")
				.description("The name of the property from the first entry to return.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.displayOptions(Map.of("show", Map.of(
					"/responseData", List.of("firstEntryJson"),
					"/responseMode", List.of("lastNode")
				)))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String httpMethod = context.getParameter("httpMethod", "GET");
		String path = context.getParameter("path", "/");
		String authentication = context.getParameter("authentication", "none");
		String responseMode = context.getParameter("responseMode", "onReceived");

		// Options (ignoreBots, ipWhitelist, rawBody, noResponseBody, responseCode)
		// are enforced by WebhookController at HTTP level before this node executes.

		log.debug("Webhook triggered: method={}, path={}, auth={}, workflow={}",
			httpMethod, path, authentication, context.getWorkflowId());

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			List<Map<String, Object>> outputItems = new ArrayList<>();
			for (Map<String, Object> item : inputData) {
				Map<String, Object> enriched = deepClone(item);
				Map<String, Object> json = unwrapJson(enriched);
				// Ensure standard webhook fields exist (may be absent in manual execution)
				json.putIfAbsent("headers", new LinkedHashMap<>());
				json.putIfAbsent("queryParams", new LinkedHashMap<>());
				json.putIfAbsent("pathParams", new LinkedHashMap<>());
				json.putIfAbsent("body", new LinkedHashMap<>());
				json.putIfAbsent("method", httpMethod);
				json.putIfAbsent("path", path);
				json.put("_webhookPath", path);
				json.put("_webhookMethod", httpMethod);
				json.put("_webhookTimestamp", Instant.now().toString());
				json.put("_webhookAuthentication", authentication);
				outputItems.add(wrapInJson(json));
			}
			return NodeExecutionResult.success(outputItems);
		}

		// No incoming data - produce a trigger item with webhook metadata
		Map<String, Object> webhookData = new HashMap<>();
		webhookData.put("headers", new LinkedHashMap<>());
		webhookData.put("queryParams", new LinkedHashMap<>());
		webhookData.put("pathParams", new LinkedHashMap<>());
		webhookData.put("body", new LinkedHashMap<>());
		webhookData.put("method", httpMethod);
		webhookData.put("path", path);
		webhookData.put("_webhookPath", path);
		webhookData.put("_webhookMethod", httpMethod);
		webhookData.put("_webhookTimestamp", Instant.now().toString());
		webhookData.put("_webhookAuthentication", authentication);
		webhookData.put("responseMode", responseMode);

		Map<String, Object> triggerItem = createTriggerItem(webhookData);
		return NodeExecutionResult.success(List.of(triggerItem));
	}
}
