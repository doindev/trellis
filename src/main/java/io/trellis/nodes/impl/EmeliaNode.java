package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Emelia — manage campaigns and contact lists using the Emelia GraphQL API.
 */
@Node(
		type = "emelia",
		displayName = "Emelia",
		description = "Manage campaigns and contact lists with Emelia",
		category = "Marketing",
		icon = "emelia",
		credentials = {"emeliaApi"},
		searchOnly = true
)
public class EmeliaNode extends AbstractApiNode {

	private static final String GRAPHQL_URL = "https://graphql.emelia.io/graphql";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "campaign");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "campaign" -> handleCampaign(context, headers, operation);
					case "contactList" -> handleContactList(context, headers, operation);
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

	private Map<String, Object> handleCampaign(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "addContact" -> {
				String campaignId = context.getParameter("campaignId", "");
				String contactEmail = context.getParameter("contactEmail", "");
				Map<String, Object> variables = new LinkedHashMap<>();
				variables.put("campaignId", campaignId);
				variables.put("contactEmail", contactEmail);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) variables.put("firstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) variables.put("lastName", lastName);
				String phoneNumber = context.getParameter("phoneNumber", "");
				if (!phoneNumber.isEmpty()) variables.put("phoneNumber", phoneNumber);
				String customFields = context.getParameter("customFields", "");
				if (!customFields.isEmpty()) variables.put("customFields", parseJson(customFields));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "mutation AddContactToCampaignHook($campaignId: ID!, $contactEmail: String!, $firstName: String, $lastName: String, $phoneNumber: String, $customFields: JSON) { addContactToCampaignHook(campaignId: $campaignId, contactEmail: $contactEmail, firstName: $firstName, lastName: $lastName, phoneNumber: $phoneNumber, customFields: $customFields) }");
				body.put("variables", variables);
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			case "create" -> {
				String campaignName = context.getParameter("campaignName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "mutation createCampaign($campaignName: String!) { createCampaign(name: $campaignName) { _id name } }");
				body.put("variables", Map.of("campaignName", campaignName));
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String campaignId = context.getParameter("campaignId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "query campaign($campaignId: ID!) { campaign(campaignId: $campaignId) { _id name status stats { sent opened replied bounced clicked unsubscribed } } }");
				body.put("variables", Map.of("campaignId", campaignId));
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "query all_campaigns { all_campaigns { _id name status stats { sent opened replied bounced clicked unsubscribed } } }");
				body.put("variables", Map.of());
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			case "pause" -> {
				String campaignId = context.getParameter("campaignId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "mutation pauseCampaign($campaignId: ID!) { pauseCampaign(campaignId: $campaignId) }");
				body.put("variables", Map.of("campaignId", campaignId));
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			case "start" -> {
				String campaignId = context.getParameter("campaignId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "mutation startCampaign($campaignId: ID!) { startCampaign(campaignId: $campaignId) }");
				body.put("variables", Map.of("campaignId", campaignId));
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			case "duplicate" -> {
				String campaignId = context.getParameter("campaignId", "");
				String campaignName = context.getParameter("campaignName", "");
				Map<String, Object> variables = new LinkedHashMap<>();
				variables.put("campaignId", campaignId);
				variables.put("campaignName", campaignName);
				variables.put("copySettings", toBoolean(context.getParameters().get("copySettings"), true));
				variables.put("copyMails", toBoolean(context.getParameters().get("copyMails"), true));
				variables.put("copyContacts", toBoolean(context.getParameters().get("copyContacts"), false));
				variables.put("copyProvider", toBoolean(context.getParameters().get("copyProvider"), true));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "mutation duplicateCampaign($campaignId: ID!, $campaignName: String!, $copySettings: Boolean, $copyMails: Boolean, $copyContacts: Boolean, $copyProvider: Boolean) { duplicateCampaign(campaignId: $campaignId, name: $campaignName, copySettings: $copySettings, copyMails: $copyMails, copyContacts: $copyContacts, copyProvider: $copyProvider) { _id name } }");
				body.put("variables", variables);
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown campaign operation: " + operation);
		};
	}

	private Map<String, Object> handleContactList(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "add" -> {
				String contactListId = context.getParameter("contactListId", "");
				String contactEmail = context.getParameter("contactEmail", "");
				Map<String, Object> variables = new LinkedHashMap<>();
				variables.put("contactListId", contactListId);
				variables.put("contactEmail", contactEmail);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) variables.put("firstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) variables.put("lastName", lastName);
				String phoneNumber = context.getParameter("phoneNumber", "");
				if (!phoneNumber.isEmpty()) variables.put("phoneNumber", phoneNumber);
				String customFields = context.getParameter("customFields", "");
				if (!customFields.isEmpty()) variables.put("customFields", parseJson(customFields));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "mutation AddContactsToListHook($contactListId: ID!, $contactEmail: String!, $firstName: String, $lastName: String, $phoneNumber: String, $customFields: JSON) { addContactsToListHook(contactListId: $contactListId, contactEmail: $contactEmail, firstName: $firstName, lastName: $lastName, phoneNumber: $phoneNumber, customFields: $customFields) }");
				body.put("variables", variables);
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("query", "query contact_lists { contact_lists { _id name contactCount } }");
				body.put("variables", Map.of());
				HttpResponse<String> response = post(GRAPHQL_URL, body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact list operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("campaign")
						.options(List.of(
								ParameterOption.builder().name("Campaign").value("campaign").build(),
								ParameterOption.builder().name("Contact List").value("contactList").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add Contact").value("addContact").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Duplicate").value("duplicate").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Pause").value("pause").build(),
								ParameterOption.builder().name("Start").value("start").build(),
								ParameterOption.builder().name("Add").value("add").build()
						)).build(),
				NodeParameter.builder()
						.name("campaignId").displayName("Campaign ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("campaignName").displayName("Campaign Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("contactListId").displayName("Contact List ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("contactEmail").displayName("Contact Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phoneNumber").displayName("Phone Number")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("customFields").displayName("Custom Fields")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Custom fields as JSON object.").build(),
				NodeParameter.builder()
						.name("copySettings").displayName("Copy Settings")
						.type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder()
						.name("copyMails").displayName("Copy Mails")
						.type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder()
						.name("copyContacts").displayName("Copy Contacts")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("copyProvider").displayName("Copy Provider")
						.type(ParameterType.BOOLEAN).defaultValue(true).build()
		);
	}
}
