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
 * TheHive 5 Node -- manage alerts, cases, comments, observables, pages,
 * queries, and tasks via TheHive v5 REST API.
 */
@Slf4j
@Node(
	type = "theHiveProject",
	displayName = "TheHive 5",
	description = "Manage alerts, cases, comments, observables, pages, queries, and tasks in TheHive 5",
	category = "Miscellaneous",
	icon = "theHive",
	credentials = {"theHiveProjectApi"}
)
public class TheHiveProjectNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("alert")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Alert").value("alert").description("Manage alerts").build(),
				ParameterOption.builder().name("Case").value("case_").description("Manage cases").build(),
				ParameterOption.builder().name("Comment").value("comment").description("Manage comments").build(),
				ParameterOption.builder().name("Observable").value("observable").description("Manage observables").build(),
				ParameterOption.builder().name("Page").value("page").description("Manage case pages").build(),
				ParameterOption.builder().name("Query").value("query").description("Run queries").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build()
			)).build());

		addOperationSelectors(params);
		addResourceParameters(params);

		return params;
	}

	private void addOperationSelectors(List<NodeParameter> params) {
		// Alert operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an alert").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an alert").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an alert").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many alerts").build(),
				ParameterOption.builder().name("Merge Into Case").value("mergeIntoCase").description("Merge alert into case").build(),
				ParameterOption.builder().name("Promote").value("promote").description("Promote alert to case").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an alert").build()
			)).build());

		// Case operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a case").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a case").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a case").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many cases").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a case").build()
			)).build());

		// Comment operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a comment").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a comment").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many comments").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a comment").build()
			)).build());

		// Observable operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an observable").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an observable").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an observable").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many observables").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an observable").build()
			)).build());

		// Page operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a page").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a page").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a page").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many pages").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a page").build()
			)).build());

		// Query operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("run")
			.displayOptions(Map.of("show", Map.of("resource", List.of("query"))))
			.options(List.of(
				ParameterOption.builder().name("Run").value("run").description("Execute a query").build()
			)).build());

		// Task operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a task").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a task").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a task").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many tasks").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a task").build()
			)).build());
	}

	private void addResourceParameters(List<NodeParameter> params) {
		// Alert parameters
		params.add(NodeParameter.builder()
			.name("alertTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertDescription").displayName("Description").type(ParameterType.STRING)
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
		params.add(NodeParameter.builder()
			.name("alertId").displayName("Alert ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("get", "delete", "update", "promote", "mergeIntoCase"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertMergeCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("mergeIntoCase"))))
			.build());
		params.add(NodeParameter.builder()
			.name("alertUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("severity").displayName("Severity").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.STRING).build()
			)).build());
		params.add(NodeParameter.builder()
			.name("alertLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("alert"), "operation", List.of("getAll"))))
			.build());

		// Case parameters
		params.add(NodeParameter.builder()
			.name("caseTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("caseDescription").displayName("Description").type(ParameterType.STRING)
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
		params.add(NodeParameter.builder()
			.name("caseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("caseUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("severity").displayName("Severity").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.STRING).build()
			)).build());
		params.add(NodeParameter.builder()
			.name("caseLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("case_"), "operation", List.of("getAll"))))
			.build());

		// Comment parameters
		params.add(NodeParameter.builder()
			.name("commentCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("commentMessage").displayName("Message").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("commentId").displayName("Comment ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"), "operation", List.of("delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("commentUpdateMessage").displayName("Message").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"), "operation", List.of("update"))))
			.build());

		// Observable parameters
		params.add(NodeParameter.builder()
			.name("observableCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("observableDataType").displayName("Data Type").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("observableData").displayName("Data").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("observableId").displayName("Observable ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("observableUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("observable"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("message").displayName("Message").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("tlp").displayName("TLP").type(ParameterType.NUMBER).build(),
				NodeParameter.builder().name("ioc").displayName("IOC").type(ParameterType.BOOLEAN).build()
			)).build());

		// Page parameters
		params.add(NodeParameter.builder()
			.name("pageCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("pageTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("pageContent").displayName("Content").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("pageId").displayName("Page ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("pageUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("page"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("content").displayName("Content").type(ParameterType.STRING).build()
			)).build());

		// Query parameters
		params.add(NodeParameter.builder()
			.name("queryBody").displayName("Query (JSON)").type(ParameterType.STRING).required(true)
			.description("TheHive v5 query in JSON format.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("query"), "operation", List.of("run"))))
			.build());

		// Task parameters
		params.add(NodeParameter.builder()
			.name("taskCaseId").displayName("Case ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskId").displayName("Task ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("taskUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("owner").displayName("Owner").type(ParameterType.STRING).build()
			)).build());
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
				case "comment" -> executeComment(context, baseUrl, headers);
				case "observable" -> executeObservable(context, baseUrl, headers);
				case "page" -> executePage(context, baseUrl, headers);
				case "query" -> executeQuery(context, baseUrl, headers);
				case "task" -> executeTask(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "TheHive 5 API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeAlert(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("alertTitle", ""));
				body.put("type", context.getParameter("alertType", ""));
				body.put("source", context.getParameter("alertSource", ""));
				body.put("sourceRef", context.getParameter("alertSourceRef", ""));
				body.put("severity", Integer.parseInt(context.getParameter("alertSeverity", "2")));
				String desc = context.getParameter("alertDescription", "");
				if (!desc.isEmpty()) body.put("description", desc);
				HttpResponse<String> response = post(baseUrl + "/api/v1/alert", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/api/v1/alert/" + encode(context.getParameter("alertId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				Map<String, Object> query = Map.of("query", List.of(Map.of("_name", "listAlert")), "_from", 0,
					"_size", toInt(context.getParameter("alertLimit", 25), 25));
				HttpResponse<String> response = post(baseUrl + "/api/v1/query", query, headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/api/v1/alert/" + encode(context.getParameter("alertId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("alertUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = patch(baseUrl + "/api/v1/alert/" + encode(context.getParameter("alertId", "")), body, headers);
				return toResult(response);
			}
			case "promote": {
				HttpResponse<String> response = post(baseUrl + "/api/v1/alert/" + encode(context.getParameter("alertId", "")) + "/case", Map.of(), headers);
				return toResult(response);
			}
			case "mergeIntoCase": {
				String caseId = context.getParameter("alertMergeCaseId", "");
				Map<String, Object> body = Map.of("caseId", caseId);
				HttpResponse<String> response = post(baseUrl + "/api/v1/alert/" + encode(context.getParameter("alertId", "")) + "/merge/" + encode(caseId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown alert operation: " + operation);
		}
	}

	private NodeExecutionResult executeCase(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("caseTitle", ""));
				body.put("severity", Integer.parseInt(context.getParameter("caseSeverity", "2")));
				String desc = context.getParameter("caseDescription", "");
				if (!desc.isEmpty()) body.put("description", desc);
				HttpResponse<String> response = post(baseUrl + "/api/v1/case", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/api/v1/case/" + encode(context.getParameter("caseId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				Map<String, Object> query = Map.of("query", List.of(Map.of("_name", "listCase")), "_from", 0,
					"_size", toInt(context.getParameter("caseLimit", 25), 25));
				HttpResponse<String> response = post(baseUrl + "/api/v1/query", query, headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/api/v1/case/" + encode(context.getParameter("caseId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("caseUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = patch(baseUrl + "/api/v1/case/" + encode(context.getParameter("caseId", "")), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown case operation: " + operation);
		}
	}

	private NodeExecutionResult executeComment(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				String caseId = context.getParameter("commentCaseId", "");
				Map<String, Object> body = Map.of("message", context.getParameter("commentMessage", ""));
				HttpResponse<String> response = post(baseUrl + "/api/v1/case/" + encode(caseId) + "/comment", body, headers);
				return toResult(response);
			}
			case "getAll": {
				String caseId = context.getParameter("commentCaseId", "");
				Map<String, Object> query = Map.of("query", List.of(
					Map.of("_name", "getCase", "idOrName", caseId),
					Map.of("_name", "comments")
				));
				HttpResponse<String> response = post(baseUrl + "/api/v1/query", query, headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/api/v1/comment/" + encode(context.getParameter("commentId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> body = Map.of("message", context.getParameter("commentUpdateMessage", ""));
				HttpResponse<String> response = patch(baseUrl + "/api/v1/comment/" + encode(context.getParameter("commentId", "")), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown comment operation: " + operation);
		}
	}

	private NodeExecutionResult executeObservable(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				String caseId = context.getParameter("observableCaseId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("dataType", context.getParameter("observableDataType", ""));
				body.put("data", context.getParameter("observableData", ""));
				HttpResponse<String> response = post(baseUrl + "/api/v1/case/" + encode(caseId) + "/observable", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/api/v1/observable/" + encode(context.getParameter("observableId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				String caseId = context.getParameter("observableCaseId", "");
				Map<String, Object> query = Map.of("query", List.of(
					Map.of("_name", "getCase", "idOrName", caseId),
					Map.of("_name", "observables")
				));
				HttpResponse<String> response = post(baseUrl + "/api/v1/query", query, headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/api/v1/observable/" + encode(context.getParameter("observableId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("observableUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = patch(baseUrl + "/api/v1/observable/" + encode(context.getParameter("observableId", "")), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown observable operation: " + operation);
		}
	}

	private NodeExecutionResult executePage(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				String caseId = context.getParameter("pageCaseId", "");
				Map<String, Object> body = Map.of("title", context.getParameter("pageTitle", ""),
					"content", context.getParameter("pageContent", ""));
				HttpResponse<String> response = post(baseUrl + "/api/v1/case/" + encode(caseId) + "/page", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/api/v1/page/" + encode(context.getParameter("pageId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				String caseId = context.getParameter("pageCaseId", "");
				Map<String, Object> query = Map.of("query", List.of(
					Map.of("_name", "getCase", "idOrName", caseId),
					Map.of("_name", "pages")
				));
				HttpResponse<String> response = post(baseUrl + "/api/v1/query", query, headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/api/v1/page/" + encode(context.getParameter("pageId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("pageUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = patch(baseUrl + "/api/v1/page/" + encode(context.getParameter("pageId", "")), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown page operation: " + operation);
		}
	}

	private NodeExecutionResult executeQuery(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String queryJson = context.getParameter("queryBody", "{}");
		Map<String, Object> body = parseJson(queryJson);
		HttpResponse<String> response = post(baseUrl + "/api/v1/query", body, headers);
		return toListResult(response);
	}

	private NodeExecutionResult executeTask(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				String caseId = context.getParameter("taskCaseId", "");
				Map<String, Object> body = Map.of("title", context.getParameter("taskTitle", ""));
				HttpResponse<String> response = post(baseUrl + "/api/v1/case/" + encode(caseId) + "/task", body, headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/api/v1/task/" + encode(context.getParameter("taskId", "")), headers);
				return toResult(response);
			}
			case "getAll": {
				String caseId = context.getParameter("taskCaseId", "");
				Map<String, Object> query = Map.of("query", List.of(
					Map.of("_name", "getCase", "idOrName", caseId),
					Map.of("_name", "tasks")
				));
				HttpResponse<String> response = post(baseUrl + "/api/v1/query", query, headers);
				return toListResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/api/v1/task/" + encode(context.getParameter("taskId", "")), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("taskUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = patch(baseUrl + "/api/v1/task/" + encode(context.getParameter("taskId", "")), body, headers);
				return toResult(response);
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
		return NodeExecutionResult.error("TheHive 5 API error (HTTP " + response.statusCode() + "): " + body);
	}
}
