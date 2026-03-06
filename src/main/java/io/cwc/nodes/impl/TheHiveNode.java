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
 * TheHive Node -- manage alerts, cases, logs, observables, and tasks
 * via TheHive v4 REST API.
 */
@Slf4j
@Node(
	type = "theHive",
	displayName = "TheHive",
	description = "Manage alerts, cases, logs, observables, and tasks in TheHive",
	category = "Miscellaneous",
	icon = "theHive",
	credentials = {"theHiveApi"},
	searchOnly = true
)
public class TheHiveNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("alert")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Alert").value("alert").description("Manage alerts").build(),
				ParameterOption.builder().name("Case").value("case_").description("Manage cases").build(),
				ParameterOption.builder().name("Log").value("log").description("Manage task logs").build(),
				ParameterOption.builder().name("Observable").value("observable").description("Manage observables").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build()
			)).build());

		addAlertOperations(params);
		addCaseOperations(params);
		addLogOperations(params);
		addObservableOperations(params);
		addTaskOperations(params);

		addAlertParameters(params);
		addCaseParameters(params);
		addLogParameters(params);
		addObservableParameters(params);
		addTaskParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addAlertOperations(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an alert").build(),
				ParameterOption.builder().name("Execute Responder").value("executeResponder").description("Execute a responder on an alert").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an alert").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all alerts").build(),
				ParameterOption.builder().name("Merge Into Case").value("mergeIntoCase").description("Merge an alert into a case").build(),
				ParameterOption.builder().name("Promote").value("promote").description("Promote an alert to a case").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an alert").build()
			)).build());
	}

	private void addCaseOperations(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a case").build(),
				ParameterOption.builder().name("Execute Responder").value("executeResponder").description("Execute a responder on a case").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a case").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all cases").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a case").build()
			)).build());
	}

	private void addLogOperations(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a task log").build(),
				ParameterOption.builder().name("Execute Responder").value("executeResponder").description("Execute a responder on a log").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a task log").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all task logs").build()
			)).build());
	}

	private void addObservableOperations(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an observable").build(),
				ParameterOption.builder().name("Execute Analyzer").value("executeAnalyzer").description("Execute an analyzer on an observable").build(),
				ParameterOption.builder().name("Execute Responder").value("executeResponder").description("Execute a responder on an observable").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an observable").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all observables").build(),
				ParameterOption.builder().name("Search").value("search").description("Search observables").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an observable").build()
			)).build());
	}

	private void addTaskOperations(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a task").build(),
				ParameterOption.builder().name("Execute Responder").value("executeResponder").description("Execute a responder on a task").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a task").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all tasks").build(),
				ParameterOption.builder().name("Search").value("search").description("Search tasks").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a task").build()
			)).build());
	}

	// ========================= Alert Parameters =========================

	private void addAlertParameters(List<NodeParameter> params) {
		// Alert > Create
		params.add(NodeParameter.builder()
			.name("alertTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertDescription").displayName("Description").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertType").displayName("Type").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertSource").displayName("Source").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertSourceRef").displayName("Source Reference").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertSeverity").displayName("Severity").type(ParameterType.OPTIONS).defaultValue("2")
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Low").value("1").build(),
				ParameterOption.builder().name("Medium").value("2").build(),
				ParameterOption.builder().name("High").value("3").build(),
				ParameterOption.builder().name("Critical").value("4").build()
			)).build());

		// Alert > Get / Update / Execute Responder / Promote / Merge
		params.add(NodeParameter.builder()
			.name("alertId").displayName("Alert ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("get", "update", "executeResponder", "promote", "mergeIntoCase"))))
			.build());

		// Alert > Update
		params.add(NodeParameter.builder()
			.name("alertUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("severity").displayName("Severity").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Low").value("1").build(),
						ParameterOption.builder().name("Medium").value("2").build(),
						ParameterOption.builder().name("High").value("3").build(),
						ParameterOption.builder().name("Critical").value("4").build()
					)).build(),
				NodeParameter.builder().name("tlp").displayName("TLP").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("White").value("0").build(),
						ParameterOption.builder().name("Green").value("1").build(),
						ParameterOption.builder().name("Amber").value("2").build(),
						ParameterOption.builder().name("Red").value("3").build()
					)).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("New").value("New").build(),
						ParameterOption.builder().name("Updated").value("Updated").build(),
						ParameterOption.builder().name("Ignored").value("Ignored").build(),
						ParameterOption.builder().name("Imported").value("Imported").build()
					)).build()
			)).build());

		// Alert > Merge Into Case
		params.add(NodeParameter.builder()
			.name("alertMergeCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("mergeIntoCase"))))
			.build());

		// Alert > Execute Responder
		params.add(NodeParameter.builder()
			.name("alertResponderId").displayName("Responder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("executeResponder"))))
			.build());

		// Alert > GetAll limit
		params.add(NodeParameter.builder()
			.name("alertLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Case Parameters =========================

	private void addCaseParameters(List<NodeParameter> params) {
		// Case > Create
		params.add(NodeParameter.builder()
			.name("caseTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("caseDescription").displayName("Description").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("caseSeverity").displayName("Severity").type(ParameterType.OPTIONS).defaultValue("2")
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Low").value("1").build(),
				ParameterOption.builder().name("Medium").value("2").build(),
				ParameterOption.builder().name("High").value("3").build(),
				ParameterOption.builder().name("Critical").value("4").build()
			)).build());

		// Case > Get / Update / Execute Responder
		params.add(NodeParameter.builder()
			.name("caseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("get", "update", "executeResponder"))))
			.build());

		// Case > Update
		params.add(NodeParameter.builder()
			.name("caseUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("severity").displayName("Severity").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Low").value("1").build(),
						ParameterOption.builder().name("Medium").value("2").build(),
						ParameterOption.builder().name("High").value("3").build(),
						ParameterOption.builder().name("Critical").value("4").build()
					)).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Open").value("Open").build(),
						ParameterOption.builder().name("Resolved").value("Resolved").build(),
						ParameterOption.builder().name("Deleted").value("Deleted").build()
					)).build()
			)).build());

		// Case > Execute Responder
		params.add(NodeParameter.builder()
			.name("caseResponderId").displayName("Responder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("executeResponder"))))
			.build());

		// Case > GetAll limit
		params.add(NodeParameter.builder()
			.name("caseLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Log Parameters =========================

	private void addLogParameters(List<NodeParameter> params) {
		// Log > Create
		params.add(NodeParameter.builder()
			.name("logTaskId").displayName("Task ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("logMessage").displayName("Message").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("create"))))
			.build());

		// Log > Get / Execute Responder
		params.add(NodeParameter.builder()
			.name("logId").displayName("Log ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("get", "executeResponder"))))
			.build());

		// Log > Execute Responder
		params.add(NodeParameter.builder()
			.name("logResponderId").displayName("Responder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("executeResponder"))))
			.build());
	}

	// ========================= Observable Parameters =========================

	private void addObservableParameters(List<NodeParameter> params) {
		// Observable > Create
		params.add(NodeParameter.builder()
			.name("observableCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("observableDataType").displayName("Data Type").type(ParameterType.STRING).required(true)
			.description("Type of the observable (ip, domain, url, mail, hash, etc.)")
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("observableData").displayName("Data").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("observableMessage").displayName("Message").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("create"))))
			.build());

		// Observable > Get / Update / Execute Analyzer / Execute Responder
		params.add(NodeParameter.builder()
			.name("observableId").displayName("Observable ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("get", "update", "executeAnalyzer", "executeResponder"))))
			.build());

		// Observable > Update
		params.add(NodeParameter.builder()
			.name("observableUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("message").displayName("Message").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("tlp").displayName("TLP").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("White").value("0").build(),
						ParameterOption.builder().name("Green").value("1").build(),
						ParameterOption.builder().name("Amber").value("2").build(),
						ParameterOption.builder().name("Red").value("3").build()
					)).build(),
				NodeParameter.builder().name("ioc").displayName("IOC").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("sighted").displayName("Sighted").type(ParameterType.BOOLEAN).build()
			)).build());

		// Observable > Execute Analyzer
		params.add(NodeParameter.builder()
			.name("observableAnalyzerId").displayName("Analyzer ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("executeAnalyzer"))))
			.build());

		// Observable > Execute Responder
		params.add(NodeParameter.builder()
			.name("observableResponderId").displayName("Responder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("executeResponder"))))
			.build());

		// Observable > Search
		params.add(NodeParameter.builder()
			.name("observableSearchQuery").displayName("Search Query (JSON)").type(ParameterType.STRING).required(true)
			.description("TheHive query JSON for searching observables.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("search"))))
			.build());
	}

	// ========================= Task Parameters =========================

	private void addTaskParameters(List<NodeParameter> params) {
		// Task > Create
		params.add(NodeParameter.builder()
			.name("taskCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskStatus").displayName("Status").type(ParameterType.OPTIONS).defaultValue("Waiting")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Waiting").value("Waiting").build(),
				ParameterOption.builder().name("In Progress").value("InProgress").build(),
				ParameterOption.builder().name("Completed").value("Completed").build(),
				ParameterOption.builder().name("Cancel").value("Cancel").build()
			)).build());

		// Task > Get / Update / Execute Responder
		params.add(NodeParameter.builder()
			.name("taskId").displayName("Task ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("get", "update", "executeResponder"))))
			.build());

		// Task > Update
		params.add(NodeParameter.builder()
			.name("taskUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Waiting").value("Waiting").build(),
						ParameterOption.builder().name("In Progress").value("InProgress").build(),
						ParameterOption.builder().name("Completed").value("Completed").build(),
						ParameterOption.builder().name("Cancel").value("Cancel").build()
					)).build(),
				NodeParameter.builder().name("owner").displayName("Owner").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build()
			)).build());

		// Task > Execute Responder
		params.add(NodeParameter.builder()
			.name("taskResponderId").displayName("Responder ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("executeResponder"))))
			.build());

		// Task > Search
		params.add(NodeParameter.builder()
			.name("taskSearchQuery").displayName("Search Query (JSON)").type(ParameterType.STRING).required(true)
			.description("TheHive query JSON for searching tasks.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("search"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "alert");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "alert" -> executeAlert(context, baseUrl, headers);
				case "case_" -> executeCase(context, baseUrl, headers);
				case "log" -> executeLog(context, baseUrl, headers);
				case "observable" -> executeObservable(context, baseUrl, headers);
				case "task" -> executeTask(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "TheHive API error: " + e.getMessage(), e);
		}
	}

	// ========================= Alert Execute =========================

	private NodeExecutionResult executeAlert(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("alertTitle", ""));
				body.put("description", context.getParameter("alertDescription", ""));
				body.put("type", context.getParameter("alertType", ""));
				body.put("source", context.getParameter("alertSource", ""));
				body.put("sourceRef", context.getParameter("alertSourceRef", ""));
				body.put("severity", Integer.parseInt(context.getParameter("alertSeverity", "2")));

				HttpResponse<String> response = post(baseUrl + "/api/alert", body, headers);
				return toResult(response);
			}
			case "get": {
				String alertId = context.getParameter("alertId", "");
				HttpResponse<String> response = get(baseUrl + "/api/alert/" + encode(alertId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("alertLimit", 25), 25);
				String url = buildUrl(baseUrl + "/api/alert", Map.of("range", "0-" + limit));
				HttpResponse<String> response = get(url, headers);
				return toListResult(response);
			}
			case "update": {
				String alertId = context.getParameter("alertId", "");
				Map<String, Object> updateFields = context.getParameter("alertUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "title", updateFields.get("title"));
				putIfPresent(body, "description", updateFields.get("description"));
				putIfPresent(body, "status", updateFields.get("status"));
				if (updateFields.get("severity") != null) {
					body.put("severity", Integer.parseInt(String.valueOf(updateFields.get("severity"))));
				}
				if (updateFields.get("tlp") != null) {
					body.put("tlp", Integer.parseInt(String.valueOf(updateFields.get("tlp"))));
				}

				HttpResponse<String> response = patch(baseUrl + "/api/alert/" + encode(alertId), body, headers);
				return toResult(response);
			}
			case "executeResponder": {
				String alertId = context.getParameter("alertId", "");
				String responderId = context.getParameter("alertResponderId", "");
				Map<String, Object> body = Map.of("responderId", responderId, "objectId", alertId, "objectType", "alert");
				HttpResponse<String> response = post(baseUrl + "/api/connector/cortex/action", body, headers);
				return toResult(response);
			}
			case "promote": {
				String alertId = context.getParameter("alertId", "");
				HttpResponse<String> response = post(baseUrl + "/api/alert/" + encode(alertId) + "/createCase", Map.of(), headers);
				return toResult(response);
			}
			case "mergeIntoCase": {
				String alertId = context.getParameter("alertId", "");
				String caseId = context.getParameter("alertMergeCaseId", "");
				HttpResponse<String> response = post(baseUrl + "/api/alert/" + encode(alertId) + "/merge/" + encode(caseId), Map.of(), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown alert operation: " + operation);
		}
	}

	// ========================= Case Execute =========================

	private NodeExecutionResult executeCase(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("caseTitle", ""));
				body.put("description", context.getParameter("caseDescription", ""));
				body.put("severity", Integer.parseInt(context.getParameter("caseSeverity", "2")));

				HttpResponse<String> response = post(baseUrl + "/api/case", body, headers);
				return toResult(response);
			}
			case "get": {
				String caseId = context.getParameter("caseId", "");
				HttpResponse<String> response = get(baseUrl + "/api/case/" + encode(caseId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("caseLimit", 25), 25);
				String url = buildUrl(baseUrl + "/api/case", Map.of("range", "0-" + limit));
				HttpResponse<String> response = get(url, headers);
				return toListResult(response);
			}
			case "update": {
				String caseId = context.getParameter("caseId", "");
				Map<String, Object> updateFields = context.getParameter("caseUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "title", updateFields.get("title"));
				putIfPresent(body, "description", updateFields.get("description"));
				putIfPresent(body, "status", updateFields.get("status"));
				if (updateFields.get("severity") != null) {
					body.put("severity", Integer.parseInt(String.valueOf(updateFields.get("severity"))));
				}

				HttpResponse<String> response = patch(baseUrl + "/api/case/" + encode(caseId), body, headers);
				return toResult(response);
			}
			case "executeResponder": {
				String caseId = context.getParameter("caseId", "");
				String responderId = context.getParameter("caseResponderId", "");
				Map<String, Object> body = Map.of("responderId", responderId, "objectId", caseId, "objectType", "case");
				HttpResponse<String> response = post(baseUrl + "/api/connector/cortex/action", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown case operation: " + operation);
		}
	}

	// ========================= Log Execute =========================

	private NodeExecutionResult executeLog(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String taskId = context.getParameter("logTaskId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("message", context.getParameter("logMessage", ""));

				HttpResponse<String> response = post(baseUrl + "/api/case/task/" + encode(taskId) + "/log", body, headers);
				return toResult(response);
			}
			case "get": {
				String logId = context.getParameter("logId", "");
				HttpResponse<String> response = get(baseUrl + "/api/case/task/log/" + encode(logId), headers);
				return toResult(response);
			}
			case "getAll": {
				String taskId = context.getParameter("logTaskId", "");
				HttpResponse<String> response = get(baseUrl + "/api/case/task/" + encode(taskId) + "/log", headers);
				return toListResult(response);
			}
			case "executeResponder": {
				String logId = context.getParameter("logId", "");
				String responderId = context.getParameter("logResponderId", "");
				Map<String, Object> body = Map.of("responderId", responderId, "objectId", logId, "objectType", "case_task_log");
				HttpResponse<String> response = post(baseUrl + "/api/connector/cortex/action", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown log operation: " + operation);
		}
	}

	// ========================= Observable Execute =========================

	private NodeExecutionResult executeObservable(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String caseId = context.getParameter("observableCaseId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("dataType", context.getParameter("observableDataType", ""));
				body.put("data", context.getParameter("observableData", ""));
				String message = context.getParameter("observableMessage", "");
				if (!message.isEmpty()) body.put("message", message);

				HttpResponse<String> response = post(baseUrl + "/api/case/" + encode(caseId) + "/artifact", body, headers);
				return toResult(response);
			}
			case "get": {
				String observableId = context.getParameter("observableId", "");
				HttpResponse<String> response = get(baseUrl + "/api/case/artifact/" + encode(observableId), headers);
				return toResult(response);
			}
			case "getAll": {
				String caseId = context.getParameter("observableCaseId", "");
				HttpResponse<String> response = get(baseUrl + "/api/case/" + encode(caseId) + "/artifact", headers);
				return toListResult(response);
			}
			case "update": {
				String observableId = context.getParameter("observableId", "");
				Map<String, Object> updateFields = context.getParameter("observableUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "message", updateFields.get("message"));
				if (updateFields.get("tlp") != null) {
					body.put("tlp", Integer.parseInt(String.valueOf(updateFields.get("tlp"))));
				}
				if (updateFields.get("ioc") != null) {
					body.put("ioc", toBoolean(updateFields.get("ioc"), false));
				}
				if (updateFields.get("sighted") != null) {
					body.put("sighted", toBoolean(updateFields.get("sighted"), false));
				}

				HttpResponse<String> response = patch(baseUrl + "/api/case/artifact/" + encode(observableId), body, headers);
				return toResult(response);
			}
			case "executeAnalyzer": {
				String observableId = context.getParameter("observableId", "");
				String analyzerId = context.getParameter("observableAnalyzerId", "");
				Map<String, Object> body = Map.of("analyzerId", analyzerId, "artifactId", observableId);
				HttpResponse<String> response = post(baseUrl + "/api/connector/cortex/job", body, headers);
				return toResult(response);
			}
			case "executeResponder": {
				String observableId = context.getParameter("observableId", "");
				String responderId = context.getParameter("observableResponderId", "");
				Map<String, Object> body = Map.of("responderId", responderId, "objectId", observableId, "objectType", "case_artifact");
				HttpResponse<String> response = post(baseUrl + "/api/connector/cortex/action", body, headers);
				return toResult(response);
			}
			case "search": {
				String queryJson = context.getParameter("observableSearchQuery", "{}");
				Map<String, Object> query = parseJson(queryJson);
				Map<String, Object> body = Map.of("query", query);
				HttpResponse<String> response = post(baseUrl + "/api/case/artifact/_search", body, headers);
				return toListResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown observable operation: " + operation);
		}
	}

	// ========================= Task Execute =========================

	private NodeExecutionResult executeTask(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String caseId = context.getParameter("taskCaseId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("taskTitle", ""));
				body.put("status", context.getParameter("taskStatus", "Waiting"));

				HttpResponse<String> response = post(baseUrl + "/api/case/" + encode(caseId) + "/task", body, headers);
				return toResult(response);
			}
			case "get": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(baseUrl + "/api/case/task/" + encode(taskId), headers);
				return toResult(response);
			}
			case "getAll": {
				String caseId = context.getParameter("taskCaseId", "");
				HttpResponse<String> response = get(baseUrl + "/api/case/" + encode(caseId) + "/task", headers);
				return toListResult(response);
			}
			case "update": {
				String taskId = context.getParameter("taskId", "");
				Map<String, Object> updateFields = context.getParameter("taskUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "title", updateFields.get("title"));
				putIfPresent(body, "status", updateFields.get("status"));
				putIfPresent(body, "owner", updateFields.get("owner"));
				putIfPresent(body, "description", updateFields.get("description"));

				HttpResponse<String> response = patch(baseUrl + "/api/case/task/" + encode(taskId), body, headers);
				return toResult(response);
			}
			case "executeResponder": {
				String taskId = context.getParameter("taskId", "");
				String responderId = context.getParameter("taskResponderId", "");
				Map<String, Object> body = Map.of("responderId", responderId, "objectId", taskId, "objectType", "case_task");
				HttpResponse<String> response = post(baseUrl + "/api/connector/cortex/action", body, headers);
				return toResult(response);
			}
			case "search": {
				String queryJson = context.getParameter("taskSearchQuery", "{}");
				Map<String, Object> query = parseJson(queryJson);
				Map<String, Object> body = Map.of("query", query);
				HttpResponse<String> response = post(baseUrl + "/api/case/task/_search", body, headers);
				return toListResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown task operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("url", "http://localhost:9000"));
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + credentials.getOrDefault("apiKey", ""));
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
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
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
		return NodeExecutionResult.error("TheHive API error (HTTP " + response.statusCode() + "): " + body);
	}
}
