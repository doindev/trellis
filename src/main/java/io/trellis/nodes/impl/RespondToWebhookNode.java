package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Respond to Webhook Node - sets the response data for webhook-triggered workflows.
 * Used in combination with the Webhook node when responseMode is set to "responseNode".
 * Stores the response in static data so the webhook handler can retrieve and send it.
 */
@Slf4j
@Node(
	type = "respondToWebhook",
	displayName = "Respond to Webhook",
	description = "Returns data for the webhook. Use with Webhook node when 'Respond' is set to 'Using Respond to Webhook Node'.",
	category = "Core",
	icon = "reply"
)
public class RespondToWebhookNode extends AbstractNode {

	private final ObjectMapper objectMapper = new ObjectMapper();

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
		return List.of(
			NodeParameter.builder()
				.name("respondWith")
				.displayName("Respond With")
				.description("The data that should be returned and send back as a response to the webhook.")
				.type(ParameterType.OPTIONS)
				.defaultValue("firstIncomingItem")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("All Incoming Items")
						.value("allIncomingItems")
						.description("Respond with all incoming items as a JSON array")
						.build(),
					ParameterOption.builder()
						.name("Binary")
						.value("binary")
						.description("Respond with a binary file")
						.build(),
					ParameterOption.builder()
						.name("First Incoming Item")
						.value("firstIncomingItem")
						.description("Respond with the first incoming item's JSON data")
						.build(),
					ParameterOption.builder()
						.name("JSON")
						.value("json")
						.description("Respond with a custom JSON body")
						.build(),
					ParameterOption.builder()
						.name("No Data")
						.value("noData")
						.description("Send response without body")
						.build(),
					ParameterOption.builder()
						.name("Redirect")
						.value("redirect")
						.description("Redirect to a URL")
						.build(),
					ParameterOption.builder()
						.name("Text")
						.value("text")
						.description("Respond with a custom text message")
						.build()
				))
				.build(),

			// Text response body
			NodeParameter.builder()
				.name("responseBody")
				.displayName("Response Body")
				.description("The text to send as the response body.")
				.type(ParameterType.STRING)
				.placeHolder("e.g. Workflow completed")
				.typeOptions(Map.of("rows", 2))
				.displayOptions(Map.of("show", Map.of("respondWith", List.of("text"))))
				.build(),

			// JSON response body
			NodeParameter.builder()
				.name("responseJson")
				.displayName("Response Body")
				.description("The JSON data to send as the response body.")
				.type(ParameterType.JSON)
				.defaultValue("{ \"myField\": \"value\" }")
				.typeOptions(Map.of("rows", 4))
				.displayOptions(Map.of("show", Map.of("respondWith", List.of("json"))))
				.build(),

			// Redirect URL
			NodeParameter.builder()
				.name("redirectURL")
				.displayName("Redirect URL")
				.description("The URL to redirect the webhook caller to.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("e.g. https://www.example.com")
				.displayOptions(Map.of("show", Map.of("respondWith", List.of("redirect"))))
				.build(),

			// Binary response data source
			NodeParameter.builder()
				.name("responseDataSource")
				.displayName("Response Data Source")
				.description("How to determine which binary data to respond with.")
				.type(ParameterType.OPTIONS)
				.defaultValue("automatically")
				.options(List.of(
					ParameterOption.builder()
						.name("Automatically Detect")
						.value("automatically")
						.description("Use the first binary property found on the input item")
						.build(),
					ParameterOption.builder()
						.name("Choose Field")
						.value("set")
						.description("Specify the binary field name to use")
						.build()
				))
				.displayOptions(Map.of("show", Map.of("respondWith", List.of("binary"))))
				.build(),

			// Binary input field name
			NodeParameter.builder()
				.name("inputFieldName")
				.displayName("Input Field Name")
				.description("The name of the binary field to use for the response.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.required(true)
				.displayOptions(Map.of("show", Map.of(
					"respondWith", List.of("binary"),
					"responseDataSource", List.of("set")
				)))
				.build(),

			// Options collection
			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("responseCode")
						.displayName("Response Code")
						.description("The HTTP status code for the response (100-599).")
						.type(ParameterType.NUMBER)
						.defaultValue(200)
						.build(),
					NodeParameter.builder()
						.name("responseHeaders")
						.displayName("Response Headers")
						.description("Custom headers to include in the response.")
						.type(ParameterType.FIXED_COLLECTION)
						.nestedParameters(List.of(
							NodeParameter.builder()
								.name("name")
								.displayName("Header Name")
								.type(ParameterType.STRING)
								.required(true)
								.placeHolder("Content-Type")
								.build(),
							NodeParameter.builder()
								.name("value")
								.displayName("Header Value")
								.type(ParameterType.STRING)
								.required(true)
								.placeHolder("application/json")
								.build()
						))
						.build(),
					NodeParameter.builder()
						.name("responseKey")
						.displayName("Response Key")
						.description("The key name to wrap the response data in.")
						.type(ParameterType.STRING)
						.placeHolder("e.g. data")
						.displayOptions(Map.of("show", Map.of(
							"/respondWith", List.of("allIncomingItems", "firstIncomingItem")
						)))
						.build()
				))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String respondWith = context.getParameter("respondWith", "firstIncomingItem");
		List<Map<String, Object>> inputData = context.getInputData();

		// Read options collection
		Map<String, Object> options = toMap(context.getParameter("options", null));
		int responseCode = toInt(options.getOrDefault("responseCode", 200), 200);
		String responseKey = toString(options.getOrDefault("responseKey", ""));

		// Build response data
		Object responseBody = null;
		String contentType = "application/json";

		switch (respondWith) {
			case "allIncomingItems":
				List<Object> allItems = new ArrayList<>();
				if (inputData != null) {
					for (Map<String, Object> item : inputData) {
						allItems.add(unwrapJson(item));
					}
				}
				if (!responseKey.isEmpty()) {
					responseBody = Map.of(responseKey, allItems);
				} else {
					responseBody = allItems;
				}
				break;

			case "firstIncomingItem":
				Object firstItem;
				if (inputData != null && !inputData.isEmpty()) {
					firstItem = unwrapJson(inputData.get(0));
				} else {
					firstItem = Map.of();
				}
				if (!responseKey.isEmpty()) {
					responseBody = Map.of(responseKey, firstItem);
				} else {
					responseBody = firstItem;
				}
				break;

			case "text":
				responseBody = context.getParameter("responseBody", "");
				contentType = "text/plain";
				break;

			case "json":
				String jsonStr = context.getParameter("responseJson", "{}");
				try {
					responseBody = objectMapper.readValue(jsonStr, Object.class);
				} catch (Exception e) {
					log.warn("Failed to parse response JSON, using raw string: {}", e.getMessage());
					responseBody = jsonStr;
				}
				break;

			case "redirect":
				String redirectURL = context.getParameter("redirectURL", "");
				responseBody = null;
				if (responseCode == 200) {
					responseCode = 302;
				}
				// Set redirect via headers (handled below)
				Map<String, String> redirectHeaders = new HashMap<>();
				redirectHeaders.put("Location", redirectURL);
				redirectHeaders.put("Content-Type", contentType);
				addCustomHeaders(redirectHeaders, options);

				Map<String, Object> redirectResponse = new HashMap<>();
				redirectResponse.put("statusCode", responseCode);
				redirectResponse.put("headers", redirectHeaders);
				redirectResponse.put("body", responseBody);

				Map<String, Object> redirectStaticData = context.getStaticData();
				if (redirectStaticData == null) {
					redirectStaticData = new HashMap<>();
				}
				redirectStaticData.put("webhookResponse", redirectResponse);

				log.debug("Respond to Webhook: redirect to {}, statusCode={}", redirectURL, responseCode);
				return NodeExecutionResult.builder()
					.output(List.of(inputData != null && !inputData.isEmpty() ? inputData :
						List.of(wrapInJson(Map.of("webhookResponseSet", true)))))
					.staticData(redirectStaticData)
					.build();

			case "binary":
				// Read binary data from input item
				String dataSource = context.getParameter("responseDataSource", "automatically");
				String fieldName = "set".equals(dataSource)
					? context.getParameter("inputFieldName", "data")
					: "data";

				if (inputData != null && !inputData.isEmpty()) {
					Map<String, Object> firstInputItem = inputData.get(0);
					Object binaryObj = firstInputItem.get("binary");
					if (binaryObj instanceof Map) {
						Map<String, Object> binaryMap = (Map<String, Object>) binaryObj;
						Object binaryField;
						if ("automatically".equals(dataSource) && !binaryMap.isEmpty()) {
							binaryField = binaryMap.values().iterator().next();
						} else {
							binaryField = binaryMap.get(fieldName);
						}
						if (binaryField instanceof Map) {
							Map<String, Object> binaryData = (Map<String, Object>) binaryField;
							responseBody = binaryData.get("data");
							String mimeType = toString(binaryData.getOrDefault("mimeType", "application/octet-stream"));
							contentType = mimeType;
						}
					}
				}
				if (responseBody == null) {
					responseBody = "";
					contentType = "application/octet-stream";
				}
				break;

			case "noData":
				responseBody = null;
				break;

			default:
				responseBody = Map.of();
				break;
		}

		// Build response headers
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", contentType);
		addCustomHeaders(headers, options);

		// Store response data in static data for the webhook handler to retrieve
		Map<String, Object> webhookResponse = new HashMap<>();
		webhookResponse.put("statusCode", responseCode);
		webhookResponse.put("headers", headers);
		webhookResponse.put("body", responseBody);

		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) {
			staticData = new HashMap<>();
		}
		staticData.put("webhookResponse", webhookResponse);

		log.debug("Respond to Webhook: respondWith={}, statusCode={}", respondWith, responseCode);

		// Pass through input data
		List<Map<String, Object>> outputItems;
		if (inputData != null && !inputData.isEmpty()) {
			outputItems = inputData;
		} else {
			outputItems = List.of(wrapInJson(Map.of("webhookResponseSet", true)));
		}

		return NodeExecutionResult.builder()
			.output(List.of(outputItems))
			.staticData(staticData)
			.build();
	}

	@SuppressWarnings("unchecked")
	private void addCustomHeaders(Map<String, String> headers, Map<String, Object> options) {
		Object responseHeadersObj = options.get("responseHeaders");
		if (responseHeadersObj instanceof List) {
			for (Object header : (List<?>) responseHeadersObj) {
				if (header instanceof Map) {
					Map<String, Object> headerMap = (Map<String, Object>) header;
					String name = toString(headerMap.get("name"));
					String value = toString(headerMap.get("value"));
					if (!name.isEmpty()) {
						headers.put(name, value);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> toMap(Object obj) {
		if (obj instanceof Map) {
			return (Map<String, Object>) obj;
		}
		return Map.of();
	}
}
