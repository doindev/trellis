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

@Slf4j
@Node(
	type = "facebookGraphApi",
	displayName = "Facebook Graph API",
	description = "Access the Facebook Graph API to read and write data.",
	category = "Social Media",
	icon = "facebook",
	credentials = {"facebookGraphApi"}
)
public class FacebookGraphApiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.facebook.com/v17.0";

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

		params.add(NodeParameter.builder()
			.name("httpMethod").displayName("HTTP Method")
			.type(ParameterType.OPTIONS).required(true).defaultValue("GET")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("GET").value("GET").description("Retrieve data").build(),
				ParameterOption.builder().name("POST").value("POST").description("Create data").build(),
				ParameterOption.builder().name("DELETE").value("DELETE").description("Delete data").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("node").displayName("Node")
			.type(ParameterType.STRING).required(true)
			.description("The Graph API node (e.g. 'me', a page ID, user ID, etc.).")
			.placeHolder("me")
			.build());

		params.add(NodeParameter.builder()
			.name("edge").displayName("Edge")
			.type(ParameterType.STRING)
			.description("The edge to query on the node (e.g. 'posts', 'feed', 'photos').")
			.placeHolder("posts")
			.build());

		params.add(NodeParameter.builder()
			.name("fields").displayName("Fields")
			.type(ParameterType.STRING)
			.description("Comma-separated list of fields to return (e.g. 'id,name,message').")
			.placeHolder("id,name,message")
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER)
			.description("Maximum number of results to return.")
			.defaultValue(25)
			.build());

		params.add(NodeParameter.builder()
			.name("requestBody").displayName("Request Body (JSON)")
			.type(ParameterType.JSON)
			.description("The JSON body to send with POST requests.")
			.displayOptions(Map.of("show", Map.of("httpMethod", List.of("POST"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalQueryParams").displayName("Additional Query Parameters")
			.type(ParameterType.COLLECTION)
			.nestedParameters(List.of(
				NodeParameter.builder().name("key").displayName("Key").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).build()
			)).build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		String httpMethod = context.getParameter("httpMethod", "GET");
		String node = context.getParameter("node", "me");
		String edge = context.getParameter("edge", "");
		String fields = context.getParameter("fields", "");
		int limit = toInt(context.getParameter("limit", 25), 25);

		try {
			// Build URL
			StringBuilder urlBuilder = new StringBuilder(BASE_URL);
			urlBuilder.append("/").append(encode(node));
			if (edge != null && !edge.isEmpty()) {
				urlBuilder.append("/").append(encode(edge));
			}

			// Build query parameters
			urlBuilder.append("?access_token=").append(encode(accessToken));
			if (fields != null && !fields.isEmpty()) {
				urlBuilder.append("&fields=").append(encode(fields));
			}
			if (limit > 0) {
				urlBuilder.append("&limit=").append(limit);
			}

			String url = urlBuilder.toString();
			Map<String, String> headers = Map.of("Content-Type", "application/json");

			HttpResponse<String> response;
			switch (httpMethod) {
				case "POST": {
					Object requestBody = context.getParameter("requestBody", Map.of());
					response = post(url, requestBody, headers);
					break;
				}
				case "DELETE": {
					response = delete(url, headers);
					break;
				}
				default: {
					response = get(url, headers);
					break;
				}
			}

			if (response.statusCode() >= 400) {
				return apiError("Facebook Graph API", response);
			}

			Map<String, Object> result = parseResponse(response);

			// If the response contains a data array, return items individually
			if (result.containsKey("data") && result.get("data") instanceof List) {
				List<Map<String, Object>> items = new ArrayList<>();
				for (Object item : (List<?>) result.get("data")) {
					if (item instanceof Map) {
						items.add(wrapInJson(item));
					}
				}
				if (items.isEmpty()) {
					return NodeExecutionResult.empty();
				}
				return NodeExecutionResult.success(items);
			}

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Facebook Graph API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult apiError(String apiName, HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error(apiName + " error (HTTP " + response.statusCode() + "): " + body);
	}
}
