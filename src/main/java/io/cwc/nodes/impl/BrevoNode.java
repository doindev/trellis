package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Brevo (formerly Sendinblue) — manage contacts and send transactional emails
 * via the Brevo API v3.
 */
@Node(
		type = "brevo",
		displayName = "Brevo",
		description = "Manage contacts and send emails via the Brevo API",
		category = "Communication / Email",
		icon = "brevo",
		credentials = {"brevoApi"}
)
public class BrevoNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.brevo.com/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("api-key", apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> handleContact(context, operation, headers);
					case "email" -> handleEmail(context, operation, headers);
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
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String email = context.getParameter("email", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String listIds = context.getParameter("listIds", "");
				String attributesJson = context.getParameter("attributes", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", email);

				Map<String, Object> attributes = new LinkedHashMap<>();
				if (!firstName.isEmpty()) attributes.put("FIRSTNAME", firstName);
				if (!lastName.isEmpty()) attributes.put("LASTNAME", lastName);

				Map<String, Object> extraAttrs = parseJson(attributesJson);
				attributes.putAll(extraAttrs);

				if (!attributes.isEmpty()) body.put("attributes", attributes);

				if (!listIds.isEmpty()) {
					List<Long> ids = new ArrayList<>();
					for (String id : listIds.split("\\s*,\\s*")) {
						ids.add(Long.parseLong(id.trim()));
					}
					body.put("listIds", ids);
				}

				HttpResponse<String> response = post(BASE_URL + "/contacts", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String identifier = context.getParameter("identifier", "");
				HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(identifier), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			case "get" -> {
				String identifier = context.getParameter("identifier", "");
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(identifier), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameter("limit", 50), 50);
				int offset = toInt(context.getParameter("offset", 0), 0);
				String url = BASE_URL + "/contacts?limit=" + limit + "&offset=" + offset;
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String identifier = context.getParameter("identifier", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				String listIds = context.getParameter("listIds", "");
				String unlinkListIds = context.getParameter("unlinkListIds", "");
				String attributesJson = context.getParameter("attributes", "{}");

				Map<String, Object> body = new LinkedHashMap<>();

				Map<String, Object> attributes = new LinkedHashMap<>();
				if (!firstName.isEmpty()) attributes.put("FIRSTNAME", firstName);
				if (!lastName.isEmpty()) attributes.put("LASTNAME", lastName);

				Map<String, Object> extraAttrs = parseJson(attributesJson);
				attributes.putAll(extraAttrs);

				if (!attributes.isEmpty()) body.put("attributes", attributes);

				if (!listIds.isEmpty()) {
					List<Long> ids = new ArrayList<>();
					for (String id : listIds.split("\\s*,\\s*")) {
						ids.add(Long.parseLong(id.trim()));
					}
					body.put("listIds", ids);
				}

				if (!unlinkListIds.isEmpty()) {
					List<Long> ids = new ArrayList<>();
					for (String id : unlinkListIds.split("\\s*,\\s*")) {
						ids.add(Long.parseLong(id.trim()));
					}
					body.put("unlinkListIds", ids);
				}

				HttpResponse<String> response = put(BASE_URL + "/contacts/" + encode(identifier), body, headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", true);
				result.put("statusCode", response.statusCode());
				yield result;
			}
			default -> throw new IllegalArgumentException("Unknown contact operation: " + operation);
		};
	}

	// ========================= Email =========================

	private Map<String, Object> handleEmail(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "send" -> {
				String senderName = context.getParameter("senderName", "");
				String senderEmail = context.getParameter("senderEmail", "");
				String toEmail = context.getParameter("toEmail", "");
				String toName = context.getParameter("toName", "");
				String subject = context.getParameter("subject", "");
				String htmlContent = context.getParameter("htmlContent", "");
				String textContent = context.getParameter("textContent", "");
				String ccEmail = context.getParameter("ccEmail", "");
				String bccEmail = context.getParameter("bccEmail", "");
				String replyToEmail = context.getParameter("replyToEmail", "");
				String tags = context.getParameter("tags", "");

				Map<String, Object> body = new LinkedHashMap<>();

				Map<String, String> sender = new LinkedHashMap<>();
				if (!senderName.isEmpty()) sender.put("name", senderName);
				sender.put("email", senderEmail);
				body.put("sender", sender);

				List<Map<String, String>> toList = new ArrayList<>();
				for (String email : toEmail.split("\\s*,\\s*")) {
					Map<String, String> to = new LinkedHashMap<>();
					to.put("email", email.trim());
					if (!toName.isEmpty()) to.put("name", toName);
					toList.add(to);
				}
				body.put("to", toList);

				body.put("subject", subject);
				if (!htmlContent.isEmpty()) body.put("htmlContent", htmlContent);
				if (!textContent.isEmpty()) body.put("textContent", textContent);

				if (!ccEmail.isEmpty()) {
					List<Map<String, String>> ccList = new ArrayList<>();
					for (String email : ccEmail.split("\\s*,\\s*")) {
						ccList.add(Map.of("email", email.trim()));
					}
					body.put("cc", ccList);
				}

				if (!bccEmail.isEmpty()) {
					List<Map<String, String>> bccList = new ArrayList<>();
					for (String email : bccEmail.split("\\s*,\\s*")) {
						bccList.add(Map.of("email", email.trim()));
					}
					body.put("bcc", bccList);
				}

				if (!replyToEmail.isEmpty()) {
					body.put("replyTo", Map.of("email", replyToEmail));
				}

				if (!tags.isEmpty()) {
					body.put("tags", Arrays.asList(tags.split("\\s*,\\s*")));
				}

				HttpResponse<String> response = post(BASE_URL + "/smtp/email", body, headers);
				yield parseResponse(response);
			}
			case "sendTemplate" -> {
				int templateId = toInt(context.getParameters().get("templateId"), 0);
				String toEmail = context.getParameter("toEmail", "");
				String toName = context.getParameter("toName", "");
				String paramsJson = context.getParameter("templateParams", "{}");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("templateId", templateId);

				List<Map<String, String>> toList = new ArrayList<>();
				for (String email : toEmail.split("\\s*,\\s*")) {
					Map<String, String> to = new LinkedHashMap<>();
					to.put("email", email.trim());
					if (!toName.isEmpty()) to.put("name", toName);
					toList.add(to);
				}
				body.put("to", toList);

				Map<String, Object> params = parseJson(paramsJson);
				if (!params.isEmpty()) body.put("params", params);

				HttpResponse<String> response = post(BASE_URL + "/smtp/email", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown email operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("contact")
						.options(List.of(
								ParameterOption.builder().name("Contact").value("contact").build(),
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
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Send Template").value("sendTemplate").build()
						)).build(),
				NodeParameter.builder()
						.name("identifier").displayName("Contact Identifier")
						.type(ParameterType.STRING).defaultValue("")
						.description("Contact ID or email address.").build(),
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
						.name("listIds").displayName("List IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list IDs to add the contact to.").build(),
				NodeParameter.builder()
						.name("unlinkListIds").displayName("Unlink List IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list IDs to remove the contact from.").build(),
				NodeParameter.builder()
						.name("attributes").displayName("Attributes (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Additional contact attributes as a JSON object.").build(),
				NodeParameter.builder()
						.name("senderName").displayName("Sender Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the email sender.").build(),
				NodeParameter.builder()
						.name("senderEmail").displayName("Sender Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address of the sender.").build(),
				NodeParameter.builder()
						.name("toEmail").displayName("To Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated recipient email addresses.").build(),
				NodeParameter.builder()
						.name("toName").displayName("To Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the recipient.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email subject line.").build(),
				NodeParameter.builder()
						.name("htmlContent").displayName("HTML Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("HTML body of the email.").build(),
				NodeParameter.builder()
						.name("textContent").displayName("Text Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("Plain text body of the email.").build(),
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
						.description("Reply-to email address.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated tags for the email.").build(),
				NodeParameter.builder()
						.name("templateId").displayName("Template ID")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("The ID of the Brevo email template.").build(),
				NodeParameter.builder()
						.name("templateParams").displayName("Template Parameters (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JSON object of template parameters.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Maximum number of contacts to return.").build(),
				NodeParameter.builder()
						.name("offset").displayName("Offset")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Index of the first contact to return.").build()
		);
	}
}
