package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Help Scout — manage conversations, customers, mailboxes and threads via the Help Scout API v2.
 */
@Slf4j
@Node(
		type = "helpScout",
		displayName = "Help Scout",
		description = "Manage conversations and customers in Help Scout",
		category = "Customer Support",
		icon = "helpScout",
		credentials = {"helpScoutOAuth2Api"}
)
public class HelpScoutNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.helpscout.net/v2";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("conversation")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Conversation").value("conversation").description("Manage conversations").build(),
						ParameterOption.builder().name("Customer").value("customer").description("Manage customers").build(),
						ParameterOption.builder().name("Mailbox").value("mailbox").description("List mailboxes").build(),
						ParameterOption.builder().name("Thread").value("thread").description("Manage conversation threads").build()
				)).build());

		// Conversation operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("create")
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get Many").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Customer operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("create")
				.displayOptions(Map.of("show", Map.of("resource", List.of("customer"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get Many").value("getAll").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Mailbox operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("mailbox"))))
				.options(List.of(
						ParameterOption.builder().name("Get Many").value("getAll").build()
				)).build());

		// Thread operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("create")
				.displayOptions(Map.of("show", Map.of("resource", List.of("thread"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Get Many").value("getAll").build()
				)).build());

		// Conversation parameters
		addConversationParameters(params);
		// Customer parameters
		addCustomerParameters(params);
		// Thread parameters
		addThreadParameters(params);

		// Common getAll parameters
		for (String res : List.of("conversation", "customer", "mailbox")) {
			params.add(NodeParameter.builder()
					.name("returnAll").displayName("Return All")
					.type(ParameterType.BOOLEAN).defaultValue(false)
					.displayOptions(Map.of("show", Map.of("resource", List.of(res), "operation", List.of("getAll"))))
					.build());
			params.add(NodeParameter.builder()
					.name("limit").displayName("Limit")
					.type(ParameterType.NUMBER).defaultValue(50)
					.displayOptions(Map.of("show", Map.of("resource", List.of(res), "operation", List.of("getAll"))))
					.build());
		}

		return params;
	}

	private void addConversationParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("conversationId").displayName("Conversation ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"), "operation", List.of("get", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("mailboxId").displayName("Mailbox ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("conversationSubject").displayName("Subject")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("customerEmail").displayName("Customer Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("threadText").displayName("Thread Text")
				.type(ParameterType.STRING).required(true).typeOptions(Map.of("rows", 4))
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("conversationType").displayName("Type")
				.type(ParameterType.OPTIONS).defaultValue("email")
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Email").value("email").build(),
						ParameterOption.builder().name("Phone").value("phone").build(),
						ParameterOption.builder().name("Chat").value("chat").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("conversationStatus").displayName("Status")
				.type(ParameterType.OPTIONS).defaultValue("active")
				.displayOptions(Map.of("show", Map.of("resource", List.of("conversation"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Active").value("active").build(),
						ParameterOption.builder().name("Pending").value("pending").build(),
						ParameterOption.builder().name("Closed").value("closed").build(),
						ParameterOption.builder().name("Spam").value("spam").build()
				)).build());
	}

	private void addCustomerParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("customerId").displayName("Customer ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("customerFirstName").displayName("First Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("customerLastName").displayName("Last Name")
				.type(ParameterType.STRING)
				.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("customerEmails").displayName("Email")
				.type(ParameterType.STRING).required(true)
				.description("Primary email address")
				.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("customerAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("company").displayName("Company").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("customerUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("firstName").displayName("First Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("lastName").displayName("Last Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("company").displayName("Company").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build()
				)).build());
	}

	private void addThreadParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("threadConversationId").displayName("Conversation ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("thread"), "operation", List.of("create", "getAll"))))
				.build());

		params.add(NodeParameter.builder()
				.name("threadType").displayName("Type")
				.type(ParameterType.OPTIONS).defaultValue("reply")
				.displayOptions(Map.of("show", Map.of("resource", List.of("thread"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Reply").value("reply").build(),
						ParameterOption.builder().name("Note").value("note").build(),
						ParameterOption.builder().name("Customer Reply").value("customer").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("threadBody").displayName("Body")
				.type(ParameterType.STRING).required(true).typeOptions(Map.of("rows", 4))
				.displayOptions(Map.of("show", Map.of("resource", List.of("thread"), "operation", List.of("create"))))
				.build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "conversation");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "conversation" -> executeConversation(context, headers);
				case "customer" -> executeCustomer(context, headers);
				case "mailbox" -> executeMailbox(context, headers);
				case "thread" -> executeThread(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Help Scout API error: " + e.getMessage(), e);
		}
	}

	// ========================= Conversation =========================

	private NodeExecutionResult executeConversation(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String mailboxId = context.getParameter("mailboxId", "");
				String subject = context.getParameter("conversationSubject", "");
				String email = context.getParameter("customerEmail", "");
				String text = context.getParameter("threadText", "");
				String type = context.getParameter("conversationType", "email");
				String status = context.getParameter("conversationStatus", "active");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("subject", subject);
				body.put("type", type);
				body.put("status", status);
				body.put("mailboxId", Integer.parseInt(mailboxId));
				body.put("customer", Map.of("email", email));
				body.put("threads", List.of(Map.of("type", "customer", "text", text)));

				HttpResponse<String> response = post(BASE_URL + "/conversations", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("conversationId", "");
				HttpResponse<String> response = get(BASE_URL + "/conversations/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(BASE_URL + "/conversations?pageSize=" + Math.min(limit, 50), headers);
				return toEmbeddedListResult(response, "conversations");
			}
			case "delete": {
				String id = context.getParameter("conversationId", "");
				HttpResponse<String> response = delete(BASE_URL + "/conversations/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown conversation operation: " + operation);
		}
	}

	// ========================= Customer =========================

	private NodeExecutionResult executeCustomer(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String firstName = context.getParameter("customerFirstName", "");
				String lastName = context.getParameter("customerLastName", "");
				String email = context.getParameter("customerEmails", "");
				Map<String, Object> additional = context.getParameter("customerAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("firstName", firstName);
				putIfPresent(body, "lastName", lastName);
				body.put("emails", List.of(Map.of("type", "work", "value", email)));
				if (additional.get("phone") != null && !String.valueOf(additional.get("phone")).isEmpty()) {
					body.put("phones", List.of(Map.of("type", "work", "value", additional.get("phone"))));
				}
				putIfPresent(body, "company", additional.get("company"));
				putIfPresent(body, "jobTitle", additional.get("jobTitle"));

				HttpResponse<String> response = post(BASE_URL + "/customers", body, headers);
				return toResult(response);
			}
			case "get": {
				String id = context.getParameter("customerId", "");
				HttpResponse<String> response = get(BASE_URL + "/customers/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(BASE_URL + "/customers?pageSize=" + Math.min(limit, 50), headers);
				return toEmbeddedListResult(response, "customers");
			}
			case "update": {
				String id = context.getParameter("customerId", "");
				Map<String, Object> updateFields = context.getParameter("customerUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "firstName", updateFields.get("firstName"));
				putIfPresent(body, "lastName", updateFields.get("lastName"));
				putIfPresent(body, "company", updateFields.get("company"));
				putIfPresent(body, "jobTitle", updateFields.get("jobTitle"));

				HttpResponse<String> response = put(BASE_URL + "/customers/" + encode(id), body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("customerId", "");
				HttpResponse<String> response = delete(BASE_URL + "/customers/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown customer operation: " + operation);
		}
	}

	// ========================= Mailbox =========================

	private NodeExecutionResult executeMailbox(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		int limit = toInt(context.getParameter("limit", 50), 50);
		HttpResponse<String> response = get(BASE_URL + "/mailboxes?pageSize=" + Math.min(limit, 50), headers);
		return toEmbeddedListResult(response, "mailboxes");
	}

	// ========================= Thread =========================

	private NodeExecutionResult executeThread(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");

		switch (operation) {
			case "create": {
				String conversationId = context.getParameter("threadConversationId", "");
				String type = context.getParameter("threadType", "reply");
				String body = context.getParameter("threadBody", "");

				Map<String, Object> threadBody = new LinkedHashMap<>();
				threadBody.put("text", body);

				String endpoint = BASE_URL + "/conversations/" + encode(conversationId);
				HttpResponse<String> response;
				switch (type) {
					case "reply" -> response = post(endpoint + "/reply", threadBody, headers);
					case "note" -> response = post(endpoint + "/notes", threadBody, headers);
					case "customer" -> response = post(endpoint + "/customer", threadBody, headers);
					default -> response = post(endpoint + "/reply", threadBody, headers);
				}
				return toResult(response);
			}
			case "getAll": {
				String conversationId = context.getParameter("threadConversationId", "");
				HttpResponse<String> response = get(BASE_URL + "/conversations/" + encode(conversationId) + "/threads", headers);
				return toEmbeddedListResult(response, "threads");
			}
			default:
				return NodeExecutionResult.error("Unknown thread operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toEmbeddedListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		// Help Scout uses HAL+JSON with _embedded
		Object embedded = parsed.get("_embedded");
		if (embedded instanceof Map) {
			Object data = ((Map<String, Object>) embedded).get(dataKey);
			if (data instanceof List) {
				List<Map<String, Object>> items = new ArrayList<>();
				for (Object item : (List<?>) data) {
					if (item instanceof Map) {
						items.add(wrapInJson(item));
					}
				}
				return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
			}
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Help Scout API error (HTTP " + response.statusCode() + "): " + body);
	}
}
