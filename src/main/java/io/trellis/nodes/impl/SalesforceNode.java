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
 * Salesforce Node -- manage accounts, contacts, leads, opportunities, cases,
 * tasks, documents, flows, and SOQL search in Salesforce CRM.
 */
@Slf4j
@Node(
	type = "salesforce",
	displayName = "Salesforce",
	description = "Manage accounts, contacts, leads, opportunities, cases, tasks, documents, flows, and search in Salesforce CRM",
	category = "CRM & Sales",
	icon = "salesforce",
	credentials = {"salesforceOAuth2Api"}
)
public class SalesforceNode extends AbstractApiNode {

	private static final String API_VERSION = "/services/data/v58.0";

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
				ParameterOption.builder().name("Account").value("account").description("Manage accounts").build(),
				ParameterOption.builder().name("Case").value("case").description("Manage cases").build(),
				ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build(),
				ParameterOption.builder().name("Lead").value("lead").description("Manage leads").build(),
				ParameterOption.builder().name("Opportunity").value("opportunity").description("Manage opportunities").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build(),
				ParameterOption.builder().name("Document").value("document").description("Upload documents").build(),
				ParameterOption.builder().name("Flow").value("flow").description("Invoke flows").build(),
				ParameterOption.builder().name("Search").value("search").description("Search using SOQL").build()
			)).build());

		// Account operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an account").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an account").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an account").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many accounts").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an account").build(),
				ParameterOption.builder().name("Upsert").value("upsert").description("Upsert an account by external ID").build()
			)).build());

		// Case operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("case"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a case").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a case").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a case").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many cases").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a case").build(),
				ParameterOption.builder().name("Add Comment").value("addComment").description("Add a comment to a case").build(),
				ParameterOption.builder().name("Get Comments").value("getComments").description("Get comments on a case").build()
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
				ParameterOption.builder().name("Update").value("update").description("Update a contact").build(),
				ParameterOption.builder().name("Upsert").value("upsert").description("Upsert a contact by external ID").build()
			)).build());

		// Lead operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a lead").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a lead").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a lead").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many leads").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a lead").build(),
				ParameterOption.builder().name("Upsert").value("upsert").description("Upsert a lead by external ID").build(),
				ParameterOption.builder().name("Convert").value("convert").description("Convert a lead to account/contact/opportunity").build()
			)).build());

		// Opportunity operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an opportunity").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an opportunity").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an opportunity").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many opportunities").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an opportunity").build(),
				ParameterOption.builder().name("Add Note").value("addNote").description("Add a note to an opportunity").build(),
				ParameterOption.builder().name("Get Notes").value("getNotes").description("Get notes on an opportunity").build()
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

		// Document operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("upload")
			.displayOptions(Map.of("show", Map.of("resource", List.of("document"))))
			.options(List.of(
				ParameterOption.builder().name("Upload").value("upload").description("Upload a document").build()
			)).build());

		// Flow operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("invoke")
			.displayOptions(Map.of("show", Map.of("resource", List.of("flow"))))
			.options(List.of(
				ParameterOption.builder().name("Invoke").value("invoke").description("Invoke a flow").build()
			)).build());

		// Search operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("query")
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"))))
			.options(List.of(
				ParameterOption.builder().name("SOQL Query").value("query").description("Execute a SOQL query").build()
			)).build());

		// ========================= Common Parameters =========================

		// Record ID
		params.add(NodeParameter.builder()
			.name("recordId").displayName("Record ID")
			.type(ParameterType.STRING).required(true)
			.description("The Salesforce record ID.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("get", "update", "delete", "addComment", "getComments", "addNote", "getNotes", "convert"))))
			.build());

		// External ID field (for upsert)
		params.add(NodeParameter.builder()
			.name("externalIdField").displayName("External ID Field")
			.type(ParameterType.STRING).required(true).defaultValue("Id")
			.description("The external ID field name for upsert operations.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("upsert"))))
			.build());

		// External ID value (for upsert)
		params.add(NodeParameter.builder()
			.name("externalIdValue").displayName("External ID Value")
			.type(ParameterType.STRING).required(true)
			.description("The external ID value for upsert operations.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("upsert"))))
			.build());

		// Account fields
		params.add(NodeParameter.builder()
			.name("accountName").displayName("Account Name")
			.type(ParameterType.STRING)
			.description("Name of the account.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"), "operation", List.of("create", "update", "upsert"))))
			.build());

		// Contact fields
		params.add(NodeParameter.builder()
			.name("firstName").displayName("First Name")
			.type(ParameterType.STRING)
			.description("First name of the contact or lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact", "lead"), "operation", List.of("create", "update", "upsert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("lastName").displayName("Last Name")
			.type(ParameterType.STRING)
			.description("Last name of the contact or lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact", "lead"), "operation", List.of("create", "update", "upsert"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email address.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("contact", "lead"), "operation", List.of("create", "update", "upsert"))))
			.build());

		// Lead fields
		params.add(NodeParameter.builder()
			.name("company").displayName("Company")
			.type(ParameterType.STRING)
			.description("Company name for the lead.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("create", "update", "upsert"))))
			.build());

		// Lead convert fields
		params.add(NodeParameter.builder()
			.name("convertedStatus").displayName("Converted Status")
			.type(ParameterType.STRING).required(true)
			.description("The status to set on the converted lead (e.g. 'Closed - Converted').")
			.displayOptions(Map.of("show", Map.of("resource", List.of("lead"), "operation", List.of("convert"))))
			.build());

		// Opportunity fields
		params.add(NodeParameter.builder()
			.name("opportunityName").displayName("Opportunity Name")
			.type(ParameterType.STRING)
			.description("Name of the opportunity.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("stageName").displayName("Stage")
			.type(ParameterType.STRING)
			.description("Stage of the opportunity.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("closeDate").displayName("Close Date")
			.type(ParameterType.STRING)
			.description("Close date (YYYY-MM-DD format).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"), "operation", List.of("create", "update"))))
			.build());

		// Case fields
		params.add(NodeParameter.builder()
			.name("subject").displayName("Subject")
			.type(ParameterType.STRING)
			.description("Subject of the case.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("case"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("status").displayName("Status")
			.type(ParameterType.STRING)
			.description("Status of the case.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("case"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("origin").displayName("Origin")
			.type(ParameterType.STRING)
			.description("Origin of the case (e.g. Phone, Web, Email).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("case"), "operation", List.of("create", "update"))))
			.build());

		// Comment body (for case addComment)
		params.add(NodeParameter.builder()
			.name("commentBody").displayName("Comment Body")
			.type(ParameterType.STRING).required(true)
			.description("The body text of the comment.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("operation", List.of("addComment"))))
			.build());

		params.add(NodeParameter.builder()
			.name("isPublished").displayName("Is Published")
			.type(ParameterType.BOOLEAN).defaultValue(true)
			.description("Whether the comment is visible to the customer.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("addComment"))))
			.build());

		// Note fields (for opportunity addNote)
		params.add(NodeParameter.builder()
			.name("noteTitle").displayName("Note Title")
			.type(ParameterType.STRING).required(true)
			.description("Title of the note.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("addNote"))))
			.build());

		params.add(NodeParameter.builder()
			.name("noteBody").displayName("Note Body")
			.type(ParameterType.STRING)
			.description("Body text of the note.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("operation", List.of("addNote"))))
			.build());

		// Task fields
		params.add(NodeParameter.builder()
			.name("taskSubject").displayName("Subject")
			.type(ParameterType.STRING)
			.description("Subject of the task.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskStatus").displayName("Status")
			.type(ParameterType.STRING)
			.description("Status of the task.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskPriority").displayName("Priority")
			.type(ParameterType.OPTIONS)
			.description("Priority of the task.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("create", "update"))))
			.options(List.of(
				ParameterOption.builder().name("High").value("High").build(),
				ParameterOption.builder().name("Normal").value("Normal").build(),
				ParameterOption.builder().name("Low").value("Low").build()
			)).build());

		// Document fields
		params.add(NodeParameter.builder()
			.name("documentName").displayName("Document Name")
			.type(ParameterType.STRING).required(true)
			.description("Name of the document.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("document"), "operation", List.of("upload"))))
			.build());

		params.add(NodeParameter.builder()
			.name("documentContent").displayName("Document Content (Base64)")
			.type(ParameterType.STRING).required(true)
			.description("Base64-encoded document content.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("document"), "operation", List.of("upload"))))
			.build());

		params.add(NodeParameter.builder()
			.name("documentFolderId").displayName("Folder ID")
			.type(ParameterType.STRING)
			.description("The ID of the folder to upload the document to.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("document"), "operation", List.of("upload"))))
			.build());

		// Flow fields
		params.add(NodeParameter.builder()
			.name("flowApiName").displayName("Flow API Name")
			.type(ParameterType.STRING).required(true)
			.description("The API name of the flow to invoke.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("flow"), "operation", List.of("invoke"))))
			.build());

		params.add(NodeParameter.builder()
			.name("flowInputs").displayName("Flow Inputs (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Input variables for the flow as JSON.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("flow"), "operation", List.of("invoke"))))
			.build());

		// SOQL Query
		params.add(NodeParameter.builder()
			.name("soqlQuery").displayName("SOQL Query")
			.type(ParameterType.STRING).required(true)
			.description("SOQL query string (e.g. SELECT Id, Name FROM Account LIMIT 10).")
			.typeOptions(Map.of("rows", 3))
			.displayOptions(Map.of("show", Map.of("resource", List.of("search"), "operation", List.of("query"))))
			.build());

		// Additional fields
		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update", "upsert"))))
			.build());

		// Limit
		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to return all results.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(100)
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
			String instanceUrl = String.valueOf(credentials.getOrDefault("instanceUrl", ""));
			if (instanceUrl.isEmpty()) {
				return NodeExecutionResult.error("Salesforce instance URL is required in credentials.");
			}
			// Remove trailing slash
			if (instanceUrl.endsWith("/")) {
				instanceUrl = instanceUrl.substring(0, instanceUrl.length() - 1);
			}
			String baseUrl = instanceUrl + API_VERSION;
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "account" -> executeAccount(context, operation, baseUrl, headers);
				case "case" -> executeCase(context, operation, baseUrl, headers);
				case "contact" -> executeContact(context, operation, baseUrl, headers);
				case "lead" -> executeLead(context, operation, baseUrl, headers);
				case "opportunity" -> executeOpportunity(context, operation, baseUrl, headers);
				case "task" -> executeTask(context, operation, baseUrl, headers);
				case "document" -> executeDocument(context, operation, baseUrl, headers);
				case "flow" -> executeFlow(context, operation, baseUrl, headers);
				case "search" -> executeSearch(context, operation, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Salesforce API error: " + e.getMessage(), e);
		}
	}

	// ========================= Account Operations =========================

	private NodeExecutionResult executeAccount(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> body = buildSObjectBody(context, Map.of("Name", context.getParameter("accountName", "")));
				HttpResponse<String> response = post(baseUrl + "/sobjects/Account", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/sobjects/Account/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/sobjects/Account/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				String soql = "SELECT FIELDS(STANDARD) FROM Account LIMIT " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			case "update": {
				String id = context.getParameter("recordId", "");
				Map<String, Object> body = buildSObjectBody(context, Map.of("Name", context.getParameter("accountName", "")));
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Account/" + encode(id), body, headers);
				return toResult(response);
			}
			case "upsert": {
				String extField = context.getParameter("externalIdField", "Id");
				String extValue = context.getParameter("externalIdValue", "");
				Map<String, Object> body = buildSObjectBody(context, Map.of("Name", context.getParameter("accountName", "")));
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Account/" + encode(extField) + "/" + encode(extValue), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown account operation: " + operation);
		}
	}

	// ========================= Case Operations =========================

	private NodeExecutionResult executeCase(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> fields = new LinkedHashMap<>();
				putIfNotEmpty(fields, "Subject", context.getParameter("subject", ""));
				putIfNotEmpty(fields, "Status", context.getParameter("status", ""));
				putIfNotEmpty(fields, "Origin", context.getParameter("origin", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = post(baseUrl + "/sobjects/Case", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/sobjects/Case/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/sobjects/Case/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				String soql = "SELECT FIELDS(STANDARD) FROM Case LIMIT " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			case "update": {
				String id = context.getParameter("recordId", "");
				Map<String, Object> fields = new LinkedHashMap<>();
				putIfNotEmpty(fields, "Subject", context.getParameter("subject", ""));
				putIfNotEmpty(fields, "Status", context.getParameter("status", ""));
				putIfNotEmpty(fields, "Origin", context.getParameter("origin", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Case/" + encode(id), body, headers);
				return toResult(response);
			}
			case "addComment": {
				String caseId = context.getParameter("recordId", "");
				String commentBody = context.getParameter("commentBody", "");
				boolean isPublished = toBoolean(context.getParameters().get("isPublished"), true);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("ParentId", caseId);
				body.put("CommentBody", commentBody);
				body.put("IsPublished", isPublished);
				HttpResponse<String> response = post(baseUrl + "/sobjects/CaseComment", body, headers);
				return toResult(response);
			}
			case "getComments": {
				String caseId = context.getParameter("recordId", "");
				String soql = "SELECT Id, CommentBody, IsPublished, CreatedDate, CreatedById FROM CaseComment WHERE ParentId = '" + caseId + "'";
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			default:
				return NodeExecutionResult.error("Unknown case operation: " + operation);
		}
	}

	// ========================= Contact Operations =========================

	private NodeExecutionResult executeContact(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> fields = buildPersonFields(context);
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = post(baseUrl + "/sobjects/Contact", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/sobjects/Contact/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/sobjects/Contact/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				String soql = "SELECT FIELDS(STANDARD) FROM Contact LIMIT " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			case "update": {
				String id = context.getParameter("recordId", "");
				Map<String, Object> fields = buildPersonFields(context);
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Contact/" + encode(id), body, headers);
				return toResult(response);
			}
			case "upsert": {
				String extField = context.getParameter("externalIdField", "Id");
				String extValue = context.getParameter("externalIdValue", "");
				Map<String, Object> fields = buildPersonFields(context);
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Contact/" + encode(extField) + "/" + encode(extValue), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown contact operation: " + operation);
		}
	}

	// ========================= Lead Operations =========================

	private NodeExecutionResult executeLead(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> fields = buildPersonFields(context);
				putIfNotEmpty(fields, "Company", context.getParameter("company", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = post(baseUrl + "/sobjects/Lead", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/sobjects/Lead/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/sobjects/Lead/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				String soql = "SELECT FIELDS(STANDARD) FROM Lead LIMIT " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			case "update": {
				String id = context.getParameter("recordId", "");
				Map<String, Object> fields = buildPersonFields(context);
				putIfNotEmpty(fields, "Company", context.getParameter("company", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Lead/" + encode(id), body, headers);
				return toResult(response);
			}
			case "upsert": {
				String extField = context.getParameter("externalIdField", "Id");
				String extValue = context.getParameter("externalIdValue", "");
				Map<String, Object> fields = buildPersonFields(context);
				putIfNotEmpty(fields, "Company", context.getParameter("company", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Lead/" + encode(extField) + "/" + encode(extValue), body, headers);
				return toResult(response);
			}
			case "convert": {
				String leadId = context.getParameter("recordId", "");
				String convertedStatus = context.getParameter("convertedStatus", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("leadId", leadId);
				body.put("convertedStatus", convertedStatus);
				// Use composite API for lead conversion
				Map<String, Object> compositeBody = new LinkedHashMap<>();
				compositeBody.put("allOrNone", true);
				List<Map<String, Object>> compositeRequests = new ArrayList<>();
				Map<String, Object> request = new LinkedHashMap<>();
				request.put("method", "POST");
				request.put("url", API_VERSION + "/sobjects/Lead/" + leadId + "/convert");
				request.put("referenceId", "leadConvert");
				request.put("body", body);
				compositeRequests.add(request);
				compositeBody.put("compositeRequest", compositeRequests);
				HttpResponse<String> response = post(baseUrl + "/composite", compositeBody, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown lead operation: " + operation);
		}
	}

	// ========================= Opportunity Operations =========================

	private NodeExecutionResult executeOpportunity(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> fields = new LinkedHashMap<>();
				putIfNotEmpty(fields, "Name", context.getParameter("opportunityName", ""));
				putIfNotEmpty(fields, "StageName", context.getParameter("stageName", ""));
				putIfNotEmpty(fields, "CloseDate", context.getParameter("closeDate", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = post(baseUrl + "/sobjects/Opportunity", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/sobjects/Opportunity/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/sobjects/Opportunity/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				String soql = "SELECT FIELDS(STANDARD) FROM Opportunity LIMIT " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			case "update": {
				String id = context.getParameter("recordId", "");
				Map<String, Object> fields = new LinkedHashMap<>();
				putIfNotEmpty(fields, "Name", context.getParameter("opportunityName", ""));
				putIfNotEmpty(fields, "StageName", context.getParameter("stageName", ""));
				putIfNotEmpty(fields, "CloseDate", context.getParameter("closeDate", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Opportunity/" + encode(id), body, headers);
				return toResult(response);
			}
			case "addNote": {
				String oppId = context.getParameter("recordId", "");
				String noteTitle = context.getParameter("noteTitle", "");
				String noteBody = context.getParameter("noteBody", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("ParentId", oppId);
				body.put("Title", noteTitle);
				if (!noteBody.isEmpty()) {
					body.put("Body", noteBody);
				}
				HttpResponse<String> response = post(baseUrl + "/sobjects/Note", body, headers);
				return toResult(response);
			}
			case "getNotes": {
				String oppId = context.getParameter("recordId", "");
				String soql = "SELECT Id, Title, Body, CreatedDate, CreatedById FROM Note WHERE ParentId = '" + oppId + "'";
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			default:
				return NodeExecutionResult.error("Unknown opportunity operation: " + operation);
		}
	}

	// ========================= Task Operations =========================

	private NodeExecutionResult executeTask(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				Map<String, Object> fields = new LinkedHashMap<>();
				putIfNotEmpty(fields, "Subject", context.getParameter("taskSubject", ""));
				putIfNotEmpty(fields, "Status", context.getParameter("taskStatus", ""));
				putIfNotEmpty(fields, "Priority", context.getParameter("taskPriority", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = post(baseUrl + "/sobjects/Task", body, headers);
				return toResult(response);
			}
			case "delete": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/sobjects/Task/" + encode(id), headers);
				return toDeleteResult(response, id);
			}
			case "get": {
				String id = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/sobjects/Task/" + encode(id), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = getLimit(context);
				String soql = "SELECT FIELDS(STANDARD) FROM Task LIMIT " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
				return toListResult(response, "records");
			}
			case "update": {
				String id = context.getParameter("recordId", "");
				Map<String, Object> fields = new LinkedHashMap<>();
				putIfNotEmpty(fields, "Subject", context.getParameter("taskSubject", ""));
				putIfNotEmpty(fields, "Status", context.getParameter("taskStatus", ""));
				putIfNotEmpty(fields, "Priority", context.getParameter("taskPriority", ""));
				Map<String, Object> body = buildSObjectBody(context, fields);
				HttpResponse<String> response = patch(baseUrl + "/sobjects/Task/" + encode(id), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown task operation: " + operation);
		}
	}

	// ========================= Document Operations =========================

	private NodeExecutionResult executeDocument(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		if ("upload".equals(operation)) {
			String docName = context.getParameter("documentName", "");
			String docContent = context.getParameter("documentContent", "");
			String folderId = context.getParameter("documentFolderId", "");

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("Name", docName);
			body.put("Body", docContent);
			if (!folderId.isEmpty()) {
				body.put("FolderId", folderId);
			}

			HttpResponse<String> response = post(baseUrl + "/sobjects/Document", body, headers);
			return toResult(response);
		}
		return NodeExecutionResult.error("Unknown document operation: " + operation);
	}

	// ========================= Flow Operations =========================

	private NodeExecutionResult executeFlow(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		if ("invoke".equals(operation)) {
			String flowApiName = context.getParameter("flowApiName", "");
			String flowInputsJson = context.getParameter("flowInputs", "{}");
			Map<String, Object> inputs = parseJson(flowInputsJson);

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("inputs", List.of(inputs));

			HttpResponse<String> response = post(baseUrl + "/actions/custom/flow/" + encode(flowApiName), body, headers);
			return toResult(response);
		}
		return NodeExecutionResult.error("Unknown flow operation: " + operation);
	}

	// ========================= Search Operations =========================

	private NodeExecutionResult executeSearch(NodeExecutionContext context, String operation, String baseUrl, Map<String, String> headers) throws Exception {
		if ("query".equals(operation)) {
			String soql = context.getParameter("soqlQuery", "");
			if (soql.isBlank()) {
				return NodeExecutionResult.error("SOQL query is required.");
			}
			HttpResponse<String> response = get(baseUrl + "/query?q=" + encode(soql), headers);
			return toListResult(response, "records");
		}
		return NodeExecutionResult.error("Unknown search operation: " + operation);
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		String token = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private int getLimit(NodeExecutionContext context) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		return returnAll ? 2000 : Math.min(limit, 2000);
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private Map<String, Object> buildPersonFields(NodeExecutionContext context) {
		Map<String, Object> fields = new LinkedHashMap<>();
		putIfNotEmpty(fields, "FirstName", context.getParameter("firstName", ""));
		putIfNotEmpty(fields, "LastName", context.getParameter("lastName", ""));
		putIfNotEmpty(fields, "Email", context.getParameter("email", ""));
		return fields;
	}

	private Map<String, Object> buildSObjectBody(NodeExecutionContext context, Map<String, Object> specificFields) {
		String additionalJson = context.getParameter("additionalFields", "{}");
		Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
		for (Map.Entry<String, Object> entry : specificFields.entrySet()) {
			if (entry.getValue() != null && !String.valueOf(entry.getValue()).isEmpty()) {
				body.put(entry.getKey(), entry.getValue());
			}
		}
		return body;
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
		return NodeExecutionResult.error("Salesforce API error (HTTP " + response.statusCode() + "): " + body);
	}
}
