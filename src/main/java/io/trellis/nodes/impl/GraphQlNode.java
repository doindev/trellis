package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * GraphQL — execute GraphQL queries and mutations against any GraphQL endpoint.
 */
@Node(
		type = "graphQl",
		displayName = "GraphQL",
		description = "Execute GraphQL queries and mutations",
		category = "Miscellaneous",
		icon = "graphql",
		credentials = {},
		searchOnly = true
)
public class GraphQlNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String endpoint = context.getParameter("endpoint", "");
		String requestMethod = context.getParameter("requestMethod", "POST");
		String query = context.getParameter("query", "");
		String variables = context.getParameter("variables", "");
		String operationName = context.getParameter("operationName", "");

		// Build headers
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		// Add custom headers
		String headerAuth = context.getParameter("headerAuth", "");
		if (!headerAuth.isEmpty()) {
			headers.put("Authorization", headerAuth);
		}

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result;

				if ("GET".equalsIgnoreCase(requestMethod)) {
					StringBuilder url = new StringBuilder(endpoint);
					url.append("?query=").append(encode(query));
					if (!variables.isEmpty()) url.append("&variables=").append(encode(variables));
					if (!operationName.isEmpty()) url.append("&operationName=").append(encode(operationName));
					HttpResponse<String> response = get(url.toString(), headers);
					result = parseResponse(response);
				} else {
					Map<String, Object> body = new LinkedHashMap<>();
					body.put("query", query);
					if (!variables.isEmpty()) {
						body.put("variables", parseJson(variables));
					}
					if (!operationName.isEmpty()) {
						body.put("operationName", operationName);
					}
					HttpResponse<String> response = post(endpoint, body, headers);
					result = parseResponse(response);
				}

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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("endpoint").displayName("Endpoint")
						.type(ParameterType.STRING).defaultValue("")
						.description("The GraphQL endpoint URL.").required(true).build(),
				NodeParameter.builder()
						.name("requestMethod").displayName("Request Method")
						.type(ParameterType.OPTIONS).defaultValue("POST")
						.options(List.of(
								ParameterOption.builder().name("GET").value("GET").build(),
								ParameterOption.builder().name("POST").value("POST").build()
						)).build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("The GraphQL query or mutation.").build(),
				NodeParameter.builder()
						.name("variables").displayName("Variables (JSON)")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON object of variables.").build(),
				NodeParameter.builder()
						.name("operationName").displayName("Operation Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the operation to execute (if query has multiple).").build(),
				NodeParameter.builder()
						.name("headerAuth").displayName("Authorization Header")
						.type(ParameterType.STRING).defaultValue("")
						.description("Authorization header value (e.g., Bearer token).").build()
		);
	}
}
