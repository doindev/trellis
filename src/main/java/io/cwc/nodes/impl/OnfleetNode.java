package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Onfleet Node -- manage admins, containers, destinations, hubs,
 * organizations, recipients, tasks, teams, and workers via the Onfleet API.
 */
@Slf4j
@Node(
	type = "onfleet",
	displayName = "Onfleet",
	description = "Manage delivery operations in Onfleet",
	category = "Miscellaneous",
	icon = "onfleet",
	credentials = {"onfleetApi"},
	searchOnly = true
)
public class OnfleetNode extends AbstractApiNode {

	private static final String BASE_URL = "https://onfleet.com/api/v2";

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

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("task")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Admin").value("admin").description("Manage administrators").build(),
				ParameterOption.builder().name("Container").value("container").description("Manage containers").build(),
				ParameterOption.builder().name("Destination").value("destination").description("Manage destinations").build(),
				ParameterOption.builder().name("Hub").value("hub").description("Manage hubs").build(),
				ParameterOption.builder().name("Organization").value("organization").description("View organization").build(),
				ParameterOption.builder().name("Recipient").value("recipient").description("Manage recipients").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build(),
				ParameterOption.builder().name("Team").value("team").description("Manage teams").build(),
				ParameterOption.builder().name("Worker").value("worker").description("Manage workers").build()
			)).build());

		addOperationSelectors(params);
		addResourceParameters(params);

		return params;
	}

	private void addOperationSelectors(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("admin"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"))))
			.options(List.of(
				ParameterOption.builder().name("Add Tasks").value("addTasks").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Update Tasks").value("updateTasks").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("destination"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Get").value("get").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("hub"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
			.options(List.of(
				ParameterOption.builder().name("Clone").value("clone").build(),
				ParameterOption.builder().name("Complete").value("complete").build(),
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Get Time Estimate").value("getTimeEstimate").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("worker"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Get Schedule").value("getSchedule").build(),
				ParameterOption.builder().name("Set Schedule").value("setSchedule").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());
	}

	private void addResourceParameters(List<NodeParameter> params) {
		// Admin parameters
		params.add(NodeParameter.builder()
			.name("adminName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("admin"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("adminEmail").displayName("Email").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("admin"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("adminId").displayName("Admin ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("admin"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("adminUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("admin"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build()
			)).build());

		// Container parameters
		params.add(NodeParameter.builder()
			.name("containerId").displayName("Container ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"))))
			.build());
		params.add(NodeParameter.builder()
			.name("containerType").displayName("Container Type").type(ParameterType.OPTIONS).defaultValue("workers")
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"))))
			.options(List.of(
				ParameterOption.builder().name("Workers").value("workers").build(),
				ParameterOption.builder().name("Teams").value("teams").build(),
				ParameterOption.builder().name("Organizations").value("organizations").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("containerTaskIds").displayName("Task IDs (comma-separated)").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("container"), "operation", List.of("addTasks", "updateTasks"))))
			.build());

		// Destination parameters
		params.add(NodeParameter.builder()
			.name("destAddress").displayName("Address").type(ParameterType.STRING).required(true)
			.description("Full street address")
			.displayOptions(Map.of("show", Map.of("resource", List.of("destination"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("destId").displayName("Destination ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("destination"), "operation", List.of("get"))))
			.build());

		// Hub parameters
		params.add(NodeParameter.builder()
			.name("hubName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("hub"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("hubAddress").displayName("Address").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("hub"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("hubId").displayName("Hub ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("hub"), "operation", List.of("update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("hubUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("hub"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build()
			)).build());

		// Recipient parameters
		params.add(NodeParameter.builder()
			.name("recipientName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("recipientPhone").displayName("Phone").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("recipientId").displayName("Recipient ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("get", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("recipientUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("notes").displayName("Notes").type(ParameterType.STRING).build()
			)).build());

		// Task parameters
		params.add(NodeParameter.builder()
			.name("taskDestination").displayName("Destination Address").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskRecipientName").displayName("Recipient Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskRecipientPhone").displayName("Recipient Phone").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskId").displayName("Task ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("get", "delete", "update", "clone", "complete"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskCompletionDetails").displayName("Completion Details").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("complete"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("success").displayName("Success").type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder().name("notes").displayName("Notes").type(ParameterType.STRING).build()
			)).build());
		params.add(NodeParameter.builder()
			.name("taskUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("notes").displayName("Notes").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("worker").displayName("Worker ID").type(ParameterType.STRING).build()
			)).build());

		// Team parameters
		params.add(NodeParameter.builder()
			.name("teamName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("teamWorkerIds").displayName("Worker IDs (comma-separated)").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("teamManagerIds").displayName("Manager IDs (comma-separated)").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("teamHubId").displayName("Hub ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("teamId").displayName("Team ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("get", "delete", "update", "getTimeEstimate"))))
			.build());
		params.add(NodeParameter.builder()
			.name("teamEstimateDropoffAddress").displayName("Dropoff Address").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("getTimeEstimate"))))
			.build());
		params.add(NodeParameter.builder()
			.name("teamUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("hub").displayName("Hub ID").type(ParameterType.STRING).build()
			)).build());

		// Worker parameters
		params.add(NodeParameter.builder()
			.name("workerName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("worker"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("workerPhone").displayName("Phone").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("worker"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("workerTeams").displayName("Team IDs (comma-separated)").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("worker"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("workerId").displayName("Worker ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("worker"), "operation", List.of("get", "delete", "update", "getSchedule", "setSchedule"))))
			.build());
		params.add(NodeParameter.builder()
			.name("workerSchedule").displayName("Schedule (JSON)").type(ParameterType.STRING)
			.description("Schedule in JSON format")
			.displayOptions(Map.of("show", Map.of("resource", List.of("worker"), "operation", List.of("setSchedule"))))
			.build());
		params.add(NodeParameter.builder()
			.name("workerUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("worker"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("phone").displayName("Phone").type(ParameterType.STRING).build()
			)).build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "task");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "admin" -> executeAdmin(context, headers);
				case "container" -> executeContainer(context, headers);
				case "destination" -> executeDestination(context, headers);
				case "hub" -> executeHub(context, headers);
				case "organization" -> executeOrganization(headers);
				case "recipient" -> executeRecipient(context, headers);
				case "task" -> executeTask(context, headers);
				case "team" -> executeTeam(context, headers);
				case "worker" -> executeWorker(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Onfleet API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeAdmin(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of("name", context.getParameter("adminName", ""),
					"email", context.getParameter("adminEmail", ""));
				HttpResponse<String> response = post(BASE_URL + "/admins", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/admins/" + encode(context.getParameter("adminId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/admins", headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(BASE_URL + "/admins/" + encode(context.getParameter("adminId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("adminUpdateFields", Map.of());
				HttpResponse<String> response = put(BASE_URL + "/admins/" + encode(context.getParameter("adminId", "")), updateFields, headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown admin operation: " + operation);
		}
	}

	private NodeExecutionResult executeContainer(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "get");
		String containerId = context.getParameter("containerId", "");
		String containerType = context.getParameter("containerType", "workers");

		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/containers/" + encode(containerType) + "/" + encode(containerId), headers);
				return toResult(response);
			}
			case "addTasks":
			case "updateTasks": {
				String taskIds = context.getParameter("containerTaskIds", "");
				List<String> tasks = Arrays.asList(taskIds.split(","));
				int type = "addTasks".equals(operation) ? 2 : 1;
				Map<String, Object> body = Map.of("tasks", tasks, "type", type);
				HttpResponse<String> response = put(BASE_URL + "/containers/" + encode(containerType) + "/" + encode(containerId), body, headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown container operation: " + operation);
		}
	}

	private NodeExecutionResult executeDestination(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");
		switch (operation) {
			case "create": {
				Map<String, Object> address = Map.of("unparsed", context.getParameter("destAddress", ""));
				Map<String, Object> body = Map.of("address", address);
				HttpResponse<String> response = post(BASE_URL + "/destinations", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/destinations/" + encode(context.getParameter("destId", "")), headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown destination operation: " + operation);
		}
	}

	private NodeExecutionResult executeHub(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> address = Map.of("unparsed", context.getParameter("hubAddress", ""));
				Map<String, Object> body = Map.of("name", context.getParameter("hubName", ""), "address", address);
				HttpResponse<String> response = post(BASE_URL + "/hubs", body, headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/hubs", headers);
				return toListResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("hubUpdateFields", Map.of());
				HttpResponse<String> response = put(BASE_URL + "/hubs/" + encode(context.getParameter("hubId", "")), updateFields, headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown hub operation: " + operation);
		}
	}

	private NodeExecutionResult executeOrganization(Map<String, String> headers) throws Exception {
		HttpResponse<String> response = get(BASE_URL + "/organization", headers);
		return toResult(response);
	}

	private NodeExecutionResult executeRecipient(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");
		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of("name", context.getParameter("recipientName", ""),
					"phone", context.getParameter("recipientPhone", ""));
				HttpResponse<String> response = post(BASE_URL + "/recipients", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/recipients/" + encode(context.getParameter("recipientId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("recipientUpdateFields", Map.of());
				HttpResponse<String> response = put(BASE_URL + "/recipients/" + encode(context.getParameter("recipientId", "")), updateFields, headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown recipient operation: " + operation);
		}
	}

	private NodeExecutionResult executeTask(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> destination = Map.of("address", Map.of("unparsed", context.getParameter("taskDestination", "")));
				Map<String, Object> recipient = Map.of("name", context.getParameter("taskRecipientName", ""),
					"phone", context.getParameter("taskRecipientPhone", ""));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("destination", destination);
				body.put("recipients", List.of(recipient));
				HttpResponse<String> response = post(BASE_URL + "/tasks", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/tasks/" + encode(context.getParameter("taskId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/tasks", headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(BASE_URL + "/tasks/" + encode(context.getParameter("taskId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("taskUpdateFields", Map.of());
				HttpResponse<String> response = put(BASE_URL + "/tasks/" + encode(context.getParameter("taskId", "")), updateFields, headers);
				return toResult(response);
			}
			case "clone": {
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(context.getParameter("taskId", "")) + "/clone", Map.of(), headers);
				return toResult(response);
			}
			case "complete": {
				Map<String, Object> details = context.getParameter("taskCompletionDetails", Map.of());
				Map<String, Object> body = Map.of("completionDetails", Map.of(
					"success", toBoolean(details.get("success"), true),
					"notes", String.valueOf(details.getOrDefault("notes", ""))
				));
				HttpResponse<String> response = post(BASE_URL + "/tasks/" + encode(context.getParameter("taskId", "")) + "/complete", body, headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown task operation: " + operation);
		}
	}

	private NodeExecutionResult executeTeam(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("teamName", ""));
				body.put("workers", Arrays.asList(context.getParameter("teamWorkerIds", "").split(",")));
				body.put("managers", Arrays.asList(context.getParameter("teamManagerIds", "").split(",")));
				body.put("hub", context.getParameter("teamHubId", ""));
				HttpResponse<String> response = post(BASE_URL + "/teams", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/teams/" + encode(context.getParameter("teamId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/teams", headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(BASE_URL + "/teams/" + encode(context.getParameter("teamId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("teamUpdateFields", Map.of());
				HttpResponse<String> response = put(BASE_URL + "/teams/" + encode(context.getParameter("teamId", "")), updateFields, headers);
				return toResult(response);
			}
			case "getTimeEstimate": {
				String teamId = context.getParameter("teamId", "");
				String dropoff = context.getParameter("teamEstimateDropoffAddress", "");
				String url = buildUrl(BASE_URL + "/teams/" + encode(teamId) + "/estimate", Map.of("dropoffAddress", dropoff));
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown team operation: " + operation);
		}
	}

	private NodeExecutionResult executeWorker(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("workerName", ""));
				body.put("phone", context.getParameter("workerPhone", ""));
				body.put("teams", Arrays.asList(context.getParameter("workerTeams", "").split(",")));
				HttpResponse<String> response = post(BASE_URL + "/workers", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(BASE_URL + "/workers/" + encode(context.getParameter("workerId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/workers", headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(BASE_URL + "/workers/" + encode(context.getParameter("workerId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("workerUpdateFields", Map.of());
				HttpResponse<String> response = put(BASE_URL + "/workers/" + encode(context.getParameter("workerId", "")), updateFields, headers);
				return toResult(response);
			}
			case "getSchedule": {
				HttpResponse<String> response = get(BASE_URL + "/workers/" + encode(context.getParameter("workerId", "")) + "/schedule", headers);
				return toResult(response);
			}
			case "setSchedule": {
				String scheduleJson = context.getParameter("workerSchedule", "{}");
				Map<String, Object> body = parseJson(scheduleJson);
				HttpResponse<String> response = post(BASE_URL + "/workers/" + encode(context.getParameter("workerId", "")) + "/schedule", body, headers);
				return toResult(response);
			}
			default: return NodeExecutionResult.error("Unknown worker operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		String auth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Basic " + auth);
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) return apiError(response);
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) return apiError(response);
		String body = response.body();
		if (body != null && body.trim().startsWith("[")) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Map<String, Object> item : parseArrayResponse(response)) {
				items.add(wrapInJson(item));
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Onfleet API error (HTTP " + response.statusCode() + "): " + body);
	}
}
