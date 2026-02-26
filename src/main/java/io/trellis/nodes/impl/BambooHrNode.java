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
 * BambooHR Node -- manage employees and time off via the BambooHR REST API.
 */
@Slf4j
@Node(
	type = "bambooHr",
	displayName = "BambooHR",
	description = "Manage employees and time-off requests in BambooHR",
	category = "HR",
	icon = "bambooHr",
	credentials = {"bambooHrApi"}
)
public class BambooHrNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("employee")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Employee").value("employee").description("Manage employees").build(),
				ParameterOption.builder().name("Time Off").value("timeOff").description("Manage time-off requests").build()
			)).build());

		// Employee operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("employee"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an employee").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an employee").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all employees").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an employee").build()
			)).build());

		// Time Off operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeOff"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get time-off requests").build()
			)).build());

		// Employee parameters
		addEmployeeParameters(params);

		// Time Off parameters
		addTimeOffParameters(params);

		return params;
	}

	// ========================= Employee Parameters =========================

	private void addEmployeeParameters(List<NodeParameter> params) {
		// Employee > Create
		params.add(NodeParameter.builder()
			.name("employeeFirstName").displayName("First Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("employee"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("employeeLastName").displayName("Last Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("employee"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("employeeAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("employee"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("workEmail").displayName("Work Email").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("department").displayName("Department").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("division").displayName("Division").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("location").displayName("Location").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("hireDate").displayName("Hire Date").type(ParameterType.STRING).placeHolder("2024-01-15").build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Active").value("Active").build(),
						ParameterOption.builder().name("Inactive").value("Inactive").build()
					)).build(),
				NodeParameter.builder().name("mobilePhone").displayName("Mobile Phone").type(ParameterType.STRING).build()
			)).build());

		// Employee > Get / Update
		params.add(NodeParameter.builder()
			.name("employeeId").displayName("Employee ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("employee"), "operation", List.of("get", "update"))))
			.build());

		// Employee > Get: fields to return
		params.add(NodeParameter.builder()
			.name("employeeFields").displayName("Fields").type(ParameterType.STRING)
			.placeHolder("firstName,lastName,workEmail,department,jobTitle")
			.description("Comma-separated list of fields to return. Leave empty for default fields.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("employee"), "operation", List.of("get"))))
			.build());

		// Employee > Update
		params.add(NodeParameter.builder()
			.name("employeeUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("employee"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("firstName").displayName("First Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("lastName").displayName("Last Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("workEmail").displayName("Work Email").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("department").displayName("Department").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("division").displayName("Division").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("jobTitle").displayName("Job Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("location").displayName("Location").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("mobilePhone").displayName("Mobile Phone").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Active").value("Active").build(),
						ParameterOption.builder().name("Inactive").value("Inactive").build()
					)).build()
			)).build());
	}

	// ========================= Time Off Parameters =========================

	private void addTimeOffParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("timeOffStart").displayName("Start Date").type(ParameterType.STRING).required(true)
			.placeHolder("2024-01-01")
			.description("Start date for querying time-off requests (YYYY-MM-DD).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeOff"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeOffEnd").displayName("End Date").type(ParameterType.STRING).required(true)
			.placeHolder("2024-12-31")
			.description("End date for querying time-off requests (YYYY-MM-DD).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeOff"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeOffStatus").displayName("Status").type(ParameterType.OPTIONS)
			.defaultValue("approved")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeOff"), "operation", List.of("getAll"))))
			.options(List.of(
				ParameterOption.builder().name("Approved").value("approved").build(),
				ParameterOption.builder().name("Denied").value("denied").build(),
				ParameterOption.builder().name("Superceded").value("superceded").build(),
				ParameterOption.builder().name("Requested").value("requested").build(),
				ParameterOption.builder().name("Canceled").value("canceled").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("timeOffEmployeeId").displayName("Employee ID").type(ParameterType.STRING)
			.description("Filter time-off requests by employee. Leave empty for all employees.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeOff"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "employee");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getApiBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "employee" -> executeEmployee(context, baseUrl, headers);
				case "timeOff" -> executeTimeOff(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "BambooHR API error: " + e.getMessage(), e);
		}
	}

	// ========================= Employee Execute =========================

	private NodeExecutionResult executeEmployee(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String firstName = context.getParameter("employeeFirstName", "");
				String lastName = context.getParameter("employeeLastName", "");
				Map<String, Object> additional = context.getParameter("employeeAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("firstName", firstName);
				body.put("lastName", lastName);
				putIfPresent(body, "workEmail", additional.get("workEmail"));
				putIfPresent(body, "department", additional.get("department"));
				putIfPresent(body, "division", additional.get("division"));
				putIfPresent(body, "jobTitle", additional.get("jobTitle"));
				putIfPresent(body, "location", additional.get("location"));
				putIfPresent(body, "hireDate", additional.get("hireDate"));
				putIfPresent(body, "status", additional.get("status"));
				putIfPresent(body, "mobilePhone", additional.get("mobilePhone"));

				HttpResponse<String> response = post(baseUrl + "/employees/", body, headers);
				// BambooHR returns the new employee ID in the Location header
				if (response.statusCode() == 201) {
					String location = response.headers().firstValue("Location").orElse("");
					String employeeId = location.replaceAll(".*/(\\d+)$", "$1");
					return NodeExecutionResult.success(List.of(wrapInJson(
						Map.of("success", true, "id", employeeId, "statusCode", response.statusCode()))));
				}
				return toResult(response);
			}
			case "get": {
				String employeeId = context.getParameter("employeeId", "");
				String fields = context.getParameter("employeeFields", "");
				String fieldsParam = fields.isBlank() ? "firstName,lastName,workEmail,department,jobTitle" : fields;
				String url = buildUrl(baseUrl + "/employees/" + encode(employeeId),
					Map.of("fields", fieldsParam));
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				// BambooHR uses a report API to get all employees
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", "Employee Report");
				body.put("fields", List.of(
					Map.of("id", "firstName"),
					Map.of("id", "lastName"),
					Map.of("id", "workEmail"),
					Map.of("id", "department"),
					Map.of("id", "jobTitle"),
					Map.of("id", "status")
				));

				HttpResponse<String> response = post(baseUrl + "/reports/custom?format=JSON", body, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object employees = parsed.getOrDefault("employees", List.of());
				if (employees instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object emp : (List<?>) employees) {
						if (emp instanceof Map) {
							items.add(wrapInJson(emp));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "update": {
				String employeeId = context.getParameter("employeeId", "");
				Map<String, Object> updateFields = context.getParameter("employeeUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "firstName", updateFields.get("firstName"));
				putIfPresent(body, "lastName", updateFields.get("lastName"));
				putIfPresent(body, "workEmail", updateFields.get("workEmail"));
				putIfPresent(body, "department", updateFields.get("department"));
				putIfPresent(body, "division", updateFields.get("division"));
				putIfPresent(body, "jobTitle", updateFields.get("jobTitle"));
				putIfPresent(body, "location", updateFields.get("location"));
				putIfPresent(body, "mobilePhone", updateFields.get("mobilePhone"));
				putIfPresent(body, "status", updateFields.get("status"));

				HttpResponse<String> response = post(baseUrl + "/employees/" + encode(employeeId), body, headers);
				if (response.statusCode() == 200 || response.statusCode() == 204) {
					return NodeExecutionResult.success(List.of(wrapInJson(
						Map.of("success", true, "id", employeeId))));
				}
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown employee operation: " + operation);
		}
	}

	// ========================= Time Off Execute =========================

	private NodeExecutionResult executeTimeOff(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String start = context.getParameter("timeOffStart", "");
		String end = context.getParameter("timeOffEnd", "");
		String status = context.getParameter("timeOffStatus", "approved");
		String employeeId = context.getParameter("timeOffEmployeeId", "");

		Map<String, Object> queryParams = new LinkedHashMap<>();
		queryParams.put("start", start);
		queryParams.put("end", end);
		queryParams.put("status", status);
		if (!employeeId.isEmpty()) {
			queryParams.put("employeeId", employeeId);
		}

		String url = buildUrl(baseUrl + "/time_off/requests/", queryParams);
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError(response);
		}

		List<Map<String, Object>> items = parseArrayResponse(response);
		if (items.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : items) {
			results.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(results);
	}

	// ========================= Helpers =========================

	private String getApiBaseUrl(Map<String, Object> credentials) {
		String companyDomain = String.valueOf(credentials.getOrDefault("companyDomain", ""));
		return "https://api.bamboohr.com/api/gateway.php/" + encode(companyDomain) + "/v1";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		String auth = Base64.getEncoder().encodeToString((apiKey + ":x").getBytes());
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Basic " + auth);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
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
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("BambooHR API error (HTTP " + response.statusCode() + "): " + body);
	}
}
