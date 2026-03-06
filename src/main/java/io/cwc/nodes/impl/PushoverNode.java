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
 * Pushover — send push notifications via the Pushover API.
 */
@Node(
		type = "pushover",
		displayName = "Pushover",
		description = "Send push notifications via Pushover",
		category = "Communication",
		icon = "bell",
		credentials = {"pushoverApi"}
)
public class PushoverNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.pushover.net/1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String userKey = context.getParameter("userKey", "");
			String message = context.getParameter("message", "");
			int priority = toInt(context.getParameters().get("priority"), 0);
			String title = context.getParameter("title", "");
			String url = context.getParameter("url", "");
			String sound = context.getParameter("sound", "pushover");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> body = new HashMap<>();
					body.put("token", apiKey);
					body.put("user", userKey);
					body.put("message", message);
					body.put("priority", priority);
					if (!title.isBlank()) body.put("title", title);
					if (!url.isBlank()) body.put("url", url);
					if (!sound.isBlank()) body.put("sound", sound);

					if (priority == 2) {
						int retry = toInt(context.getParameters().get("retry"), 30);
						int expire = toInt(context.getParameters().get("expire"), 3600);
						body.put("retry", Math.max(retry, 30));
						body.put("expire", Math.min(expire, 10800));
					}

					var response = post(BASE_URL + "/messages.json", body,
							Map.of("Content-Type", "application/json"));
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
						.name("userKey").displayName("User Key")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Pushover user/group key.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Notification message.").build(),
				NodeParameter.builder()
						.name("priority").displayName("Priority")
						.type(ParameterType.OPTIONS).defaultValue("0")
						.options(List.of(
								ParameterOption.builder().name("Lowest").value("-2").build(),
								ParameterOption.builder().name("Low").value("-1").build(),
								ParameterOption.builder().name("Normal").value("0").build(),
								ParameterOption.builder().name("High").value("1").build(),
								ParameterOption.builder().name("Emergency").value("2").build()
						)).build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sound").displayName("Sound")
						.type(ParameterType.STRING).defaultValue("pushover")
						.description("Notification sound name.").build(),
				NodeParameter.builder()
						.name("retry").displayName("Retry (seconds)")
						.type(ParameterType.NUMBER).defaultValue(30)
						.description("Retry interval for emergency priority (min 30s).").build(),
				NodeParameter.builder()
						.name("expire").displayName("Expire (seconds)")
						.type(ParameterType.NUMBER).defaultValue(3600)
						.description("Expiry time for emergency priority (max 10800s).").build()
		);
	}
}
