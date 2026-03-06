package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "boxTrigger",
	displayName = "Box Trigger",
	description = "Triggers when events occur in Box (file uploads, updates, deletions, etc.).",
	category = "Cloud Storage",
	icon = "box",
	trigger = true,
	credentials = {"boxOAuth2Api"}
)
public class BoxTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.box.com/2.0";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("eventType").displayName("Event Type")
				.type(ParameterType.OPTIONS).required(true).defaultValue("all")
				.options(List.of(
					ParameterOption.builder().name("All Events").value("all")
						.description("Trigger on all events").build(),
					ParameterOption.builder().name("File Uploaded").value("ITEM_UPLOAD")
						.description("Trigger when a file is uploaded").build(),
					ParameterOption.builder().name("File Downloaded").value("ITEM_DOWNLOAD")
						.description("Trigger when a file is downloaded").build(),
					ParameterOption.builder().name("File Previewed").value("ITEM_PREVIEW")
						.description("Trigger when a file is previewed").build(),
					ParameterOption.builder().name("File Moved").value("ITEM_MOVE")
						.description("Trigger when a file is moved").build(),
					ParameterOption.builder().name("File Copied").value("ITEM_COPY")
						.description("Trigger when a file is copied").build(),
					ParameterOption.builder().name("File Trashed").value("ITEM_TRASH")
						.description("Trigger when a file is trashed").build(),
					ParameterOption.builder().name("Comment Created").value("COMMENT_CREATE")
						.description("Trigger when a comment is created").build(),
					ParameterOption.builder().name("Folder Created").value("FOLDER_CREATE")
						.description("Trigger when a folder is created").build()
				)).build(),

			NodeParameter.builder()
				.name("streamPosition").displayName("Stream Position")
				.type(ParameterType.STRING)
				.description("Start position for event stream. Leave empty to start from now.")
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String eventType = context.getParameter("eventType", "all");

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			// Get stream position
			String streamPosition = staticData.containsKey("streamPosition")
				? String.valueOf(staticData.get("streamPosition"))
				: context.getParameter("streamPosition", "now");

			String url = BASE_URL + "/events?stream_type=changes&stream_position=" + encode(streamPosition);

			HttpResponse<String> response = get(url, headers);
			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Box Trigger error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);

			// Update stream position
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			Object nextStreamPosition = parsed.get("next_stream_position");
			if (nextStreamPosition != null) {
				newStaticData.put("streamPosition", String.valueOf(nextStreamPosition));
			}

			// Extract events
			Object entriesObj = parsed.get("entries");
			List<Map<String, Object>> items = new ArrayList<>();
			if (entriesObj instanceof List) {
				for (Object entry : (List<?>) entriesObj) {
					if (entry instanceof Map) {
						Map<String, Object> event = (Map<String, Object>) entry;
						String type = String.valueOf(event.getOrDefault("event_type", ""));

						if ("all".equals(eventType) || eventType.equals(type)) {
							Map<String, Object> item = new LinkedHashMap<>(event);
							item.put("_triggerTimestamp", System.currentTimeMillis());
							items.add(wrapInJson(item));
						}
					}
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Box trigger: found {} events", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Box Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		return headers;
	}
}
