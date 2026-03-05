package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * HighLevel — manage data in HighLevel CRM.
 */
@Node(
		type = "highLevel",
		displayName = "HighLevel",
		description = "Manage data in HighLevel CRM",
		category = "CRM",
		icon = "highLevel",
		credentials = {"highLevelOAuth2Api"}
)
public class HighLevelNode extends AbstractApiNode {

	private static final String BASE_URL = "https://services.leadconnectorhq.com";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
				.options(List.of(
						ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build(),
						ParameterOption.builder().name("Opportunity").value("opportunity").description("Manage opportunities").build(),
						ParameterOption.builder().name("Task").value("task").description("Manage tasks").build()
				)).build());

		// Contact operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Opportunity operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("opportunity"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Task operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Update").value("update").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Delete").value("delete").build()
				)).build());

		// Common parameters
		params.add(NodeParameter.builder()
				.name("resourceId").displayName("ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the resource.").build());

		params.add(NodeParameter.builder()
				.name("contactId").displayName("Contact ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the contact.").build());

		params.add(NodeParameter.builder()
				.name("locationId").displayName("Location ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the location/sub-account.").build());

		params.add(NodeParameter.builder()
				.name("pipelineId").displayName("Pipeline ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the pipeline.").build());

		params.add(NodeParameter.builder()
				.name("pipelineStageId").displayName("Pipeline Stage ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the pipeline stage.").build());

		params.add(NodeParameter.builder()
				.name("firstName").displayName("First Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("First name.").build());

		params.add(NodeParameter.builder()
				.name("lastName").displayName("Last Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Last name.").build());

		params.add(NodeParameter.builder()
				.name("email").displayName("Email")
				.type(ParameterType.STRING).defaultValue("")
				.description("Email address.").build());

		params.add(NodeParameter.builder()
				.name("phone").displayName("Phone")
				.type(ParameterType.STRING).defaultValue("")
				.description("Phone number.").build());

		params.add(NodeParameter.builder()
				.name("name").displayName("Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Name of the opportunity.").build());

		params.add(NodeParameter.builder()
				.name("title").displayName("Title")
				.type(ParameterType.STRING).defaultValue("")
				.description("Title of the task.").build());

		params.add(NodeParameter.builder()
				.name("description").displayName("Description")
				.type(ParameterType.STRING).defaultValue("")
				.description("Description or body.").build());

		params.add(NodeParameter.builder()
				.name("dueDate").displayName("Due Date")
				.type(ParameterType.STRING).defaultValue("")
				.description("Due date in ISO 8601 format.").build());

		params.add(NodeParameter.builder()
				.name("monetaryValue").displayName("Monetary Value")
				.type(ParameterType.NUMBER).defaultValue(0)
				.description("Monetary value of the opportunity.").build());

		params.add(NodeParameter.builder()
				.name("status").displayName("Status")
				.type(ParameterType.OPTIONS).defaultValue("open")
				.options(List.of(
						ParameterOption.builder().name("Open").value("open").build(),
						ParameterOption.builder().name("Won").value("won").build(),
						ParameterOption.builder().name("Lost").value("lost").build(),
						ParameterOption.builder().name("Abandoned").value("abandoned").build()
				)).build());

		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields (JSON)")
				.type(ParameterType.JSON).defaultValue("{}")
				.description("Additional fields as JSON.").build());

		params.add(NodeParameter.builder()
				.name("returnAll").displayName("Return All")
				.type(ParameterType.BOOLEAN).defaultValue(false)
				.description("Whether to return all results.").build());

		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Max number of results to return.").build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Version", "2021-07-28");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> executeContact(context, operation, headers);
					case "opportunity" -> executeOpportunity(context, operation, headers);
					case "task" -> executeTask(context, operation, headers);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> executeContact(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String email = context.getParameter("email", "");
				String phone = context.getParameter("phone", "");
				String locationId = context.getParameter("locationId", "");
				if (!firstName.isEmpty()) body.put("firstName", firstName);
				if (!lastName.isEmpty()) body.put("lastName", lastName);
				if (!email.isEmpty()) body.put("email", email);
				if (!phone.isEmpty()) body.put("phone", phone);
				if (!locationId.isEmpty()) body.put("locationId", locationId);

				HttpResponse<String> response = post(BASE_URL + "/contacts/", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String email = context.getParameter("email", "");
				String phone = context.getParameter("phone", "");
				if (!firstName.isEmpty()) body.put("firstName", firstName);
				if (!lastName.isEmpty()) body.put("lastName", lastName);
				if (!email.isEmpty()) body.put("email", email);
				if (!phone.isEmpty()) body.put("phone", phone);

				HttpResponse<String> response = put(BASE_URL + "/contacts/" + encode(id), body, headers);
				return parseResponse(response);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(id), headers);
				return parseResponse(response);
			}
			case "getAll": {
				String locationId = context.getParameter("locationId", "");
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/contacts/?locationId=" + encode(locationId) + "&limit=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(id), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("id", id);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown contact operation: " + operation);
		}
	}

	private Map<String, Object> executeOpportunity(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String name = context.getParameter("name", "");
				String pipelineId = context.getParameter("pipelineId", "");
				String pipelineStageId = context.getParameter("pipelineStageId", "");
				String contactId = context.getParameter("contactId", "");
				String locationId = context.getParameter("locationId", "");
				String status = context.getParameter("status", "open");
				double monetaryValue = toDouble(context.getParameters().get("monetaryValue"), 0);
				if (!name.isEmpty()) body.put("name", name);
				if (!pipelineId.isEmpty()) body.put("pipelineId", pipelineId);
				if (!pipelineStageId.isEmpty()) body.put("pipelineStageId", pipelineStageId);
				if (!contactId.isEmpty()) body.put("contactId", contactId);
				if (!locationId.isEmpty()) body.put("locationId", locationId);
				body.put("status", status);
				if (monetaryValue > 0) body.put("monetaryValue", monetaryValue);

				HttpResponse<String> response = post(BASE_URL + "/opportunities/", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String name = context.getParameter("name", "");
				String status = context.getParameter("status", "");
				double monetaryValue = toDouble(context.getParameters().get("monetaryValue"), 0);
				if (!name.isEmpty()) body.put("name", name);
				if (!status.isEmpty()) body.put("status", status);
				if (monetaryValue > 0) body.put("monetaryValue", monetaryValue);

				HttpResponse<String> response = put(BASE_URL + "/opportunities/" + encode(id), body, headers);
				return parseResponse(response);
			}
			case "get": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/opportunities/" + encode(id), headers);
				return parseResponse(response);
			}
			case "getAll": {
				String locationId = context.getParameter("locationId", "");
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/opportunities/search?location_id=" + encode(locationId) + "&limit=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "delete": {
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/opportunities/" + encode(id), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("id", id);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown opportunity operation: " + operation);
		}
	}

	private Map<String, Object> executeTask(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String contactId = context.getParameter("contactId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String title = context.getParameter("title", "");
				String description = context.getParameter("description", "");
				String dueDate = context.getParameter("dueDate", "");
				if (!title.isEmpty()) body.put("title", title);
				if (!description.isEmpty()) body.put("body", description);
				if (!dueDate.isEmpty()) body.put("dueDate", dueDate);

				HttpResponse<String> response = post(BASE_URL + "/contacts/" + encode(contactId) + "/tasks", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String contactId = context.getParameter("contactId", "");
				String id = context.getParameter("resourceId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String title = context.getParameter("title", "");
				String description = context.getParameter("description", "");
				String dueDate = context.getParameter("dueDate", "");
				if (!title.isEmpty()) body.put("title", title);
				if (!description.isEmpty()) body.put("body", description);
				if (!dueDate.isEmpty()) body.put("dueDate", dueDate);

				HttpResponse<String> response = put(BASE_URL + "/contacts/" + encode(contactId) + "/tasks/" + encode(id), body, headers);
				return parseResponse(response);
			}
			case "get": {
				String contactId = context.getParameter("contactId", "");
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(contactId) + "/tasks/" + encode(id), headers);
				return parseResponse(response);
			}
			case "getAll": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(contactId) + "/tasks", headers);
				return parseResponse(response);
			}
			case "delete": {
				String contactId = context.getParameter("contactId", "");
				String id = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(contactId) + "/tasks/" + encode(id), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("id", id);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown task operation: " + operation);
		}
	}
}
