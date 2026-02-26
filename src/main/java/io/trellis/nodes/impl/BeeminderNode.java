package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Beeminder — track goals and datapoints using the Beeminder API.
 */
@Node(
		type = "beeminder",
		displayName = "Beeminder",
		description = "Track goals and add datapoints in Beeminder",
		category = "Miscellaneous",
		icon = "beeminder",
		credentials = {"beeminderApi"}
)
public class BeeminderNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.beeminder.com/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String authToken = context.getCredentialString("authToken", "");
		String userId = context.getCredentialString("userId", "");

		String resource = context.getParameter("resource", "datapoint");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = Map.of("Accept", "application/json", "Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "datapoint" -> handleDatapoint(context, userId, authToken, headers, operation);
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

	private Map<String, Object> handleDatapoint(NodeExecutionContext context, String userId, String authToken,
												 Map<String, String> headers, String operation) throws Exception {
		String goalName = context.getParameter("goalName", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("auth_token", authToken);
				body.put("value", toDouble(context.getParameters().get("value"), 0.0));
				String timestamp = context.getParameter("timestamp", "");
				if (!timestamp.isEmpty()) body.put("timestamp", timestamp);
				String comment = context.getParameter("comment", "");
				if (!comment.isEmpty()) body.put("comment", comment);
				HttpResponse<String> response = post(BASE_URL + "/users/" + encode(userId) + "/goals/" + encode(goalName) + "/datapoints.json", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String datapointId = context.getParameter("datapointId", "");
				HttpResponse<String> response = delete(BASE_URL + "/users/" + encode(userId) + "/goals/" + encode(goalName) + "/datapoints/" + encode(datapointId) + ".json?auth_token=" + encode(authToken), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/users/" + encode(userId) + "/goals/" + encode(goalName) + "/datapoints.json?auth_token=" + encode(authToken), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String datapointId = context.getParameter("datapointId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("auth_token", authToken);
				double value = toDouble(context.getParameters().get("value"), 0.0);
				if (value != 0.0) body.put("value", value);
				String comment = context.getParameter("comment", "");
				if (!comment.isEmpty()) body.put("comment", comment);
				HttpResponse<String> response = put(BASE_URL + "/users/" + encode(userId) + "/goals/" + encode(goalName) + "/datapoints/" + encode(datapointId) + ".json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown datapoint operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("datapoint")
						.options(List.of(
								ParameterOption.builder().name("Datapoint").value("datapoint").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("goalName").displayName("Goal Name")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The slug name of the goal.").build(),
				NodeParameter.builder()
						.name("value").displayName("Value")
						.type(ParameterType.NUMBER).defaultValue(0).build(),
				NodeParameter.builder()
						.name("datapointId").displayName("Datapoint ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("timestamp").displayName("Timestamp")
						.type(ParameterType.STRING).defaultValue("")
						.description("Unix timestamp (optional).").build(),
				NodeParameter.builder()
						.name("comment").displayName("Comment")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
