package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Lemlist — manage email outreach campaigns, leads, and enrichment using the Lemlist API.
 */
@Node(
		type = "lemlist",
		displayName = "Lemlist",
		description = "Manage email outreach with Lemlist",
		category = "Marketing",
		icon = "lemlist",
		credentials = {"lemlistApi"},
		searchOnly = true
)
public class LemlistNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.lemlist.com/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "lead");
		String operation = context.getParameter("operation", "getAll");

		String credentials = Base64.getEncoder().encodeToString((":" + apiKey).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "activity" -> handleActivity(context, headers, operation);
					case "campaign" -> handleCampaign(context, headers, operation);
					case "enrichment" -> handleEnrichment(context, headers, operation);
					case "lead" -> handleLead(context, headers, operation);
					case "team" -> handleTeam(context, headers, operation);
					case "unsubscribe" -> handleUnsubscribe(context, headers, operation);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
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

	private Map<String, Object> handleActivity(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/activities?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown activity operation: " + operation);
		};
	}

	private Map<String, Object> handleCampaign(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/campaigns", headers);
				yield parseResponse(response);
			}
			case "getStats" -> {
				String campaignId = context.getParameter("campaignId", "");
				StringBuilder url = new StringBuilder(BASE_URL + "/campaigns/" + encode(campaignId) + "/stats");
				String startDate = context.getParameter("startDate", "");
				String endDate = context.getParameter("endDate", "");
				if (!startDate.isEmpty() || !endDate.isEmpty()) {
					url.append("?");
					if (!startDate.isEmpty()) url.append("startDate=").append(encode(startDate));
					if (!startDate.isEmpty() && !endDate.isEmpty()) url.append("&");
					if (!endDate.isEmpty()) url.append("endDate=").append(encode(endDate));
				}
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown campaign operation: " + operation);
		};
	}

	private Map<String, Object> handleEnrichment(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String enrichId = context.getParameter("enrichId", "");
				HttpResponse<String> response = get(BASE_URL + "/enrich/" + encode(enrichId), headers);
				yield parseResponse(response);
			}
			case "enrichLead" -> {
				String leadId = context.getParameter("leadId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("findEmail", toBoolean(context.getParameters().get("findEmail"), false));
				body.put("verifyEmail", toBoolean(context.getParameters().get("verifyEmail"), false));
				body.put("linkedinEnrichment", toBoolean(context.getParameters().get("linkedinEnrichment"), false));
				body.put("findPhone", toBoolean(context.getParameters().get("findPhone"), false));
				HttpResponse<String> response = post(BASE_URL + "/leads/" + encode(leadId) + "/enrich/", body, headers);
				yield parseResponse(response);
			}
			case "enrichPerson" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("findEmail", toBoolean(context.getParameters().get("findEmail"), false));
				body.put("verifyEmail", toBoolean(context.getParameters().get("verifyEmail"), false));
				body.put("linkedinEnrichment", toBoolean(context.getParameters().get("linkedinEnrichment"), false));
				body.put("findPhone", toBoolean(context.getParameters().get("findPhone"), false));
				String linkedinUrl = context.getParameter("linkedinUrl", "");
				if (!linkedinUrl.isEmpty()) body.put("linkedinUrl", linkedinUrl);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("firstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("lastName", lastName);
				String companyName = context.getParameter("companyName", "");
				if (!companyName.isEmpty()) body.put("companyName", companyName);
				HttpResponse<String> response = post(BASE_URL + "/enrich/", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown enrichment operation: " + operation);
		};
	}

	private Map<String, Object> handleLead(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String campaignId = context.getParameter("campaignId", "");
				String email = context.getParameter("email", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("firstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("lastName", lastName);
				String companyName = context.getParameter("companyName", "");
				if (!companyName.isEmpty()) body.put("companyName", companyName);
				boolean deduplicate = toBoolean(context.getParameters().get("deduplicate"), false);
				String url = BASE_URL + "/campaigns/" + encode(campaignId) + "/leads/" + encode(email);
				if (deduplicate) url += "?deduplicate=true";
				HttpResponse<String> response = post(url, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String campaignId = context.getParameter("campaignId", "");
				String email = context.getParameter("email", "");
				HttpResponse<String> response = delete(BASE_URL + "/campaigns/" + encode(campaignId) + "/leads/" + encode(email) + "?action=remove", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String email = context.getParameter("email", "");
				HttpResponse<String> response = get(BASE_URL + "/leads/" + encode(email), headers);
				yield parseResponse(response);
			}
			case "unsubscribe" -> {
				String campaignId = context.getParameter("campaignId", "");
				String email = context.getParameter("email", "");
				HttpResponse<String> response = delete(BASE_URL + "/campaigns/" + encode(campaignId) + "/leads/" + encode(email), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown lead operation: " + operation);
		};
	}

	private Map<String, Object> handleTeam(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				HttpResponse<String> response = get(BASE_URL + "/team", headers);
				yield parseResponse(response);
			}
			case "getCredits" -> {
				HttpResponse<String> response = get(BASE_URL + "/team/credits", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown team operation: " + operation);
		};
	}

	private Map<String, Object> handleUnsubscribe(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "add" -> {
				String email = context.getParameter("email", "");
				HttpResponse<String> response = post(BASE_URL + "/unsubscribes/" + encode(email), Map.of(), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String email = context.getParameter("email", "");
				HttpResponse<String> response = delete(BASE_URL + "/unsubscribes/" + encode(email), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/unsubscribes?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown unsubscribe operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("lead")
						.options(List.of(
								ParameterOption.builder().name("Activity").value("activity").build(),
								ParameterOption.builder().name("Campaign").value("campaign").build(),
								ParameterOption.builder().name("Enrichment").value("enrichment").build(),
								ParameterOption.builder().name("Lead").value("lead").build(),
								ParameterOption.builder().name("Team").value("team").build(),
								ParameterOption.builder().name("Unsubscribe").value("unsubscribe").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Enrich Lead").value("enrichLead").build(),
								ParameterOption.builder().name("Enrich Person").value("enrichPerson").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get Credits").value("getCredits").build(),
								ParameterOption.builder().name("Get Stats").value("getStats").build(),
								ParameterOption.builder().name("Unsubscribe").value("unsubscribe").build()
						)).build(),
				NodeParameter.builder()
						.name("campaignId").displayName("Campaign ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("enrichId").displayName("Enrichment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("leadId").displayName("Lead ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("companyName").displayName("Company Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("linkedinUrl").displayName("LinkedIn URL")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("startDate").displayName("Start Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start date for stats filter (ISO 8601).").build(),
				NodeParameter.builder()
						.name("endDate").displayName("End Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("End date for stats filter (ISO 8601).").build(),
				NodeParameter.builder()
						.name("findEmail").displayName("Find Email")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("verifyEmail").displayName("Verify Email")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("linkedinEnrichment").displayName("LinkedIn Enrichment")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("findPhone").displayName("Find Phone")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("deduplicate").displayName("Deduplicate")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to deduplicate the lead.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of results to return.").build()
		);
	}
}
