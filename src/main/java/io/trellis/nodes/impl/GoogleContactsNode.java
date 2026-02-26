package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Google Contacts — manage contacts via the Google People API.
 */
@Node(
		type = "googleContacts",
		displayName = "Google Contacts",
		description = "Manage Google Contacts via the People API",
		category = "Google",
		icon = "googleContacts",
		credentials = {"googleContactsOAuth2Api"}
)
public class GoogleContactsNode extends AbstractApiNode {

	private static final String BASE_URL = "https://people.googleapis.com/v1";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
				.noDataExpression(true)
				.options(List.of(
						ParameterOption.builder().name("Contact").value("contact")
								.description("Manage contacts").build()
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
						ParameterOption.builder().name("Get All").value("getAll").description("Get all contacts").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a contact").build()
				)).build());

		// Contact > Create parameters
		params.add(NodeParameter.builder()
				.name("givenName").displayName("First Name")
				.type(ParameterType.STRING).required(true)
				.description("The first name of the contact.")
				.placeHolder("John")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("familyName").displayName("Last Name")
				.type(ParameterType.STRING)
				.description("The last name of the contact.")
				.placeHolder("Doe")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
				.build());

		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("create"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("email").displayName("Email")
								.type(ParameterType.STRING)
								.description("Email address of the contact.")
								.placeHolder("john@example.com")
								.build(),
						NodeParameter.builder().name("emailType").displayName("Email Type")
								.type(ParameterType.OPTIONS).defaultValue("home")
								.options(List.of(
										ParameterOption.builder().name("Home").value("home").build(),
										ParameterOption.builder().name("Work").value("work").build(),
										ParameterOption.builder().name("Other").value("other").build()
								)).build(),
						NodeParameter.builder().name("phone").displayName("Phone Number")
								.type(ParameterType.STRING)
								.description("Phone number of the contact.")
								.build(),
						NodeParameter.builder().name("phoneType").displayName("Phone Type")
								.type(ParameterType.OPTIONS).defaultValue("mobile")
								.options(List.of(
										ParameterOption.builder().name("Mobile").value("mobile").build(),
										ParameterOption.builder().name("Home").value("home").build(),
										ParameterOption.builder().name("Work").value("work").build()
								)).build(),
						NodeParameter.builder().name("company").displayName("Company")
								.type(ParameterType.STRING)
								.description("Company name.")
								.build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title")
								.type(ParameterType.STRING)
								.description("Job title.")
								.build(),
						NodeParameter.builder().name("notes").displayName("Notes")
								.type(ParameterType.STRING)
								.typeOptions(Map.of("rows", 4))
								.description("Additional notes about the contact.")
								.build()
				)).build());

		// Contact > Get / Delete / Update: resourceName
		params.add(NodeParameter.builder()
				.name("resourceName").displayName("Resource Name")
				.type(ParameterType.STRING).required(true)
				.description("The resource name of the contact (e.g., people/c123456).")
				.placeHolder("people/c123456")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("get", "delete", "update"))))
				.build());

		// Contact > Get: personFields
		params.add(NodeParameter.builder()
				.name("personFields").displayName("Person Fields")
				.type(ParameterType.STRING).defaultValue("names,emailAddresses,phoneNumbers,organizations")
				.description("Comma-separated list of person fields to return.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("get"))))
				.build());

		// Contact > Update: update fields
		params.add(NodeParameter.builder()
				.name("updateFields").displayName("Update Fields")
				.type(ParameterType.COLLECTION)
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("update"))))
				.nestedParameters(List.of(
						NodeParameter.builder().name("givenName").displayName("First Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("familyName").displayName("Last Name")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("email").displayName("Email")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("phone").displayName("Phone Number")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("company").displayName("Company")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("jobTitle").displayName("Job Title")
								.type(ParameterType.STRING).build(),
						NodeParameter.builder().name("notes").displayName("Notes")
								.type(ParameterType.STRING).typeOptions(Map.of("rows", 4)).build()
				)).build());

		// Contact > GetAll: limit
		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Maximum number of contacts to return.")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"), "operation", List.of("getAll"))))
				.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (operation) {
				case "create" -> executeCreate(context, credentials);
				case "delete" -> executeDelete(context, credentials);
				case "get" -> executeGet(context, credentials);
				case "getAll" -> executeGetAll(context, credentials);
				case "update" -> executeUpdate(context, credentials);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "Google Contacts error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeCreate(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String givenName = context.getParameter("givenName", "");
		String familyName = context.getParameter("familyName", "");
		Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());

		Map<String, Object> body = new LinkedHashMap<>();
		Map<String, Object> name = new LinkedHashMap<>();
		name.put("givenName", givenName);
		if (familyName != null && !familyName.isEmpty()) {
			name.put("familyName", familyName);
		}
		body.put("names", List.of(name));

		if (additionalFields.get("email") != null) {
			String emailType = additionalFields.get("emailType") != null
					? (String) additionalFields.get("emailType") : "home";
			body.put("emailAddresses", List.of(Map.of(
					"value", additionalFields.get("email"),
					"type", emailType
			)));
		}

		if (additionalFields.get("phone") != null) {
			String phoneType = additionalFields.get("phoneType") != null
					? (String) additionalFields.get("phoneType") : "mobile";
			body.put("phoneNumbers", List.of(Map.of(
					"value", additionalFields.get("phone"),
					"type", phoneType
			)));
		}

		if (additionalFields.get("company") != null || additionalFields.get("jobTitle") != null) {
			Map<String, Object> org = new LinkedHashMap<>();
			if (additionalFields.get("company") != null) {
				org.put("name", additionalFields.get("company"));
			}
			if (additionalFields.get("jobTitle") != null) {
				org.put("title", additionalFields.get("jobTitle"));
			}
			body.put("organizations", List.of(org));
		}

		if (additionalFields.get("notes") != null) {
			body.put("biographies", List.of(Map.of(
					"value", additionalFields.get("notes"),
					"contentType", "TEXT_PLAIN"
			)));
		}

		HttpResponse<String> response = post(BASE_URL + "/people:createContact", body, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeDelete(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String resourceName = context.getParameter("resourceName", "");
		HttpResponse<String> response = delete(BASE_URL + "/" + resourceName + ":deleteContact", headers);
		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "deleted", resourceName))));
		}
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeGet(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String resourceName = context.getParameter("resourceName", "");
		String personFields = context.getParameter("personFields", "names,emailAddresses,phoneNumbers,organizations");

		String url = BASE_URL + "/" + resourceName + "?personFields=" + encode(personFields);
		HttpResponse<String> response = get(url, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeGetAll(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		int limit = toInt(context.getParameter("limit", 100), 100);
		String url = BASE_URL + "/people/me/connections?pageSize=" + limit
				+ "&personFields=names,emailAddresses,phoneNumbers,organizations";

		HttpResponse<String> response = get(url, headers);
		Map<String, Object> result = parseResponse(response);

		Object connections = result.get("connections");
		if (connections instanceof List) {
			List<Map<String, Object>> contactItems = new ArrayList<>();
			for (Object conn : (List<?>) connections) {
				if (conn instanceof Map) {
					contactItems.add(wrapInJson(conn));
				}
			}
			return contactItems.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(contactItems);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeUpdate(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		Map<String, String> headers = getAuthHeaders(accessToken);

		String resourceName = context.getParameter("resourceName", "");
		Map<String, Object> updateFields = context.getParameter("updateFields", Map.of());

		// First, get the existing contact to obtain the etag
		String getUrl = BASE_URL + "/" + resourceName + "?personFields=names,emailAddresses,phoneNumbers,organizations,biographies";
		HttpResponse<String> getResponse = get(getUrl, headers);
		Map<String, Object> existing = parseResponse(getResponse);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("etag", existing.get("etag"));

		List<String> updatePersonFields = new ArrayList<>();

		if (updateFields.get("givenName") != null || updateFields.get("familyName") != null) {
			Map<String, Object> name = new LinkedHashMap<>();
			if (updateFields.get("givenName") != null) {
				name.put("givenName", updateFields.get("givenName"));
			}
			if (updateFields.get("familyName") != null) {
				name.put("familyName", updateFields.get("familyName"));
			}
			body.put("names", List.of(name));
			updatePersonFields.add("names");
		}

		if (updateFields.get("email") != null) {
			body.put("emailAddresses", List.of(Map.of("value", updateFields.get("email"))));
			updatePersonFields.add("emailAddresses");
		}

		if (updateFields.get("phone") != null) {
			body.put("phoneNumbers", List.of(Map.of("value", updateFields.get("phone"))));
			updatePersonFields.add("phoneNumbers");
		}

		if (updateFields.get("company") != null || updateFields.get("jobTitle") != null) {
			Map<String, Object> org = new LinkedHashMap<>();
			if (updateFields.get("company") != null) {
				org.put("name", updateFields.get("company"));
			}
			if (updateFields.get("jobTitle") != null) {
				org.put("title", updateFields.get("jobTitle"));
			}
			body.put("organizations", List.of(org));
			updatePersonFields.add("organizations");
		}

		if (updateFields.get("notes") != null) {
			body.put("biographies", List.of(Map.of("value", updateFields.get("notes"), "contentType", "TEXT_PLAIN")));
			updatePersonFields.add("biographies");
		}

		String url = BASE_URL + "/" + resourceName + ":updateContact?updatePersonFields=" + String.join(",", updatePersonFields);
		HttpResponse<String> response = patch(url, body, headers);
		Map<String, Object> result = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}
}
