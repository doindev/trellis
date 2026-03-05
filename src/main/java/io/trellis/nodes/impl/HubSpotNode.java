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
 * HubSpot Node -- manage contacts, companies, deals, engagements, forms,
 * and tickets in HubSpot CRM.
 */
@Slf4j
@Node(
	type = "hubspot",
	displayName = "HubSpot",
	description = "Manage contacts, companies, deals, engagements, forms, and tickets in HubSpot CRM",
	category = "CRM",
	icon = "hubspot",
	credentials = {"hubspotApi"}
)
public class HubSpotNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.hubapi.com";

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
				ParameterOption.builder().name("Company").value("company").description("Manage companies").build(),
				ParameterOption.builder().name("Deal").value("deal").description("Manage deals").build(),
				ParameterOption.builder().name("Engagement").value("engagement").description("Manage engagements").build(),
				ParameterOption.builder().name("Form").value("form").description("Manage forms").build(),
				ParameterOption.builder().name("Ticket").value("ticket").description("Manage tickets").build()
			)).build());

		// Contact operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a contact").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a contact").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a contact").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many contacts").build(),
				ParameterOption.builder().name("Get Recently Created").value("getRecentlyCreated").description("Get recently created contacts").build(),
				ParameterOption.builder().name("Get Recently Updated").value("getRecentlyUpdated").description("Get recently updated contacts").build(),
				ParameterOption.builder().name("Search").value("search").description("Search contacts").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a contact").build()
			)).build());

		// Company operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("company"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a company").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a company").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a company").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many companies").build(),
				ParameterOption.builder().name("Get Recently Created").value("getRecentlyCreated").description("Get recently created companies").build(),
				ParameterOption.builder().name("Get Recently Updated").value("getRecentlyUpdated").description("Get recently updated companies").build(),
				ParameterOption.builder().name("Search").value("search").description("Search companies").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a company").build()
			)).build());

		// Deal operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a deal").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a deal").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a deal").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many deals").build(),
				ParameterOption.builder().name("Get Recently Created").value("getRecentlyCreated").description("Get recently created deals").build(),
				ParameterOption.builder().name("Get Recently Updated").value("getRecentlyUpdated").description("Get recently updated deals").build(),
				ParameterOption.builder().name("Search").value("search").description("Search deals").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a deal").build()
			)).build());

		// Engagement operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("engagement"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an engagement").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an engagement").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an engagement").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many engagements").build()
			)).build());

		// Form operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("form"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").description("Delete a form").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a form").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many forms").build(),
				ParameterOption.builder().name("Get Fields").value("getFields").description("Get form fields").build(),
				ParameterOption.builder().name("Get Submissions").value("getSubmissions").description("Get form submissions").build()
			)).build());

		// Ticket operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a ticket").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a ticket").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a ticket").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many tickets").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a ticket").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("resourceId").displayName("ID")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete", "getFields", "getSubmissions"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email address of the contact.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("firstName").displayName("First Name")
			.type(ParameterType.STRING)
			.description("First name of the contact.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("lastName").displayName("Last Name")
			.type(ParameterType.STRING)
			.description("Last name of the contact.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the company.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("company"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealName").displayName("Deal Name")
			.type(ParameterType.STRING)
			.description("Name of the deal.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("pipeline").displayName("Pipeline")
			.type(ParameterType.STRING)
			.description("Pipeline for the deal.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dealStage").displayName("Deal Stage")
			.type(ParameterType.STRING)
			.description("Stage of the deal.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("deal"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ticketName").displayName("Ticket Name")
			.type(ParameterType.STRING)
			.description("Name/subject of the ticket.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ticketPipeline").displayName("Ticket Pipeline")
			.type(ParameterType.STRING)
			.description("Pipeline for the ticket.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("ticketStatus").displayName("Ticket Status")
			.type(ParameterType.STRING)
			.description("Status of the ticket.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("engagementType").displayName("Engagement Type")
			.type(ParameterType.OPTIONS)
			.description("Type of engagement.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("engagement"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Note").value("NOTE").build(),
				ParameterOption.builder().name("Email").value("EMAIL").build(),
				ParameterOption.builder().name("Task").value("TASK").build(),
				ParameterOption.builder().name("Meeting").value("MEETING").build(),
				ParameterOption.builder().name("Call").value("CALL").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("searchQuery").displayName("Search Query")
			.type(ParameterType.STRING).required(true)
			.description("Search query string.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "getRecentlyCreated", "getRecentlyUpdated", "search", "getSubmissions"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Max number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll", "getRecentlyCreated", "getRecentlyUpdated", "search", "getSubmissions"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "contact" -> executeContact(context, operation, headers);
				case "company" -> executeCompany(context, operation, headers);
				case "deal" -> executeDeal(context, operation, headers);
				case "engagement" -> executeEngagement(context, operation, headers);
				case "form" -> executeForm(context, operation, headers);
				case "ticket" -> executeTicket(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "HubSpot API error: " + e.getMessage(), e);
		}
	}

	// ========================= Contact Operations =========================

	private NodeExecutionResult executeContact(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> properties = buildContactProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = post(BASE_URL + "/crm/v3/objects/contacts", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/crm/v3/objects/contacts/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/contacts/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/contacts?limit=" + limit, headers);
				return toListResult(response, "results");
			}
			case "getRecentlyCreated": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/contacts?limit=" + limit + "&sort=-createdate", headers);
				return toListResult(response, "results");
			}
			case "getRecentlyUpdated": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/contacts?limit=" + limit + "&sort=-hs_lastmodifieddate", headers);
				return toListResult(response, "results");
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				int limit = getLimit(context);
				Map<String, Object> body = Map.of("query", query, "limit", limit);
				HttpResponse<String> response = post(BASE_URL + "/crm/v3/objects/contacts/search", body, headers);
				return toListResult(response, "results");
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> properties = buildContactProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = patch(BASE_URL + "/crm/v3/objects/contacts/" + encode(id), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown contact operation: " + operation);
		}
	}

	// ========================= Company Operations =========================

	private NodeExecutionResult executeCompany(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> properties = buildCompanyProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = post(BASE_URL + "/crm/v3/objects/companies", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/crm/v3/objects/companies/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/companies/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/companies?limit=" + limit, headers);
				return toListResult(response, "results");
			}
			case "getRecentlyCreated": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/companies?limit=" + limit + "&sort=-createdate", headers);
				return toListResult(response, "results");
			}
			case "getRecentlyUpdated": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/companies?limit=" + limit + "&sort=-hs_lastmodifieddate", headers);
				return toListResult(response, "results");
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				int limit = getLimit(context);
				Map<String, Object> body = Map.of("query", query, "limit", limit);
				HttpResponse<String> response = post(BASE_URL + "/crm/v3/objects/companies/search", body, headers);
				return toListResult(response, "results");
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> properties = buildCompanyProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = patch(BASE_URL + "/crm/v3/objects/companies/" + encode(id), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown company operation: " + operation);
		}
	}

	// ========================= Deal Operations =========================

	private NodeExecutionResult executeDeal(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> properties = buildDealProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = post(BASE_URL + "/crm/v3/objects/deals", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/crm/v3/objects/deals/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/deals/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/deals?limit=" + limit, headers);
				return toListResult(response, "results");
			}
			case "getRecentlyCreated": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/deals?limit=" + limit + "&sort=-createdate", headers);
				return toListResult(response, "results");
			}
			case "getRecentlyUpdated": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/deals?limit=" + limit + "&sort=-hs_lastmodifieddate", headers);
				return toListResult(response, "results");
			}
			case "search": {
				String query = context.getParameter("searchQuery", "");
				int limit = getLimit(context);
				Map<String, Object> body = Map.of("query", query, "limit", limit);
				HttpResponse<String> response = post(BASE_URL + "/crm/v3/objects/deals/search", body, headers);
				return toListResult(response, "results");
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> properties = buildDealProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = patch(BASE_URL + "/crm/v3/objects/deals/" + encode(id), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown deal operation: " + operation);
		}
	}

	// ========================= Engagement Operations =========================

	private NodeExecutionResult executeEngagement(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String engagementType = context.getParameter("engagementType", "NOTE");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> additional = new LinkedHashMap<>(parseJson(additionalJson));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("engagement", Map.of("type", engagementType));
				body.put("metadata", additional);
				HttpResponse<String> response = post(BASE_URL + "/engagements/v1/engagements", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/engagements/v1/engagements/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/engagements/v1/engagements/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/engagements/v1/engagements/paged?limit=" + limit, headers);
				return toListResult(response, "results");
			}
			default:
				return NodeExecutionResult.error("Unknown engagement operation: " + operation);
		}
	}

	// ========================= Form Operations =========================

	private NodeExecutionResult executeForm(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/marketing/v3/forms/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/marketing/v3/forms/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/marketing/v3/forms/?limit=" + limit, headers);
				return toListResult(response, "results");
			}
			case "getFields": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/marketing/v3/forms/" + encode(id), headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object fieldGroups = parsed.get("fieldGroups");
				if (fieldGroups != null) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("fieldGroups", fieldGroups))));
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "getSubmissions": {
				String id = context.getParameter("resourceId", "");
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/form-integrations/v1/submissions/forms/" + encode(id) + "?limit=" + limit, headers);
				return toListResult(response, "results");
			}
			default:
				return NodeExecutionResult.error("Unknown form operation: " + operation);
		}
	}

	// ========================= Ticket Operations =========================

	private NodeExecutionResult executeTicket(NodeExecutionContext context, String operation, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> properties = buildTicketProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = post(BASE_URL + "/crm/v3/objects/tickets", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/crm/v3/objects/tickets/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/tickets/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				HttpResponse<String> response = get(BASE_URL + "/crm/v3/objects/tickets?limit=" + limit, headers);
				return toListResult(response, "results");
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				Map<String, Object> properties = buildTicketProperties(context);
				Map<String, Object> body = Map.of("properties", properties);
				HttpResponse<String> response = patch(BASE_URL + "/crm/v3/objects/tickets/" + encode(id), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown ticket operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		String token = String.valueOf(credentials.getOrDefault("accessToken",
			credentials.getOrDefault("apiKey", "")));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		return returnAll ? 100 : Math.min(limit, 100);
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private Map<String, Object> buildContactProperties(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> props = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(props, "email", context.getParameter("email", ""));
		putIfNotEmpty(props, "firstname", context.getParameter("firstName", ""));
		putIfNotEmpty(props, "lastname", context.getParameter("lastName", ""));
		return props;
	}

	private Map<String, Object> buildCompanyProperties(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> props = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(props, "name", context.getParameter("name", ""));
		return props;
	}

	private Map<String, Object> buildDealProperties(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> props = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(props, "dealname", context.getParameter("dealName", ""));
		putIfNotEmpty(props, "pipeline", context.getParameter("pipeline", ""));
		putIfNotEmpty(props, "dealstage", context.getParameter("dealStage", ""));
		return props;
	}

	private Map<String, Object> buildTicketProperties(NodeExecutionContext context) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> props = new LinkedHashMap<>(parseJson(additionalJson));
		putIfNotEmpty(props, "subject", context.getParameter("ticketName", ""));
		putIfNotEmpty(props, "hs_pipeline", context.getParameter("ticketPipeline", ""));
		putIfNotEmpty(props, "hs_pipeline_stage", context.getParameter("ticketStatus", ""));
		return props;
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

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("HubSpot API error (HTTP " + response.statusCode() + "): " + body);
	}
}
