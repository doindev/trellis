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
	type = "googleDriveTrigger",
	displayName = "Google Drive Trigger",
	description = "Polls Google Drive for new or modified files.",
	category = "Cloud Storage",
	icon = "googleDrive",
	trigger = true,
	polling = true,
	credentials = {"googleDriveOAuth2Api"}
)
public class GoogleDriveTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.googleapis.com/drive/v3";

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
				.name("triggerOn").displayName("Trigger On")
				.type(ParameterType.OPTIONS).required(true).defaultValue("fileCreated")
				.options(List.of(
					ParameterOption.builder().name("File Created").value("fileCreated")
						.description("Trigger when a new file is created").build(),
					ParameterOption.builder().name("File Updated").value("fileUpdated")
						.description("Trigger when a file is modified").build(),
					ParameterOption.builder().name("File Created or Updated").value("fileCreatedOrUpdated")
						.description("Trigger when a file is created or modified").build()
				)).build(),

			NodeParameter.builder()
				.name("folderId").displayName("Watch Folder ID")
				.type(ParameterType.STRING)
				.description("Only watch for changes within this folder. Leave empty to watch the entire drive.")
				.build(),

			NodeParameter.builder()
				.name("driveId").displayName("Shared Drive ID")
				.type(ParameterType.STRING)
				.description("The ID of the shared drive to watch. Leave empty for 'My Drive'.")
				.build(),

			NodeParameter.builder()
				.name("includeItemsFromAllDrives").displayName("Include All Drives")
				.type(ParameterType.BOOLEAN).defaultValue(false)
				.description("Include changes from shared drives.")
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String triggerOn = context.getParameter("triggerOn", "fileCreated");
		String folderId = context.getParameter("folderId", "");
		String driveId = context.getParameter("driveId", "");
		boolean includeAllDrives = toBoolean(context.getParameter("includeItemsFromAllDrives", false), false);

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			// Use the Changes API with a start page token for efficient polling
			String pageToken = (String) staticData.get("startPageToken");

			if (pageToken == null) {
				// First run: get the start page token
				String startTokenUrl = BASE_URL + "/changes/startPageToken";
				if (!driveId.isEmpty()) {
					startTokenUrl += "?driveId=" + encode(driveId) + "&supportsAllDrives=true";
				}

				HttpResponse<String> tokenResponse = get(startTokenUrl, headers);
				if (tokenResponse.statusCode() >= 400) {
					String body = tokenResponse.body() != null ? tokenResponse.body() : "";
					if (body.length() > 300) body = body.substring(0, 300) + "...";
					return NodeExecutionResult.error("Google Drive Trigger: Failed to get start page token (HTTP " + tokenResponse.statusCode() + "): " + body);
				}

				Map<String, Object> tokenParsed = parseResponse(tokenResponse);
				pageToken = String.valueOf(tokenParsed.get("startPageToken"));

				Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
				newStaticData.put("startPageToken", pageToken);

				// Return empty on first poll (just initializing the token)
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			// Poll for changes since the last page token
			String changesUrl = BASE_URL + "/changes?pageToken=" + encode(pageToken)
				+ "&fields=nextPageToken,newStartPageToken,changes(fileId,file(id,name,mimeType,size,modifiedTime,createdTime,parents,trashed),removed,time,type)"
				+ "&includeRemoved=false";

			if (includeAllDrives || !driveId.isEmpty()) {
				changesUrl += "&includeItemsFromAllDrives=true&supportsAllDrives=true";
			}
			if (!driveId.isEmpty()) {
				changesUrl += "&driveId=" + encode(driveId);
			}

			HttpResponse<String> response = get(changesUrl, headers);
			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Google Drive Trigger: Changes poll failed (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);

			// Update the page token for next poll
			String newStartPageToken = (String) parsed.get("newStartPageToken");
			String nextPageToken = (String) parsed.get("nextPageToken");
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("startPageToken", newStartPageToken != null ? newStartPageToken : nextPageToken);

			// Process changes
			Object changesObj = parsed.get("changes");
			List<Map<String, Object>> items = new ArrayList<>();

			if (changesObj instanceof List) {
				for (Object change : (List<?>) changesObj) {
					if (!(change instanceof Map)) continue;
					Map<String, Object> changeMap = (Map<String, Object>) change;

					// Skip removed files
					if (Boolean.TRUE.equals(changeMap.get("removed"))) continue;

					Object fileObj = changeMap.get("file");
					if (!(fileObj instanceof Map)) continue;
					Map<String, Object> file = (Map<String, Object>) fileObj;

					// Skip trashed files
					if (Boolean.TRUE.equals(file.get("trashed"))) continue;

					// Apply folder filter
					if (!folderId.isEmpty()) {
						Object parents = file.get("parents");
						if (parents instanceof List) {
							if (!((List<?>) parents).contains(folderId)) continue;
						} else {
							continue;
						}
					}

					// Apply trigger type filter
					boolean include = false;
					String createdTime = String.valueOf(file.getOrDefault("createdTime", ""));
					String modifiedTime = String.valueOf(file.getOrDefault("modifiedTime", ""));

					switch (triggerOn) {
						case "fileCreated":
							// Include if createdTime equals modifiedTime (new file)
							include = createdTime.equals(modifiedTime);
							break;
						case "fileUpdated":
							// Include if modifiedTime is different from createdTime (updated file)
							include = !createdTime.equals(modifiedTime);
							break;
						case "fileCreatedOrUpdated":
							include = true;
							break;
					}

					if (include) {
						Map<String, Object> item = new LinkedHashMap<>(file);
						item.put("_changeType", changeMap.get("type"));
						item.put("_changeTime", changeMap.get("time"));
						item.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(item));
					}
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Google Drive trigger: found {} changed files", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Google Drive Trigger error: " + e.getMessage(), e);
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
