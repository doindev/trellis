package io.trellis.nodes.impl;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * HTTP Request Node - makes HTTP requests to external services.
 * Supports all standard HTTP methods, authentication types, custom headers,
 * query parameters, and request bodies.
 */
@Slf4j
@Node(
	type = "httpRequest",
	displayName = "HTTP Request",
	description = "Makes an HTTP request and returns the response data.",
	category = "Core",
	icon = "globe",
	credentials = {"httpHeaderAuth", "httpBasicAuth", "httpQueryAuth", "oAuth2Api"}
)
public class HttpRequestNode extends AbstractApiNode {

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
				.name("method")
				.displayName("Method")
				.description("The HTTP method to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("GET")
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
				.name("url")
				.displayName("URL")
				.description("The URL to make the request to.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("https://api.example.com/endpoint")
				.build(),

			NodeParameter.builder()
				.name("authentication")
				.displayName("Authentication")
				.description("The authentication method to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("none")
				.options(List.of(
					ParameterOption.builder().name("None").value("none").build(),
					ParameterOption.builder()
						.name("Predefined Credential Type")
						.value("predefinedCredentialType")
						.description("Use a pre-configured credential")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("sendBody")
				.displayName("Send Body")
				.description("Whether to send a request body.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("method", List.of("POST", "PUT", "PATCH", "DELETE"))))
				.build(),

			NodeParameter.builder()
				.name("bodyType")
				.displayName("Body Content Type")
				.description("The content type of the request body.")
				.type(ParameterType.OPTIONS)
				.defaultValue("json")
				.options(List.of(
					ParameterOption.builder().name("JSON").value("json").build(),
					ParameterOption.builder().name("Form Data").value("form").build(),
					ParameterOption.builder().name("Raw").value("raw").build()
				))
				.displayOptions(Map.of("show", Map.of("sendBody", List.of(true))))
				.build(),

			NodeParameter.builder()
				.name("body")
				.displayName("Body")
				.description("The JSON body to send with the request.")
				.type(ParameterType.JSON)
				.defaultValue("{}")
				.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "bodyType", List.of("json"))))
				.build(),

			NodeParameter.builder()
				.name("sendQuery")
				.displayName("Send Query Parameters")
				.description("Whether to send query parameters.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("queryParameters")
				.displayName("Query Parameters")
				.description("The query parameters to send.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("sendQuery", List.of(true))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("name")
						.displayName("Name")
						.type(ParameterType.STRING)
						.required(true)
						.build(),
					NodeParameter.builder()
						.name("value")
						.displayName("Value")
						.type(ParameterType.STRING)
						.required(true)
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("sendHeaders")
				.displayName("Send Headers")
				.description("Whether to send custom headers.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("headerParameters")
				.displayName("Header Parameters")
				.description("The custom headers to send.")
				.type(ParameterType.FIXED_COLLECTION)
				.displayOptions(Map.of("show", Map.of("sendHeaders", List.of(true))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("name")
						.displayName("Name")
						.type(ParameterType.STRING)
						.required(true)
						.build(),
					NodeParameter.builder()
						.name("value")
						.displayName("Value")
						.type(ParameterType.STRING)
						.required(true)
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.description("Additional options for the request.")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("timeout")
						.displayName("Timeout (ms)")
						.description("Request timeout in milliseconds.")
						.type(ParameterType.NUMBER)
						.defaultValue(30000)
						.build(),
					NodeParameter.builder()
						.name("followRedirects")
						.displayName("Follow Redirects")
						.description("Whether to follow HTTP redirects.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(true)
						.build(),
					NodeParameter.builder()
						.name("fullResponse")
						.displayName("Full Response")
						.description("Return the full response including status code and headers.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build()
				))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String method = context.getParameter("method", "GET");
		String url = context.getParameter("url", "");
		String authentication = context.getParameter("authentication", "none");
		boolean sendBody = toBoolean(context.getParameter("sendBody", false), false);
		boolean sendQuery = toBoolean(context.getParameter("sendQuery", false), false);
		boolean sendHeaders = toBoolean(context.getParameter("sendHeaders", false), false);

		if (url == null || url.isBlank()) {
			return NodeExecutionResult.error("URL is required");
		}

		try {
			// Build query parameters
			if (sendQuery) {
				Object queryParamsObj = context.getParameter("queryParameters", null);
				if (queryParamsObj instanceof List) {
					Map<String, Object> queryMap = new HashMap<>();
					for (Object param : (List<?>) queryParamsObj) {
						if (param instanceof Map) {
							Map<String, Object> paramMap = (Map<String, Object>) param;
							String name = toString(paramMap.get("name"));
							String value = toString(paramMap.get("value"));
							if (!name.isEmpty()) {
								queryMap.put(name, value);
							}
						}
					}
					url = buildUrl(url, queryMap);
				}
			}

			// Build headers
			Map<String, String> headers = new HashMap<>();
			if (sendHeaders) {
				Object headerParamsObj = context.getParameter("headerParameters", null);
				if (headerParamsObj instanceof List) {
					for (Object param : (List<?>) headerParamsObj) {
						if (param instanceof Map) {
							Map<String, Object> paramMap = (Map<String, Object>) param;
							String name = toString(paramMap.get("name"));
							String value = toString(paramMap.get("value"));
							if (!name.isEmpty()) {
								headers.put(name, value);
							}
						}
					}
				}
			}

			// Build body
			Object body = null;
			if (sendBody) {
				String bodyType = context.getParameter("bodyType", "json");
				if ("json".equals(bodyType)) {
					Object bodyParam = context.getParameter("body", "{}");
					if (bodyParam instanceof String) {
						body = parseJsonObject((String) bodyParam);
					} else if (bodyParam instanceof Map) {
						body = bodyParam;
					}
					headers.putIfAbsent("Content-Type", "application/json");
				} else if ("form".equals(bodyType)) {
					body = context.getParameter("body", "");
					headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
				} else {
					body = context.getParameter("body", "");
				}
			}

			// Get options
			Map<String, Object> options = context.getParameter("options", Map.of());
			int timeout = toInt(options.get("timeout"), 30000);
			boolean fullResponse = toBoolean(options.get("fullResponse"), false);

			// Apply authentication
			Map<String, Object> credentials = "none".equals(authentication) ? null : context.getCredentials();

			// Make request
			org.springframework.http.HttpHeaders springHeaders = new org.springframework.http.HttpHeaders();
			headers.forEach(springHeaders::add);
			if (credentials != null) {
				applyAuthentication(springHeaders, credentials);
			}

			// Use Java HttpClient for the actual request
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofMillis(timeout));

			// Add headers
			springHeaders.forEach((key, values) -> {
				for (String value : values) {
					requestBuilder.header(key, value);
				}
			});

			// Set method and body
			if (body != null) {
				String bodyStr = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
				requestBuilder.method(method, HttpRequest.BodyPublishers.ofString(bodyStr));
			} else {
				requestBuilder.method(method, HttpRequest.BodyPublishers.noBody());
			}

			HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

			// Parse response
			List<Map<String, Object>> items = new ArrayList<>();

			if (fullResponse) {
				Map<String, Object> responseData = new HashMap<>();
				responseData.put("statusCode", response.statusCode());
				responseData.put("headers", response.headers().map());

				String responseBody = response.body();
				if (responseBody != null && !responseBody.isBlank()) {
					try {
						Object parsed = objectMapper.readValue(responseBody, Object.class);
						responseData.put("body", parsed);
					} catch (Exception e) {
						responseData.put("body", responseBody);
					}
				}
				items.add(wrapInJson(responseData));
			} else {
				String responseBody = response.body();
				if (responseBody != null && !responseBody.isBlank()) {
					try {
						Object parsed = objectMapper.readValue(responseBody, Object.class);
						if (parsed instanceof List) {
							for (Object item : (List<?>) parsed) {
								if (item instanceof Map) {
									items.add(wrapInJson(item));
								} else {
									items.add(wrapInJson(Map.of("value", item)));
								}
							}
						} else if (parsed instanceof Map) {
							items.add(wrapInJson(parsed));
						} else {
							items.add(wrapInJson(Map.of("data", parsed)));
						}
					} catch (Exception e) {
						items.add(wrapInJson(Map.of("data", responseBody)));
					}
				} else {
					items.add(wrapInJson(Map.of("statusCode", response.statusCode())));
				}
			}

			log.debug("HTTP {} {} returned status {}", method, url, response.statusCode());
			return NodeExecutionResult.success(items);

		} catch (Exception e) {
			return handleError(context, "HTTP request failed: " + e.getMessage(), e);
		}
	}
}
