package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Salesmate — manage companies, activities, and deals in Salesmate CRM.
 */
@Node(
		type = "salesmate",
		displayName = "Salesmate",
		description = "Manage companies, activities, and deals in Salesmate CRM",
		category = "CRM",
		icon = "salesmate",
		credentials = {"salesmateApi"}
)
public class SalesmateNode extends AbstractApiNode {

	private static final String BASE_URL = "https://apis.salesmate.io";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String sessionToken = (String) credentials.getOrDefault("sessionToken", "");
		String linkName = (String) credentials.getOrDefault("linkName", "");

		String resource = context.getParameter("resource", "company");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("sessionToken", sessionToken);
		headers.put("x-linkname", linkName);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "company" -> handleCompany(context, headers, operation);
					case "activity" -> handleActivity(context, headers, operation);
					case "deal" -> handleDeal(context, headers, operation);
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

	private Map<String, Object> handleCompany(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("owner", toInt(context.getParameters().get("owner"), 0));
				body.put("name", context.getParameter("name", ""));
				String website = context.getParameter("website", "");
				if (!website.isEmpty()) body.put("website", website);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) body.put("phone", phone);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				HttpResponse<String> response = post(BASE_URL + "/v1/companies", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("companyId", "");
				HttpResponse<String> response = get(BASE_URL + "/v1/companies/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 25);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("fields", List.of("name", "owner", "website", "phone"));
				body.put("rows", limit);
				body.put("pageNo", 1);
				HttpResponse<String> response = post(BASE_URL + "/v2/companies/search", body, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("companyId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
				String website = context.getParameter("website", "");
				if (!website.isEmpty()) body.put("website", website);
				HttpResponse<String> response = put(BASE_URL + "/v1/companies/" + id, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("companyId", "");
				HttpResponse<String> response = delete(BASE_URL + "/v1/companies/" + id, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown company operation: " + operation);
		};
	}

	private Map<String, Object> handleActivity(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("owner", toInt(context.getParameters().get("owner"), 0));
				body.put("title", context.getParameter("title", ""));
				body.put("type", context.getParameter("activityType", ""));
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) body.put("dueDate", dueDate);
				String actDesc = context.getParameter("description", "");
				if (!actDesc.isEmpty()) body.put("description", actDesc);
				HttpResponse<String> response = post(BASE_URL + "/v1/activities", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("activityId", "");
				HttpResponse<String> response = get(BASE_URL + "/v1/activities/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 25);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("fields", List.of("title", "owner", "type", "dueDate"));
				body.put("rows", limit);
				body.put("pageNo", 1);
				HttpResponse<String> response = post(BASE_URL + "/v2/activities/search", body, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("activityId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String actDesc = context.getParameter("description", "");
				if (!actDesc.isEmpty()) body.put("description", actDesc);
				HttpResponse<String> response = put(BASE_URL + "/v1/activities/" + id, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("activityId", "");
				HttpResponse<String> response = delete(BASE_URL + "/v1/activities/" + id, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown activity operation: " + operation);
		};
	}

	private Map<String, Object> handleDeal(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("owner", toInt(context.getParameters().get("owner"), 0));
				body.put("pipeline", context.getParameter("pipeline", ""));
				body.put("status", context.getParameter("dealStatus", ""));
				body.put("stage", context.getParameter("stage", ""));
				body.put("currency", context.getParameter("currency", "USD"));
				String primaryContact = context.getParameter("primaryContact", "");
				if (!primaryContact.isEmpty()) body.put("primaryContact", primaryContact);
				String dealValue = context.getParameter("dealValue", "");
				if (!dealValue.isEmpty()) body.put("dealValue", toDouble(dealValue, 0));
				String desc = context.getParameter("description", "");
				if (!desc.isEmpty()) body.put("description", desc);
				HttpResponse<String> response = post(BASE_URL + "/v1/deals", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("dealId", "");
				HttpResponse<String> response = get(BASE_URL + "/v1/deals/" + id, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 25);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("fields", List.of("title", "owner", "pipeline", "status", "stage", "dealValue"));
				body.put("rows", limit);
				body.put("pageNo", 1);
				HttpResponse<String> response = post(BASE_URL + "/v2/deals/search", body, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("dealId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) body.put("title", title);
				String dealStatus = context.getParameter("dealStatus", "");
				if (!dealStatus.isEmpty()) body.put("status", dealStatus);
				String dealValue = context.getParameter("dealValue", "");
				if (!dealValue.isEmpty()) body.put("dealValue", toDouble(dealValue, 0));
				HttpResponse<String> response = put(BASE_URL + "/v1/deals/" + id, body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("dealId", "");
				HttpResponse<String> response = delete(BASE_URL + "/v1/deals/" + id, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown deal operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("company")
						.options(List.of(
								ParameterOption.builder().name("Activity").value("activity").build(),
								ParameterOption.builder().name("Company").value("company").build(),
								ParameterOption.builder().name("Deal").value("deal").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("owner").displayName("Owner")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Owner user ID.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Company name.").build(),
				NodeParameter.builder()
						.name("website").displayName("Website")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("companyId").displayName("Company ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("activityType").displayName("Activity Type")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("activityId").displayName("Activity ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dueDate").displayName("Due Date")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dealId").displayName("Deal ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dealStatus").displayName("Status")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("pipeline").displayName("Pipeline")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("stage").displayName("Stage")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("currency").displayName("Currency")
						.type(ParameterType.STRING).defaultValue("USD").build(),
				NodeParameter.builder()
						.name("primaryContact").displayName("Primary Contact")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dealValue").displayName("Deal Value")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(25)
						.description("Max results to return.").build()
		);
	}
}
