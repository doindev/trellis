package io.trellis.nodes.impl;

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
 * Used in combination with the Webhook node when responseMode is set to "lastNode".
 * Stores the response in static data so the webhook handler can retrieve and send it.
 */
@Slf4j
@Node(
	type = "respondToWebhook",
	displayName = "Respond to Webhook",
	description = "Set the response data to send back to the webhook caller. Use with Webhook node in 'When Last Node Finishes' response mode.",
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
				.description("What data to send in the webhook response.")
				.type(ParameterType.OPTIONS)
				.defaultValue("firstIncomingItem")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("First Incoming Item")
						.value("firstIncomingItem")
						.description("Respond with the first item from the input")
						.build(),
					ParameterOption.builder()
						.name("Text")
						.value("text")
						.description("Respond with a custom text string")
						.build(),
					ParameterOption.builder()
						.name("JSON")
						.value("json")
						.description("Respond with custom JSON data")
						.build(),
					ParameterOption.builder()
						.name("No Data")
						.value("noData")
						.description("Respond with an empty body")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("responseBody")
				.displayName("Response Body")
				.description("The text to send as the response body.")
				.type(ParameterType.STRING)
				.placeHolder("Response text here...")
				.displayOptions(Map.of("show", Map.of("respondWith", List.of("text"))))
				.build(),

			NodeParameter.builder()
				.name("responseJson")
				.displayName("Response JSON")
				.description("The JSON data to send as the response body.")
				.type(ParameterType.JSON)
				.defaultValue("{}")
				.displayOptions(Map.of("show", Map.of("respondWith", List.of("json"))))
				.build(),

			NodeParameter.builder()
				.name("responseCode")
				.displayName("Response Code")
				.description("The HTTP status code for the response.")
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
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String respondWith = context.getParameter("respondWith", "firstIncomingItem");
		int responseCode = toInt(context.getParameter("responseCode", 200), 200);
		List<Map<String, Object>> inputData = context.getInputData();

		// Build response data
		Object responseBody = null;
		String contentType = "application/json";

		switch (respondWith) {
			case "firstIncomingItem":
				if (inputData != null && !inputData.isEmpty()) {
					responseBody = unwrapJson(inputData.get(0));
				} else {
					responseBody = Map.of();
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

		Object responseHeadersObj = context.getParameter("responseHeaders", null);
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

		// Pass through input data and include the static data for webhook response
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
}
