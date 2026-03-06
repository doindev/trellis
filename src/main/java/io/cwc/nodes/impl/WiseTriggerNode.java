package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.time.Instant;
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

/**
 * Wise Trigger Node -- polls for new transfers on a Wise profile.
 */
@Slf4j
@Node(
	type = "wiseTrigger",
	displayName = "Wise Trigger",
	description = "Polls for new transfers on a Wise account",
	category = "Finance",
	icon = "wise",
	trigger = true,
	polling = true,
	credentials = {"wiseApi"},
	searchOnly = true,
	triggerCategory = "Other"
)
public class WiseTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.transferwise.com";

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
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("profileId").displayName("Profile ID")
			.type(ParameterType.STRING).required(true)
			.description("The Wise profile ID to poll for transfers.")
			.build());

		params.add(NodeParameter.builder()
			.name("triggerOn").displayName("Trigger On")
			.type(ParameterType.OPTIONS).required(true).defaultValue("newTransfers")
			.options(List.of(
				ParameterOption.builder().name("New Transfers").value("newTransfers")
					.description("Trigger when new transfers are created").build(),
				ParameterOption.builder().name("Transfer Status Change").value("statusChange")
					.description("Trigger when a transfer status changes").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("statusFilter").displayName("Status Filter")
			.type(ParameterType.OPTIONS).defaultValue("")
			.displayOptions(Map.of("show", Map.of("triggerOn", List.of("statusChange"))))
			.options(List.of(
				ParameterOption.builder().name("All Statuses").value("").build(),
				ParameterOption.builder().name("Incoming Payment Waiting").value("incoming_payment_waiting").build(),
				ParameterOption.builder().name("Processing").value("processing").build(),
				ParameterOption.builder().name("Funds Converted").value("funds_converted").build(),
				ParameterOption.builder().name("Outgoing Payment Sent").value("outgoing_payment_sent").build(),
				ParameterOption.builder().name("Cancelled").value("cancelled").build(),
				ParameterOption.builder().name("Funds Refunded").value("funds_refunded").build(),
				ParameterOption.builder().name("Bounced Back").value("bounced_back").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.nestedParameters(List.of(
				NodeParameter.builder().name("limit").displayName("Limit")
					.type(ParameterType.NUMBER).defaultValue(10)
					.description("Maximum number of transfers to return per poll.").build()
			)).build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		try {
			String apiToken = String.valueOf(credentials.getOrDefault("apiToken",
				credentials.getOrDefault("apiKey", "")));
			Map<String, String> headers = getAuthHeaders(apiToken);

			String profileId = context.getParameter("profileId", "");
			String triggerOn = context.getParameter("triggerOn", "newTransfers");
			String statusFilter = context.getParameter("statusFilter", "");
			Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());
			int limit = toInt(additionalFields.get("limit"), 10);

			String now = Instant.now().toString();
			String lastPollTime = (String) staticData.getOrDefault("lastPollTime",
				Instant.now().minusSeconds(300).toString());
			Map<String, Object> lastSeenStatuses = (Map<String, Object>) staticData.getOrDefault("lastSeenStatuses",
				new LinkedHashMap<>());

			// Build transfers URL
			Map<String, Object> queryParams = new LinkedHashMap<>();
			queryParams.put("profile", profileId);
			queryParams.put("limit", limit);
			queryParams.put("createdDateStart", lastPollTime);
			if (statusFilter != null && !statusFilter.isEmpty()) {
				queryParams.put("status", statusFilter);
			}

			String url = buildUrl(BASE_URL + "/v1/transfers", queryParams);
			HttpResponse<String> response = get(url, headers);

			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTime", now);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Wise API error (HTTP " + response.statusCode() + "): " + body);
			}

			List<Map<String, Object>> transfers = parseArrayResponse(response);
			List<Map<String, Object>> transferItems = new ArrayList<>();

			Map<String, Object> newLastSeenStatuses = new LinkedHashMap<>(lastSeenStatuses);

			for (Map<String, Object> transfer : transfers) {
				String transferId = String.valueOf(transfer.getOrDefault("id", ""));
				boolean include = false;

				if ("newTransfers".equals(triggerOn)) {
					// Include all transfers from the polling window (API already filters by createdDateStart)
					include = true;
				} else if ("statusChange".equals(triggerOn)) {
					String currentStatus = String.valueOf(transfer.getOrDefault("status", ""));
					String previousStatus = (String) lastSeenStatuses.get(transferId);
					if (previousStatus == null || !previousStatus.equals(currentStatus)) {
						include = true;
					}
					newLastSeenStatuses.put(transferId, currentStatus);
				}

				if (include) {
					Map<String, Object> enriched = new LinkedHashMap<>(transfer);
					enriched.put("_triggerTimestamp", System.currentTimeMillis());
					enriched.put("_triggerEvent", triggerOn);
					transferItems.add(wrapInJson(enriched));
				}
			}

			newStaticData.put("lastSeenStatuses", newLastSeenStatuses);

			if (transferItems.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Wise trigger: found {} transfers", transferItems.size());
			return NodeExecutionResult.builder()
				.output(List.of(transferItems))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Wise Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String apiToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + apiToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
