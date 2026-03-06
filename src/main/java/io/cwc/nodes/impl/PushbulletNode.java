package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Pushbullet — create, delete, get and update push notifications via the Pushbullet API.
 */
@Node(
		type = "pushbullet",
		displayName = "Pushbullet",
		description = "Create, delete and manage push notifications with Pushbullet",
		category = "Communication / Chat & Messaging",
		icon = "pushbullet",
		credentials = {"pushbulletOAuth2Api"}
)
public class PushbulletNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.pushbullet.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("Access-Token", accessToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> handleCreate(context, headers);
					case "delete" -> handleDelete(context, headers);
					case "getMany" -> handleGetMany(context, headers);
					case "update" -> handleUpdate(context, headers);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	private Map<String, Object> handleCreate(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String type = context.getParameter("type", "note");
		String title = context.getParameter("title", "");
		String body = context.getParameter("body", "");
		String url = context.getParameter("url", "");
		String target = context.getParameter("target", "default");
		String targetValue = context.getParameter("targetValue", "");

		Map<String, Object> requestBody = new LinkedHashMap<>();
		requestBody.put("type", type);
		if (!title.isBlank()) {
			requestBody.put("title", title);
		}
		if (!body.isBlank()) {
			requestBody.put("body", body);
		}
		if ("link".equals(type) && !url.isBlank()) {
			requestBody.put("url", url);
		}

		// Set target
		if (!"default".equals(target) && !targetValue.isBlank()) {
			requestBody.put(target, targetValue);
		}

		HttpResponse<String> response = post(BASE_URL + "/pushes", requestBody, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleDelete(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String pushId = context.getParameter("pushId", "");
		HttpResponse<String> response = delete(BASE_URL + "/pushes/" + encode(pushId), headers);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("pushId", pushId);
		return result;
	}

	private Map<String, Object> handleGetMany(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		boolean active = toBoolean(context.getParameters().get("active"), true);

		String url = BASE_URL + "/pushes?active=" + active;
		if (!returnAll) {
			url += "&limit=" + Math.min(limit, 500);
		}

		if (returnAll) {
			List<Object> allPushes = new ArrayList<>();
			String cursor = null;
			boolean hasMore = true;

			while (hasMore) {
				String pageUrl = url + "&limit=500";
				if (cursor != null) {
					pageUrl += "&cursor=" + encode(cursor);
				}
				HttpResponse<String> response = get(pageUrl, headers);
				Map<String, Object> parsed = parseResponse(response);

				@SuppressWarnings("unchecked")
				List<Object> pushes = (List<Object>) parsed.get("pushes");
				if (pushes != null && !pushes.isEmpty()) {
					allPushes.addAll(pushes);
				}

				cursor = (String) parsed.get("cursor");
				hasMore = cursor != null && !cursor.isBlank();
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("pushes", allPushes);
			return result;
		} else {
			HttpResponse<String> response = get(url, headers);
			return parseResponse(response);
		}
	}

	private Map<String, Object> handleUpdate(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String pushId = context.getParameter("pushId", "");
		boolean dismissed = toBoolean(context.getParameters().get("dismissed"), false);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dismissed", dismissed);

		HttpResponse<String> response = post(BASE_URL + "/pushes/" + encode(pushId), body, headers);
		return parseResponse(response);
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
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("type").displayName("Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("note")
						.options(List.of(
								ParameterOption.builder().name("Note").value("note").build(),
								ParameterOption.builder().name("Link").value("link").build(),
								ParameterOption.builder().name("File").value("file").build()
						)).build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Title of the push notification.").build(),
				NodeParameter.builder()
						.name("body").displayName("Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("Body of the push notification.").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL to open (link type only).").build(),
				NodeParameter.builder()
						.name("target").displayName("Target")
						.type(ParameterType.OPTIONS)
						.defaultValue("default")
						.options(List.of(
								ParameterOption.builder().name("Default (All Devices)").value("default").build(),
								ParameterOption.builder().name("Device").value("device_iden").build(),
								ParameterOption.builder().name("Channel Tag").value("channel_tag").build(),
								ParameterOption.builder().name("Email").value("email").build()
						)).build(),
				NodeParameter.builder()
						.name("targetValue").displayName("Target Value")
						.type(ParameterType.STRING).defaultValue("")
						.description("Identifier for the target (device ID, channel tag, or email).").build(),
				NodeParameter.builder()
						.name("pushId").displayName("Push ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the push.").build(),
				NodeParameter.builder()
						.name("dismissed").displayName("Dismissed")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Mark the push notification as dismissed.").build(),
				NodeParameter.builder()
						.name("active").displayName("Active Only")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Only return active pushes.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of results to return.").build()
		);
	}
}
