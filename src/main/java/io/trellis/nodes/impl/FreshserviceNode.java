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
 * Freshservice — manage IT service management resources via the Freshservice REST API v2.
 */
@Slf4j
@Node(
		type = "freshservice",
		displayName = "Freshservice",
		description = "Manage IT service management in Freshservice",
		category = "Customer Support",
		icon = "freshservice",
		credentials = {"freshserviceApi"},
		searchOnly = true
)
public class FreshserviceNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("ticket")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Ticket").value("ticket").description("Manage service desk tickets").build(),
						ParameterOption.builder().name("Agent").value("agent").description("Manage agents").build(),
						ParameterOption.builder().name("Agent Group").value("agentGroup").description("Manage agent groups").build(),
						ParameterOption.builder().name("Department").value("department").description("Manage departments").build(),
						ParameterOption.builder().name("Requester").value("requester").description("Manage requesters").build()
				)).build());

		// Operation selectors per resource
		addOperationSelector(params, "ticket", List.of("create", "get", "getAll", "update", "delete"));
		addOperationSelector(params, "agent", List.of("create", "get", "getAll", "update", "delete"));
		addOperationSelector(params, "agentGroup", List.of("create", "get", "getAll", "update", "delete"));
		addOperationSelector(params, "department", List.of("create", "get", "getAll", "update", "delete"));
		addOperationSelector(params, "requester", List.of("create", "get", "getAll", "update", "delete"));

		// Ticket parameters
		addTicketParameters(params);
		// Agent parameters
		addAgentParameters(params);
		// Agent Group parameters
		addAgentGroupParameters(params);
		// Department parameters
		addDepartmentParameters(params);
		// Requester parameters
		addRequesterParameters(params);

		// Common getAll parameters
		for (String res : List.of("ticket", "agent", "agentGroup", "department", "requester")) {
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

	private void addOperationSelector(List<NodeParameter> params, String resource, List<String> ops) {
		List<ParameterOption> options = new ArrayList<>();
		for (String op : ops) {
			String name = switch (op) {
				case "create" -> "Create";
				case "get" -> "Get";
				case "getAll" -> "Get Many";
				case "update" -> "Update";
				case "delete" -> "Delete";
				default -> op;
			};
			options.add(ParameterOption.builder().name(name).value(op).build());
		}
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue(ops.get(0))
				.displayOptions(Map.of("show", Map.of("resource", List.of(resource))))
				.options(options).build());
	}

	private void addTicketParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("ticketId").displayName("Ticket ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketEmail").displayName("Requester Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketSubject").displayName("Subject")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketDescription").displayName("Description")
				.type(ParameterType.STRING).required(true).typeOptions(Map.of("rows", 4))
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("ticketPriority").displayName("Priority")
				.type(ParameterType.OPTIONS).defaultValue(1)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Low").value(1).build(),
						ParameterOption.builder().name("Medium").value(2).build(),
						ParameterOption.builder().name("High").value(3).build(),
						ParameterOption.builder().name("Urgent").value(4).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("ticketStatus").displayName("Status")
				.type(ParameterType.OPTIONS).defaultValue(2)
				.displayOptions(Map.of("show", Map.of("resource", List.of("ticket"), "operation", List.of("create"))))
				.options(List.of(
						ParameterOption.builder().name("Open").value(2).build(),
						ParameterOption.builder().name("Pending").value(3).build(),
						ParameterOption.builder().name("Resolved").value(4).build(),
						ParameterOption.builder().name("Closed").value(5).build()
				)).build());

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
								)).build()
				)).build());
	}

	private void addAgentParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("agentId").displayName("Agent ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agent"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("agentEmail").displayName("Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agent"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("agentAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agent"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("firstName").displayName("First Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("lastName").displayName("Last Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("departmentIds").displayName("Department IDs").type(ParameterType.STRING)
								.description("Comma-separated department IDs").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("agentUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agent"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("firstName").displayName("First Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("lastName").displayName("Last Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build()
				)).build());
	}

	private void addAgentGroupParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("agentGroupId").displayName("Agent Group ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agentGroup"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("agentGroupName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agentGroup"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("agentGroupAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agentGroup"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("escalateToId").displayName("Escalate To ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("memberIds").displayName("Member IDs").type(ParameterType.STRING)
								.description("Comma-separated agent IDs").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("agentGroupUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("agentGroup"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build()
				)).build());
	}

	private void addDepartmentParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("departmentId").displayName("Department ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("department"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("departmentName").displayName("Name")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("department"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("departmentAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("department"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("headUserId").displayName("Head User ID").type(ParameterType.NUMBER).build(),
						NodeParameter.builder().name("primeUserId").displayName("Prime User ID").type(ParameterType.NUMBER).build()
				)).build());

		params.add(NodeParameter.builder()
				.name("departmentUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("department"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build()
				)).build());
	}

	private void addRequesterParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
				.name("requesterId").displayName("Requester ID")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("requester"), "operation", List.of("get", "update", "delete"))))
				.build());

		params.add(NodeParameter.builder()
				.name("requesterEmail").displayName("Email")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("resource", List.of("requester"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("requesterAdditionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("requester"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("firstName").displayName("First Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("lastName").displayName("Last Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("departmentIds").displayName("Department IDs").type(ParameterType.STRING)
								.description("Comma-separated department IDs").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("requesterUpdateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("requester"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("firstName").displayName("First Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("lastName").displayName("Last Name").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("email").displayName("Email").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build()
				)).build());
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "ticket");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "ticket" -> executeTicket(context, credentials);
				case "agent" -> executeAgent(context, credentials);
				case "agentGroup" -> executeAgentGroup(context, credentials);
				case "department" -> executeDepartment(context, credentials);
				case "requester" -> executeRequester(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Freshservice API error: " + e.getMessage(), e);
		}
	}

	// ========================= Ticket =========================

	private NodeExecutionResult executeTicket(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", context.getParameter("ticketEmail", ""));
				body.put("subject", context.getParameter("ticketSubject", ""));
				body.put("description", context.getParameter("ticketDescription", ""));
				body.put("priority", toInt(context.getParameter("ticketPriority", 1), 1));
				body.put("status", toInt(context.getParameter("ticketStatus", 2), 2));

				HttpResponse<String> response = post(baseUrl + "/tickets", body, headers);
				return toResult(response, "ticket");
			}
			case "get": {
				String id = context.getParameter("ticketId", "");
				HttpResponse<String> response = get(baseUrl + "/tickets/" + encode(id), headers);
				return toResult(response, "ticket");
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/tickets?per_page=" + Math.min(limit, 100), headers);
				return toListResult(response, "tickets");
			}
			case "update": {
				String id = context.getParameter("ticketId", "");
				Map<String, Object> updateFields = context.getParameter("ticketUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "subject", updateFields.get("subject"));
				putIfPresent(body, "description", updateFields.get("description"));
				if (updateFields.get("priority") != null) body.put("priority", toInt(updateFields.get("priority"), 1));
				if (updateFields.get("status") != null) body.put("status", toInt(updateFields.get("status"), 2));

				HttpResponse<String> response = put(baseUrl + "/tickets/" + encode(id), body, headers);
				return toResult(response, "ticket");
			}
			case "delete": {
				String id = context.getParameter("ticketId", "");
				HttpResponse<String> response = delete(baseUrl + "/tickets/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown ticket operation: " + operation);
		}
	}

	// ========================= Agent =========================

	private NodeExecutionResult executeAgent(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", context.getParameter("agentEmail", ""));
				Map<String, Object> additional = context.getParameter("agentAdditionalFields", Map.of());
				putIfPresent(body, "first_name", additional.get("firstName"));
				putIfPresent(body, "last_name", additional.get("lastName"));
				putIfPresent(body, "phone", additional.get("phone"));
				if (additional.get("departmentIds") != null && !String.valueOf(additional.get("departmentIds")).isEmpty()) {
					body.put("department_ids", parseLongCsv(String.valueOf(additional.get("departmentIds"))));
				}

				HttpResponse<String> response = post(baseUrl + "/agents", body, headers);
				return toResult(response, "agent");
			}
			case "get": {
				String id = context.getParameter("agentId", "");
				HttpResponse<String> response = get(baseUrl + "/agents/" + encode(id), headers);
				return toResult(response, "agent");
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/agents?per_page=" + Math.min(limit, 100), headers);
				return toListResult(response, "agents");
			}
			case "update": {
				String id = context.getParameter("agentId", "");
				Map<String, Object> updateFields = context.getParameter("agentUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "first_name", updateFields.get("firstName"));
				putIfPresent(body, "last_name", updateFields.get("lastName"));
				putIfPresent(body, "phone", updateFields.get("phone"));
				putIfPresent(body, "email", updateFields.get("email"));

				HttpResponse<String> response = put(baseUrl + "/agents/" + encode(id), body, headers);
				return toResult(response, "agent");
			}
			case "delete": {
				String id = context.getParameter("agentId", "");
				HttpResponse<String> response = delete(baseUrl + "/agents/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown agent operation: " + operation);
		}
	}

	// ========================= Agent Group =========================

	private NodeExecutionResult executeAgentGroup(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("agentGroupName", ""));
				Map<String, Object> additional = context.getParameter("agentGroupAdditionalFields", Map.of());
				putIfPresent(body, "description", additional.get("description"));
				putIfPresent(body, "escalate_to", additional.get("escalateToId"));
				if (additional.get("memberIds") != null && !String.valueOf(additional.get("memberIds")).isEmpty()) {
					body.put("members", parseLongCsv(String.valueOf(additional.get("memberIds"))));
				}

				HttpResponse<String> response = post(baseUrl + "/groups", body, headers);
				return toResult(response, "group");
			}
			case "get": {
				String id = context.getParameter("agentGroupId", "");
				HttpResponse<String> response = get(baseUrl + "/groups/" + encode(id), headers);
				return toResult(response, "group");
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/groups?per_page=" + Math.min(limit, 100), headers);
				return toListResult(response, "groups");
			}
			case "update": {
				String id = context.getParameter("agentGroupId", "");
				Map<String, Object> updateFields = context.getParameter("agentGroupUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "description", updateFields.get("description"));

				HttpResponse<String> response = put(baseUrl + "/groups/" + encode(id), body, headers);
				return toResult(response, "group");
			}
			case "delete": {
				String id = context.getParameter("agentGroupId", "");
				HttpResponse<String> response = delete(baseUrl + "/groups/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown agent group operation: " + operation);
		}
	}

	// ========================= Department =========================

	private NodeExecutionResult executeDepartment(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("departmentName", ""));
				Map<String, Object> additional = context.getParameter("departmentAdditionalFields", Map.of());
				putIfPresent(body, "description", additional.get("description"));
				putIfPresent(body, "head_user_id", additional.get("headUserId"));
				putIfPresent(body, "prime_user_id", additional.get("primeUserId"));

				HttpResponse<String> response = post(baseUrl + "/departments", body, headers);
				return toResult(response, "department");
			}
			case "get": {
				String id = context.getParameter("departmentId", "");
				HttpResponse<String> response = get(baseUrl + "/departments/" + encode(id), headers);
				return toResult(response, "department");
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/departments?per_page=" + Math.min(limit, 100), headers);
				return toListResult(response, "departments");
			}
			case "update": {
				String id = context.getParameter("departmentId", "");
				Map<String, Object> updateFields = context.getParameter("departmentUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "description", updateFields.get("description"));

				HttpResponse<String> response = put(baseUrl + "/departments/" + encode(id), body, headers);
				return toResult(response, "department");
			}
			case "delete": {
				String id = context.getParameter("departmentId", "");
				HttpResponse<String> response = delete(baseUrl + "/departments/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown department operation: " + operation);
		}
	}

	// ========================= Requester =========================

	private NodeExecutionResult executeRequester(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", context.getParameter("requesterEmail", ""));
				Map<String, Object> additional = context.getParameter("requesterAdditionalFields", Map.of());
				putIfPresent(body, "first_name", additional.get("firstName"));
				putIfPresent(body, "last_name", additional.get("lastName"));
				putIfPresent(body, "phone", additional.get("phone"));
				putIfPresent(body, "job_title", additional.get("jobTitle"));
				if (additional.get("departmentIds") != null && !String.valueOf(additional.get("departmentIds")).isEmpty()) {
					body.put("department_ids", parseLongCsv(String.valueOf(additional.get("departmentIds"))));
				}

				HttpResponse<String> response = post(baseUrl + "/requesters", body, headers);
				return toResult(response, "requester");
			}
			case "get": {
				String id = context.getParameter("requesterId", "");
				HttpResponse<String> response = get(baseUrl + "/requesters/" + encode(id), headers);
				return toResult(response, "requester");
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				HttpResponse<String> response = get(baseUrl + "/requesters?per_page=" + Math.min(limit, 100), headers);
				return toListResult(response, "requesters");
			}
			case "update": {
				String id = context.getParameter("requesterId", "");
				Map<String, Object> updateFields = context.getParameter("requesterUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "first_name", updateFields.get("firstName"));
				putIfPresent(body, "last_name", updateFields.get("lastName"));
				putIfPresent(body, "email", updateFields.get("email"));
				putIfPresent(body, "phone", updateFields.get("phone"));
				putIfPresent(body, "job_title", updateFields.get("jobTitle"));

				HttpResponse<String> response = put(baseUrl + "/requesters/" + encode(id), body, headers);
				return toResult(response, "requester");
			}
			case "delete": {
				String id = context.getParameter("requesterId", "");
				HttpResponse<String> response = delete(baseUrl + "/requesters/" + encode(id), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown requester operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String domain = String.valueOf(credentials.getOrDefault("domain", ""));
		return "https://" + domain + ".freshservice.com/api/v2";
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

	private List<Long> parseLongCsv(String value) {
		if (value == null || value.isBlank()) return List.of();
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(Long::parseLong)
				.toList();
	}

	private NodeExecutionResult toResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof Map) {
			return NodeExecutionResult.success(List.of(wrapInJson(data)));
		}
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

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Freshservice API error (HTTP " + response.statusCode() + "): " + body);
	}
}
