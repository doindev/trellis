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
	type = "microsoftOneDriveTrigger",
	displayName = "Microsoft OneDrive Trigger",
	description = "Polls Microsoft OneDrive for new or modified files.",
	category = "Cloud Storage",
	icon = "microsoftOneDrive",
	trigger = true,
	polling = true,
	credentials = {"microsoftOneDriveOAuth2Api"}
)
public class MicrosoftOneDriveTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.microsoft.com/v1.0/me/drive";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("fileCreatedOrUpdated")
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
				.name("includeSubfolders").displayName("Include Subfolders")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.description("Also detect changes in subfolders.")
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String triggerOn = context.getParameter("triggerOn", "fileCreatedOrUpdated");
		String folderId = context.getParameter("folderId", "");

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			// Use the delta API for efficient change tracking
			String deltaLink = (String) staticData.get("deltaLink");

			String deltaUrl;
			if (deltaLink != null) {
				// Use the saved delta link for subsequent polls
				deltaUrl = deltaLink;
			} else if (!folderId.isEmpty()) {
				// Watch a specific folder
				deltaUrl = BASE_URL + "/items/" + encode(folderId) + "/delta";
			} else {
				// Watch the root
				deltaUrl = BASE_URL + "/root/delta";
			}

			// Collect all changed items across pages
			List<Map<String, Object>> allChanges = new ArrayList<>();
			String nextLink = deltaUrl;
			String newDeltaLink = null;

			while (nextLink != null) {
				HttpResponse<String> response = get(nextLink, headers);

				if (response.statusCode() >= 400) {
					String body = response.body() != null ? response.body() : "";
					if (body.length() > 300) body = body.substring(0, 300) + "...";
					return NodeExecutionResult.error("OneDrive Trigger: Delta poll failed (HTTP " + response.statusCode() + "): " + body);
				}

				Map<String, Object> parsed = parseResponse(response);

				Object value = parsed.get("value");
				if (value instanceof List) {
					for (Object item : (List<?>) value) {
						if (item instanceof Map) {
							allChanges.add((Map<String, Object>) item);
						}
					}
				}

				nextLink = (String) parsed.get("@odata.nextLink");
				if (parsed.containsKey("@odata.deltaLink")) {
					newDeltaLink = (String) parsed.get("@odata.deltaLink");
				}
			}

			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			if (newDeltaLink != null) {
				newStaticData.put("deltaLink", newDeltaLink);
			}

			// On first run (no saved deltaLink), just save the delta link and return empty
			if (deltaLink == null) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			// Filter changes based on trigger type
			List<Map<String, Object>> items = new ArrayList<>();
			for (Map<String, Object> change : allChanges) {
				// Skip deleted items
				if (change.containsKey("deleted")) continue;

				// Skip folders (we only trigger on files)
				Object folder = change.get("folder");
				if (folder != null) continue;

				// Apply trigger type filter
				boolean include = false;
				String createdDateTime = String.valueOf(change.getOrDefault("createdDateTime", ""));
				String lastModifiedDateTime = String.valueOf(change.getOrDefault("lastModifiedDateTime", ""));

				switch (triggerOn) {
					case "fileCreated":
						include = createdDateTime.equals(lastModifiedDateTime);
						break;
					case "fileUpdated":
						include = !createdDateTime.equals(lastModifiedDateTime);
						break;
					case "fileCreatedOrUpdated":
						include = true;
						break;
				}

				if (include) {
					Map<String, Object> item = new LinkedHashMap<>(change);
					item.put("_triggerTimestamp", System.currentTimeMillis());
					items.add(wrapInJson(item));
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("OneDrive trigger: found {} changed files", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Microsoft OneDrive Trigger error: " + e.getMessage(), e);
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
