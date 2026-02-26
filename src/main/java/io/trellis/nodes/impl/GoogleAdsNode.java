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

/**
 * Google Ads Node -- manage campaigns, ad groups, and ads using the
 * Google Ads API with Google Ads Query Language (GAQL).
 */
@Slf4j
@Node(
	type = "googleAds",
	displayName = "Google Ads",
	description = "Manage campaigns, ad groups, and ads in Google Ads using GAQL queries",
	category = "Google Services",
	icon = "googleAds",
	credentials = {"googleAdsOAuth2Api"}
)
public class GoogleAdsNode extends AbstractApiNode {

	private static final String BASE_URL = "https://googleads.googleapis.com/v15";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("campaign")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Campaign").value("campaign").description("Manage campaigns").build(),
				ParameterOption.builder().name("Ad Group").value("adGroup").description("Manage ad groups").build(),
				ParameterOption.builder().name("Ad").value("ad").description("Manage ads").build()
			)).build());

		// Campaign operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a campaign").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many campaigns").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a campaign").build()
			)).build());

		// Ad Group operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("adGroup"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get an ad group").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many ad groups").build()
			)).build());

		// Ad operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ad"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get an ad").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many ads").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("customerId").displayName("Customer ID")
			.type(ParameterType.STRING).required(true)
			.description("The Google Ads customer ID (without dashes, e.g. 1234567890).")
			.build());

		params.add(NodeParameter.builder()
			.name("campaignId").displayName("Campaign ID")
			.type(ParameterType.STRING)
			.description("The ID of the campaign.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"), "operation", List.of("get", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("adGroupId").displayName("Ad Group ID")
			.type(ParameterType.STRING)
			.description("The ID of the ad group.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("adGroup"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("adGroupAdId").displayName("Ad Group Ad ID")
			.type(ParameterType.STRING)
			.description("The resource name of the ad (e.g. customers/123/adGroupAds/456~789).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ad"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("gaqlQuery").displayName("GAQL Query")
			.type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 4))
			.description("Custom Google Ads Query Language (GAQL) query. Overrides default query when provided.")
			.build());

		params.add(NodeParameter.builder()
			.name("campaignStatus").displayName("Campaign Status")
			.type(ParameterType.OPTIONS)
			.description("New status for the campaign.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"), "operation", List.of("update"))))
			.options(List.of(
				ParameterOption.builder().name("Enabled").value("ENABLED").build(),
				ParameterOption.builder().name("Paused").value("PAUSED").build(),
				ParameterOption.builder().name("Removed").value("REMOVED").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("campaignName").displayName("Campaign Name")
			.type(ParameterType.STRING)
			.description("New name for the campaign.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("campaign"), "operation", List.of("update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Maximum number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "campaign");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String customerId = context.getParameter("customerId", "");

		try {
			Map<String, String> headers = authHeaders(credentials);
			return switch (resource) {
				case "campaign" -> executeCampaign(context, operation, customerId, headers);
				case "adGroup" -> executeAdGroup(context, operation, customerId, headers);
				case "ad" -> executeAd(context, operation, customerId, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Google Ads API error: " + e.getMessage(), e);
		}
	}

	// ========================= Campaign Operations =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeCampaign(NodeExecutionContext context, String operation,
			String customerId, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String campaignId = context.getParameter("campaignId", "");
				String gaql = context.getParameter("gaqlQuery", "");
				if (gaql.isEmpty()) {
					gaql = "SELECT campaign.id, campaign.name, campaign.status, campaign.advertising_channel_type, "
						 + "campaign.start_date, campaign.end_date, campaign.budget_amount_micros "
						 + "FROM campaign WHERE campaign.id = " + campaignId;
				}
				return executeGaqlQuery(customerId, gaql, headers);
			}
			case "getAll": {
				String gaql = context.getParameter("gaqlQuery", "");
				int limit = toInt(context.getParameter("limit", 100), 100);
				if (gaql.isEmpty()) {
					gaql = "SELECT campaign.id, campaign.name, campaign.status, campaign.advertising_channel_type, "
						 + "campaign.start_date, campaign.end_date "
						 + "FROM campaign ORDER BY campaign.id LIMIT " + limit;
				}
				return executeGaqlQuery(customerId, gaql, headers);
			}
			case "update": {
				String campaignId = context.getParameter("campaignId", "");
				String status = context.getParameter("campaignStatus", "");
				String name = context.getParameter("campaignName", "");

				Map<String, Object> campaignOp = new LinkedHashMap<>();
				Map<String, Object> campaign = new LinkedHashMap<>();
				campaign.put("resourceName", "customers/" + customerId + "/campaigns/" + campaignId);
				if (!status.isEmpty()) campaign.put("status", status);
				if (!name.isEmpty()) campaign.put("name", name);

				String updateMask = "";
				if (!status.isEmpty()) updateMask += "status";
				if (!name.isEmpty()) {
					if (!updateMask.isEmpty()) updateMask += ",";
					updateMask += "name";
				}

				campaignOp.put("update", campaign);
				campaignOp.put("updateMask", updateMask);

				Map<String, Object> body = Map.of("operations", List.of(campaignOp));
				String url = BASE_URL + "/customers/" + customerId + "/campaigns:mutate";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown campaign operation: " + operation);
		}
	}

	// ========================= Ad Group Operations =========================

	private NodeExecutionResult executeAdGroup(NodeExecutionContext context, String operation,
			String customerId, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String adGroupId = context.getParameter("adGroupId", "");
				String gaql = context.getParameter("gaqlQuery", "");
				if (gaql.isEmpty()) {
					gaql = "SELECT ad_group.id, ad_group.name, ad_group.status, ad_group.campaign, ad_group.type "
						 + "FROM ad_group WHERE ad_group.id = " + adGroupId;
				}
				return executeGaqlQuery(customerId, gaql, headers);
			}
			case "getAll": {
				String gaql = context.getParameter("gaqlQuery", "");
				int limit = toInt(context.getParameter("limit", 100), 100);
				if (gaql.isEmpty()) {
					gaql = "SELECT ad_group.id, ad_group.name, ad_group.status, ad_group.campaign, ad_group.type "
						 + "FROM ad_group ORDER BY ad_group.id LIMIT " + limit;
				}
				return executeGaqlQuery(customerId, gaql, headers);
			}
			default:
				return NodeExecutionResult.error("Unknown adGroup operation: " + operation);
		}
	}

	// ========================= Ad Operations =========================

	private NodeExecutionResult executeAd(NodeExecutionContext context, String operation,
			String customerId, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String adGroupAdId = context.getParameter("adGroupAdId", "");
				String gaql = context.getParameter("gaqlQuery", "");
				if (gaql.isEmpty()) {
					gaql = "SELECT ad_group_ad.ad.id, ad_group_ad.ad.name, ad_group_ad.ad.type, "
						 + "ad_group_ad.status, ad_group_ad.ad_group "
						 + "FROM ad_group_ad WHERE ad_group_ad.ad.id = " + adGroupAdId;
				}
				return executeGaqlQuery(customerId, gaql, headers);
			}
			case "getAll": {
				String gaql = context.getParameter("gaqlQuery", "");
				int limit = toInt(context.getParameter("limit", 100), 100);
				if (gaql.isEmpty()) {
					gaql = "SELECT ad_group_ad.ad.id, ad_group_ad.ad.name, ad_group_ad.ad.type, "
						 + "ad_group_ad.status, ad_group_ad.ad_group "
						 + "FROM ad_group_ad ORDER BY ad_group_ad.ad.id LIMIT " + limit;
				}
				return executeGaqlQuery(customerId, gaql, headers);
			}
			default:
				return NodeExecutionResult.error("Unknown ad operation: " + operation);
		}
	}

	// ========================= GAQL Query Execution =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeGaqlQuery(String customerId, String gaql,
			Map<String, String> headers) throws Exception {
		String url = BASE_URL + "/customers/" + customerId + "/googleAds:searchStream";
		Map<String, Object> body = Map.of("query", gaql);
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			return googleAdsError(response);
		}

		// Response is a JSON array of batches, each containing results
		List<Map<String, Object>> parsedBatches = parseArrayResponse(response);
		List<Map<String, Object>> allResults = new ArrayList<>();

		for (Map<String, Object> batch : parsedBatches) {
			Object results = batch.get("results");
			if (results instanceof List) {
				for (Object result : (List<?>) results) {
					if (result instanceof Map) {
						allResults.add(wrapInJson(result));
					}
				}
			}
		}

		if (allResults.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		return NodeExecutionResult.success(allResults);
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		String developerToken = String.valueOf(credentials.getOrDefault("developerToken", ""));
		String loginCustomerId = String.valueOf(credentials.getOrDefault("loginCustomerId", ""));

		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("developer-token", developerToken);
		if (!loginCustomerId.isEmpty()) {
			headers.put("login-customer-id", loginCustomerId);
		}
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return googleAdsError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult googleAdsError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Google Ads API error (HTTP " + response.statusCode() + "): " + body);
	}
}
