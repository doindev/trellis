package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Freshdesk — manage tickets and contacts via the Freshdesk REST API v2.
 */
@Slf4j
@Node(
		type = "freshdesk",
		displayName = "Freshdesk",
		description = "Manage tickets and contacts in Freshdesk",
		category = "Customer Support",
		icon = "freshdesk",
		credentials = {"freshdeskApi"},
		searchOnly = true
)
public class FreshdeskNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("ticket")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Ticket").value("ticket").description("Manage support tickets").build(),
						ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build()
				)).build());

		// Ticket operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("create")
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a ticket").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a ticket by ID").build(),
						ParameterOption.builder().name("Get Many").value("getAll").description("Get many tickets").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a ticket").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a ticket").build()
				)).build());

		// Contact operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("create")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a contact").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a contact by ID").build(),
						ParameterOption.builder().name("Get Many").value("getAll").description("Get many contacts").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a contact").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a contact").build()
				)).build());

		// Ticket ID
		params.add(NodeParameter.builder()
				.name("ticketId").displayName("Ticket ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("get", "update", "delete"))))
				.build());

		// Ticket > Create fields
		params.add(NodeParameter.builder()
				.name("subject").displayName("Subject")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("description").displayName("Description")
				.type(ParameterType.STRING).required(true)
				.typeOptions(Map.of("rows", 4))
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("email").displayName("Requester Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("priority").displayName("Priority")
				.type(ParameterType.OPTIONS).defaultValue(1)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Low").value(1).build(),
						ParameterOption.builder().name("Medium").value(2).build(),
						ParameterOption.builder().name("High").value(3).build(),
						ParameterOption.builder().name("Urgent").value(4).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("status").displayName("Status")
				.type(ParameterType.OPTIONS).defaultValue(2)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Open").value(2).build(),
						ParameterOption.builder().name("Pending").value(3).build(),
						ParameterOption.builder().name("Resolved").value(4).build(),
						ParameterOption.builder().name("Closed").value(5).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("ticketAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("type").displayName("Type")
								.type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Question").value("Question").build(),
										ParameterOption.builder().name("Incident").value("Incident").build(),
										ParameterOption.builder().name("Problem").value("Problem").build(),
										ParameterOption.builder().name("Feature Request").value("Feature Request").build()
								)).build(),
						NodeParameter.builder().name("tags").displayName("Tags")
								.type(ParameterType.STRING).description("Comma-separated tags").build(),
						NodeParameter.builder().name("groupId").displayName("Group ID")
								.type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("responderId").displayName("Responder ID")
								.type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("ccEmails").displayName("CC Emails")
								.type(ParameterType.STRING).description("Comma-separated CC email addresses").build()
				)).build());

		// Ticket > Update fields
		params.add(NodeParameter.builder()
				.name("ticketUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("subject").displayName("Subject").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("priority").displayName("Priority").type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Low").value(1).build(),
										ParameterOption.builder().name("Medium").value(2).build(),
										ParameterOption.builder().name("High").value(3).build(),
										ParameterOption.builder().name("Urgent").value(4).build()
								)).build(),
						NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
								.options(List.of(
										ParameterOption.builder().name("Open").value(2).build(),
										ParameterOption.builder().name("Pending").value(3).build(),
										ParameterOption.builder().name("Resolved").value(4).build(),
										ParameterOption.builder().name("Closed").value(5).build()
								)).build(),
						NodeParameter.builder().name("tags").displayName("Tags")
								.type(ParameterType.STRING).description("Comma-separated tags").build()
				)).build());

		// Ticket > GetAll: limit
		params.add(NodeParameter.builder()
				.name("returnAll").displayName("Return All")
				.type(ParameterType.BOOLEAN).defaultValue(false)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("getAll"))))
				.build());

		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(50)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("getAll"))))
				.build());

		// Contact ID
		params.add(NodeParameter.builder()
				.name("contactId").displayName("Contact ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("get", "update", "delete"))))
				.build());

		// Contact > Create fields
		params.add(NodeParameter.builder()
				.name("contactName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("contactEmail").displayName("Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("contactAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("mobile").displayName("Mobile").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("companyId").displayName("Company ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("twitterId").displayName("Twitter ID").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build()
				)).build());

		// Contact > Update fields
		params.add(NodeParameter.builder()
				.name("contactUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("mobile").displayName("Mobile").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build()
				)).build());

		// Contact > GetAll: limit
		params.add(NodeParameter.builder()
				.name("returnAll").displayName("Return All")
				.type(ParameterType.BOOLEAN).defaultValue(false)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("getAll"))))
				.build());

		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(50)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("getAll"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "ticket");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "ticket" -> executeTicket(context, credentials);
				case "contact" -> executeContact(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Freshdesk API error: " + e.getMessage(), e);
		}
	}

	// ========================= Ticket =========================

	private NodeExecutionResult executeTicket(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String subject = context.getParameter("subject", "");
				String description = context.getParameter("description", "");
				String email = context.getParameter("email", "");
				int priority = toInt(context.getParameter("priority", 1), 1);
				int status = toInt(context.getParameter("status", 2), 2);
				Map<String, Object> additional = context.getParameter("ticketAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("subject", subject);
				body.put("description", description);
				body.put("email", email);
				body.put("priority", priority);
				body.put("status", status);
				putIfPresent(body, "type", additional.get("type"));
				if (additional.get("tags") != null && !String.valueOf(additional.get("tags")).isEmpty()) {
					body.put("tags", parseCsv(String.valueOf(additional.get("tags"))));
				}
				putIfPresent(body, "group_id", additional.get("groupId"));
				putIfPresent(body, "responder_id", additional.get("responderId"));
				if (additional.get("ccEmails") != null && !String.valueOf(additional.get("ccEmails")).isEmpty()) {
					body.put("cc_emails", parseCsv(String.valueOf(additional.get("ccEmails"))));
				}

				HttpResponse<String> response = post(baseUrl + "/tickets", body, headers);
				return toResult(response);
			}
			case "get": {
				String ticketId = context.getParameter("ticketId", "");
				HttpResponse<String> response = get(baseUrl + "/tickets/" + encode(ticketId), headers);
				return toResult(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameter("returnAll", false), false);
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = baseUrl + "/tickets?per_page=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String ticketId = context.getParameter("ticketId", "");
				Map<String, Object> updateFields = context.getParameter("ticketUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "subject", updateFields.get("subject"));
				putIfPresent(body, "description", updateFields.get("description"));
				if (updateFields.get("priority") != null) {
					body.put("priority", toInt(updateFields.get("priority"), 1));
				}
				if (updateFields.get("status") != null) {
					body.put("status", toInt(updateFields.get("status"), 2));
				}
				if (updateFields.get("tags") != null && !String.valueOf(updateFields.get("tags")).isEmpty()) {
					body.put("tags", parseCsv(String.valueOf(updateFields.get("tags"))));
				}

				HttpResponse<String> response = put(baseUrl + "/tickets/" + encode(ticketId), body, headers);
				return toResult(response);
			}
			case "delete": {
				String ticketId = context.getParameter("ticketId", "");
				HttpResponse<String> response = delete(baseUrl + "/tickets/" + encode(ticketId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown ticket operation: " + operation);
		}
	}

	// ========================= Contact =========================

	private NodeExecutionResult executeContact(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String name = context.getParameter("contactName", "");
				String email = context.getParameter("contactEmail", "");
				Map<String, Object> additional = context.getParameter("contactAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("email", email);
				putIfPresent(body, "phone", additional.get("phone"));
				putIfPresent(body, "mobile", additional.get("mobile"));
				putIfPresent(body, "job_title", additional.get("jobTitle"));
				putIfPresent(body, "company_id", additional.get("companyId"));
				putIfPresent(body, "twitter_id", additional.get("twitterId"));
				putIfPresent(body, "description", additional.get("description"));

				HttpResponse<String> response = post(baseUrl + "/contacts", body, headers);
				return toResult(response);
			}
			case "get": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(baseUrl + "/contacts/" + encode(contactId), headers);
				return toResult(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameter("returnAll", false), false);
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = baseUrl + "/contacts?per_page=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String contactId = context.getParameter("contactId", "");
				Map<String, Object> updateFields = context.getParameter("contactUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "email", updateFields.get("email"));
				putIfPresent(body, "phone", updateFields.get("phone"));
				putIfPresent(body, "mobile", updateFields.get("mobile"));
				putIfPresent(body, "job_title", updateFields.get("jobTitle"));
				putIfPresent(body, "description", updateFields.get("description"));

				HttpResponse<String> response = put(baseUrl + "/contacts/" + encode(contactId), body, headers);
				return toResult(response);
			}
			case "delete": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(baseUrl + "/contacts/" + encode(contactId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown contact operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String domain = String.valueOf(credentials.getOrDefault("domain", ""));
		return "https://" + domain + ".freshdesk.com/api/v2";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		String auth = Base64.getEncoder().encodeToString((apiKey + ":X").getBytes());
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Basic " + auth);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	private List<String> parseCsv(String value) {
		if (value == null || value.isBlank()) return List.of();
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}

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

	private NodeExecutionResult toArrayResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		List<Map<String, Object>> items = parseArrayResponse(response);
		List<Map<String, Object>> wrapped = new ArrayList<>();
		for (Map<String, Object> item : items) {
			wrapped.add(wrapInJson(item));
		}
		return wrapped.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(wrapped);
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Freshdesk API error (HTTP " + response.statusCode() + "): " + body);
	}
}
