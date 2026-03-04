package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Airtable Trigger — polls an Airtable base/table for new or updated records.
 * Uses the Airtable REST API to detect changes by comparing record modification times.
 */
@Slf4j
@Node(
		type = "airtableTrigger",
		displayName = "Airtable Trigger",
		description = "Polls Airtable for new or updated records",
		category = "Data",
		icon = "airtable",
		trigger = true,
		polling = true,
		credentials = {"airtableApi"},
		other = true
)
public class AirtableTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.airtable.com/v0";

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("baseId").displayName("Base ID")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The ID of the Airtable base (starts with 'app').").build(),
				NodeParameter.builder()
						.name("tableId").displayName("Table ID or Name")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The ID or name of the table to poll.").build(),
				NodeParameter.builder()
						.name("triggerOn").displayName("Trigger On")
						.type(ParameterType.OPTIONS).required(true).defaultValue("newRecords")
						.options(List.of(
								ParameterOption.builder().name("New Records").value("newRecords")
										.description("Trigger when new records are created").build(),
								ParameterOption.builder().name("Updated Records").value("updatedRecords")
										.description("Trigger when existing records are modified").build(),
								ParameterOption.builder().name("New or Updated Records").value("newOrUpdated")
										.description("Trigger on both new and updated records").build()
						)).build(),
				NodeParameter.builder()
						.name("viewId").displayName("View ID (Optional)")
						.type(ParameterType.STRING).defaultValue("")
						.description("Only poll records from this specific view.").build(),
				NodeParameter.builder()
						.name("filterFormula").displayName("Filter Formula")
						.type(ParameterType.STRING).defaultValue("")
						.description("Airtable formula to filter records (e.g., {Status}='Active').").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated field names to include. Leave empty for all fields.").build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String accessToken = context.getCredentialString("accessToken", "");
		String baseId = context.getParameter("baseId", "");
		String tableId = context.getParameter("tableId", "");
		String triggerOn = context.getParameter("triggerOn", "newRecords");
		String viewId = context.getParameter("viewId", "");
		String filterFormula = context.getParameter("filterFormula", "");
		String fields = context.getParameter("fields", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		try {
			// Build the list records URL
			StringBuilder url = new StringBuilder(BASE_URL + "/" + encode(baseId) + "/" + encode(tableId));
			url.append("?pageSize=100");

			if (!viewId.isEmpty()) url.append("&view=").append(encode(viewId));
			if (!filterFormula.isEmpty()) url.append("&filterByFormula=").append(encode(filterFormula));
			if (!fields.isEmpty()) {
				for (String field : fields.split("\\s*,\\s*")) {
					url.append("&fields%5B%5D=").append(encode(field.trim()));
				}
			}

			// Sort by created time for detecting new records
			url.append("&sort%5B0%5D%5Bfield%5D=Created&sort%5B0%5D%5Bdirection%5D=desc");

			HttpResponse<String> response = get(url.toString(), headers);
			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Airtable poll failed (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			List<Map<String, Object>> records = (List<Map<String, Object>>) parsed.getOrDefault("records", List.of());

			// Get last known record IDs and timestamps from static data
			Set<String> lastKnownIds = new HashSet<>();
			Object lastIdsObj = staticData.get("lastKnownIds");
			if (lastIdsObj instanceof List) {
				for (Object id : (List<?>) lastIdsObj) {
					lastKnownIds.add(String.valueOf(id));
				}
			}

			Map<String, String> lastModifiedTimes = new LinkedHashMap<>();
			Object lastModObj = staticData.get("lastModifiedTimes");
			if (lastModObj instanceof Map) {
				for (Map.Entry<String, Object> entry : ((Map<String, Object>) lastModObj).entrySet()) {
					lastModifiedTimes.put(entry.getKey(), String.valueOf(entry.getValue()));
				}
			}

			List<Map<String, Object>> newItems = new ArrayList<>();
			Set<String> currentIds = new HashSet<>();
			Map<String, String> currentModifiedTimes = new LinkedHashMap<>();

			for (Map<String, Object> record : records) {
				String recordId = String.valueOf(record.getOrDefault("id", ""));
				String createdTime = String.valueOf(record.getOrDefault("createdTime", ""));
				Map<String, Object> recordFields = (Map<String, Object>) record.getOrDefault("fields", Map.of());

				currentIds.add(recordId);

				// Use Last Modified Time field if available, otherwise use createdTime
				String modifiedTime = recordFields.containsKey("Last Modified")
						? String.valueOf(recordFields.get("Last Modified"))
						: createdTime;
				currentModifiedTimes.put(recordId, modifiedTime);

				boolean isNew = !lastKnownIds.contains(recordId);
				boolean isUpdated = !isNew && lastModifiedTimes.containsKey(recordId)
						&& !lastModifiedTimes.get(recordId).equals(modifiedTime);

				boolean shouldInclude = switch (triggerOn) {
					case "newRecords" -> isNew;
					case "updatedRecords" -> isUpdated;
					case "newOrUpdated" -> isNew || isUpdated;
					default -> isNew;
				};

				// On first poll (no static data), skip to just populate static data
				if (lastKnownIds.isEmpty() && lastModifiedTimes.isEmpty()) {
					shouldInclude = false;
				}

				if (shouldInclude) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("id", recordId);
					item.put("createdTime", createdTime);
					item.putAll(recordFields);
					item.put("_triggerTimestamp", System.currentTimeMillis());
					item.put("_isNew", isNew);
					newItems.add(wrapInJson(item));
				}
			}

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastKnownIds", new ArrayList<>(currentIds));
			newStaticData.put("lastModifiedTimes", currentModifiedTimes);

			if (newItems.isEmpty()) {
				log.debug("Airtable trigger: no new/updated records found");
				return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
			}

			log.debug("Airtable trigger: found {} new/updated records", newItems.size());
			return NodeExecutionResult.builder()
					.output(List.of(newItems))
					.staticData(newStaticData)
					.build();
		} catch (Exception e) {
			return handleError(context, "Airtable Trigger error: " + e.getMessage(), e);
		}
	}
}
