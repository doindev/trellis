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
 * Zoho CRM Node -- manage contacts, deals, leads, accounts, products,
 * quotes, and invoices in Zoho CRM.
 */
@Slf4j
@Node(
	type = "zohoCrm",
	displayName = "Zoho CRM",
	description = "Manage contacts, deals, leads, accounts, products, quotes, and invoices in Zoho CRM",
	category = "CRM",
	icon = "zohoCrm",
	credentials = {"zohoCrmOAuth2Api"}
)
public class ZohoCrmNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.zohoapis.com/crm/v2";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build(),
				ParameterOption.builder().name("Deal").value("deal").description("Manage deals").build(),
				ParameterOption.builder().name("Lead").value("lead").description("Manage leads").build(),
				ParameterOption.builder().name("Account").value("account").description("Manage accounts").build(),
				ParameterOption.builder().name("Product").value("product").description("Manage products").build(),
				ParameterOption.builder().name("Quote").value("quote").description("Manage quotes").build(),
				ParameterOption.builder().name("Invoice").value("invoice").description("Manage invoices").build()
			)).build());

		// Common operations for all resources
		for (String res : List.of("contact", "deal", "lead", "account", "product", "quote", "invoice")) {
			params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of(res))))
				.options(List.of(
					ParameterOption.builder().name("Create").value("create").description("Create a " + res).build(),
					ParameterOption.builder().name("Delete").value("delete").description("Delete a " + res).build(),
					ParameterOption.builder().name("Get").value("get").description("Get a " + res).build(),
					ParameterOption.builder().name("Get Many").value("getAll").description("Get many " + res + "s").build(),
					ParameterOption.builder().name("Update").value("update").description("Update a " + res).build()
				)).build());
		}

		// Record ID
		params.add(NodeParameter.builder()
			.name("recordId").displayName("Record ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the record.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete"))))
			.build());

		// Contact/Lead fields
		params.add(NodeParameter.builder()
			.name("firstName").displayName("First Name")
			.type(ParameterType.STRING)
			.description("First name of the contact or lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact", "lead"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("lastName").displayName("Last Name")
			.type(ParameterType.STRING).required(true)
			.description("Last name of the contact or lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact", "lead"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email address.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact", "lead"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("company").displayName("Company")
			.type(ParameterType.STRING)
			.description("Company name.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("create", "update"))))
			.build());

		// Deal fields
		params.add(NodeParameter.builder()
			.name("dealName").displayName("Deal Name")
			.type(ParameterType.STRING).required(true)
			.description("Name of the deal.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealStage").displayName("Stage")
			.type(ParameterType.STRING)
			.description("Stage of the deal.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealAmount").displayName("Amount")
			.type(ParameterType.NUMBER)
			.description("Amount of the deal.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		// Account fields
		params.add(NodeParameter.builder()
			.name("accountName").displayName("Account Name")
			.type(ParameterType.STRING).required(true)
			.description("Name of the account.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"), "operation", List.of("create"))))
			.build());

		// Product fields
		params.add(NodeParameter.builder()
			.name("productName").displayName("Product Name")
			.type(ParameterType.STRING).required(true)
			.description("Name of the product.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"), "operation", List.of("create"))))
			.build());

		// Quote/Invoice subject
		params.add(NodeParameter.builder()
			.name("subject").displayName("Subject")
			.type(ParameterType.STRING).required(true)
			.description("Subject of the quote or invoice.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote", "invoice"), "operation", List.of("create"))))
			.build());

		// Additional fields
		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		// Pagination
		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(200)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);
			String module = getModuleName(resource);

			return switch (operation) {
				case "create" -> executeCreate(context, module, headers);
				case "delete" -> executeDelete(context, module, headers);
				case "get" -> executeGet(context, module, headers);
				case "getAll" -> executeGetAll(context, module, headers);
				case "update" -> executeUpdate(context, module, headers);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Zoho CRM API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeCreate(NodeExecutionContext context, String module, Map<String, String> headers) throws Exception {
		String resource = context.getParameter("resource", "contact");
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> record = new LinkedHashMap<>(parseJson(additionalJson));

		switch (resource) {
			case "contact", "lead" -> {
				putIfNotEmpty(record, "First_Name", context.getParameter("firstName", ""));
				putIfNotEmpty(record, "Last_Name", context.getParameter("lastName", ""));
				putIfNotEmpty(record, "Email", context.getParameter("email", ""));
				if ("lead".equals(resource)) {
					putIfNotEmpty(record, "Company", context.getParameter("company", ""));
				}
			}
			case "deal" -> {
				putIfNotEmpty(record, "Deal_Name", context.getParameter("dealName", ""));
				putIfNotEmpty(record, "Stage", context.getParameter("dealStage", ""));
				Object amount = context.getParameters().get("dealAmount");
				if (amount != null) record.put("Amount", toDouble(amount, 0));
			}
			case "account" -> putIfNotEmpty(record, "Account_Name", context.getParameter("accountName", ""));
			case "product" -> putIfNotEmpty(record, "Product_Name", context.getParameter("productName", ""));
			case "quote", "invoice" -> putIfNotEmpty(record, "Subject", context.getParameter("subject", ""));
		}

		Map<String, Object> body = Map.of("data", List.of(record));
		HttpResponse<String> response = post(BASE_URL + "/" + module, body, headers);
		return toResult(response);
	}

	private NodeExecutionResult executeDelete(NodeExecutionContext context, String module, Map<String, String> headers) throws Exception {
		String recordId = context.getParameter("recordId", "");
		HttpResponse<String> response = delete(BASE_URL + "/" + module + "/" + encode(recordId), headers);
		if (response.statusCode() >= 400) {
			return zohoError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", recordId))));
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, String module, Map<String, String> headers) throws Exception {
		String recordId = context.getParameter("recordId", "");
		HttpResponse<String> response = get(BASE_URL + "/" + module + "/" + encode(recordId), headers);
		return toResult(response);
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeGetAll(NodeExecutionContext context, String module, Map<String, String> headers) throws Exception {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 200);
		int perPage = returnAll ? 200 : Math.min(limit, 200);

		String url = BASE_URL + "/" + module + "?per_page=" + perPage + "&page=1";
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return zohoError(response);
		}

		Map<String, Object> parsed = parseResponse(response);
		Object dataObj = parsed.get("data");
		if (dataObj instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) dataObj) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, String module, Map<String, String> headers) throws Exception {
		String recordId = context.getParameter("recordId", "");
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> record = new LinkedHashMap<>(parseJson(additionalJson));

		String resource = context.getParameter("resource", "contact");
		switch (resource) {
			case "contact", "lead" -> {
				putIfNotEmpty(record, "First_Name", context.getParameter("firstName", ""));
				putIfNotEmpty(record, "Last_Name", context.getParameter("lastName", ""));
				putIfNotEmpty(record, "Email", context.getParameter("email", ""));
			}
			case "deal" -> {
				putIfNotEmpty(record, "Deal_Name", context.getParameter("dealName", ""));
				putIfNotEmpty(record, "Stage", context.getParameter("dealStage", ""));
			}
		}

		Map<String, Object> body = Map.of("data", List.of(record));
		HttpResponse<String> response = put(BASE_URL + "/" + module + "/" + encode(recordId), body, headers);
		return toResult(response);
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", credentials.getOrDefault("oauthAccessToken", "")));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Zoho-oauthtoken " + accessToken);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private String getModuleName(String resource) {
		return switch (resource) {
			case "contact" -> "Contacts";
			case "deal" -> "Deals";
			case "lead" -> "Leads";
			case "account" -> "Accounts";
			case "product" -> "Products";
			case "quote" -> "Quotes";
			case "invoice" -> "Invoices";
			default -> resource;
		};
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return zohoError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object dataObj = parsed.get("data");
		if (dataObj instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) dataObj) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		} else if (dataObj instanceof Map) {
			return NodeExecutionResult.success(List.of(wrapInJson(dataObj)));
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult zohoError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Zoho CRM API error (HTTP " + response.statusCode() + "): " + body);
	}
}
