package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Agile CRM — manage contacts, companies, and deals in Agile CRM.
 */
@Node(
		type = "agileCrm",
		displayName = "Agile CRM",
		description = "Manage contacts and deals in Agile CRM",
		category = "CRM",
		icon = "agileCrm",
		credentials = {"agileCrmApi"}
)
public class AgileCrmNode extends AbstractApiNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
				.options(List.of(
						ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build(),
						ParameterOption.builder().name("Company").value("company").description("Manage companies").build(),
						ParameterOption.builder().name("Deal").value("deal").description("Manage deals").build()
				)).build());

		// Contact operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a contact").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a contact").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a contact").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all contacts").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a contact").build()
				)).build());

		// Company operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("company"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a company").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a company").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a company").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all companies").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a company").build()
				)).build());

		// Deal operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("deal"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Create a deal").build(),
						ParameterOption.builder().name("Update").value("update").description("Update a deal").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a deal").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all deals").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a deal").build()
				)).build());

		// Common parameters
		params.add(NodeParameter.builder()
				.name("contactId").displayName("Contact ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the contact.").build());

		params.add(NodeParameter.builder()
				.name("companyId").displayName("Company ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the company.").build());

		params.add(NodeParameter.builder()
				.name("dealId").displayName("Deal ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the deal.").build());

		params.add(NodeParameter.builder()
				.name("firstName").displayName("First Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("First name of the contact.").build());

		params.add(NodeParameter.builder()
				.name("lastName").displayName("Last Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Last name of the contact.").build());

		params.add(NodeParameter.builder()
				.name("email").displayName("Email")
				.type(ParameterType.STRING).defaultValue("")
				.description("Email address of the contact.").build());

		params.add(NodeParameter.builder()
				.name("company").displayName("Company Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Name of the company.").build());

		params.add(NodeParameter.builder()
				.name("dealName").displayName("Deal Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Name of the deal.").build());

		params.add(NodeParameter.builder()
				.name("dealValue").displayName("Deal Value")
				.type(ParameterType.NUMBER).defaultValue(0)
				.description("Value of the deal.").build());

		params.add(NodeParameter.builder()
				.name("dealMilestone").displayName("Milestone")
				.type(ParameterType.STRING).defaultValue("")
				.description("Milestone/stage of the deal.").build());

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
		String subdomain = context.getCredentialString("subdomain", "");
		String email = context.getCredentialString("email", "");
		String apiKey = context.getCredentialString("apiKey", "");
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		String baseUrl = "https://" + subdomain + ".agilecrm.com/dev/api";

		// Agile CRM uses Basic Auth with email:apiKey
		String auth = Base64.getEncoder().encodeToString((email + ":" + apiKey).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + auth);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> executeContact(context, operation, baseUrl, headers);
					case "company" -> executeCompany(context, operation, baseUrl, headers);
					case "deal" -> executeDeal(context, operation, baseUrl, headers);
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
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String email = context.getParameter("email", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				List<Map<String, Object>> properties = new ArrayList<>();
				if (!firstName.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "first_name", "value", firstName));
				}
				if (!lastName.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "last_name", "value", lastName));
				}
				if (!email.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "email", "subtype", "work", "value", email));
				}
				body.put("properties", properties);
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = post(baseUrl + "/contacts", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String contactId = context.getParameter("contactId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", contactId);
				List<Map<String, Object>> properties = new ArrayList<>();
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String email = context.getParameter("email", "");
				if (!firstName.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "first_name", "value", firstName));
				}
				if (!lastName.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "last_name", "value", lastName));
				}
				if (!email.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "email", "subtype", "work", "value", email));
				}
				if (!properties.isEmpty()) body.put("properties", properties);
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = put(baseUrl + "/contacts/edit-properties", body, headers);
				return parseResponse(response);
			}
			case "get": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(baseUrl + "/contacts/" + encode(contactId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = baseUrl + "/contacts?page_size=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "delete": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(baseUrl + "/contacts/" + encode(contactId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("contactId", contactId);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown contact operation: " + operation);
		}
	}

	private Map<String, Object> executeCompany(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String companyName = context.getParameter("company", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("type", "COMPANY");
				List<Map<String, Object>> properties = new ArrayList<>();
				if (!companyName.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "name", "value", companyName));
				}
				body.put("properties", properties);
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = post(baseUrl + "/contacts", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String companyId = context.getParameter("companyId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", companyId);
				body.put("type", "COMPANY");
				String companyName = context.getParameter("company", "");
				List<Map<String, Object>> properties = new ArrayList<>();
				if (!companyName.isEmpty()) {
					properties.add(Map.of("type", "SYSTEM", "name", "name", "value", companyName));
				}
				if (!properties.isEmpty()) body.put("properties", properties);
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = put(baseUrl + "/contacts/edit-properties", body, headers);
				return parseResponse(response);
			}
			case "get": {
				String companyId = context.getParameter("companyId", "");
				HttpResponse<String> response = get(baseUrl + "/contacts/" + encode(companyId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = baseUrl + "/contacts/companies/list?page_size=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = post(url, Map.of(), headers);
				return parseResponse(response);
			}
			case "delete": {
				String companyId = context.getParameter("companyId", "");
				HttpResponse<String> response = delete(baseUrl + "/contacts/" + encode(companyId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("companyId", companyId);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown company operation: " + operation);
		}
	}

	private Map<String, Object> executeDeal(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String dealName = context.getParameter("dealName", "");
				double dealValue = toDouble(context.getParameters().get("dealValue"), 0);
				String milestone = context.getParameter("dealMilestone", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", dealName);
				body.put("expected_value", dealValue);
				if (!milestone.isEmpty()) body.put("milestone", milestone);
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = post(baseUrl + "/opportunity", body, headers);
				return parseResponse(response);
			}
			case "update": {
				String dealId = context.getParameter("dealId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", dealId);
				String dealName = context.getParameter("dealName", "");
				String milestone = context.getParameter("dealMilestone", "");
				if (!dealName.isEmpty()) body.put("name", dealName);
				if (!milestone.isEmpty()) body.put("milestone", milestone);
				double dealValue = toDouble(context.getParameters().get("dealValue"), 0);
				if (dealValue > 0) body.put("expected_value", dealValue);
				body.putAll(parseJson(additionalJson));

				HttpResponse<String> response = put(baseUrl + "/opportunity/partial-update", body, headers);
				return parseResponse(response);
			}
			case "get": {
				String dealId = context.getParameter("dealId", "");
				HttpResponse<String> response = get(baseUrl + "/opportunity/" + encode(dealId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = baseUrl + "/opportunity?page_size=" + (returnAll ? 100 : Math.min(limit, 100));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "delete": {
				String dealId = context.getParameter("dealId", "");
				HttpResponse<String> response = delete(baseUrl + "/opportunity/" + encode(dealId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("dealId", dealId);
				return result;
			}
			default:
				throw new IllegalArgumentException("Unknown deal operation: " + operation);
		}
	}
}
