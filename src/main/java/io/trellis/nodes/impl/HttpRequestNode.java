package io.trellis.nodes.impl;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import io.trellis.credentials.CredentialType;
import io.trellis.credentials.CredentialTypeRegistry;
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
 * Supports all standard HTTP methods, multiple authentication types, custom headers,
 * query parameters, multiple body content types, and comprehensive request options.
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

	@Autowired
	private CredentialTypeRegistry credentialTypeRegistry;

	private static final List<ParameterOption> HTTP_METHOD_OPTIONS = List.of(
		ParameterOption.builder().name("DELETE").value("DELETE").build(),
		ParameterOption.builder().name("GET").value("GET").build(),
		ParameterOption.builder().name("HEAD").value("HEAD").build(),
		ParameterOption.builder().name("OPTIONS").value("OPTIONS").build(),
		ParameterOption.builder().name("PATCH").value("PATCH").build(),
		ParameterOption.builder().name("POST").value("POST").build(),
		ParameterOption.builder().name("PUT").value("PUT").build()
	);

	private List<ParameterOption> buildCredentialTypeOptions() {
		List<ParameterOption> options = new ArrayList<>();
		for (CredentialType ct : credentialTypeRegistry.getAllTypes()) {
			options.add(ParameterOption.builder()
				.name(ct.getDisplayName())
				.value(ct.getType())
				.description(ct.getDescription())
				.build());
		}
		return options;
	}

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
		List<NodeParameter> params = new ArrayList<>();

		// ── Method ──
		params.add(NodeParameter.builder()
			.name("method")
			.displayName("Method")
			.description("The HTTP method to use.")
			.type(ParameterType.OPTIONS)
			.defaultValue("GET")
			.required(true)
			.noDataExpression(true)
			.options(HTTP_METHOD_OPTIONS)
			.build());

		// ── URL ──
		params.add(NodeParameter.builder()
			.name("url")
			.displayName("URL")
			.description("The URL to make the request to.")
			.type(ParameterType.STRING)
			.required(true)
			.placeHolder("https://api.example.com/endpoint")
			.build());

		// ── Authentication ──
		params.add(NodeParameter.builder()
			.name("authentication")
			.displayName("Authentication")
			.description("The authentication method to use.")
			.type(ParameterType.OPTIONS)
			.defaultValue("none")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder()
					.name("None")
					.value("none")
					.description("No authentication")
					.build(),
				ParameterOption.builder()
					.name("Predefined Credential Type")
					.value("predefinedCredentialType")
					.description("Use a pre-configured credential")
					.build(),
				ParameterOption.builder()
					.name("Generic Credential Type")
					.value("genericCredentialType")
					.description("Use generic authentication (header, query, or basic)")
					.build()
			))
			.build());

		// ── Credential Type selector (shown when auth = predefinedCredentialType) ──
		params.add(NodeParameter.builder()
			.name("nodeCredentialType")
			.displayName("Credential Type")
			.description("Select which type of credential to use.")
			.type(ParameterType.OPTIONS)
			.options(buildCredentialTypeOptions())
			.displayOptions(Map.of("show", Map.of("authentication", List.of("predefinedCredentialType"))))
			.build());

		// ── Generic Credential Type selector (shown when auth = genericCredentialType) ──
		params.add(NodeParameter.builder()
			.name("genericAuthType")
			.displayName("Generic Auth Type")
			.description("Select the generic authentication mechanism.")
			.type(ParameterType.OPTIONS)
			.defaultValue("httpHeaderAuth")
			.options(List.of(
				ParameterOption.builder().name("HTTP Basic Auth").value("httpBasicAuth").build(),
				ParameterOption.builder().name("HTTP Header Auth").value("httpHeaderAuth").build(),
				ParameterOption.builder().name("HTTP Query Auth").value("httpQueryAuth").build()
			))
			.displayOptions(Map.of("show", Map.of("authentication", List.of("genericCredentialType"))))
			.build());

		// ── Send Query Parameters ──
		params.add(NodeParameter.builder()
			.name("sendQuery")
			.displayName("Send Query Parameters")
			.description("Whether to send query parameters.")
			.type(ParameterType.BOOLEAN)
			.defaultValue(false)
			.noDataExpression(true)
			.build());

		// ── Query Parameters ──
		params.add(NodeParameter.builder()
			.name("queryParameters")
			.displayName("Query Parameters")
			.description("The query parameters to send.")
			.type(ParameterType.FIXED_COLLECTION)
			.displayOptions(Map.of("show", Map.of("sendQuery", List.of(true))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).required(true).placeHolder("key").build(),
				NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).required(true).placeHolder("value").build()
			))
			.build());

		// ── Send Headers ──
		params.add(NodeParameter.builder()
			.name("sendHeaders")
			.displayName("Send Headers")
			.description("Whether to send custom headers.")
			.type(ParameterType.BOOLEAN)
			.defaultValue(false)
			.noDataExpression(true)
			.build());

		// ── Header Parameters ──
		params.add(NodeParameter.builder()
			.name("headerParameters")
			.displayName("Header Parameters")
			.description("The custom headers to send.")
			.type(ParameterType.FIXED_COLLECTION)
			.displayOptions(Map.of("show", Map.of("sendHeaders", List.of(true))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).required(true).placeHolder("Content-Type").build(),
				NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).required(true).placeHolder("application/json").build()
			))
			.build());

		// ── Send Body ──
		params.add(NodeParameter.builder()
			.name("sendBody")
			.displayName("Send Body")
			.description("Whether to send a request body.")
			.type(ParameterType.BOOLEAN)
			.defaultValue(false)
			.noDataExpression(true)
			.displayOptions(Map.of("show", Map.of("method", List.of("POST", "PUT", "PATCH", "DELETE"))))
			.build());

		// ── Body Content Type ──
		params.add(NodeParameter.builder()
			.name("contentType")
			.displayName("Body Content Type")
			.description("The content type of the request body.")
			.type(ParameterType.OPTIONS)
			.defaultValue("json")
			.options(List.of(
				ParameterOption.builder().name("JSON").value("json").description("Send data as JSON").build(),
				ParameterOption.builder().name("Form URL Encoded").value("formUrlEncoded").description("Send data as application/x-www-form-urlencoded").build(),
				ParameterOption.builder().name("Form Data (Multipart)").value("multipartFormData").description("Send data as multipart/form-data").build(),
				ParameterOption.builder().name("Binary").value("binary").description("Send raw binary data").build(),
				ParameterOption.builder().name("Raw").value("raw").description("Send raw text with custom content type").build()
			))
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true))))
			.build());

		// ── JSON Body (specifyBody mode) ──
		params.add(NodeParameter.builder()
			.name("specifyBody")
			.displayName("Specify Body")
			.description("How to specify the JSON body.")
			.type(ParameterType.OPTIONS)
			.defaultValue("keypair")
			.options(List.of(
				ParameterOption.builder().name("Using Key-Value Pairs").value("keypair").build(),
				ParameterOption.builder().name("Using JSON").value("json").build()
			))
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("json"))))
			.build());

		// ── JSON Body (raw JSON text) ──
		params.add(NodeParameter.builder()
			.name("jsonBody")
			.displayName("JSON Body")
			.description("The JSON body to send with the request.")
			.type(ParameterType.JSON)
			.defaultValue("{}")
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("json"), "specifyBody", List.of("json"))))
			.build());

		// ── JSON Body key-value pairs ──
		params.add(NodeParameter.builder()
			.name("bodyParameters")
			.displayName("Body Parameters")
			.description("Key-value pairs for the request body.")
			.type(ParameterType.FIXED_COLLECTION)
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("json", "formUrlEncoded", "multipartFormData"), "specifyBody", List.of("keypair"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).required(true).placeHolder("key").build(),
				NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).required(true).placeHolder("value").build()
			))
			.build());

		// ── Form URL Encoded body specifier ──
		params.add(NodeParameter.builder()
			.name("formParameters")
			.displayName("Form Parameters")
			.description("Key-value pairs for form body.")
			.type(ParameterType.FIXED_COLLECTION)
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("formUrlEncoded"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).required(true).build(),
				NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).required(true).build()
			))
			.build());

		// ── Multipart Form Data ──
		params.add(NodeParameter.builder()
			.name("multipartParameters")
			.displayName("Multipart Fields")
			.description("Fields to send as multipart/form-data.")
			.type(ParameterType.FIXED_COLLECTION)
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("multipartFormData"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Field Name").type(ParameterType.STRING).required(true).build(),
				NodeParameter.builder().name("value").displayName("Field Value").type(ParameterType.STRING).required(true).build(),
				NodeParameter.builder().name("parameterType")
					.displayName("Parameter Type")
					.type(ParameterType.OPTIONS)
					.defaultValue("string")
					.options(List.of(
						ParameterOption.builder().name("String").value("string").build(),
						ParameterOption.builder().name("Binary").value("binary").build()
					))
					.build()
			))
			.build());

		// ── Binary Property Name ──
		params.add(NodeParameter.builder()
			.name("binaryPropertyName")
			.displayName("Input Binary Field")
			.description("The name of the input binary field containing the file data.")
			.type(ParameterType.STRING)
			.defaultValue("data")
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("binary"))))
			.build());

		// ── Raw Body Content Type ──
		params.add(NodeParameter.builder()
			.name("rawContentType")
			.displayName("Content Type")
			.description("The MIME type for the raw body.")
			.type(ParameterType.STRING)
			.defaultValue("text/plain")
			.placeHolder("text/plain")
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("raw"))))
			.build());

		// ── Raw Body ──
		params.add(NodeParameter.builder()
			.name("rawBody")
			.displayName("Body")
			.description("The raw body content.")
			.type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 5))
			.displayOptions(Map.of("show", Map.of("sendBody", List.of(true), "contentType", List.of("raw"))))
			.build());

		// ── Options (collection) ──
		params.add(NodeParameter.builder()
			.name("options")
			.displayName("Options")
			.description("Additional options for the request.")
			.type(ParameterType.COLLECTION)
			.defaultValue(Map.of())
			.nestedParameters(buildOptionsNestedParameters())
			.build());

		// ── Settings: Batching ──
		params.add(NodeParameter.builder()
			.name("batching")
			.displayName("Batching")
			.description("Send requests in batches with a delay between each batch.")
			.type(ParameterType.BOOLEAN)
			.defaultValue(false)
			.isNodeSetting(true)
			.build());

		params.add(NodeParameter.builder()
			.name("batchSize")
			.displayName("Batch Size")
			.description("Number of requests per batch.")
			.type(ParameterType.NUMBER)
			.defaultValue(1)
			.isNodeSetting(true)
			.displayOptions(Map.of("show", Map.of("batching", List.of(true))))
			.build());

		params.add(NodeParameter.builder()
			.name("batchInterval")
			.displayName("Batch Interval (ms)")
			.description("Delay in milliseconds between batches.")
			.type(ParameterType.NUMBER)
			.defaultValue(1000)
			.isNodeSetting(true)
			.displayOptions(Map.of("show", Map.of("batching", List.of(true))))
			.build());

		// ── Settings: SSL Certificates ──
		params.add(NodeParameter.builder()
			.name("provideSslCertificates")
			.displayName("SSL Certificates")
			.description("Whether to provide custom SSL certificates.")
			.type(ParameterType.BOOLEAN)
			.defaultValue(false)
			.noDataExpression(true)
			.isNodeSetting(true)
			.build());

		params.add(NodeParameter.builder()
			.name("sslCertificateNotice")
			.displayName("")
			.description("To use SSL certificates, you need to add them to the 'SSL Certificates' credential.")
			.type(ParameterType.NOTICE)
			.noDataExpression(true)
			.isNodeSetting(true)
			.displayOptions(Map.of("show", Map.of("provideSslCertificates", List.of(true))))
			.build());

		return params;
	}

	private List<NodeParameter> buildOptionsNestedParameters() {
		return List.of(
			NodeParameter.builder()
				.name("allowUnauthorizedCerts")
				.displayName("Allow Unauthorized Certs")
				.description("Allow connections to servers with self-signed or untrusted SSL certificates.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("arrayFormat")
				.displayName("Array Format in Query Parameters")
				.description("How to format arrays in query parameters.")
				.type(ParameterType.OPTIONS)
				.defaultValue("brackets")
				.options(List.of(
					ParameterOption.builder().name("No Brackets").value("repeat").description("key=1&key=2").build(),
					ParameterOption.builder().name("Brackets Only").value("brackets").description("key[]=1&key[]=2").build(),
					ParameterOption.builder().name("With Indices").value("indices").description("key[0]=1&key[1]=2").build()
				))
				.build(),

			NodeParameter.builder()
				.name("lowercaseHeaders")
				.displayName("Lowercase Headers")
				.description("Convert all header names to lowercase before sending.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("proxy")
				.displayName("Proxy")
				.description("HTTP proxy URL (e.g. http://proxy:8080).")
				.type(ParameterType.STRING)
				.placeHolder("http://proxy:8080")
				.build(),

			NodeParameter.builder()
				.name("followRedirects")
				.displayName("Follow Redirects")
				.description("Whether to follow HTTP redirects.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build(),

			NodeParameter.builder()
				.name("maxRedirects")
				.displayName("Max Redirects")
				.description("Maximum number of redirects to follow.")
				.type(ParameterType.NUMBER)
				.defaultValue(21)
				.displayOptions(Map.of("show", Map.of("followRedirects", List.of(true))))
				.build(),

			NodeParameter.builder()
				.name("timeout")
				.displayName("Timeout (ms)")
				.description("Request timeout in milliseconds. 0 means no timeout.")
				.type(ParameterType.NUMBER)
				.defaultValue(300000)
				.build(),

			NodeParameter.builder()
				.name("responseFormat")
				.displayName("Response Format")
				.description("How to parse the response body.")
				.type(ParameterType.OPTIONS)
				.defaultValue("autodetect")
				.options(List.of(
					ParameterOption.builder().name("Autodetect").value("autodetect").description("Automatically detect JSON or return as text").build(),
					ParameterOption.builder().name("JSON").value("json").description("Force parse as JSON").build(),
					ParameterOption.builder().name("Text").value("text").description("Return as plain text").build(),
					ParameterOption.builder().name("Binary").value("binary").description("Return as binary data").build(),
					ParameterOption.builder().name("File").value("file").description("Save response as file binary").build()
				))
				.build(),

			NodeParameter.builder()
				.name("outputPropertyName")
				.displayName("Output Property Name")
				.description("The property name for binary or file output.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.displayOptions(Map.of("show", Map.of("responseFormat", List.of("binary", "file"))))
				.build(),

			NodeParameter.builder()
				.name("fullResponse")
				.displayName("Full Response")
				.description("Return the full response including status code and headers.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("neverError")
				.displayName("Never Error")
				.description("Succeed even when the HTTP status code is not 2xx.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build(),

			NodeParameter.builder()
				.name("pagination")
				.displayName("Pagination")
				.description("Automatically handle paginated API responses.")
				.type(ParameterType.OPTIONS)
				.defaultValue("off")
				.options(List.of(
					ParameterOption.builder().name("Off").value("off").description("No pagination").build(),
					ParameterOption.builder().name("Response Contains Next URL").value("responseContainsNextUrl").description("The response contains a URL for the next page").build(),
					ParameterOption.builder().name("Response Contains Page Number").value("pageNumber").description("Increment a page number parameter").build(),
					ParameterOption.builder().name("Response Contains Cursor").value("cursor").description("Use a cursor from the response").build()
				))
				.build(),

			NodeParameter.builder()
				.name("paginationNextUrl")
				.displayName("Next URL Expression")
				.description("Expression to extract the next page URL from the response (e.g. 'next' or 'links.next').")
				.type(ParameterType.STRING)
				.placeHolder("next")
				.displayOptions(Map.of("show", Map.of("pagination", List.of("responseContainsNextUrl"))))
				.build(),

			NodeParameter.builder()
				.name("paginationMaxRequests")
				.displayName("Max Pages")
				.description("Maximum number of pages to fetch. 0 means unlimited.")
				.type(ParameterType.NUMBER)
				.defaultValue(100)
				.displayOptions(Map.of("show", Map.of("pagination", List.of("responseContainsNextUrl", "pageNumber", "cursor"))))
				.build(),

			NodeParameter.builder()
				.name("paginationPageParam")
				.displayName("Page Parameter Name")
				.description("The query parameter name for the page number.")
				.type(ParameterType.STRING)
				.defaultValue("page")
				.displayOptions(Map.of("show", Map.of("pagination", List.of("pageNumber"))))
				.build(),

			NodeParameter.builder()
				.name("paginationCursorParam")
				.displayName("Cursor Parameter Name")
				.description("The query parameter name for the cursor.")
				.type(ParameterType.STRING)
				.defaultValue("cursor")
				.displayOptions(Map.of("show", Map.of("pagination", List.of("cursor"))))
				.build(),

			NodeParameter.builder()
				.name("paginationCursorPath")
				.displayName("Cursor Path in Response")
				.description("Dot-notation path to extract the cursor from the response (e.g. 'meta.next_cursor').")
				.type(ParameterType.STRING)
				.placeHolder("meta.next_cursor")
				.displayOptions(Map.of("show", Map.of("pagination", List.of("cursor"))))
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
					Map<String, Object> queryMap = new LinkedHashMap<>();
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
			Map<String, String> headers = new LinkedHashMap<>();
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
				String contentType = context.getParameter("contentType", "json");

				switch (contentType) {
					case "json" -> {
						String specifyBody = context.getParameter("specifyBody", "keypair");
						if ("json".equals(specifyBody)) {
							Object bodyParam = context.getParameter("jsonBody", "{}");
							if (bodyParam instanceof String) {
								body = parseJsonObject((String) bodyParam);
							} else if (bodyParam instanceof Map) {
								body = bodyParam;
							}
						} else {
							// keypair mode
							body = extractKeyValuePairs(context.getParameter("bodyParameters", null));
						}
						headers.putIfAbsent("Content-Type", "application/json");
					}
					case "formUrlEncoded" -> {
						Object formParams = context.getParameter("formParameters", null);
						body = buildFormEncodedBody(formParams);
						headers.putIfAbsent("Content-Type", "application/x-www-form-urlencoded");
					}
					case "multipartFormData" -> {
						// For multipart, build key-value pairs (binary handled in future)
						body = extractKeyValuePairs(context.getParameter("multipartParameters", null));
						headers.putIfAbsent("Content-Type", "multipart/form-data");
					}
					case "binary" -> {
						// Binary body from input data
						body = context.getParameter("binaryPropertyName", "data");
					}
					case "raw" -> {
						body = context.getParameter("rawBody", "");
						String rawContentType = context.getParameter("rawContentType", "text/plain");
						headers.putIfAbsent("Content-Type", rawContentType);
					}
				}
			}

			// Get options
			Map<String, Object> options = context.getParameter("options", Map.of());
			int timeout = toInt(options.get("timeout"), 300000);
			boolean fullResponse = toBoolean(options.get("fullResponse"), false);
			boolean neverError = toBoolean(options.get("neverError"), false);
			boolean lowercaseHeaders = toBoolean(options.get("lowercaseHeaders"), false);
			String responseFormat = toString(options.getOrDefault("responseFormat", "autodetect"));

			// Apply lowercase headers
			if (lowercaseHeaders) {
				Map<String, String> lowered = new LinkedHashMap<>();
				headers.forEach((k, v) -> lowered.put(k.toLowerCase(), v));
				headers = lowered;
			}

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
				.timeout(Duration.ofMillis(timeout > 0 ? timeout : 300000));

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

			// Check for error status codes
			if (!neverError && response.statusCode() >= 400) {
				return NodeExecutionResult.error(
					"HTTP " + response.statusCode() + " " + method + " " + url +
					(response.body() != null ? " — " + truncate(response.body(), 200) : ""));
			}

			// Parse response
			List<Map<String, Object>> items = parseHttpResponse(response, fullResponse, responseFormat, options);

			log.debug("HTTP {} {} returned status {}", method, url, response.statusCode());
			return NodeExecutionResult.success(items);

		} catch (Exception e) {
			return handleError(context, "HTTP request failed: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> extractKeyValuePairs(Object paramsObj) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (paramsObj instanceof List) {
			for (Object param : (List<?>) paramsObj) {
				if (param instanceof Map) {
					Map<String, Object> paramMap = (Map<String, Object>) param;
					String name = toString(paramMap.get("name"));
					Object value = paramMap.get("value");
					if (!name.isEmpty()) {
						result.put(name, value);
					}
				}
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private String buildFormEncodedBody(Object paramsObj) {
		StringBuilder sb = new StringBuilder();
		if (paramsObj instanceof List) {
			boolean first = true;
			for (Object param : (List<?>) paramsObj) {
				if (param instanceof Map) {
					Map<String, Object> paramMap = (Map<String, Object>) param;
					String name = toString(paramMap.get("name"));
					String value = toString(paramMap.get("value"));
					if (!name.isEmpty()) {
						if (!first) sb.append("&");
						sb.append(encode(name)).append("=").append(encode(value));
						first = false;
					}
				}
			}
		}
		return sb.toString();
	}

	private List<Map<String, Object>> parseHttpResponse(
		HttpResponse<String> response, boolean fullResponse, String responseFormat, Map<String, Object> options
	) {
		List<Map<String, Object>> items = new ArrayList<>();

		if (fullResponse) {
			Map<String, Object> responseData = new HashMap<>();
			responseData.put("statusCode", response.statusCode());
			responseData.put("headers", response.headers().map());

			String responseBody = response.body();
			if (responseBody != null && !responseBody.isBlank()) {
				responseData.put("body", parseBodyByFormat(responseBody, responseFormat));
			}
			items.add(wrapInJson(responseData));
		} else {
			String responseBody = response.body();
			if (responseBody != null && !responseBody.isBlank()) {
				Object parsed = parseBodyByFormat(responseBody, responseFormat);
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
			} else {
				items.add(wrapInJson(Map.of("statusCode", response.statusCode())));
			}
		}

		return items;
	}

	private Object parseBodyByFormat(String body, String responseFormat) {
		switch (responseFormat) {
			case "json" -> {
				try {
					return objectMapper.readValue(body, Object.class);
				} catch (Exception e) {
					log.warn("Failed to parse response as JSON, returning as text");
					return body;
				}
			}
			case "text" -> {
				return body;
			}
			default -> {
				// autodetect: try JSON first
				try {
					return objectMapper.readValue(body, Object.class);
				} catch (Exception e) {
					return body;
				}
			}
		}
	}

	private String truncate(String str, int maxLen) {
		if (str == null) return "";
		return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
	}
}
