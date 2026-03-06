package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Gotify — send and manage messages via a self-hosted Gotify push notification server.
 */
@Node(
		type = "gotify",
		displayName = "Gotify",
		description = "Send and manage messages via Gotify",
		category = "Communication",
		icon = "bell",
		credentials = {"gotifyApi"}
)
public class GotifyNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String baseUrl = context.getCredentialString("url", "http://localhost:8080");
			String appToken = context.getCredentialString("appApiToken", "");
			String clientToken = context.getCredentialString("clientApiToken", "");
			String operation = context.getParameter("operation", "create");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> result;
					switch (operation) {
						case "create" -> {
							String message = context.getParameter("message", "");
							String title = context.getParameter("title", "");
							int priority = toInt(context.getParameters().get("priority"), 0);

							Map<String, Object> body = new HashMap<>();
							body.put("message", message);
							if (!title.isBlank()) body.put("title", title);
							if (priority > 0) body.put("priority", priority);

							var response = post(baseUrl + "/message", body,
									Map.of("X-Gotify-Key", appToken, "Content-Type", "application/json"));
							result = parseResponse(response);
						}
						case "delete" -> {
							String messageId = context.getParameter("messageId", "");
							var response = delete(baseUrl + "/message/" + encode(messageId),
									Map.of("X-Gotify-Key", clientToken));
							result = Map.of("success", response.statusCode() < 300);
						}
						case "getAll" -> {
							int limit = toInt(context.getParameters().get("limit"), 20);
							var response = get(baseUrl + "/message?limit=" + limit,
									Map.of("X-Gotify-Key", clientToken));
							result = parseResponse(response);
						}
						default -> result = Map.of("error", "Unknown operation: " + operation);
					}
					results.add(wrapInJson(result));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get Many").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("The message content to send.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Optional title for the message.").build(),
				NodeParameter.builder()
						.name("priority").displayName("Priority")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Message priority (0-10).").build(),
				NodeParameter.builder()
						.name("messageId").displayName("Message ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the message to delete.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(20)
						.description("Max number of messages to return.").build()
		);
	}
}
