package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * ActiveCampaign — marketing automation: manage accounts, contacts, deals, lists, and tags.
 */
@Slf4j
@Node(
	type = "activeCampaign",
	displayName = "ActiveCampaign",
	description = "Interact with the ActiveCampaign API for marketing automation",
	category = "Marketing",
	icon = "activeCampaign",
	credentials = {"activeCampaignApi"},
	searchOnly = true
)
public class ActiveCampaignNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
			.options(List.of(
				ParameterOption.builder().name("Account").value("account").build(),
				ParameterOption.builder().name("Contact").value("contact").build(),
				ParameterOption.builder().name("Contact List").value("contactList").build(),
				ParameterOption.builder().name("Contact Tag").value("contactTag").build(),
				ParameterOption.builder().name("Deal").value("deal").build(),
				ParameterOption.builder().name("List").value("list").build(),
				ParameterOption.builder().name("Tag").value("tag").build()
			)).build());

		// Account operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Contact operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Contact List operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contactList"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").build(),
				ParameterOption.builder().name("Remove").value("remove").build()
			)).build());

		// Contact Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contactTag"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").build(),
				ParameterOption.builder().name("Remove").value("remove").build()
			)).build());

		// Deal operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// List operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("list"))))
			.options(List.of(
				ParameterOption.builder().name("Get All").value("getAll").build()
			)).build());

		// Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get All").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Account fields
		params.add(NodeParameter.builder()
			.name("accountName").displayName("Account Name")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("accountUrl").displayName("Account URL")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("accountId").displayName("Account ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"), "operation", List.of("get", "update", "delete"))))
			.build());

		// Contact fields
		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("firstName").displayName("First Name")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("lastName").displayName("Last Name")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("phone").displayName("Phone")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("contactId").displayName("Contact ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("get", "update", "delete"))))
			.build());

		// Contact List fields
		params.add(NodeParameter.builder()
			.name("contactListContactId").displayName("Contact ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contactList"))))
			.build());

		params.add(NodeParameter.builder()
			.name("listId").displayName("List ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contactList"))))
			.build());

		// Contact Tag fields
		params.add(NodeParameter.builder()
			.name("contactTagContactId").displayName("Contact ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contactTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagId").displayName("Tag ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contactTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("contactTagId").displayName("Contact Tag ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.description("The ID of the contact-tag association to remove")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contactTag"), "operation", List.of("remove"))))
			.build());

		// Deal fields
		params.add(NodeParameter.builder()
			.name("dealTitle").displayName("Deal Title")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealValue").displayName("Deal Value (cents)")
			.type(ParameterType.NUMBER).defaultValue(0)
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealCurrency").displayName("Currency")
			.type(ParameterType.STRING).defaultValue("usd")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealPipelineId").displayName("Pipeline ID")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealStageId").displayName("Stage ID")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealContactId").displayName("Contact ID")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealId").displayName("Deal ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("get", "update", "delete"))))
			.build());

		// Tag fields
		params.add(NodeParameter.builder()
			.name("tagName").displayName("Tag Name")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagType").displayName("Tag Type")
			.type(ParameterType.OPTIONS).defaultValue("contact")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("create", "update"))))
			.options(List.of(
				ParameterOption.builder().name("Contact").value("contact").build(),
				ParameterOption.builder().name("Deal").value("deal").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("tagDescription").displayName("Description")
			.type(ParameterType.STRING).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagIdParam").displayName("Tag ID")
			.type(ParameterType.STRING).required(true).defaultValue("")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("get", "update", "delete"))))
			.build());

		// Pagination
		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(20)
			.description("Maximum number of results to return")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("offset").displayName("Offset")
			.type(ParameterType.NUMBER).defaultValue(0)
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiUrl = context.getCredentialString("apiUrl", "");
			String apiKey = context.getCredentialString("apiKey", "");
			String baseUrl = apiUrl.endsWith("/") ? apiUrl + "api/3" : apiUrl + "/api/3";

			String resource = context.getParameter("resource", "contact");
			String operation = context.getParameter("operation", "get");

			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Api-Token", apiKey);
			headers.put("Content-Type", "application/json");

			return switch (resource) {
				case "account" -> executeAccount(context, baseUrl, headers, operation);
				case "contact" -> executeContact(context, baseUrl, headers, operation);
				case "contactList" -> executeContactList(context, baseUrl, headers, operation);
				case "contactTag" -> executeContactTag(context, baseUrl, headers, operation);
				case "deal" -> executeDeal(context, baseUrl, headers, operation);
				case "list" -> executeList(context, baseUrl, headers, operation);
				case "tag" -> executeTag(context, baseUrl, headers, operation);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "ActiveCampaign API error: " + e.getMessage(), e);
		}
	}

	// ========================= Account =========================

	private NodeExecutionResult executeAccount(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> account = new LinkedHashMap<>();
				account.put("name", context.getParameter("accountName", ""));
				String url = context.getParameter("accountUrl", "");
				if (!url.isEmpty()) account.put("accountUrl", url);
				Map<String, Object> body = Map.of("account", account);
				HttpResponse<String> response = post(baseUrl + "/accounts", body, headers);
				return toResult(response);
			}
			case "delete": {
				String accountId = context.getParameter("accountId", "");
				HttpResponse<String> response = delete(baseUrl + "/accounts/" + encode(accountId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String accountId = context.getParameter("accountId", "");
				HttpResponse<String> response = get(baseUrl + "/accounts/" + encode(accountId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 20), 20);
				int offset = toInt(context.getParameter("offset", 0), 0);
				String url = baseUrl + "/accounts?limit=" + limit + "&offset=" + offset;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "accounts");
			}
			case "update": {
				String accountId = context.getParameter("accountId", "");
				Map<String, Object> account = new LinkedHashMap<>();
				String name = context.getParameter("accountName", "");
				if (!name.isEmpty()) account.put("name", name);
				String url = context.getParameter("accountUrl", "");
				if (!url.isEmpty()) account.put("accountUrl", url);
				Map<String, Object> body = Map.of("account", account);
				HttpResponse<String> response = put(baseUrl + "/accounts/" + encode(accountId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown account operation: " + operation);
		}
	}

	// ========================= Contact =========================

	private NodeExecutionResult executeContact(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> contact = new LinkedHashMap<>();
				contact.put("email", context.getParameter("email", ""));
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) contact.put("firstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) contact.put("lastName", lastName);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) contact.put("phone", phone);
				Map<String, Object> body = Map.of("contact", contact);
				HttpResponse<String> response = post(baseUrl + "/contacts", body, headers);
				return toResult(response);
			}
			case "delete": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(baseUrl + "/contacts/" + encode(contactId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(baseUrl + "/contacts/" + encode(contactId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 20), 20);
				int offset = toInt(context.getParameter("offset", 0), 0);
				String url = baseUrl + "/contacts?limit=" + limit + "&offset=" + offset;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "contacts");
			}
			case "update": {
				String contactId = context.getParameter("contactId", "");
				Map<String, Object> contact = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) contact.put("email", email);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) contact.put("firstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) contact.put("lastName", lastName);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) contact.put("phone", phone);
				Map<String, Object> body = Map.of("contact", contact);
				HttpResponse<String> response = put(baseUrl + "/contacts/" + encode(contactId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown contact operation: " + operation);
		}
	}

	// ========================= Contact List =========================

	private NodeExecutionResult executeContactList(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String contactId = context.getParameter("contactListContactId", "");
		String listId = context.getParameter("listId", "");

		Map<String, Object> contactList = new LinkedHashMap<>();
		contactList.put("list", listId);
		contactList.put("contact", contactId);
		contactList.put("status", "add".equals(operation) ? 1 : 2);

		Map<String, Object> body = Map.of("contactList", contactList);
		HttpResponse<String> response = post(baseUrl + "/contactLists", body, headers);
		return toResult(response);
	}

	// ========================= Contact Tag =========================

	private NodeExecutionResult executeContactTag(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "add": {
				String contactId = context.getParameter("contactTagContactId", "");
				String tagId = context.getParameter("tagId", "");
				Map<String, Object> contactTag = new LinkedHashMap<>();
				contactTag.put("contact", contactId);
				contactTag.put("tag", tagId);
				Map<String, Object> body = Map.of("contactTag", contactTag);
				HttpResponse<String> response = post(baseUrl + "/contactTags", body, headers);
				return toResult(response);
			}
			case "remove": {
				String contactTagId = context.getParameter("contactTagId", "");
				HttpResponse<String> response = delete(baseUrl + "/contactTags/" + encode(contactTagId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown contactTag operation: " + operation);
		}
	}

	// ========================= Deal =========================

	private NodeExecutionResult executeDeal(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> deal = new LinkedHashMap<>();
				deal.put("title", context.getParameter("dealTitle", ""));
				deal.put("value", toInt(context.getParameter("dealValue", 0), 0));
				deal.put("currency", context.getParameter("dealCurrency", "usd"));
				String pipelineId = context.getParameter("dealPipelineId", "");
				if (!pipelineId.isEmpty()) deal.put("group", pipelineId);
				String stageId = context.getParameter("dealStageId", "");
				if (!stageId.isEmpty()) deal.put("stage", stageId);
				String contactId = context.getParameter("dealContactId", "");
				if (!contactId.isEmpty()) deal.put("contact", contactId);
				Map<String, Object> body = Map.of("deal", deal);
				HttpResponse<String> response = post(baseUrl + "/deals", body, headers);
				return toResult(response);
			}
			case "delete": {
				String dealId = context.getParameter("dealId", "");
				HttpResponse<String> response = delete(baseUrl + "/deals/" + encode(dealId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String dealId = context.getParameter("dealId", "");
				HttpResponse<String> response = get(baseUrl + "/deals/" + encode(dealId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 20), 20);
				int offset = toInt(context.getParameter("offset", 0), 0);
				String url = baseUrl + "/deals?limit=" + limit + "&offset=" + offset;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "deals");
			}
			case "update": {
				String dealId = context.getParameter("dealId", "");
				Map<String, Object> deal = new LinkedHashMap<>();
				String title = context.getParameter("dealTitle", "");
				if (!title.isEmpty()) deal.put("title", title);
				int value = toInt(context.getParameter("dealValue", 0), 0);
				if (value > 0) deal.put("value", value);
				String currency = context.getParameter("dealCurrency", "");
				if (!currency.isEmpty()) deal.put("currency", currency);
				String stageId = context.getParameter("dealStageId", "");
				if (!stageId.isEmpty()) deal.put("stage", stageId);
				Map<String, Object> body = Map.of("deal", deal);
				HttpResponse<String> response = put(baseUrl + "/deals/" + encode(dealId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown deal operation: " + operation);
		}
	}

	// ========================= List =========================

	private NodeExecutionResult executeList(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		if ("getAll".equals(operation)) {
			int limit = toInt(context.getParameter("limit", 20), 20);
			int offset = toInt(context.getParameter("offset", 0), 0);
			String url = baseUrl + "/lists?limit=" + limit + "&offset=" + offset;
			HttpResponse<String> response = get(url, headers);
			return toListResult(response, "lists");
		}
		return NodeExecutionResult.error("Unknown list operation: " + operation);
	}

	// ========================= Tag =========================

	private NodeExecutionResult executeTag(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> tag = new LinkedHashMap<>();
				tag.put("tag", context.getParameter("tagName", ""));
				tag.put("tagType", context.getParameter("tagType", "contact"));
				String description = context.getParameter("tagDescription", "");
				if (!description.isEmpty()) tag.put("description", description);
				Map<String, Object> body = Map.of("tag", tag);
				HttpResponse<String> response = post(baseUrl + "/tags", body, headers);
				return toResult(response);
			}
			case "delete": {
				String tagIdParam = context.getParameter("tagIdParam", "");
				HttpResponse<String> response = delete(baseUrl + "/tags/" + encode(tagIdParam), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String tagIdParam = context.getParameter("tagIdParam", "");
				HttpResponse<String> response = get(baseUrl + "/tags/" + encode(tagIdParam), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 20), 20);
				int offset = toInt(context.getParameter("offset", 0), 0);
				String url = baseUrl + "/tags?limit=" + limit + "&offset=" + offset;
				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "tags");
			}
			case "update": {
				String tagIdParam = context.getParameter("tagIdParam", "");
				Map<String, Object> tag = new LinkedHashMap<>();
				String tagName = context.getParameter("tagName", "");
				if (!tagName.isEmpty()) tag.put("tag", tagName);
				String tagType = context.getParameter("tagType", "");
				if (!tagType.isEmpty()) tag.put("tagType", tagType);
				String description = context.getParameter("tagDescription", "");
				if (!description.isEmpty()) tag.put("description", description);
				Map<String, Object> body = Map.of("tag", tag);
				HttpResponse<String> response = put(baseUrl + "/tags/" + encode(tagIdParam), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown tag operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("ActiveCampaign API error (HTTP " + response.statusCode() + "): " + body);
	}
}
