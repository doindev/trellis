package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Gong — access call recordings and user data via the Gong API.
 */
@Slf4j
@Node(
		type = "gong",
		displayName = "Gong",
		description = "Access Gong call recordings and data",
		category = "Miscellaneous",
		icon = "gong",
		credentials = {"gongApi"},
		searchOnly = true
)
public class GongNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.gong.io/v2";

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
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).required(true).defaultValue("call")
						.options(List.of(
								ParameterOption.builder().name("Call").value("call").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).required(true).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("callId").displayName("Call ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the call.").build(),
				NodeParameter.builder()
						.name("fromDateTime").displayName("From Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start date for filtering (ISO 8601 format).").build(),
				NodeParameter.builder()
						.name("toDateTime").displayName("To Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("End date for filtering (ISO 8601 format).").build(),
				NodeParameter.builder()
						.name("cursor").displayName("Cursor")
						.type(ParameterType.STRING).defaultValue("")
						.description("Pagination cursor for retrieving more results.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max number of results to return.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "call");
		String operation = context.getParameter("operation", "get");
		Map<String, Object> credentials = context.getCredentials();

		Map<String, String> headers = getAuthHeaders(credentials);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "call" -> handleCall(context, headers, operation);
					case "user" -> handleUser(context, headers, operation);
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

	private Map<String, Object> handleCall(NodeExecutionContext context, Map<String, String> headers,
			String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String callId = context.getParameter("callId", "");
				Map<String, Object> body = Map.of("filter", Map.of("callIds", List.of(callId)));
				HttpResponse<String> response = post(BASE_URL + "/calls/extensive", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String fromDateTime = context.getParameter("fromDateTime", "");
				String toDateTime = context.getParameter("toDateTime", "");
				String cursor = context.getParameter("cursor", "");

				Map<String, Object> filter = new LinkedHashMap<>();
				if (!fromDateTime.isEmpty()) filter.put("fromDateTime", fromDateTime);
				if (!toDateTime.isEmpty()) filter.put("toDateTime", toDateTime);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("filter", filter);
				if (!cursor.isEmpty()) body.put("cursor", cursor);

				HttpResponse<String> response = post(BASE_URL + "/calls/list", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown call operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, Map<String, String> headers,
			String operation) throws Exception {
		if ("getAll".equals(operation)) {
			String fromDateTime = context.getParameter("fromDateTime", "");
			String toDateTime = context.getParameter("toDateTime", "");
			String cursor = context.getParameter("cursor", "");

			String url = BASE_URL + "/users";
			List<String> queryParts = new ArrayList<>();
			if (!fromDateTime.isEmpty()) queryParts.add("fromDateTime=" + encode(fromDateTime));
			if (!toDateTime.isEmpty()) queryParts.add("toDateTime=" + encode(toDateTime));
			if (!cursor.isEmpty()) queryParts.add("cursor=" + encode(cursor));
			if (!queryParts.isEmpty()) url += "?" + String.join("&", queryParts);

			HttpResponse<String> response = get(url, headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown user operation: " + operation);
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessKey = String.valueOf(credentials.getOrDefault("accessKey", ""));
		String accessKeySecret = String.valueOf(credentials.getOrDefault("accessKeySecret", ""));

		String auth = Base64.getEncoder().encodeToString((accessKey + ":" + accessKeySecret).getBytes());

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Basic " + auth);
		return headers;
	}
}
