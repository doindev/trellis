package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Mautic — manage contacts, campaigns, and send emails via the Mautic REST API.
 * Base URL is derived from the credentials (self-hosted Mautic instance).
 */
@Node(
		type = "mautic",
		displayName = "Mautic",
		description = "Manage contacts, campaigns, and emails in Mautic",
		category = "Marketing",
		icon = "mautic",
		credentials = {"mauticApi"}
)
public class MauticNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("baseUrl", "").replaceAll("/$", "");
		String username = context.getCredentialString("username", "");
		String password = context.getCredentialString("password", "");
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Content-Type", "application/json");

		String apiUrl = baseUrl + "/api";

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> handleContact(context, operation, apiUrl, headers);
					case "campaign" -> handleCampaign(context, operation, apiUrl, headers);
					case "email" -> handleEmail(context, operation, apiUrl, headers);
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

	// ========================= Contact =========================

	private Map<String, Object> handleContact(NodeExecutionContext context, String operation,
			String apiUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String email = context.getParameter("email", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String company = context.getParameter("company", "");
				String phone = context.getParameter("phone", "");
				String additionalFieldsJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!email.isEmpty()) body.put("email", email);
				if (!firstName.isEmpty()) body.put("firstname", firstName);
				if (!lastName.isEmpty()) body.put("lastname", lastName);
				if (!company.isEmpty()) body.put("company", company);
				if (!phone.isEmpty()) body.put("phone", phone);

				Map<String, Object> additionalFields = parseJson(additionalFieldsJson);
				body.putAll(additionalFields);

				HttpResponse<String> response = post(apiUrl + "/contacts/new", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(apiUrl + "/contacts/" + encode(contactId) + "/delete", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(apiUrl + "/contacts/" + encode(contactId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 30), 30);
				int start = toInt(context.getParameter("start", 0), 0);
				String search = context.getParameter("search", "");
				String orderBy = context.getParameter("orderBy", "");
				String orderByDir = context.getParameter("orderByDir", "asc");

				String url = apiUrl + "/contacts?limit=" + limit + "&start=" + start;
				if (!search.isEmpty()) url += "&search=" + encode(search);
				if (!orderBy.isEmpty()) url += "&orderBy=" + encode(orderBy) + "&orderByDir=" + encode(orderByDir);

				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String contactId = context.getParameter("contactId", "");
				String email = context.getParameter("email", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String company = context.getParameter("company", "");
				String phone = context.getParameter("phone", "");
				String additionalFieldsJson = context.getParameter("additionalFields", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				if (!email.isEmpty()) body.put("email", email);
				if (!firstName.isEmpty()) body.put("firstname", firstName);
				if (!lastName.isEmpty()) body.put("lastname", lastName);
				if (!company.isEmpty()) body.put("company", company);
				if (!phone.isEmpty()) body.put("phone", phone);

				Map<String, Object> additionalFields = parseJson(additionalFieldsJson);
				body.putAll(additionalFields);

				HttpResponse<String> response = patch(apiUrl + "/contacts/" + encode(contactId) + "/edit", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact operation: " + operation);
		};
	}

	// ========================= Campaign =========================

	private Map<String, Object> handleCampaign(NodeExecutionContext context, String operation,
			String apiUrl, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "get" -> {
				String campaignId = context.getParameter("campaignId", "");
				HttpResponse<String> response = get(apiUrl + "/campaigns/" + encode(campaignId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 30), 30);
				int start = toInt(context.getParameter("start", 0), 0);
				String search = context.getParameter("search", "");

				String url = apiUrl + "/campaigns?limit=" + limit + "&start=" + start;
				if (!search.isEmpty()) url += "&search=" + encode(search);

				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown campaign operation: " + operation);
		};
	}

	// ========================= Email =========================

	private Map<String, Object> handleEmail(NodeExecutionContext context, String operation,
			String apiUrl, Map<String, String> headers) throws Exception {
		if ("send".equals(operation)) {
			String emailId = context.getParameter("emailId", "");
			String contactId = context.getParameter("contactId", "");

			Map<String, Object> body = new LinkedHashMap<>();
			// Mautic sends email to a specific contact
			HttpResponse<String> response = post(
					apiUrl + "/emails/" + encode(emailId) + "/contact/" + encode(contactId) + "/send",
					body, headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown email operation: " + operation);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("contact")
						.options(List.of(
								ParameterOption.builder().name("Contact").value("contact").build(),
								ParameterOption.builder().name("Campaign").value("campaign").build(),
								ParameterOption.builder().name("Email").value("email").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Send").value("send").build()
						)).build(),
				NodeParameter.builder()
						.name("contactId").displayName("Contact ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the contact.").build(),
				NodeParameter.builder()
						.name("campaignId").displayName("Campaign ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the campaign.").build(),
				NodeParameter.builder()
						.name("emailId").displayName("Email ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the email to send.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's email address.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's first name.").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's last name.").build(),
				NodeParameter.builder()
						.name("company").displayName("Company")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's company name.").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's phone number.").build(),
				NodeParameter.builder()
						.name("additionalFields").displayName("Additional Fields (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Additional contact fields as a JSON object.").build(),
				NodeParameter.builder()
						.name("search").displayName("Search")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search string to filter results.").build(),
				NodeParameter.builder()
						.name("orderBy").displayName("Order By")
						.type(ParameterType.STRING).defaultValue("")
						.description("Field to order results by.").build(),
				NodeParameter.builder()
						.name("orderByDir").displayName("Order Direction")
						.type(ParameterType.OPTIONS).defaultValue("asc")
						.options(List.of(
								ParameterOption.builder().name("Ascending").value("asc").build(),
								ParameterOption.builder().name("Descending").value("desc").build()
						)).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(30)
						.description("Maximum number of items to return.").build(),
				NodeParameter.builder()
						.name("start").displayName("Start")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Index of the first item to return.").build()
		);
	}
}
