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
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Pushcut — send push notifications via the Pushcut API (iOS).
 */
@Node(
		type = "pushcut",
		displayName = "Pushcut",
		description = "Send push notifications via Pushcut",
		category = "Communication",
		icon = "bell",
		credentials = {"pushcutApi"}
)
public class PushcutNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.pushcut.io/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String notificationName = context.getParameter("notificationName", "");
			String title = context.getParameter("title", "");
			String text = context.getParameter("text", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> body = new HashMap<>();
					if (!title.isBlank()) body.put("title", title);
					if (!text.isBlank()) body.put("text", text);

					var response = post(
							BASE_URL + "/notifications/" + encode(notificationName),
							body,
							Map.of("API-Key", apiKey, "Content-Type", "application/json"));
					results.add(wrapInJson(parseResponse(response)));
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
						.name("notificationName").displayName("Notification Name")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Name of the notification to trigger.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Override notification title.").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Override notification text.").build()
		);
	}
}
