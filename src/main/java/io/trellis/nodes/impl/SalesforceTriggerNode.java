package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.time.Instant;
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

/**
 * Salesforce Trigger Node -- polls for new or updated records
 * (leads, contacts, accounts, opportunities) using SOQL queries.
 */
@Slf4j
@Node(
	type = "salesforceTrigger",
	displayName = "Salesforce Trigger",
	description = "Polls for new or updated records in Salesforce using SOQL queries",
	category = "CRM",
	icon = "salesforce",
	trigger = true,
	polling = true,
	credentials = {"salesforceOAuth2Api"}
)
public class SalesforceTriggerNode extends AbstractApiNode {

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
			.name("objectType").displayName("Object Type")
			.type(ParameterType.OPTIONS).required(true).defaultValue("Lead")
			.options(List.of(
				ParameterOption.builder().name("Lead").value("Lead").description("Poll for leads").build(),
				ParameterOption.builder().name("Contact").value("Contact").description("Poll for contacts").build(),
				ParameterOption.builder().name("Account").value("Account").description("Poll for accounts").build(),
				ParameterOption.builder().name("Opportunity").value("Opportunity").description("Poll for opportunities").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("triggerOn").displayName("Trigger On")
			.type(ParameterType.OPTIONS).required(true).defaultValue("new")
			.options(List.of(
				ParameterOption.builder().name("New Records").value("new")
					.description("Trigger when new records are created").build(),
				ParameterOption.builder().name("Updated Records").value("updated")
					.description("Trigger when records are updated").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional SOQL Fields")
			.type(ParameterType.STRING)
			.description("Comma-separated list of additional fields to select (e.g. Phone,Website).")
			.build());

		params.add(NodeParameter.builder()
			.name("additionalConditions").displayName("Additional SOQL WHERE Conditions")
			.type(ParameterType.STRING)
			.description("Additional WHERE conditions to append (e.g. AND Status = 'Open').")
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Maximum number of records to return per poll.")
			.build());

		return params;
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		try {
			String instanceUrl = String.valueOf(credentials.getOrDefault("instanceUrl",
					credentials.getOrDefault("baseUrl", "")));
			if (instanceUrl.endsWith("/")) {
				instanceUrl = instanceUrl.substring(0, instanceUrl.length() - 1);
			}
			String baseUrl = instanceUrl + "/services/data/v58.0";
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			Map<String, String> headers = authHeaders(accessToken);

			String objectType = context.getParameter("objectType", "Lead");
			String triggerOn = context.getParameter("triggerOn", "new");
			String additionalFields = context.getParameter("additionalFields", "");
			String additionalConditions = context.getParameter("additionalConditions", "");
			int limit = toInt(context.getParameter("limit", 100), 100);

			// Build field list
			String fields = getDefaultFields(objectType);
			if (!additionalFields.isEmpty()) {
				fields += "," + additionalFields;
			}

			// Build date filter
			String lastPollTime = (String) staticData.get("lastPollTime");
			String dateField = "new".equals(triggerOn) ? "CreatedDate" : "LastModifiedDate";

			StringBuilder soql = new StringBuilder();
			soql.append("SELECT ").append(fields);
			soql.append(" FROM ").append(objectType);

			if (lastPollTime != null) {
				soql.append(" WHERE ").append(dateField).append(" > ").append(lastPollTime);
			}

			if (!additionalConditions.isEmpty()) {
				if (lastPollTime != null) {
					soql.append(" ").append(additionalConditions);
				} else {
					soql.append(" WHERE 1=1 ").append(additionalConditions);
				}
			}

			soql.append(" ORDER BY ").append(dateField).append(" DESC");
			soql.append(" LIMIT ").append(limit);

			// Execute SOQL
			String url = baseUrl + "/query?q=" + encode(soql.toString());
			HttpResponse<String> response = get(url, headers);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Salesforce API error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object recordsObj = parsed.get("records");

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTime", Instant.now().toString());

			List<Map<String, Object>> items = new ArrayList<>();
			if (recordsObj instanceof List) {
				for (Object record : (List<?>) recordsObj) {
					if (record instanceof Map) {
						Map<String, Object> recordMap = new LinkedHashMap<>((Map<String, Object>) record);
						recordMap.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(recordMap));
					}
				}
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Salesforce trigger: found {} {} records", items.size(), objectType);
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Salesforce Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private String getDefaultFields(String objectType) {
		return switch (objectType) {
			case "Lead" -> "Id,Name,Email,Company,Status,CreatedDate,LastModifiedDate";
			case "Contact" -> "Id,Name,Email,Phone,AccountId,CreatedDate,LastModifiedDate";
			case "Account" -> "Id,Name,Industry,Type,CreatedDate,LastModifiedDate";
			case "Opportunity" -> "Id,Name,StageName,Amount,CloseDate,CreatedDate,LastModifiedDate";
			default -> "Id,Name,CreatedDate,LastModifiedDate";
		};
	}
}
