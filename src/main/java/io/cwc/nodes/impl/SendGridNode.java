package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * SendGrid — manage contacts, lists, and send emails using the SendGrid API.
 */
@Node(
		type = "sendGrid",
		displayName = "SendGrid",
		description = "Consume SendGrid API",
		category = "Communication",
		icon = "sendGrid",
		credentials = {"sendGridApi"}
)
public class SendGridNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.sendgrid.com/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "list");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> handleContact(context, headers, operation);
					case "list" -> handleList(context, headers, operation);
					case "mail" -> handleMail(context, headers, operation);
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

	private Map<String, Object> handleContact(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				String query = context.getParameter("query", "");
				if (!query.isEmpty()) {
					Map<String, Object> body = Map.of("query", query);
					HttpResponse<String> response = post(BASE_URL + "/marketing/contacts/search", body, headers);
					yield parseResponse(response);
				} else {
					HttpResponse<String> response = get(BASE_URL + "/marketing/contacts", headers);
					yield parseResponse(response);
				}
			}
			case "get" -> {
				String by = context.getParameter("by", "id");
				if ("id".equals(by)) {
					String contactId = context.getParameter("contactId", "");
					HttpResponse<String> response = get(BASE_URL + "/marketing/contacts/" + encode(contactId), headers);
					yield parseResponse(response);
				} else {
					String email = context.getParameter("email", "");
					Map<String, Object> body = Map.of("query", "email LIKE '" + email + "' ");
					HttpResponse<String> response = post(BASE_URL + "/marketing/contacts/search", body, headers);
					Map<String, Object> data = parseResponse(response);
					@SuppressWarnings("unchecked")
					List<Object> resultList = (List<Object>) data.get("result");
					if (resultList != null && !resultList.isEmpty()) {
						@SuppressWarnings("unchecked")
						Map<String, Object> first = (Map<String, Object>) resultList.get(0);
						yield first;
					}
					yield data;
				}
			}
			case "upsert" -> {
				String email = context.getParameter("email", "");
				Map<String, Object> contact = new LinkedHashMap<>();
				contact.put("email", email);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) contact.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) contact.put("last_name", lastName);
				String city = context.getParameter("city", "");
				if (!city.isEmpty()) contact.put("city", city);
				String country = context.getParameter("country", "");
				if (!country.isEmpty()) contact.put("country", country);
				String postalCode = context.getParameter("postalCode", "");
				if (!postalCode.isEmpty()) contact.put("postal_code", postalCode);
				String stateProvinceRegion = context.getParameter("stateProvinceRegion", "");
				if (!stateProvinceRegion.isEmpty()) contact.put("state_province_region", stateProvinceRegion);
				String alternateEmails = context.getParameter("alternateEmails", "");
				if (!alternateEmails.isEmpty()) {
					contact.put("alternate_emails", Arrays.asList(alternateEmails.split("\\s*,\\s*")));
				}
				String addressLine1 = context.getParameter("addressLine1", "");
				if (!addressLine1.isEmpty()) contact.put("address_line_1", addressLine1);
				String addressLine2 = context.getParameter("addressLine2", "");
				if (!addressLine2.isEmpty()) contact.put("address_line_2", addressLine2);

				Map<String, Object> body = new LinkedHashMap<>();
				String listIds = context.getParameter("listIds", "");
				if (!listIds.isEmpty()) {
					body.put("list_ids", Arrays.asList(listIds.split("\\s*,\\s*")));
				}
				body.put("contacts", List.of(contact));

				HttpResponse<String> response = put(BASE_URL + "/marketing/contacts", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String ids = context.getParameter("ids", "").replaceAll("\\s", "");
				boolean deleteAll = toBoolean(context.getParameters().get("deleteAll"), false);
				StringBuilder url = new StringBuilder(BASE_URL + "/marketing/contacts");
				if (deleteAll) {
					url.append("?delete_all_contacts=true");
				} else if (!ids.isEmpty()) {
					url.append("?ids=").append(encode(ids));
				}
				HttpResponse<String> response = delete(url.toString(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact operation: " + operation);
		};
	}

	private Map<String, Object> handleList(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/marketing/lists", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String listId = context.getParameter("listId", "");
				boolean contactSample = toBoolean(context.getParameters().get("contactSample"), false);
				String url = BASE_URL + "/marketing/lists/" + encode(listId);
				if (contactSample) url += "?contact_sample=true";
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "create" -> {
				String name = context.getParameter("name", "");
				HttpResponse<String> response = post(BASE_URL + "/marketing/lists", Map.of("name", name), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String listId = context.getParameter("listId", "");
				boolean deleteContacts = toBoolean(context.getParameters().get("deleteContacts"), false);
				String url = BASE_URL + "/marketing/lists/" + encode(listId);
				if (deleteContacts) url += "?delete_contacts=true";
				HttpResponse<String> response = delete(url, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			case "update" -> {
				String listId = context.getParameter("listId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = patch(BASE_URL + "/marketing/lists/" + encode(listId), Map.of("name", name), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown list operation: " + operation);
		};
	}

	private Map<String, Object> handleMail(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "send" -> {
				String toEmail = context.getParameter("toEmail", "");
				String fromEmail = context.getParameter("fromEmail", "");
				String fromName = context.getParameter("fromName", "");

				List<Map<String, String>> toList = new ArrayList<>();
				for (String email : toEmail.split("\\s*,\\s*")) {
					toList.add(Map.of("email", email.trim()));
				}

				Map<String, Object> personalization = new LinkedHashMap<>();
				personalization.put("to", toList);

				boolean dynamicTemplate = toBoolean(context.getParameters().get("dynamicTemplate"), false);

				Map<String, Object> body = new LinkedHashMap<>();

				if (dynamicTemplate) {
					String templateId = context.getParameter("templateId", "");
					body.put("template_id", templateId);
				} else {
					String subject = context.getParameter("subject", "");
					personalization.put("subject", subject);

					String contentType = context.getParameter("contentType", "text/plain");
					String contentValue = context.getParameter("contentValue", "");
					body.put("content", List.of(Map.of("type", contentType, "value", contentValue)));
				}

				String ccEmail = context.getParameter("ccEmail", "");
				if (!ccEmail.isEmpty()) {
					List<Map<String, String>> ccList = new ArrayList<>();
					for (String email : ccEmail.split("\\s*,\\s*")) {
						ccList.add(Map.of("email", email.trim()));
					}
					personalization.put("cc", ccList);
				}

				String bccEmail = context.getParameter("bccEmail", "");
				if (!bccEmail.isEmpty()) {
					List<Map<String, String>> bccList = new ArrayList<>();
					for (String email : bccEmail.split("\\s*,\\s*")) {
						bccList.add(Map.of("email", email.trim()));
					}
					personalization.put("bcc", bccList);
				}

				body.put("personalizations", List.of(personalization));

				Map<String, String> from = new LinkedHashMap<>();
				from.put("email", fromEmail.trim());
				if (!fromName.isEmpty()) from.put("name", fromName);
				body.put("from", from);

				String replyToEmail = context.getParameter("replyToEmail", "");
				if (!replyToEmail.isEmpty()) {
					List<Map<String, String>> replyToList = new ArrayList<>();
					for (String email : replyToEmail.split("\\s*,\\s*")) {
						replyToList.add(Map.of("email", email.trim()));
					}
					body.put("reply_to_list", replyToList);
				}

				String categories = context.getParameter("categories", "");
				if (!categories.isEmpty()) {
					body.put("categories", Arrays.asList(categories.split("\\s*,\\s*")));
				}

				String ipPoolName = context.getParameter("ipPoolName", "");
				if (!ipPoolName.isEmpty()) body.put("ip_pool_name", ipPoolName);

				boolean enableSandbox = toBoolean(context.getParameters().get("enableSandbox"), false);
				body.put("mail_settings", Map.of("sandbox_mode", Map.of("enable", enableSandbox)));

				HttpResponse<String> response = post(BASE_URL + "/mail/send", body, headers);

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				String messageId = response.headers().firstValue("x-message-id").orElse("");
				if (!messageId.isEmpty()) result.put("messageId", messageId);
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown mail operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("list")
						.options(List.of(
								ParameterOption.builder().name("Contact").value("contact").build(),
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("Mail").value("mail").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Upsert").value("upsert").build()
						)).build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the list.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the list.").build(),
				NodeParameter.builder()
						.name("contactId").displayName("Contact ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the contact.").build(),
				NodeParameter.builder()
						.name("by").displayName("Get By")
						.type(ParameterType.OPTIONS).defaultValue("id")
						.options(List.of(
								ParameterOption.builder().name("ID").value("id").build(),
								ParameterOption.builder().name("Email").value("email").build()
						)).build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address of the contact.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's first name.").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's last name.").build(),
				NodeParameter.builder()
						.name("city").displayName("City")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's city.").build(),
				NodeParameter.builder()
						.name("country").displayName("Country")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's country.").build(),
				NodeParameter.builder()
						.name("postalCode").displayName("Postal Code")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's postal code.").build(),
				NodeParameter.builder()
						.name("stateProvinceRegion").displayName("State/Province/Region")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's state, province, or region.").build(),
				NodeParameter.builder()
						.name("alternateEmails").displayName("Alternate Emails")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated alternate email addresses.").build(),
				NodeParameter.builder()
						.name("addressLine1").displayName("Address Line 1")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's address line 1.").build(),
				NodeParameter.builder()
						.name("addressLine2").displayName("Address Line 2")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact's address line 2.").build(),
				NodeParameter.builder()
						.name("listIds").displayName("List IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list IDs to add the contact to.").build(),
				NodeParameter.builder()
						.name("ids").displayName("Contact IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated contact IDs to delete.").build(),
				NodeParameter.builder()
						.name("deleteAll").displayName("Delete All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Delete all contacts.").build(),
				NodeParameter.builder()
						.name("deleteContacts").displayName("Delete Contacts")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Also delete contacts in the list.").build(),
				NodeParameter.builder()
						.name("contactSample").displayName("Contact Sample")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Include a sample of contacts in the response.").build(),
				NodeParameter.builder()
						.name("query").displayName("Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("SGQL query to filter contacts.").build(),
				NodeParameter.builder()
						.name("toEmail").displayName("To Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated recipient email addresses.").build(),
				NodeParameter.builder()
						.name("fromEmail").displayName("From Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender email address.").build(),
				NodeParameter.builder()
						.name("fromName").displayName("From Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sender name.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email subject.").build(),
				NodeParameter.builder()
						.name("contentType").displayName("Content Type")
						.type(ParameterType.OPTIONS).defaultValue("text/plain")
						.options(List.of(
								ParameterOption.builder().name("Plain Text").value("text/plain").build(),
								ParameterOption.builder().name("HTML").value("text/html").build()
						)).build(),
				NodeParameter.builder()
						.name("contentValue").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email body content.").build(),
				NodeParameter.builder()
						.name("dynamicTemplate").displayName("Dynamic Template")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Use a dynamic template.").build(),
				NodeParameter.builder()
						.name("templateId").displayName("Template ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The dynamic template ID.").build(),
				NodeParameter.builder()
						.name("ccEmail").displayName("CC Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated CC email addresses.").build(),
				NodeParameter.builder()
						.name("bccEmail").displayName("BCC Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated BCC email addresses.").build(),
				NodeParameter.builder()
						.name("replyToEmail").displayName("Reply To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated reply-to email addresses.").build(),
				NodeParameter.builder()
						.name("categories").displayName("Categories")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated email categories.").build(),
				NodeParameter.builder()
						.name("ipPoolName").displayName("IP Pool Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the IP pool to send from.").build(),
				NodeParameter.builder()
						.name("enableSandbox").displayName("Sandbox Mode")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Enable sandbox mode for testing.").build()
		);
	}
}
