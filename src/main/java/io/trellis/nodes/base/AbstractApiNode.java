package io.trellis.nodes.base;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.trellis.nodes.core.NodeExecutionResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractApiNode extends AbstractNode {
	// RestClient
	protected final RestClient restClient = RestClient.create();
	
	// java jdk
	protected final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(30))
			.build();
	protected final ObjectMapper objectMapper = new ObjectMapper();
	
	protected ResponseEntity<Object> makeRequest(
		String method,
		String url,
		Map<String, String> headersMap,
		Object body,
		Map<String, Object> credentials
	){
		HttpHeaders httpHeaders = new HttpHeaders();
		
		if (headersMap != null) {
			headersMap.forEach(httpHeaders::add);
		}
		
		applyAuthentication(httpHeaders, credentials);
		
		if (body != null && !httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
			httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		}
		
		return restClient
					.method(HttpMethod.valueOf(method))
					.uri(URI.create(url))
					.headers(headers -> headers.addAll(httpHeaders))
					.body(body)
					.retrieve()
					.toEntity(Object.class);
					
	}

	protected ResponseEntity<Object> get(String url, Map<String, String> headers, Map<String, Object> credentials) {
		return makeRequest(RequestMethod.GET.name(), url, headers, null, credentials);
	}
	
	protected ResponseEntity<Object> post(String url, Map<String, String> headers, Object body, Map<String, Object> credentials) {
		return makeRequest(RequestMethod.POST.name(), url, headers, body, credentials);
	}
	
	protected ResponseEntity<Object> put(String url, Map<String, String> headers, Object body, Map<String, Object> credentials) {
		return makeRequest(RequestMethod.PUT.name(), url, headers, body, credentials);
	}
	
	protected ResponseEntity<Object> delete(String url, Map<String, String> headers, Map<String, Object> credentials) {
		return makeRequest(RequestMethod.DELETE.name(), url, headers, null, credentials);
	}
	
	protected void applyAuthentication(HttpHeaders headers, Map<String, Object> credentials) {
		if (credentials == null) return;
		String authType = (String) credentials.get("authType");
		if (authType == null) authType = "apiKey";
		
		switch(authType.toLowerCase()) {
		case "apiKey" -> {
			String apiKey = (String) credentials.get("apiKey");
			String headerName = (String) credentials.getOrDefault("headerName", HttpHeaders.AUTHORIZATION);
			String prefix = (String) credentials.getOrDefault("prefix", "Bearer");
			if (apiKey != null) {
				headers.add(headerName, prefix.isEmpty() ? apiKey : prefix + " " + apiKey);
			}
		}
		case "basic" -> {
			String username = (String) credentials.get("username");
			String password = (String) credentials.get("password");
			if (username != null && password != null) {
				String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
				headers.add(HttpHeaders.AUTHORIZATION, "Basic " + auth);
			}
		}
		case "oauth2" -> {
			String accessToken = (String) credentials.get("accessToken");
			if (accessToken != null ) {
				headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			}
		}
		}
	}
	
	protected String buildUrl(String baseUrl, Map<String, Object> queryParams) {
		if (queryParams == null || queryParams.isEmpty()) {
			return baseUrl;
		}
		
		StringBuilder sb = new StringBuilder(baseUrl);
		sb.append(baseUrl.contains("?") ? "&" : "?");
		
		boolean first = true;
		for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
			if (!first) sb.append("&");
			sb
				.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
				.append("=")
				.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
			first = false;
		}
		
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	protected List<Map<String, Object>> parseResponseToItems(Object response, String dataPath) {
		List<Map<String, Object>> items = new ArrayList<>();	
		
		if (response == null) {
			return items;
		}
		
		Object data = response;
		if (dataPath != null && !dataPath.isEmpty()) {
			String[] parts = dataPath.split("\\.");
			for (String part : parts) {
				if (data instanceof Map) {
					data = ((Map<String, Object>) data).get(part);
				} else {
					break;
				}
			}
		}
		
		if (data instanceof List) {
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				} else {
					items.add(wrapInJson(Map.of("value", item)));
				}
			}
		} else if (data instanceof Map) {
			items.add(wrapInJson(data));
		} else if (data != null) {
			items.add(wrapInJson(Map.of("value", data)));
		}
		
		return items;
	}
	
	// override this method in subclasses to provide api specifi url
	protected String getBaseUrl(Map<String, Object> credentials) {
		return "";
	}
	
	protected String sendGetRequest(String url, Map<String, String> headers) throws Exception {
		ResponseEntity<Object> response = get(url, headers, null);
		return response.getBody() != null ? response.getBody().toString() : "";
	}

	protected ResponseEntity<Object> sendGetRequestWithResponse(String url, Map<String, String> headers) throws Exception {
		return get(url, headers, null);
	}
	
	protected String sendPostRequest(String url, Map<String, String> headers, Object body) throws Exception {
		ResponseEntity<Object> response = post(url, headers, body, null);
		return response.getBody() != null ? response.getBody().toString() : "";
	}
	
	protected ResponseEntity<Object> sendPostRequestWithResponse(String url, Object body, Map<String, String> headers) throws Exception {
		return post(url, headers, body, null);
	}
	
	protected String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
	
	
	// GET request using Java HttpClient
	protected HttpResponse<String> get(String url, Map<String, String> headers) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(60));
		
		if (headers != null) {
			headers.forEach(builder::header);
		}
		
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
	
	// POST request using Java HttpClient
	protected HttpResponse<String> post(String url, Object body, Map<String, String> headers) throws Exception {
		String jsonBody = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
		
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
				.timeout(Duration.ofSeconds(60));
		
		if (headers != null) {
			headers.forEach(builder::header);
		}
		
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
	
	// PUT request using Java HttpClient
	protected HttpResponse<String> put(String url, Object body, Map<String, String> headers) throws Exception {
		String jsonBody = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
		
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
				.timeout(Duration.ofSeconds(60));
		
		if (headers != null) {
			headers.forEach(builder::header);
		}
		
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
	
	// PATCH request using Java HttpClient
	protected HttpResponse<String> patch(String url, Object body, Map<String, String> headers) throws Exception {
		String jsonBody = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
		
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
				.timeout(Duration.ofSeconds(60));
		
		if (headers != null) {
			headers.forEach(builder::header);
		}
		
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
	
	// DELETE request using Java HttpClient
	protected HttpResponse<String> delete(String url, Map<String, String> headers) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.DELETE()
				.timeout(Duration.ofSeconds(60));
		
		if (headers != null) {
			headers.forEach(builder::header);
		}
		
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
	
	// DELETE request using Java HttpClient with body
	protected HttpResponse<String> deleteWithBody(String url, Object body, Map<String, String> headers) throws Exception {
		String jsonBody = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
		
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody))
				.timeout(Duration.ofSeconds(60));
		
		if (headers != null) {
			headers.forEach(builder::header);
		}
		
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
	
	// parse HttpResponse into a Map
	protected Map<String, Object> parseResponse(HttpResponse<String> response) throws Exception {
		String body = response.body();
		if (body == null || body.isBlank()) {
			return Map.of("statusCode", response.statusCode());
		}
		
		return objectMapper.readValue(body,  new TypeReference<Map<String, Object>>() {});
	}
	
	// parse HttpResponse into a List of Map's
	protected List<Map<String, Object>> parseArrayResponse(HttpResponse<String> response) throws Exception {
		String body = response.body();
		if (body == null || body.isBlank()) {
			return List.of();
		}
		
		if (body.trim().startsWith("[")) {
			return objectMapper.readValue(body,  new TypeReference<List<Map<String, Object>>>() {});
		}
		
		return List.of(objectMapper.readValue(body,  new TypeReference<Map<String, Object>>() {}));
	}
	
	// handles exception and returns an error result
	protected NodeExecutionResult handleError(Exception e) {
		log.error("API request failed",e);
		return NodeExecutionResult.error("API request failed: " + e.getMessage(), e);
	}
	
	// convert object to JSON
	protected String toJson(Object object) {
		try {
			return objectMapper.writeValueAsString(object);
		} catch (Exception e) {
			log.error("Failed to serialize object to JSON", e);
			return "{}";
		}
	}
	
	// wrap list of results in the standard format
	protected List<Map<String, Object>> wrapInJson(List<Map<String, Object>> items) {
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : items) {
			result.add(wrapInJson((Object) item));
		}
		return result;
	}
	
	//parse a JSON string into a Map
	protected Map<String, Object> parseJsonObject(String json) {
		return parseJson(json);
	}
	
	// parse a JSON string into a Map
	protected Map<String, Object> parseJsonToMap(String json) {
		return parseJson(json);
	}
	
	// parse a JSON response string into a Map
	protected Map<String, Object> parseJsonResponse(String json) {
		return parseJson(json);
	}
	
	// parse JSON string into a Map
	protected Map<String, Object> parseJson(String json) {
		try {
			if (json == null || json.isBlank()) {
				return Map.of();
			}
			return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
		} catch (Exception e) {
			log.error("Failed to parse JSON: {}", json, e);
			return Map.of();
		}
	}
	
	// parse JSON string into a List
	protected List<Map<String, Object>> parseJsonArray(String json) {
		try {
			if (json == null || json.isBlank()) {
				return List.of();
			}
			if (json.trim().startsWith("[")) {
				return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
			}
			// wrap single object in list
			return List.of(objectMapper.readValue(json,  new TypeReference<Map<String, Object>>() {}));
		} catch (Exception e) {
			log.error("Failed to parse JSON array: {}", json, e);
			return List.of();
		}
	}
	
	// make a API call and returns the respoonse body as Map
	protected Map<String, Object> makeApiCall(String url, String method, Map<String, Object> body, Map<String, String> headers) throws Exception {
		HttpResponse<String> response;
		switch(method.toUpperCase()) {
			case "GET" -> response = get(url, headers);
			case "POST" -> response = post(url, body, headers);
			case "PUT" -> response = put(url, body, headers);
			case "PATCH" -> response = patch(url, body, headers);
			case "DELETE" -> response = get(url, headers);
			default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
		}
		return parseResponse(response);
	}
	
	// send DELETE request and get the response body as String
	protected String sendDeleteRequest(String url, Map<String, String> headers) throws Exception {
		HttpResponse<String> response = delete(url, headers);
		return response.body();
	}
	
	// send DELETE request and get the response body as HttpResponse
	protected HttpResponse<String> sendDeleteRequestWithResponse(String url, Map<String, String> headers) throws Exception {
		return delete(url, headers);
	}
	
	// send PUT request and get the response body as String
	protected String sendPutRequest(String url, Object body, Map<String, String> headers) throws Exception {
		HttpResponse<String> response = put(url, body, headers);
		return response.body();
	}
	
	// send PUT request and get the response body as HttpResponse
	protected HttpResponse<String> sendPutRequestWithResponse(String url, Object body, Map<String, String> headers) throws Exception {
		return put(url, body, headers);
	}
	
	// send PATCH request and get the response body as String
	protected String sendPatchRequest(String url, Object body, Map<String, String> headers) throws Exception {
		HttpResponse<String> response = patch(url, body, headers);
		return response.body();
	}
	
	// send PATCH request and get the response body as HttpResponse
	protected HttpResponse<String> sendPatchRequestWithResponse(String url, Object body, Map<String, String> headers) throws Exception {
		return patch(url, body, headers);
	}
	
}
